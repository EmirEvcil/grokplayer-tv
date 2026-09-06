package com.grokplayer.tv.data

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.documentfile.provider.DocumentFile
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

class LibraryStore(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("library", Context.MODE_PRIVATE)

    var videos by mutableStateOf<List<LibraryVideo>>(emptyList())
        private set
    var recents by mutableStateOf<List<String>>(emptyList())
        private set
    var progress by mutableStateOf<Map<String, Long>>(emptyList<Pair<String, Long>>().toMap())
        private set
    var extraFolders by mutableStateOf(loadFolders())
        private set
    private var opened by mutableStateOf(loadOpened())
    private val durations = HashMap<String, Long>()
    private val indexFile = File(app.filesDir, "library_index.json")
    private val durationFile = File(app.filesDir, "duration_cache.json")
    private val scanLock = Mutex()

    init {
        loadDurations()
        videos = loadIndex()
        recents = prefs.getString("recents", "")
            .orEmpty()
            .split('|')
            .filter { it.isNotBlank() }
        progress = prefs.getString("progress", "")
            .orEmpty()
            .split('|')
            .mapNotNull {
                val parts = it.split('=')
                if (parts.size == 2) parts[0] to (parts[1].toLongOrNull() ?: return@mapNotNull null) else null
            }
            .toMap()
    }

    suspend fun refresh() {
        if (!scanLock.tryLock()) return
        try {
            videos = withContext(Dispatchers.IO) {
                val result = Perf.measure("scan") { scanAll() }
                persistIndex(result)
                persistDurations()
                result
            }
            android.util.Log.i("GrokPlayer", "perf library size=${videos.size} durations=${durations.size}")
        } finally {
            scanLock.unlock()
        }
    }

    fun addFolder(uri: Uri) {
        val encoded = uri.toString()
        if (encoded in extraFolders) return
        extraFolders = extraFolders + encoded
        prefs.edit().putStringSet("folders", extraFolders.toSet()).apply()
        runCatching {
            app.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    fun markPlayed(id: String, positionMs: Long) {
        progress = progress + (id to positionMs)
        recents = (listOf(id) + recents.filterNot { it == id }).take(24)
        persistState()
    }

    fun touch(video: LibraryVideo, positionMs: Long = 0L) {
        opened = (listOf(video) + opened.filterNot { sameOpened(it, video) }).take(24)
        recents = (listOf(video.id) + recents.filterNot { id ->
            id == video.id || resolveOpened(id)?.let { sameOpened(it, video) } == true
        }).take(24)
        if (positionMs > 0L || video.isLive || video.isStream) {
            progress = progress + (video.id to positionMs)
        }
        persistState()
        persistOpened()
    }

    fun forget(id: String) {
        recents = recents.filterNot { it == id }
        opened = opened.filterNot { it.id == id }
        persistState()
        persistOpened()
    }

    fun forgetPath(path: String) {
        val normalized = normalizePath(path)
        recents = recents.filterNot { id ->
            resolveOpened(id)?.path?.let { normalizePath(it) } == normalized
        }
        opened = opened.filterNot { it.path?.let { p -> normalizePath(p) } == normalized }
        persistState()
        persistOpened()
    }

    fun progressOf(id: String): Long = progress[id] ?: 0L

    fun progressFraction(video: LibraryVideo): Float? {
        val pos = progress[video.id] ?: return null
        if (video.durationMs <= 0L || pos <= 1_000L) return null
        return (pos.toFloat() / video.durationMs).coerceIn(0.04f, 0.96f)
    }

    fun recentVideos(): List<LibraryVideo> {
        val out = ArrayList<LibraryVideo>(10)
        for (id in recents) {
            val video = resolveOpened(id) ?: continue
            if (!video.isReachable()) continue
            if (out.any { sameOpened(it, video) }) continue
            out += video
            if (out.size == 10) break
        }
        return out
    }

    private fun resolveOpened(id: String): LibraryVideo? =
        videos.find { it.id == id } ?: opened.find { it.id == id }

    private fun LibraryVideo.isReachable(): Boolean {
        if (isStream || uri.scheme == "http" || uri.scheme == "https" || uri.scheme == "content") return true
        path?.let { return java.io.File(it).exists() }
        if (uri.scheme == "file") return uri.path?.let { java.io.File(it).exists() } == true
        return true
    }

    fun continueWatching(): LibraryVideo? = recentVideos().firstOrNull()

    private fun loadFolders(): List<String> =
        prefs.getStringSet("folders", emptySet())?.toList().orEmpty()

    private fun persistState() {
        prefs.edit()
            .putString("recents", recents.joinToString("|"))
            .putString("progress", progress.entries.joinToString("|") { "${it.key}=${it.value}" })
            .apply()
    }

    private fun persistOpened() {
        val array = org.json.JSONArray()
        opened.forEach { video ->
            array.put(
                org.json.JSONObject()
                    .put("id", video.id)
                    .put("title", video.title)
                    .put("uri", video.uri.toString())
                    .put("durationMs", video.durationMs)
                    .put("format", video.format)
                    .put("source", video.source.name)
                    .put("dateAdded", video.dateAdded)
                    .put("lastModified", video.lastModified)
                    .put("path", video.path.orEmpty())
                    .put("isLive", video.isLive)
                    .put("isStream", video.isStream)
                    .put("originUrl", video.originUrl.orEmpty()),
            )
        }
        prefs.edit().putString("opened_json", array.toString()).apply()
    }

    private fun loadOpened(): List<LibraryVideo> {
        val raw = prefs.getString("opened_json", null) ?: return emptyList()
        return runCatching {
            val array = org.json.JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        LibraryVideo(
                            id = obj.getString("id"),
                            title = obj.getString("title"),
                            uri = Uri.parse(obj.getString("uri")),
                            durationMs = obj.optLong("durationMs"),
                            format = obj.optString("format", "VIDEO"),
                            source = runCatching { StorageSource.valueOf(obj.optString("source")) }
                                .getOrDefault(StorageSource.Internal),
                            dateAdded = obj.optLong("dateAdded"),
                            lastModified = obj.optLong("lastModified"),
                            path = obj.optString("path").ifBlank { null },
                            isLive = obj.optBoolean("isLive"),
                            isStream = obj.optBoolean("isStream"),
                            originUrl = obj.optString("originUrl").ifBlank { null },
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun scanAll(): List<LibraryVideo> {
        val found = LinkedHashMap<String, LibraryVideo>()
        val byPath = HashMap<String, String>()
        scanMediaStore(found, byPath)
        scanVolumes(found, byPath)
        walkFiles(DownloadPaths.dir(app), found, byPath)
        extraFolders.forEach { raw ->
            val uri = Uri.parse(raw)
            if (uri.scheme == "file") {
                uri.path?.let { walkFiles(File(it), found, byPath) }
            } else {
                scanTree(uri, found, byPath)
            }
        }
        return found.values.toList()
    }

    private fun scanMediaStore(
        into: MutableMap<String, LibraryVideo>,
        byPath: MutableMap<String, String>,
    ) {
        val resolver = app.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val projection = mutableListOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.MIME_TYPE,
        )
        if (Build.VERSION.SDK_INT >= 29) {
            projection += MediaStore.Video.Media.VOLUME_NAME
            projection += MediaStore.Video.Media.RELATIVE_PATH
        }
        resolver.query(collection, projection.toTypedArray(), null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val modCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
            val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
            val mimeCol = cursor.getColumnIndex(MediaStore.Video.Media.MIME_TYPE)
            val volumeCol = if (Build.VERSION.SDK_INT >= 29) {
                cursor.getColumnIndex(MediaStore.Video.Media.VOLUME_NAME)
            } else {
                -1
            }
            val relativeCol = if (Build.VERSION.SDK_INT >= 29) {
                cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)
            } else {
                -1
            }
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = Uri.withAppendedPath(collection, id.toString())
                val data = if (dataCol >= 0) cursor.getString(dataCol) else null
                val volumeName = if (volumeCol >= 0) cursor.getString(volumeCol) else null
                val relative = if (relativeCol >= 0) cursor.getString(relativeCol) else null
                val name = cursor.getString(nameCol) ?: data?.substringAfterLast('/') ?: "Video"
                val path = resolveMediaPath(data, volumeName, relative, name)
                val modified = cursor.getLong(modCol) * 1000L
                var duration = cursor.getLong(durCol)
                if (duration <= 0L) duration = durationFor(uri, path, modified)
                putVideo(
                    into = into,
                    byPath = byPath,
                    video = LibraryVideo(
                        id = uri.toString(),
                        title = name.substringBeforeLast('.'),
                        uri = uri,
                        durationMs = duration,
                        format = formatOf(name, if (mimeCol >= 0) cursor.getString(mimeCol) else null),
                        source = sourceOf(path, volumeName),
                        dateAdded = cursor.getLong(addedCol) * 1000L,
                        lastModified = modified,
                        path = path,
                    ),
                    path = path,
                )
            }
        }
    }

    private fun scanVolumes(
        into: MutableMap<String, LibraryVideo>,
        byPath: MutableMap<String, String>,
    ) {
        val manager = app.getSystemService(StorageManager::class.java) ?: return
        manager.storageVolumes.forEach { volume ->
            if (volume.isPrimary) return@forEach
            val rawDir: File? = if (Build.VERSION.SDK_INT >= 30) {
                volume.directory
            } else {
                null
            }
            val dir = rawDir ?: return@forEach
            walkFiles(dir, into, byPath)
        }
    }

    private fun walkFiles(
        root: File,
        into: MutableMap<String, LibraryVideo>,
        byPath: MutableMap<String, String>,
    ) {
        if (!root.exists()) return
        val stack = ArrayDeque<File>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val dir = stack.removeFirst()
            val children = dir.listFiles() ?: continue
            children.forEach { child ->
                if (child.isDirectory) {
                    if (!child.name.startsWith('.')) stack.add(child)
                } else if (isVideoName(child.name)) {
                    val path = child.absolutePath
                    val normalized = normalizePath(path)
                    if (normalized in byPath) {
                        val existingId = byPath.getValue(normalized)
                        val existing = into[existingId]
                        if (existing != null) {
                            into[existingId] = existing.copy(source = sourceOf(path, null), path = existing.path ?: path)
                        }
                        return@forEach
                    }
                    val uri = Uri.fromFile(child)
                    val modified = child.lastModified()
                    putVideo(
                        into = into,
                        byPath = byPath,
                        video = LibraryVideo(
                            id = uri.toString(),
                            title = child.nameWithoutExtension,
                            uri = uri,
                            durationMs = durationFor(uri, path, modified),
                            format = formatOf(child.name, null),
                            source = sourceOf(path, null),
                            dateAdded = modified,
                            lastModified = modified,
                            path = path,
                        ),
                        path = path,
                    )
                }
            }
        }
    }

    private fun scanTree(
        tree: Uri,
        into: MutableMap<String, LibraryVideo>,
        byPath: MutableMap<String, String>,
    ) {
        val root = DocumentFile.fromTreeUri(app, tree) ?: return
        fun walk(folder: DocumentFile) {
            folder.listFiles().forEach { child ->
                if (child.isDirectory) {
                    walk(child)
                } else if (child.type?.startsWith("video/") == true || isVideoName(child.name.orEmpty())) {
                    val uri = child.uri
                    val name = child.name ?: queryName(uri) ?: "Video"
                    val path = documentPath(uri)
                    putVideo(
                        into = into,
                        byPath = byPath,
                        video = LibraryVideo(
                            id = uri.toString(),
                            title = name.substringBeforeLast('.'),
                            uri = uri,
                            durationMs = durationFor(uri, path, child.lastModified()),
                            format = formatOf(name, child.type),
                            source = sourceOf(path, documentVolume(uri)),
                            dateAdded = child.lastModified(),
                            lastModified = child.lastModified(),
                            path = path,
                        ),
                        path = path,
                    )
                }
            }
        }
        walk(root)
    }

    private fun putVideo(
        into: MutableMap<String, LibraryVideo>,
        byPath: MutableMap<String, String>,
        video: LibraryVideo,
        path: String?,
    ) {
        val normalized = path?.let { normalizePath(it) }
        if (normalized != null) {
            val existingId = byPath[normalized]
            if (existingId != null) {
                val existing = into[existingId] ?: return
                into[existingId] = mergeVideo(existing, video)
                return
            }
        }
        into[video.id] = video
        if (normalized != null) byPath[normalized] = video.id
    }

    private fun mergeVideo(existing: LibraryVideo, incoming: LibraryVideo): LibraryVideo {
        val preferIncomingUri = existing.uri.scheme != "content" && incoming.uri.scheme == "content"
        val path = existing.path ?: incoming.path
        return existing.copy(
            uri = if (preferIncomingUri) incoming.uri else existing.uri,
            durationMs = maxOf(existing.durationMs, incoming.durationMs),
            source = sourceOf(path, null),
            dateAdded = listOf(existing.dateAdded, incoming.dateAdded).filter { it > 0L }.minOrNull()
                ?: existing.dateAdded,
            lastModified = maxOf(existing.lastModified, incoming.lastModified),
            path = path,
            format = if (existing.format.isNotBlank()) existing.format else incoming.format,
        )
    }

    private fun queryName(uri: Uri): String? {
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) return it.getString(0)
        }
        return DocumentsContract.getDocumentId(uri).substringAfterLast(':').substringAfterLast('/')
    }

    private fun durationFor(uri: Uri, path: String?, lastModified: Long): Long {
        val key = path?.let { "${normalizePath(it)}|$lastModified" }
        if (key != null) {
            durations[key]?.let { return it }
        }
        if (ThumbnailCache.playbackActive) return 0L
        val value = MediaProbe.durationMs(app, uri, path)
        if (key != null && value > 0L) durations[key] = value
        return value
    }

    private fun persistIndex(items: List<LibraryVideo>) {
        val array = org.json.JSONArray()
        items.forEach { video ->
            array.put(
                org.json.JSONObject()
                    .put("id", video.id)
                    .put("title", video.title)
                    .put("uri", video.uri.toString())
                    .put("durationMs", video.durationMs)
                    .put("format", video.format)
                    .put("source", video.source.name)
                    .put("dateAdded", video.dateAdded)
                    .put("lastModified", video.lastModified)
                    .put("path", video.path.orEmpty())
                    .put("isLive", video.isLive)
                    .put("isStream", video.isStream)
                    .put("originUrl", video.originUrl.orEmpty()),
            )
        }
        runCatching { indexFile.writeText(array.toString()) }
    }

    private fun loadIndex(): List<LibraryVideo> {
        if (!indexFile.exists()) return emptyList()
        return runCatching {
            val array = org.json.JSONArray(indexFile.readText())
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        LibraryVideo(
                            id = obj.getString("id"),
                            title = obj.getString("title"),
                            uri = Uri.parse(obj.getString("uri")),
                            durationMs = obj.optLong("durationMs"),
                            format = obj.optString("format", "VIDEO"),
                            source = runCatching { StorageSource.valueOf(obj.optString("source")) }
                                .getOrDefault(StorageSource.Internal),
                            dateAdded = obj.optLong("dateAdded"),
                            lastModified = obj.optLong("lastModified"),
                            path = obj.optString("path").ifBlank { null },
                            isLive = obj.optBoolean("isLive"),
                            isStream = obj.optBoolean("isStream"),
                            originUrl = obj.optString("originUrl").ifBlank { null },
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun persistDurations() {
        val obj = org.json.JSONObject()
        durations.forEach { (key, value) -> obj.put(key, value) }
        runCatching { durationFile.writeText(obj.toString()) }
    }

    private fun loadDurations() {
        if (!durationFile.exists()) return
        runCatching {
            val obj = org.json.JSONObject(durationFile.readText())
            obj.keys().forEach { key ->
                durations[key] = obj.optLong(key)
            }
        }
    }

    private fun resolveMediaPath(
        data: String?,
        volumeName: String?,
        relativePath: String?,
        name: String,
    ): String? {
        if (!data.isNullOrBlank()) return data
        if (volumeName.isNullOrBlank()) return null
        val root = when (volumeName) {
            MediaStore.VOLUME_EXTERNAL_PRIMARY, "external_primary" -> "/storage/emulated/0"
            else -> "/storage/$volumeName"
        }
        val relative = relativePath.orEmpty().trim('/')
        return if (relative.isBlank()) {
            "$root/$name"
        } else {
            "$root/$relative/$name"
        }
    }

    private fun documentPath(uri: Uri): String? {
        val docId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
            ?: runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
            ?: return uri.path
        val volume = docId.substringBefore(':', "")
        val relative = docId.substringAfter(':', "")
        val root = when (volume.lowercase()) {
            "primary", "home" -> "/storage/emulated/0"
            else -> if (volume.isNotBlank()) "/storage/$volume" else return uri.path
        }
        return if (relative.isBlank()) root else "$root/$relative"
    }

    private fun documentVolume(uri: Uri): String? {
        val docId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
            ?: runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
            ?: return null
        return docId.substringBefore(':', "").ifBlank { null }
    }

    companion object {
        private val videoExt = setOf(
            "mp4", "mkv", "webm", "avi", "mov", "m4v", "3gp", "3g2",
            "ts", "m2ts", "mts", "m2t", "wmv", "flv", "f4v", "ogv",
            "mpg", "mpeg", "mpe", "vob", "asf", "divx", "xvid",
        )

        fun isVideoName(name: String): Boolean =
            name.substringAfterLast('.', "").lowercase() in videoExt

        fun formatOf(name: String, mime: String?): String {
            val ext = name.substringAfterLast('.', "").uppercase()
            if (ext.isNotBlank()) return ext
            return mime?.substringAfterLast('/')?.uppercase() ?: "VIDEO"
        }

        fun sourceOf(path: String?, volumeName: String?): StorageSource {
            val volume = volumeName?.lowercase()
            if (volume != null) {
                if (volume == MediaStore.VOLUME_EXTERNAL_PRIMARY || volume == "external_primary" ||
                    volume == "primary" || volume == "home"
                ) {
                    return StorageSource.Internal
                }
                if (volume.matches(Regex("[0-9a-f]{4}-[0-9a-f]{4}")) ||
                    volume.contains("usb") || volume.contains("remov")
                ) {
                    return StorageSource.Usb
                }
            }
            if (path.isNullOrBlank()) return StorageSource.Internal
            val lower = path.replace('\\', '/').lowercase()
            if (lower.contains("/storage/emulated/") ||
                lower.contains("/data/media/") ||
                lower.contains("/storage/self/primary") ||
                lower.startsWith("/sdcard")
            ) {
                return StorageSource.Internal
            }
            if (Regex("/storage/[0-9a-f]{4}-[0-9a-f]{4}(/|$)").containsMatchIn(lower) ||
                lower.contains("/mnt/media_rw/") ||
                lower.contains("/mnt/usb") ||
                lower.contains("/storage/usb")
            ) {
                return StorageSource.Usb
            }
            return StorageSource.Internal
        }

        fun normalizePath(path: String): String {
            val canonical = runCatching { File(path).canonicalPath }.getOrDefault(path)
            return canonical.replace('\\', '/').trimEnd('/').lowercase()
        }
    }
}
