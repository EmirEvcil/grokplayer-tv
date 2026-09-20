package com.grokplayer.tv.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class DownloadStatus { Queued, Running, Done, Failed }

data class DownloadItem(
    val id: String,
    val title: String,
    val url: String,
    val status: DownloadStatus,
    val progress: Float,
    val localPath: String?,
    val error: String?,
    val addedAt: Long,
    val pageUrl: String? = null,
    val durationMs: Long = 0L,
) {
    fun toVideo(): LibraryVideo {
        val file = localPath?.let { File(it) }
        val duration = durationMs.takeIf { it > 0L }
            ?: file?.let { localHlsDurationMs(it) }?.takeIf { it > 0L }
            ?: 0L
        return LibraryVideo(
            id = "download:$id",
            title = title,
            uri = if (file != null) Uri.fromFile(file) else Uri.parse(url),
            durationMs = duration,
            format = file?.let { sniffContainer(it).uppercase() } ?: "VOD",
            source = StorageSource.Internal,
            dateAdded = addedAt,
            lastModified = file?.lastModified() ?: addedAt,
            path = localPath,
            originUrl = pageUrl ?: url,
        )
    }
}

object DownloadPaths {
    fun dir(context: Context): File =
        File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "downloads").apply { mkdirs() }

    fun freeBytes(context: Context): Long = dir(context).usableSpace
}

