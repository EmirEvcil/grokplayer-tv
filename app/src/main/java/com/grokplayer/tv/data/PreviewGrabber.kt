package com.grokplayer.tv.data

import android.content.Context
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.abs
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

object SeekPreviewPlan {
    const val TILE_WIDTH = 240
    const val FIRST_WAVE = 12
    const val MAX_PRELOAD = 64
    const val BUCKET_MS = 200L
    const val NEAREST_MS = 1_500L

    fun times(durationMs: Long, stepMs: Long): List<Long> = seekTimes(durationMs, stepMs)

    fun prioritize(times: List<Long>, aroundMs: Long): List<Long> =
        times.sortedBy { abs(it - aroundMs) }

    fun firstWave(times: List<Long>, aroundMs: Long, size: Int = FIRST_WAVE): List<Long> =
        prioritize(times, aroundMs).take(size.coerceAtLeast(1))

    fun aroundBucket(timeMs: Long, sizeMs: Long = 5_000L): Long =
        timeMs.coerceAtLeast(0L) / sizeMs.coerceAtLeast(1L)

    fun workList(times: List<Long>, aroundMs: Long, have: Set<Long>): List<Long> =
        prioritize(times, aroundMs).filter { it !in have }.take(MAX_PRELOAD)

    fun bucket(timeMs: Long): Long = timeMs / BUCKET_MS

    fun nearest(times: List<Long>, targetMs: Long, maxDeltaMs: Long = NEAREST_MS): Long? {
        if (times.isEmpty()) return null
        val best = times.minBy { abs(it - targetMs) }
        return best.takeIf { abs(it - targetMs) <= maxDeltaMs }
    }

    fun canExtract(uri: Uri, path: String?): Boolean =
        canExtract(uri.scheme, uri.path, path)

    fun canExtract(scheme: String?, uriPath: String?, filePath: String?): Boolean {
        val path = (filePath ?: uriPath).orEmpty().lowercase()
        if (path.endsWith(".m3u8") || path.endsWith(".m3u") || path.endsWith(".mpd")) return false
        if (!filePath.isNullOrBlank() && java.io.File(filePath).canRead()) return true
        val kind = scheme?.lowercase()
        if (kind == "file" || kind == "content") return true
        if (kind != "http" && kind != "https") return false
        val name = uriPath.orEmpty().lowercase()
        return name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".webm") ||
            name.endsWith(".mov") || name.endsWith(".m4v")
    }
}

object PreviewGrabber {
    private val memory = object : LruCache<String, ImageBitmap>(96) {}

    private fun key(uri: String, timeMs: Long): String = "$uri:${SeekPreviewPlan.bucket(timeMs)}"

    fun cached(uri: Uri, timeMs: Long): ImageBitmap? = memory.get(key(uri.toString(), timeMs))

    fun put(uri: Uri, timeMs: Long, image: ImageBitmap) {
        memory.put(key(uri.toString(), timeMs), image)
    }

    suspend fun preload(
        context: Context,
        uri: Uri,
        path: String?,
        timesMs: List<Long>,
        aroundMs: Long,
        onFrame: (Long, ImageBitmap) -> Unit,
        storyboardSpec: String? = null,
        durationMs: Long = 0L,
    ) {
        if (timesMs.isEmpty()) return
        timesMs.forEach { time ->
            cached(uri, time)?.let { onFrame(time, it) }
        }
        val have = timesMs.filter { cached(uri, it) != null }.toMutableSet()
        val missing = SeekPreviewPlan.workList(timesMs, aroundMs, have)
        if (missing.isEmpty()) return
        withContext(Dispatchers.IO) {
            val playing = ThumbnailCache.playbackActive
            val batch = if (playing) missing.take(SeekPreviewPlan.FIRST_WAVE) else missing
            val playlist = path?.let { File(it) }?.takeIf { it.isFile && it.extension.equals("m3u8", true) }
            val onDevice = uri.scheme == "file" || uri.scheme == "content" ||
                (!path.isNullOrBlank() && File(path).isFile)
            val steps = previewSteps(
                playlist = playlist != null,
                storyboard = !storyboardSpec.isNullOrBlank(),
                onDevice = onDevice && SeekPreviewPlan.canExtract(uri, path),
                playing = playing,
            )
            var pending = batch
            for (step in steps) {
                if (pending.isEmpty()) break
                when (step) {
                    PreviewStep.LocalHls -> playlist?.let {
                        grabHls(context, it, pending, uri, onFrame, paced = playing)
                    }
                    PreviewStep.Storyboard -> grabStoryboard(
                        storyboardSpec.orEmpty(),
                        pending,
                        durationMs,
                        uri,
                        onFrame,
                    )
                    PreviewStep.DeviceFile -> grabOneByOne(
                        context,
                        uri,
                        path,
                        pending,
                        onFrame,
                        paced = playing && onDevice,
                    )
                }
                pending = pending.filter { cached(uri, it) == null }
            }
        }
    }

