package com.grokplayer.tv.data

import android.content.Context
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
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
    ) {
        if (timesMs.isEmpty()) return
        timesMs.forEach { time ->
            cached(uri, time)?.let { onFrame(time, it) }
        }
        if (!SeekPreviewPlan.canExtract(uri, path)) return
        val missing = SeekPreviewPlan.prioritize(timesMs, aroundMs)
            .filter { cached(uri, it) == null }
            .take(SeekPreviewPlan.MAX_PRELOAD)
        if (missing.isEmpty()) return
        withContext(Dispatchers.IO) {
            val first = missing.take(SeekPreviewPlan.FIRST_WAVE)
            val rest = missing.drop(first.size)
            grab(context, uri, path, first, onFrame)
            if (rest.isNotEmpty()) {
                grab(context, uri, path, rest, onFrame)
            }
        }
    }

    private suspend fun grab(
        context: Context,
        uri: Uri,
        path: String?,
        times: List<Long>,
        onFrame: (Long, ImageBitmap) -> Unit,
    ) {
        if (times.isEmpty()) return
        val frames = MediaProbe.framesAt(context, uri, path, times, SeekPreviewPlan.TILE_WIDTH)
        frames.forEach { (time, bitmap) ->
            yield()
            val image = runCatching { bitmap.asImageBitmap() }.getOrNull() ?: return@forEach
            put(uri, time, image)
            withContext(Dispatchers.Main.immediate) { onFrame(time, image) }
        }
    }
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
