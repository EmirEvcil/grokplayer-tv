package com.grokplayer.tv.data

import android.content.Context
import android.media.MediaMetadataRetriever
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

    init {
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
        videos = withContext(Dispatchers.IO) { scanAll() }
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
        recents = (listOf(id) + recents.filterNot { it == id }).take(12)
        persistState()
    }

    fun progressOf(id: String): Long = progress[id] ?: 0L

    fun progressFraction(video: LibraryVideo): Float? {
        val pos = progress[video.id] ?: return null
        if (video.durationMs <= 0L || pos <= 1_000L) return null
        return (pos.toFloat() / video.durationMs).coerceIn(0.04f, 0.96f)
    }

    fun recentVideos(): List<LibraryVideo> =
        recents.mapNotNull { id -> videos.find { it.id == id } }

    fun continueWatching(): LibraryVideo? = recentVideos().firstOrNull {
        val pos = progress[it.id] ?: 0L
        pos > 1_000L && (it.durationMs <= 0L || pos < it.durationMs - 2_000L)
    }

    private fun loadFolders(): List<String> =
        prefs.getStringSet("folders", emptySet())?.toList().orEmpty()

    private fun persistState() {
        prefs.edit()
            .putString("recents", recents.joinToString("|"))
            .putString("progress", progress.entries.joinToString("|") { "${it.key}=${it.value}" })
            .apply()
    }

    private fun scanAll(): List<LibraryVideo> {
        val found = LinkedHashMap<String, LibraryVideo>()
        scanMediaStore(found)
        scanVolumes(found)
        extraFolders.forEach { raw ->
            val uri = Uri.parse(raw)
            if (uri.scheme == "file") {
                uri.path?.let { walkFiles(File(it), removable = true, found) }
            } else {
                scanTree(uri, found)
            }
        }
        return found.values.toList()
    }

    private fun scanMediaStore(into: MutableMap<String, LibraryVideo>) {
        val resolver = app.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= 29) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.MIME_TYPE,
        )
        resolver.query(collection, projection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val modCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
            val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
            val mimeCol = cursor.getColumnIndex(MediaStore.Video.Media.MIME_TYPE)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = Uri.withAppendedPath(collection, id.toString())
                val path = if (dataCol >= 0) cursor.getString(dataCol) else null
                val name = cursor.getString(nameCol) ?: path?.substringAfterLast('/') ?: "Video"
                var duration = cursor.getLong(durCol)
                if (duration <= 0L) duration = probeDuration(uri, path)
                into[uri.toString()] = LibraryVideo(
                    id = uri.toString(),
                    title = name.substringBeforeLast('.'),
                    uri = uri,
                    durationMs = duration,
                    format = formatOf(name, if (mimeCol >= 0) cursor.getString(mimeCol) else null),
                    source = sourceOf(path),
                    dateAdded = cursor.getLong(addedCol) * 1000L,
                    lastModified = cursor.getLong(modCol) * 1000L,
                )
            }
        }
    }

    private fun scanVolumes(into: MutableMap<String, LibraryVideo>) {
        val manager = app.getSystemService(StorageManager::class.java) ?: return
        manager.storageVolumes.forEach { volume ->
            val dir = if (Build.VERSION.SDK_INT >= 30) volume.directory else {
                if (volume.isPrimary) Environment.getExternalStorageDirectory() else null
            } ?: return@forEach
            walkFiles(dir, volume.isRemovable, into)
        }
    }

    private fun walkFiles(root: File, removable: Boolean, into: MutableMap<String, LibraryVideo>) {
        val stack = ArrayDeque<File>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val dir = stack.removeFirst()
            val children = dir.listFiles() ?: continue
            children.forEach { child ->
                if (child.isDirectory) {
                    if (!child.name.startsWith('.')) stack.add(child)
                } else if (isVideoName(child.name)) {
                    val uri = Uri.fromFile(child)
                    val key = uri.toString()
                    if (key in into) {
                        val existing = into.getValue(key)
                        if (removable && existing.source != StorageSource.Usb) {
                            into[key] = existing.copy(source = StorageSource.Usb)
                        }
                    } else {
                        into[key] = LibraryVideo(
                            id = key,
                            title = child.nameWithoutExtension,
                            uri = uri,
                            durationMs = probeDuration(uri, child.absolutePath),
                            format = formatOf(child.name, null),
                            source = if (removable) StorageSource.Usb else StorageSource.Internal,
                            dateAdded = child.lastModified(),
                            lastModified = child.lastModified(),
                        )
                    }
                }
            }
        }
    }

    private fun scanTree(tree: Uri, into: MutableMap<String, LibraryVideo>) {
        val root = DocumentFile.fromTreeUri(app, tree) ?: return
        fun walk(folder: DocumentFile) {
            folder.listFiles().forEach { child ->
                if (child.isDirectory) {
                    walk(child)
                } else if (child.type?.startsWith("video/") == true || isVideoName(child.name.orEmpty())) {
                    val uri = child.uri
                    val name = child.name ?: queryName(uri) ?: "Video"
                    into[uri.toString()] = LibraryVideo(
                        id = uri.toString(),
                        title = name.substringBeforeLast('.'),
                        uri = uri,
                        durationMs = probeDuration(uri, null),
                        format = formatOf(name, child.type),
                        source = StorageSource.Internal,
                        dateAdded = child.lastModified(),
                        lastModified = child.lastModified(),
                    )
                }
            }
        }
        walk(root)
    }

    private fun queryName(uri: Uri): String? {
        app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) return it.getString(0)
        }
        return DocumentsContract.getDocumentId(uri).substringAfterLast(':').substringAfterLast('/')
    }

    private fun probeDuration(uri: Uri, path: String?): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            if (path != null && File(path).canRead()) {
                retriever.setDataSource(path)
            } else {
                retriever.setDataSource(app, uri)
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun sourceOf(path: String?): StorageSource {
        if (path == null) return StorageSource.Internal
        val lower = path.lowercase()
        return if (
            lower.contains("/storage/emulated/") ||
            lower.contains("/data/media/0")
        ) {
            StorageSource.Internal
        } else {
            StorageSource.Usb
        }
    }

    companion object {
        private val videoExt = setOf("mp4", "mkv", "webm", "avi", "mov", "m4v", "ts", "m2ts", "wmv", "flv")

        fun isVideoName(name: String): Boolean =
            name.substringAfterLast('.', "").lowercase() in videoExt

        fun formatOf(name: String, mime: String?): String {
            val ext = name.substringAfterLast('.', "").uppercase()
            if (ext.isNotBlank()) return ext
            return mime?.substringAfterLast('/')?.uppercase() ?: "MP4"
        }
    }
}