    private suspend fun grabHls(
        context: Context,
        playlist: File,
        times: List<Long>,
        uri: Uri,
        onFrame: (Long, ImageBitmap) -> Unit,
        paced: Boolean,
    ) {
        var misses = 0
        times.forEach { time ->
            yield()
            if (paced) delay(180)
            val slice = hlsPreviewSlice(playlist, time) ?: return@forEach
            val jpeg = previewJpegForTime(playlist, time)
            var bitmap = readPreviewJpeg(jpeg)
            if (bitmap == null) {
                bitmap = MediaProbe.frameInSegment(
                    context,
                    slice.file,
                    slice.offsetMs,
                    SeekPreviewPlan.TILE_WIDTH,
                )
                if (bitmap != null) writePreviewJpeg(jpeg, bitmap)
            }
            if (bitmap == null) {
                misses += 1
                if (misses >= 2) return
                return@forEach
            }
            misses = 0
            val image = runCatching { bitmap.asImageBitmap() }.getOrNull() ?: return@forEach
            put(uri, time, image)
            withContext(Dispatchers.Main.immediate) { onFrame(time, image) }
        }
    }

    private suspend fun grabOneByOne(
        context: Context,
        uri: Uri,
        path: String?,
        times: List<Long>,
        onFrame: (Long, ImageBitmap) -> Unit,
        paced: Boolean = false,
    ) {
        times.forEach { time ->
            yield()
            if (paced) delay(80)
            val bitmap = MediaProbe.frameBitmap(
                context,
                uri,
                path,
                time,
                SeekPreviewPlan.TILE_WIDTH,
                exact = true,
            )
                ?: return@forEach
            val image = runCatching { bitmap.asImageBitmap() }.getOrNull() ?: return@forEach
            put(uri, time, image)
            withContext(Dispatchers.Main.immediate) { onFrame(time, image) }
        }
    }

    private suspend fun grabStoryboard(
        spec: String,
        times: List<Long>,
        durationMs: Long,
        uri: Uri,
        onFrame: (Long, ImageBitmap) -> Unit,
    ) {
        val level = StoryboardSpec.fastLevel(StoryboardSpec.parse(spec)) ?: return
        val sheets = HashMap<String, android.graphics.Bitmap>()
        times.forEach { time ->
            yield()
            val cell = level.cellAt(time, durationMs) ?: return@forEach
            val sheet = sheets.getOrPut(cell.url) {
                val bytes = runCatching { StreamHttp.readBytes(cell.url) }.getOrNull()
                    ?: return@forEach
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: return@forEach
            }
            val x = (cell.column * cell.width).coerceAtLeast(0)
            val y = (cell.row * cell.height).coerceAtLeast(0)
            if (x + cell.width > sheet.width || y + cell.height > sheet.height) return@forEach
            val cropped = runCatching {
                android.graphics.Bitmap.createBitmap(sheet, x, y, cell.width, cell.height)
            }.getOrNull() ?: return@forEach
            val image = runCatching { cropped.asImageBitmap() }.getOrNull() ?: return@forEach
            put(uri, time, image)
            withContext(Dispatchers.Main.immediate) { onFrame(time, image) }
        }
    }
}

