package com.grokplayer.tv.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
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
) {
    fun toVideo(): LibraryVideo {
        val file = localPath?.let { File(it) }
        return LibraryVideo(
            id = "download:$id",
            title = title,
            uri = if (file != null) Uri.fromFile(file) else Uri.parse(url),
            durationMs = 0L,
            format = file?.extension?.uppercase() ?: "VOD",
            source = StorageSource.Internal,
            dateAdded = addedAt,
            lastModified = file?.lastModified() ?: addedAt,
            path = localPath,
            originUrl = url,
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
        items = load().map { item ->
            if (item.status == DownloadStatus.Running) item.copy(status = DownloadStatus.Queued) else item
        }
        persist()
        pump(settings.downloadHeight)
    }

    fun statusOf(url: String): DownloadStatus? = items.firstOrNull { it.url == url }?.status

    fun enqueue(title: String, url: String, maxHeight: Int = settings.downloadHeight): String? {
        val existing = items.firstOrNull { it.url == url }
        if (existing?.status == DownloadStatus.Queued || existing?.status == DownloadStatus.Running) {
            return null
        }
        if (existing?.status == DownloadStatus.Done && existing.localPath?.let { File(it).exists() } == true) {
            return existing.id
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
        )
        items = listOf(item) + items.filterNot { it.url == url }
        persist()
        pump(maxHeight)
        return item.id
    }

    fun cancel(id: String) {
        cancelled += id
        items = items.map {
            if (it.id == id && it.status != DownloadStatus.Done) {
                it.copy(status = DownloadStatus.Failed, error = "İptal edildi")
            } else {
                it
            }
        }
        persist()
    }

    fun remove(id: String) {
        cancelled += id
        val item = items.firstOrNull { it.id == id }
        item?.localPath?.let { path ->
            val file = File(path)
            file.delete()
            file.parentFile?.listFiles()
                ?.filter { it.nameWithoutExtension == file.nameWithoutExtension && it != file }
                ?.forEach { it.delete() }
        }
        items = items.filterNot { it.id == id }
        persist()
    }

    private fun pump(maxHeight: Int) {
        if (job?.isActive == true) return
        val next = items.firstOrNull { it.status == DownloadStatus.Queued } ?: return
        job = scope.launch { runItem(next, maxHeight) }
    }

    private suspend fun runItem(item: DownloadItem, maxHeight: Int) {
        withContext(Dispatchers.Main) {
            items = items.map {
                if (it.id == item.id) it.copy(status = DownloadStatus.Running, progress = 0f, error = null) else it
            }
        }
        val stem = safeName(item.title)
        val dest = File(DownloadPaths.dir(app), "$stem.ts")
        val cap = if (maxHeight <= 0) Int.MAX_VALUE else maxHeight
        val result = runCatching {
            VodDownloader.download(
                url = item.url,
                dest = dest,
                maxHeight = cap,
                cancelled = { item.id in cancelled },
                onProgress = { value ->
                    scope.launch(Dispatchers.Main.immediate) {
                        items = items.map { current ->
                            if (current.id == item.id) current.copy(progress = value) else current
                        }
                    }
                },
            )
        }
        withContext(Dispatchers.Main) {
            val file = result.getOrNull()
            items = items.map { current ->
                if (current.id != item.id) {
                    current
                } else if (file != null && file.exists() && file.length() > 0L) {
                    current.copy(
                        status = DownloadStatus.Done,
                        progress = 1f,
                        localPath = file.absolutePath,
                        error = null,
                    )
                } else {
                    dest.delete()
                    current.copy(
                        status = DownloadStatus.Failed,
                        error = result.exceptionOrNull()?.message ?: "İndirilemedi",
                    )
                }
            }
            persist()
        }
        job = null
        if (item.id !in cancelled) pump(maxHeight)
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
                    .put("addedAt", item.addedAt),
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
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun safeName(title: String): String {
        val cleaned = title.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_')
        return cleaned.ifBlank { "vod" }.take(48)
    }
}

internal object VodDownloader {
    fun download(
        url: String,
        dest: File,
        maxHeight: Int,
        cancelled: () -> Boolean,
        onProgress: (Float) -> Unit,
    ): File {
        dest.parentFile?.mkdirs()
        if ((dest.parentFile?.usableSpace ?: 0L) < 80L * 1024L * 1024L) {
            error("Yetersiz boş alan")
        }
        val resolved = StreamProbe.resolveFinalUrl(url)
        val head = fetchText(resolved)
        if (head == null || (!head.contains("#EXTM3U") && !looksLikePlaylist(resolved))) {
            val mp4 = File(dest.parentFile, dest.nameWithoutExtension + ".mp4")
            downloadProgressive(resolved, mp4, cancelled, onProgress)
            return mp4
        }
        if (head.contains("#EXT-X-KEY")) error("Şifreli yayın indirilemez")
        val variant = pickVariant(resolved, head, maxHeight)
        val mediaUrl = variant ?: resolved
        val media = if (variant != null) fetchText(variant) ?: error("Liste alınamadı") else head
        if (media.contains("#EXT-X-KEY")) error("Şifreli yayın indirilemez")
        if (!media.contains("#EXT-X-ENDLIST") && media.contains("#EXTINF")) {
            error("Canlı yayın indirilemez")
        }
        val segments = mediaLines(media).filter { it.isNotEmpty() && !it.startsWith("#") }
        if (segments.isEmpty()) {
            val mp4 = File(dest.parentFile, dest.nameWithoutExtension + ".mp4")
            downloadProgressive(mediaUrl, mp4, cancelled, onProgress)
            return mp4
        }
        val unique = segments.distinct()
        if (unique.size == 1) {
            downloadProgressive(resolve(mediaUrl, unique.first()), dest, cancelled, onProgress)
        } else {
            FileOutputStream(dest).use { out ->
                unique.forEachIndexed { index, ref ->
                    if (cancelled()) error("İptal edildi")
                    httpCopy(resolve(mediaUrl, ref), out)
                    onProgress((index + 1).toFloat() / unique.size)
                }
            }
        }
        downloadSubtitles(resolved, head, dest)
        return dest
    }

    private fun downloadSubtitles(masterUrl: String, master: String, videoFile: File) {
        subtitleEntries(master).forEach { (lang, uri) ->
            val playlistUrl = resolve(masterUrl, uri)
            val body = fetchText(playlistUrl) ?: return@forEach
            val parts = mediaLines(body).filter { it.isNotEmpty() && !it.startsWith("#") }
            if (parts.isEmpty()) return@forEach
            val outFile = File(videoFile.parentFile, "${videoFile.nameWithoutExtension}.$lang.vtt")
            val text = buildString {
                appendLine("WEBVTT")
                appendLine()
                parts.forEach { ref ->
                    val chunk = runCatching { String(httpBytes(resolve(playlistUrl, ref)), Charsets.UTF_8) }
                        .getOrNull() ?: return@forEach
                    append(stripVttHeader(chunk).trim())
                    appendLine()
                    appendLine()
                }
            }
            outFile.writeText(text, Charsets.UTF_8)
        }
    }

    private fun pickVariant(masterUrl: String, body: String, maxHeight: Int): String? {
        val lines = mediaLines(body)
        data class Variant(val height: Int, val bandwidth: Int, val uri: String)
        val variants = mutableListOf<Variant>()
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            if (line.startsWith("#EXT-X-STREAM-INF:")) {
                val height = Regex("RESOLUTION=(\\d+)x(\\d+)", RegexOption.IGNORE_CASE)
                    .find(line)?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0
                val bandwidth = Regex("BANDWIDTH=(\\d+)", RegexOption.IGNORE_CASE)
                    .find(line)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                val uri = lines.drop(index + 1).firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
                if (uri != null && height > 0) {
                    variants += Variant(height, bandwidth, resolve(masterUrl, uri))
                }
            }
            index++
        }
        if (variants.isEmpty()) return null
        val fit = variants.filter { it.height <= maxHeight }
        val chosen = fit.maxByOrNull { it.bandwidth } ?: variants.minBy { it.height }
        return chosen.uri
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
        val conn = open(url)
        val total = conn.contentLengthLong.takeIf { it > 0L } ?: -1L
        conn.inputStream.use { input ->
            FileOutputStream(dest).use { out ->
                val buf = ByteArray(64 * 1024)
                var copied = 0L
                while (true) {
                    if (cancelled()) error("İptal edildi")
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    copied += n
                    if (total > 0L) onProgress((copied.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
        conn.disconnect()
        onProgress(1f)
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

    private fun httpBytes(url: String): ByteArray {
        val conn = open(url)
        return try {
            conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    private fun httpCopy(url: String, out: FileOutputStream): Long {
        val conn = open(url)
        return try {
            conn.inputStream.use { input ->
                val buf = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    total += n
                }
                total
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection {
        val conn = URL(StreamProbe.resolveFinalUrl(url)).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", StreamProbe.USER_AGENT)
        return conn
    }

    private fun resolve(base: String, ref: String): String {
        if (ref.startsWith("http://") || ref.startsWith("https://")) return ref
        return try {
            URL(URL(base), ref).toString()
        } catch (_: Exception) {
            ref
        }
    }
}