class DownloadStore(context: Context, private val settings: PlaybackSettings) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("downloads", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val cancelled = mutableSetOf<String>()

    var items by mutableStateOf(emptyList<DownloadItem>())
        private set

    init {
        items = dedupeDownloads(load()).map { item ->
            val progress = item.progress.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
            when {
                item.status == DownloadStatus.Running -> item.copy(status = DownloadStatus.Queued, progress = 0f)
                item.status == DownloadStatus.Done && item.localPath?.let { File(it).exists() } != true ->
                    item.copy(status = DownloadStatus.Failed, progress = 0f, localPath = null, error = "Dosya bulunamadı")
                item.status == DownloadStatus.Done && item.localPath?.let { isPlayableDownload(File(it)) } != true ->
                    item.copy(
                        status = DownloadStatus.Failed,
                        progress = 0f,
                        error = "Dosya oynatılamıyor — yeniden dene",
                    )
                item.status == DownloadStatus.Failed && isCertError(item.error) ->
                    item.copy(status = DownloadStatus.Queued, progress = 0f, error = null)
                else -> item.copy(progress = progress)
            }
        }
        persist()
        pump(settings.downloadHeight)
    }

    fun statusOf(url: String): DownloadStatus? = items.firstOrNull { it.url == url }?.status

    fun retry(id: String, url: String? = null, title: String? = null, pageUrl: String? = null): Boolean {
        val item = items.firstOrNull { it.id == id } ?: return false
        if (item.status != DownloadStatus.Failed &&
            !(item.status == DownloadStatus.Done && item.localPath?.let { File(it).exists() } != true)
        ) {
            return false
        }
        purgePartial(item)
        items = items.map {
            if (it.id == id) {
                it.copy(
                    title = title?.ifBlank { it.title } ?: it.title,
                    url = url?.ifBlank { it.url } ?: it.url,
                    pageUrl = pageUrl?.ifBlank { null } ?: it.pageUrl,
                    status = DownloadStatus.Queued,
                    progress = 0f,
                    error = null,
                    localPath = null,
                    durationMs = 0L,
                )
            } else {
                it
            }
        }
        persist()
        pump(settings.downloadHeight)
        return true
    }

    fun enqueueAll(
        requests: List<Pair<String, String>>,
        maxHeight: Int = settings.downloadHeight,
    ): DownloadPolicy.BatchResult {
        var queued = 0
        var retried = 0
        var skippedDone = 0
        var skippedActive = 0
        requests.forEach { (title, url) ->
            when (enqueue(title, url, maxHeight, count = false, pageUrl = null)) {
                DownloadPolicy.Action.Enqueue -> queued += 1
                DownloadPolicy.Action.RetryFailed -> retried += 1
                DownloadPolicy.Action.SkipDone -> skippedDone += 1
                DownloadPolicy.Action.SkipActive -> skippedActive += 1
            }
        }
        pump(maxHeight)
        return DownloadPolicy.BatchResult(queued, retried, skippedDone, skippedActive)
    }

    fun enqueue(
        title: String,
        url: String,
        maxHeight: Int = settings.downloadHeight,
        pageUrl: String? = null,
    ): String? {
        val snapshots = items.map {
            DownloadPolicy.Snapshot(it.id, it.title, it.url, it.status, it.localPath)
        }
        val existingId = DownloadPolicy.decide(snapshots, title, url) { path -> File(path).exists() }.existingId
        return when (enqueue(title, url, maxHeight, count = true, pageUrl = pageUrl)) {
            DownloadPolicy.Action.SkipActive -> null
            else -> existingId ?: items.firstOrNull {
                DownloadPolicy.identity(it.title, it.url) == DownloadPolicy.identity(title, url)
            }?.id
        }
    }

    private fun enqueue(
        title: String,
        url: String,
        maxHeight: Int,
        count: Boolean,
        pageUrl: String? = null,
    ): DownloadPolicy.Action {
        val snapshots = items.map {
            DownloadPolicy.Snapshot(it.id, it.title, it.url, it.status, it.localPath)
        }
        val decision = DownloadPolicy.decide(snapshots, title, url) { path -> File(path).exists() }
        when (decision.action) {
            DownloadPolicy.Action.SkipDone, DownloadPolicy.Action.SkipActive -> return decision.action
            DownloadPolicy.Action.RetryFailed -> {
                decision.existingId?.let { retry(it, url = url, title = title, pageUrl = pageUrl) }
                return DownloadPolicy.Action.RetryFailed
            }
            DownloadPolicy.Action.Enqueue -> Unit
        }
        val existing = decision.existingId?.let { id -> items.firstOrNull { it.id == id } }
        if (existing != null && existing.status != DownloadStatus.Done) {
            purgePartial(existing)
        }
        val item = DownloadItem(
            id = UUID.randomUUID().toString(),
            title = title.ifBlank { url.substringAfterLast('/').ifBlank { "İndirme" } },
            url = url,
            status = DownloadStatus.Queued,
            progress = 0f,
            localPath = null,
            error = null,
            addedAt = System.currentTimeMillis(),
            pageUrl = pageUrl,
        )
        items = listOf(item) + items.filterNot {
            DownloadPolicy.identity(it.title, it.url) == DownloadPolicy.identity(title, url)
        }
        persist()
        if (count) pump(maxHeight)
        return DownloadPolicy.Action.Enqueue
    }

    fun cancel(id: String) {
        cancelled += id
        val item = items.firstOrNull { it.id == id }
        if (item != null && item.status != DownloadStatus.Done) {
            purgePartial(item)
        }
        items = items.map {
            if (it.id == id && it.status != DownloadStatus.Done) {
                it.copy(status = DownloadStatus.Failed, error = "İptal edildi", localPath = null)
            } else {
                it
            }
        }
        persist()
    }

    fun remove(id: String) {
        cancelled += id
        val item = items.firstOrNull { it.id == id }
        if (item != null) purgePartial(item)
        items = items.filterNot { it.id == id }
        persist()
    }

    fun titleForPath(path: String): String? =
        items.firstOrNull { it.localPath == path }?.title

    private fun pump(maxHeight: Int) {
        if (job?.isActive == true) return
        val next = items.firstOrNull { it.status == DownloadStatus.Queued } ?: return
        job = scope.launch { runItem(next, maxHeight) }
    }

    private suspend fun runItem(item: DownloadItem, maxHeight: Int) {
        if (item.id in cancelled) {
            job = null
            pump(maxHeight)
            return
        }
        withContext(Dispatchers.Main) {
            if (item.id in cancelled) return@withContext
            items = items.map {
                if (it.id == item.id && it.status == DownloadStatus.Queued) {
                    it.copy(status = DownloadStatus.Running, progress = 0f, error = null)
                } else {
                    it
                }
            }
        }
        if (item.id in cancelled) {
            job = null
            pump(maxHeight)
            return
        }
        val dest = destFile(item, "ts")
        val cap = if (maxHeight <= 0) Int.MAX_VALUE else maxHeight
        var lastEmitAt = 0L
        var lastShown = -1f
        val result = runCatching {
            StreamHttp.client(app)
            VodDownloader.download(
                url = item.url,
                dest = dest,
                maxHeight = cap,
                cancelled = { item.id in cancelled },
                preferredAudioLang = settings.audioLang.ifBlank { null },
                onProgress = { value ->
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (value < 1f && value - lastShown < 0.005f && now - lastEmitAt < 150L) return@download
                    lastShown = value
                    lastEmitAt = now
                    scope.launch(Dispatchers.Main.immediate) {
                        items = items.map { current ->
                            if (current.id == item.id && current.status == DownloadStatus.Running) {
                                current.copy(progress = value)
                            } else {
                                current
                            }
                        }
                    }
                },
            )
        }
        val file = result.getOrNull()
        val aborted = item.id in cancelled || isCancel(result.exceptionOrNull())
        if (file != null && !aborted) {
            downloadYoutubeCaptions(item, file)
        }
        val duration = file?.let { localHlsDurationMs(it) } ?: 0L
        withContext(Dispatchers.Main) {
            items = items.map { current ->
                if (current.id != item.id) {
                    current
                } else if (aborted) {
                    purgePartial(item, dest, file)
                    current.copy(
                        status = DownloadStatus.Failed,
                        error = "İptal edildi",
                        localPath = null,
                        durationMs = 0L,
                    )
                } else if (file != null && file.exists() && file.length() > 0L) {
                    writeMeta(item, file, duration)
                    current.copy(
                        status = DownloadStatus.Done,
                        progress = 1f,
                        localPath = file.absolutePath,
                        error = null,
                        durationMs = duration,
                    )
                } else {
                    purgePartial(item, dest, file)
                    current.copy(
                        status = DownloadStatus.Failed,
                        error = friendlyError(result.exceptionOrNull()),
                        localPath = null,
                        durationMs = 0L,
                    )
                }
            }
            persist()
        }
        job = null
        pump(maxHeight)
    }

    private fun persist() {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("title", item.title)
                    .put("url", item.url)
                    .put("status", item.status.name)
                    .put("progress", item.progress.toDouble())
                    .put("localPath", item.localPath ?: "")
                    .put("error", item.error ?: "")
                    .put("addedAt", item.addedAt)
                    .put("pageUrl", item.pageUrl ?: "")
                    .put("durationMs", item.durationMs),
            )
        }
        prefs.edit().putString("items", array.toString()).apply()
    }

    private fun load(): List<DownloadItem> {
        val raw = prefs.getString("items", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        DownloadItem(
                            id = obj.getString("id"),
                            title = obj.getString("title"),
                            url = obj.getString("url"),
                            status = runCatching { DownloadStatus.valueOf(obj.getString("status")) }
                                .getOrDefault(DownloadStatus.Failed),
                            progress = obj.optDouble("progress").toFloat(),
                            localPath = obj.optString("localPath").ifBlank { null },
                            error = obj.optString("error").ifBlank { null },
                            addedAt = obj.optLong("addedAt"),
                            pageUrl = obj.optString("pageUrl").ifBlank { null },
                            durationMs = obj.optLong("durationMs"),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun isCertError(error: String?): Boolean {
        val text = error.orEmpty()
        return text.contains("Trust anchor", ignoreCase = true) ||
            text.contains("CertPath", ignoreCase = true) ||
            text.contains("SSLHandshake", ignoreCase = true)
    }

    private fun isCancel(error: Throwable?): Boolean {
        val text = error?.message.orEmpty()
        return text.contains("İptal edildi") || error is java.util.concurrent.CancellationException
    }

    private fun friendlyError(error: Throwable?): String {
        val text = error?.message.orEmpty()
        return when {
            isCancel(error) -> "İptal edildi"
            isCertError(text) -> "Bağlantı kurulamadı"
            else -> text.ifBlank { "İndirilemedi" }
        }
    }

    private fun purgePartial(item: DownloadItem, vararg extras: File?) {
        val root = DownloadPaths.dir(app)
        val keep = DownloadOwnership.keepPaths(
            root,
            items.filter { it.id != item.id }.map { it.ref() },
        )
        DownloadOwnership.purge(
            root = root,
            item = item.ref(),
            extras = extras.filterNotNull(),
            keep = keep,
        )
    }

    private fun writeMeta(item: DownloadItem, videoFile: File, durationMs: Long) {
        val parent = videoFile.parentFile ?: return
        parent.mkdirs()
        File(parent, "meta.json").writeText(
            JSONObject()
                .put("id", item.id)
                .put("title", item.title)
                .put("url", item.url)
                .put("pageUrl", item.pageUrl ?: "")
                .put("durationMs", durationMs)
                .toString(),
        )
    }

    private fun downloadYoutubeCaptions(item: DownloadItem, videoFile: File) {
        val page = item.pageUrl ?: item.url
        val id = com.grokplayer.tv.data.scan.YouTubeResolver.videoId(page) ?: return
        val hit = runCatching {
            com.grokplayer.tv.data.scan.YouTubeResolver.resolve(app, id, page)
        }.getOrNull() ?: return
        val stem = videoFile.nameWithoutExtension
        val parent = videoFile.parentFile ?: return
        hit.captions.forEach { track ->
            val lines = track.lines.ifEmpty {
                com.grokplayer.tv.data.scan.YouTubeCaptions.fetchLines(
                    app,
                    track,
                    hit.referer,
                    hit.userAgent,
                )
            }
            if (lines.isEmpty()) return@forEach
            val lang = track.language.ifBlank { "und" }.lowercase()
            File(parent, "$stem.$lang.vtt").writeText(
                com.grokplayer.tv.data.scan.YouTubeCaptions.toVtt(lines),
                Charsets.UTF_8,
            )
        }
    }

    private fun destFile(item: DownloadItem, ext: String): File {
        val dest = DownloadOwnership.dest(DownloadPaths.dir(app), item.id, item.title, ext)
        dest.parentFile?.mkdirs()
        return dest
    }

    private fun DownloadItem.ref() = DownloadOwnership.ItemRef(id, title, localPath)
}

internal fun dedupeDownloads(list: List<DownloadItem>): List<DownloadItem> {
    val seen = HashSet<String>()
    return list.filter { item ->
        val id = item.id.ifBlank { return@filter false }
        seen.add(id)
    }
}

internal fun downloadFileName(title: String): String = DownloadOwnership.fileName(title)

internal fun downloadStem(title: String, id: String): String =
    DownloadOwnership.legacyDest(File("."), id, title, "x").nameWithoutExtension

internal data class HlsMediaParts(
    val mapUri: String?,
    val segments: List<String>,
) {
    val fragmentedMp4: Boolean
        get() {
            if (mapUri != null) return true
            return segments.any { ref ->
                val name = ref.lowercase().substringBefore('?')
                name.endsWith(".m4s") || name.endsWith(".mp4") ||
                    name.endsWith(".cmfv") || name.endsWith(".cmfa")
            }
        }
}

internal fun hlsMediaParts(body: String): HlsMediaParts {
    val lines = body.replace("\r\n", "\n").lines().map { it.trim() }
    var mapUri: String? = null
    val segments = ArrayList<String>()
    lines.forEach { line ->
        if (line.startsWith("#EXT-X-MAP", ignoreCase = true)) {
            val quoted = Regex("""URI="([^"]+)"""", RegexOption.IGNORE_CASE).find(line)?.groupValues?.getOrNull(1)
            val bare = Regex("""URI=([^,]+)""", RegexOption.IGNORE_CASE).find(line)?.groupValues?.getOrNull(1)
            mapUri = quoted ?: bare?.trim()?.trim('"')
        } else if (line.isNotEmpty() && !line.startsWith("#")) {
            segments += line
        }
    }
    return HlsMediaParts(mapUri, segments)
}

internal fun hlsDurationMs(body: String): Long {
    val total = Regex("#EXTINF:([0-9.]+)", RegexOption.IGNORE_CASE)
        .findAll(body)
        .sumOf { it.groupValues[1].toDoubleOrNull() ?: 0.0 }
    return (total * 1000.0).toLong()
}

internal fun localHlsDurationMs(file: File): Long {
    if (!file.isFile) return 0L
    val text = runCatching { file.readText() }.getOrNull() ?: return 0L
    val direct = hlsDurationMs(text)
    if (direct > 0L) return direct
    if (!file.extension.equals("m3u8", true)) return 0L
    val childName = text.replace("\r\n", "\n").lines()
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
        ?: return 0L
    val child = File(file.parentFile, childName.substringAfterLast('/').substringBefore('?'))
    return if (child.isFile) hlsDurationMs(child.readText()) else 0L
}

internal fun rewriteMediaPlaylist(body: String, localNames: List<String>): String {
    val lines = body.replace("\r\n", "\n").lines()
    var index = 0
    val out = ArrayList<String>(lines.size + 2)
    var hasType = false
    var hasEnd = false
    lines.forEach { raw ->
        val line = raw.trim()
        when {
            line.startsWith("#EXT-X-MAP", ignoreCase = true) -> {
                val name = localNames.getOrNull(index++) ?: return@forEach
                out += "#EXT-X-MAP:URI=\"$name\""
            }
            line.startsWith("#EXT-X-BYTERANGE", ignoreCase = true) -> Unit
            line.startsWith("#EXT-X-PLAYLIST-TYPE", ignoreCase = true) -> {
                hasType = true
                out += "#EXT-X-PLAYLIST-TYPE:VOD"
            }
            line == "#EXT-X-ENDLIST" -> {
                hasEnd = true
                out += line
            }
            line.isNotEmpty() && !line.startsWith("#") -> {
                val name = localNames.getOrNull(index++) ?: return@forEach
                out += name
            }
            else -> out += raw
        }
    }
    if (!hasType) {
        val at = out.indexOfFirst { it.startsWith("#EXTM3U") }.let { if (it >= 0) it + 1 else 0 }
        out.add(at, "#EXT-X-PLAYLIST-TYPE:VOD")
    }
    if (!hasEnd) out += "#EXT-X-ENDLIST"
    return out.joinToString("\n", postfix = "\n")
}

internal fun pickDefaultAudio(
    tracks: List<com.grokplayer.tv.data.scan.HlsMediaTag>,
    groupId: String?,
    preferredLang: String?,
): com.grokplayer.tv.data.scan.HlsMediaTag? {
    val audio = tracks.filter { it.type.equals("AUDIO", true) }
    val inGroup = if (groupId.isNullOrBlank()) {
        audio
    } else {
        audio.filter { it.groupId == groupId }.ifEmpty { audio }
    }
    if (inGroup.isEmpty()) return null
    inGroup.firstOrNull { it.isDefault }?.let { return it }
    inGroup.firstOrNull { it.name.contains("original", ignoreCase = true) }?.let { return it }
    val want = preferredLang?.trim()?.lowercase().orEmpty()
    if (want.isNotBlank()) {
        inGroup.firstOrNull { it.language.lowercase().startsWith(want.take(2)) }?.let { return it }
    }
    return inGroup.firstOrNull()
}

internal fun buildHlsMaster(
    videoPlaylist: String,
    audio: List<Pair<com.grokplayer.tv.data.scan.HlsMediaTag, String>>,
    defaultTag: com.grokplayer.tv.data.scan.HlsMediaTag?,
): String = buildString {
    appendLine("#EXTM3U")
    appendLine("#EXT-X-INDEPENDENT-SEGMENTS")
    audio.forEach { (tag, file) ->
        val name = tag.name.ifBlank { tag.language }.replace("\"", "")
        val lang = tag.language.ifBlank { "und" }
        val isDef = defaultTag != null && tag.uri == defaultTag.uri
        append("#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"aud\",NAME=\"$name\",LANGUAGE=\"$lang\"")
        if (isDef) append(",DEFAULT=YES,AUTOSELECT=YES") else append(",DEFAULT=NO")
        appendLine(",URI=\"$file\"")
    }
    if (audio.isNotEmpty()) {
        appendLine("#EXT-X-STREAM-INF:BANDWIDTH=1,AUDIO=\"aud\"")
    } else {
        appendLine("#EXT-X-STREAM-INF:BANDWIDTH=1")
    }
    appendLine(videoPlaylist)
}

internal fun sniffBox(file: File): String? {
    val bytes = ByteArray(16)
    val n = runCatching { file.inputStream().use { it.read(bytes) } }.getOrDefault(-1)
    if (n >= 8) {
        val box = String(bytes, 4, 4, Charsets.US_ASCII)
        if (box == "ftyp" || box == "moof" || box == "mdat" || box == "styp") return box
    }
    if (n > 0 && bytes[0] == 0x47.toByte()) return "ts"
    return null
}

internal fun sniffContainer(file: File): String {
    val box = sniffBox(file)
    if (box == "ftyp" || box == "moof" || box == "mdat" || box == "styp") return "mp4"
    if (box == "ts") return "ts"
    return file.extension.lowercase().ifBlank { "mp4" }
}

internal fun isPlayableDownload(file: File): Boolean {
    if (!file.isFile) return false
    if (file.extension.equals("m3u8", true) && file.length() > 8L) {
        val head = runCatching { file.bufferedReader().use { it.readLine() } }.getOrNull().orEmpty()
        return head.contains("EXTM3U")
    }
    if (file.length() < 32L) return false
    return when (sniffBox(file)) {
        "ftyp", "ts" -> true
        "moof", "mdat", "styp" -> false
        else -> file.extension.lowercase() in PLAYABLE_FALLBACK_EXT
    }
}

internal fun localPlaybackFile(path: String?, fileUriPath: String?): File? {
    val file = path?.let { File(it) }?.takeIf { it.isFile && it.length() > 32L }
        ?: fileUriPath?.let { File(it) }?.takeIf { it.isFile && it.length() > 32L }
    return file?.takeIf { isPlayableDownload(it) }
}

private val PLAYABLE_FALLBACK_EXT = setOf("mp4", "mkv", "webm", "mov", "m4v", "avi", "m4a")

internal object VodDownloader {
    fun download(
        url: String,
        dest: File,
        maxHeight: Int,
        cancelled: () -> Boolean,
        onProgress: (Float) -> Unit,
        preferredAudioLang: String? = null,
    ): File {
        dest.parentFile?.mkdirs()
        if ((dest.parentFile?.usableSpace ?: 0L) < 80L * 1024L * 1024L) {
            error("Yetersiz boş alan")
        }
        val resolved = StreamProbe.playUrl(url)
        if (!looksLikePlaylist(resolved)) {
            val mp4 = File(dest.parentFile, dest.nameWithoutExtension + ".mp4")
            downloadProgressive(resolved, mp4, cancelled, onProgress)
            return mp4
        }
        val head = fetchText(resolved)
        if (head == null || !head.contains("#EXTM3U")) {
            val mp4 = File(dest.parentFile, dest.nameWithoutExtension + ".mp4")
            downloadProgressive(resolved, mp4, cancelled, onProgress)
            return mp4
        }
        if (head.contains("#EXT-X-KEY")) error("Şifreli yayın indirilemez")
        val picked = pickVariant(resolved, head, maxHeight)
        val mediaUrl = picked?.uri ?: resolved
        val media = if (picked != null) fetchText(picked.uri) ?: error("Liste alınamadı") else head
        if (media.contains("#EXT-X-KEY")) error("Şifreli yayın indirilemez")
        if (!media.contains("#EXT-X-ENDLIST") && media.contains("#EXTINF")) {
            error("Canlı yayın indirilemez")
        }
        val parts = hlsMediaParts(media)
        if (parts.segments.isEmpty() && parts.mapUri == null) {
            val mp4 = File(dest.parentFile, dest.nameWithoutExtension + ".mp4")
            downloadProgressive(mediaUrl, mp4, cancelled, onProgress)
            return mp4
        }
        val dir = dest.parentFile ?: dest
        val hasSubs = subtitleEntries(head).isNotEmpty()
        if (parts.fragmentedMp4) {
            val created = mutableListOf<File>()
            try {
                val videoPlaylist = saveHlsMedia(mediaUrl, media, parts, dir, "v", cancelled) { frac ->
                    onProgress(frac * 0.7f)
                }
                created += videoPlaylist
                val audioTags = com.grokplayer.tv.data.scan.YouTubeCaptions.parseExtXMedia(head)
                    .filter { it.type.equals("AUDIO", true) }
                val defaultAudio = pickDefaultAudio(audioTags, picked?.audioGroup, preferredAudioLang)
                val inGroup = if (picked?.audioGroup.isNullOrBlank()) {
                    audioTags
                } else {
                    audioTags.filter { it.groupId == picked?.audioGroup }.ifEmpty { audioTags }
                }
                val audioFiles = ArrayList<Pair<com.grokplayer.tv.data.scan.HlsMediaTag, String>>()
                val unique = inGroup.distinctBy { it.uri }
                unique.forEachIndexed { aIndex, tag ->
                    if (cancelled()) error("İptal edildi")
                    val audioUrl = resolve(resolved, tag.uri)
                    val audioBody = fetchText(audioUrl) ?: return@forEachIndexed
                    val audioParts = hlsMediaParts(audioBody)
                    if (audioParts.segments.isEmpty() && audioParts.mapUri == null) return@forEachIndexed
                    val lang = tag.language.ifBlank { "a$aIndex" }.replace(Regex("[^A-Za-z0-9-]"), "")
                    val prefix = if (unique.count { it.language.equals(tag.language, true) } > 1 && !tag.isDefault) {
                        "a-$lang-$aIndex"
                    } else {
                        "a-$lang"
                    }
                    val slice = 0.25f / unique.size.coerceAtLeast(1)
                    val audioPlaylist = saveHlsMedia(
                        audioUrl,
                        audioBody,
                        audioParts,
                        dir,
                        prefix,
                        cancelled,
                    ) { frac ->
                        onProgress(0.7f + slice * aIndex + frac * slice)
                    }
                    created += audioPlaylist
                    audioFiles += tag to audioPlaylist.name
                }
                val master = File(dir, dest.nameWithoutExtension + ".m3u8")
                master.writeText(buildHlsMaster(videoPlaylist.name, audioFiles, defaultAudio))
                created += master
                if (cancelled()) error("İptal edildi")
                downloadSubtitles(resolved, head, master, cancelled) { frac ->
                    onProgress(0.95f + 0.05f * frac)
                }
                onProgress(1f)
                return master
            } catch (error: Throwable) {
                if (cancelled() || error.message == "İptal edildi") {
                    created.forEach { it.delete() }
                    dest.delete()
                    dir.listFiles()?.forEach { child ->
                        val name = child.name.lowercase()
                        if (name.endsWith(".seg") || name.endsWith(".m3u8")) child.delete()
                    }
                }
                throw error
            }
        }
        val out = dest
        val subShare = if (hasSubs) 0.03f else 0f
        val videoShare = 1f - subShare
        try {
            concatHls(mediaUrl, parts, out, cancelled) { frac -> onProgress(frac * videoShare) }
        } catch (error: Throwable) {
            if (cancelled() || error.message == "İptal edildi") {
                out.delete()
                dest.delete()
            }
            throw error
        }
        if (cancelled()) error("İptal edildi")
        downloadSubtitles(resolved, head, out, cancelled) { frac ->
            onProgress(videoShare + (1f - videoShare) * frac)
        }
        if (cancelled()) error("İptal edildi")
        onProgress(1f)
        return out
    }

    private fun saveHlsMedia(
        playlistUrl: String,
        playlistBody: String,
        parts: HlsMediaParts,
        dir: File,
        prefix: String,
        cancelled: () -> Boolean,
        onProgress: (Float) -> Unit,
    ): File {
        dir.mkdirs()
        val names = buildList {
            if (parts.mapUri != null) add("$prefix-init.seg")
            parts.segments.distinct().forEachIndexed { index, _ ->
                add("$prefix-${index.toString().padStart(4, '0')}.seg")
            }
        }
        val refs = buildList {
            parts.mapUri?.let { add(it) }
            addAll(parts.segments.distinct())
        }
        if (refs.isEmpty()) error("Liste boş")
        refs.forEachIndexed { index, ref ->
            if (cancelled()) error("İptal edildi")
            val dest = File(dir, names[index])
            FileOutputStream(dest).use { stream ->
                val base = index.toFloat() / refs.size
                val slice = 1f / refs.size
                StreamHttp.copyTo(resolve(playlistUrl, ref), stream, cancelled) { copied, length ->
                    val frac = if (length > 0L) (copied.toFloat() / length).coerceIn(0f, 1f) else 0f
                    onProgress(base + slice * frac)
                }
            }
            onProgress((index + 1).toFloat() / refs.size)
        }
        val playlist = File(dir, "$prefix.m3u8")
        playlist.writeText(rewriteMediaPlaylist(playlistBody, names))
        onProgress(1f)
        return playlist
    }

    private fun concatHls(
        playlistUrl: String,
        parts: HlsMediaParts,
        out: File,
        cancelled: () -> Boolean,
        onProgress: (Float) -> Unit,
    ) {
        val refs = buildList {
            parts.mapUri?.let { add(it) }
            addAll(parts.segments.distinct())
        }
        if (refs.isEmpty()) error("Liste boş")
        FileOutputStream(out).use { stream ->
            refs.forEachIndexed { index, ref ->
                if (cancelled()) error("İptal edildi")
                val base = index.toFloat() / refs.size
                val slice = 1f / refs.size
                StreamHttp.copyTo(resolve(playlistUrl, ref), stream, cancelled) { copied, length ->
                    val frac = if (length > 0L) (copied.toFloat() / length).coerceIn(0f, 1f) else 0f
                    onProgress(base + slice * frac)
                }
                onProgress((index + 1).toFloat() / refs.size)
            }
        }
    }

    private fun downloadSubtitles(
        masterUrl: String,
        master: String,
        videoFile: File,
        cancelled: () -> Boolean,
        onProgress: (Float) -> Unit,
    ) {
        val entries = subtitleEntries(master)
        if (entries.isEmpty()) return
        entries.forEachIndexed { index, (lang, uri) ->
            if (cancelled()) error("İptal edildi")
            val playlistUrl = resolve(masterUrl, uri)
            val body = fetchText(playlistUrl) ?: return@forEachIndexed
            val parts = mediaLines(body).filter { it.isNotEmpty() && !it.startsWith("#") }
            if (parts.isEmpty()) return@forEachIndexed
            val outFile = File(videoFile.parentFile, "${videoFile.nameWithoutExtension}.$lang.vtt")
            val text = buildString {
                appendLine("WEBVTT")
                appendLine()
                parts.forEach { ref ->
                    if (cancelled()) error("İptal edildi")
                    val chunk = runCatching { String(httpBytes(resolve(playlistUrl, ref)), Charsets.UTF_8) }
                        .getOrNull() ?: return@forEach
                    append(stripVttHeader(chunk).trim())
                    appendLine()
                    appendLine()
                }
            }
            outFile.writeText(text, Charsets.UTF_8)
            onProgress((index + 1).toFloat() / entries.size)
        }
    }

    private data class PickedVariant(val uri: String, val audioGroup: String?)

    private fun pickVariant(masterUrl: String, body: String, maxHeight: Int): PickedVariant? {
        val lines = mediaLines(body)
        data class Variant(val height: Int, val bandwidth: Int, val uri: String, val audioGroup: String?)
        val variants = mutableListOf<Variant>()
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            if (line.startsWith("#EXT-X-STREAM-INF:")) {
                val height = Regex("RESOLUTION=(\\d+)x(\\d+)", RegexOption.IGNORE_CASE)
                    .find(line)?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0
                val bandwidth = Regex("BANDWIDTH=(\\d+)", RegexOption.IGNORE_CASE)
                    .find(line)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                val audioGroup = attr(line, "AUDIO")
                val uri = lines.drop(index + 1).firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
                if (uri != null && height > 0) {
                    variants += Variant(height, bandwidth, resolve(masterUrl, uri), audioGroup)
                }
            }
            index++
        }
        if (variants.isEmpty()) return null
        val fit = variants.filter { it.height <= maxHeight }
        val chosen = fit.maxByOrNull { it.bandwidth } ?: variants.minBy { it.height }
        return PickedVariant(chosen.uri, chosen.audioGroup)
    }

    private fun subtitleEntries(master: String): List<Pair<String, String>> {
        return mediaLines(master).mapNotNull { line ->
            if (!line.startsWith("#EXT-X-MEDIA") || !line.contains("TYPE=SUBTITLES")) return@mapNotNull null
            val uri = attr(line, "URI") ?: return@mapNotNull null
            val lang = attr(line, "LANGUAGE")?.lowercase() ?: "und"
            val forced = attr(line, "FORCED")?.equals("YES", true) == true
            if (forced) return@mapNotNull null
            lang to uri
        }.distinctBy { it.first }
    }

    private fun attr(line: String, name: String): String? {
        val match = Regex("""$name="([^"]+)"""").find(line) ?: return null
        return match.groupValues[1]
    }

    private fun downloadProgressive(
        url: String,
        dest: File,
        cancelled: () -> Boolean,
        onProgress: (Float) -> Unit,
    ) {
        try {
            FileOutputStream(dest).use { out ->
                if (cancelled()) error("İptal edildi")
                StreamHttp.copyTo(url, out, cancelled) { copied, length ->
                    if (length > 0L) onProgress((copied.toFloat() / length).coerceIn(0f, 1f))
                }
            }
            if (cancelled()) error("İptal edildi")
            onProgress(1f)
        } catch (error: Throwable) {
            if (cancelled() || error.message == "İptal edildi") dest.delete()
            throw error
        }
    }

    private fun stripVttHeader(raw: String): String {
        val lines = raw.replace("\r\n", "\n").lines()
        val start = lines.indexOfFirst { it.isNotBlank() && !it.startsWith("WEBVTT") && !it.startsWith("X-TIMESTAMP-MAP") && !it.startsWith("NOTE") }
        if (start < 0) return ""
        return lines.drop(start).joinToString("\n")
    }

    private fun looksLikePlaylist(url: String): Boolean {
        val lower = url.lowercase()
        return ".m3u8" in lower || ".m3u" in lower
    }

    private fun mediaLines(body: String): List<String> =
        body.replace("\r\n", "\n").lines().map { it.trim() }

    private fun fetchText(url: String): String? = runCatching { String(httpBytes(url), Charsets.UTF_8) }.getOrNull()

    private fun httpBytes(url: String): ByteArray = StreamHttp.readBytes(url)

    private fun resolve(base: String, ref: String): String {
        if (ref.startsWith("http://") || ref.startsWith("https://")) return ref
        return try {
            URL(URL(base), ref).toString()
        } catch (_: Exception) {
            ref
        }
    }
}