internal enum class PreviewStep { LocalHls, Storyboard, DeviceFile }

internal fun previewSteps(
    playlist: Boolean,
    storyboard: Boolean,
    onDevice: Boolean,
    playing: Boolean,
): List<PreviewStep> {
    val steps = ArrayList<PreviewStep>(3)
    if (playlist) steps += PreviewStep.LocalHls
    if (storyboard) steps += PreviewStep.Storyboard
    if (!playlist && (onDevice || !playing)) steps += PreviewStep.DeviceFile
    return steps
}

internal fun exactPreviewFrame(frames: Map<Long, Int>, time: Long): Int? = frames[time]

internal fun previewJpegForTime(playlist: File, timeMs: Long): File {
    val root = playlist.parentFile ?: playlist
    return File(root, "previews/t-$timeMs.jpg")
}

internal fun posterUsesNetwork(localPath: String?): Boolean {
    if (localPath.isNullOrBlank()) return true
    return !File(localPath).isFile
}

internal fun readPreviewJpeg(file: File): android.graphics.Bitmap? {
    if (!file.isFile || file.length() < 32L) return null
    return android.graphics.BitmapFactory.decodeFile(file.absolutePath)
}

internal fun writePreviewJpeg(file: File, bitmap: android.graphics.Bitmap) {
    file.parentFile?.mkdirs()
    runCatching {
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, it) }
    }
}

internal data class HlsPreviewSlice(val file: File, val offsetMs: Long)

internal fun hlsPreviewSlice(playlist: File, timeMs: Long): HlsPreviewSlice? {
    if (!playlist.isFile) return null
    val text = runCatching { playlist.readText() }.getOrNull() ?: return null
    val media = hlsMediaPlaylist(playlist, text) ?: return null
    val body = if (media == playlist) text else runCatching { media.readText() }.getOrNull() ?: return null
    var cursor = 0L
    var pendingMs = -1L
    var last: HlsPreviewSlice? = null
    for (raw in body.replace("\r\n", "\n").lines()) {
        val line = raw.trim()
        if (line.startsWith("#EXTINF:", ignoreCase = true)) {
            val seconds = line.substringAfter(':').substringBefore(',').toDoubleOrNull() ?: -1.0
            pendingMs = if (seconds > 0.0) (seconds * 1000.0).toLong() else -1L
            continue
        }
        if (pendingMs < 0L || line.isEmpty() || line.startsWith("#")) continue
        val start = cursor
        cursor += pendingMs
        val segment = File(media.parentFile, line.substringAfterLast('/').substringBefore('?'))
        if (segment.isFile) {
            val slice = HlsPreviewSlice(segment, (timeMs - start).coerceAtLeast(0L))
            if (timeMs < cursor) return slice
            last = slice
        }
        pendingMs = -1L
    }
    return last
}

private fun hlsMediaPlaylist(playlist: File, text: String): File? {
    if (text.contains("#EXTINF:", ignoreCase = true)) return playlist
    val folder = playlist.parentFile ?: return null
    val video = File(folder, "v.m3u8")
    if (video.isFile) return video
    val child = text.replace("\r\n", "\n").lines().map { it.trim() }
        .firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
        ?: return null
    val next = File(folder, child.substringAfterLast('/').substringBefore('?'))
    return next.takeIf { it.isFile }
}

fun seekCursor(previewVisible: Boolean, previewPos: Long, position: Long): Long =
    if (previewVisible) previewPos else position

fun seekTimes(duration: Long, step: Long): List<Long> {
    if (duration <= 0L || step <= 0L) return listOf(0L)
    var stride = step.coerceAtLeast(1_000L)
    if (duration / stride > 360) {
        stride = ((duration / 360L) / 1_000L).coerceAtLeast(1L) * 1_000L
    }
    val times = ArrayList<Long>()
    var t = 0L
    while (t < duration - stride / 2 && times.size < 400) {
        times += t
        t += stride
    }
    val end = duration
    val last = times.lastOrNull()
    if (last == null) {
        times += 0L
    } else if (end / 1000L != last / 1000L && end - last >= step / 2) {
        times += end
    }
    return times
}
