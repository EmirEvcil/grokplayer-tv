package com.grokplayer.tv.data

import android.content.Context
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PreviewGrabber {
    private val memory = object : LruCache<String, ImageBitmap>(24) {}

    private fun key(uri: String, timeMs: Long): String = "$uri:${timeMs / 200}"

    fun cached(uri: Uri, timeMs: Long): ImageBitmap? = memory.get(key(uri.toString(), timeMs))

    suspend fun loadWindow(
        context: Context,
        uri: Uri,
        path: String?,
        timesMs: List<Long>,
        allowExo: Boolean = true,
        onFrame: (Long, ImageBitmap) -> Unit,
    ) {
        if (timesMs.isEmpty()) return
        val missing = timesMs.distinct().filter { cached(uri, it) == null }
        timesMs.forEach { time ->
            cached(uri, time)?.let { onFrame(time, it) }
        }
        if (missing.isEmpty()) return
        withContext(Dispatchers.IO) {
            val first = MediaProbe.frameBitmap(context, uri, path, missing.first())
            if (first != null) {
                remember(uri, missing.first(), first)?.let { onFrame(missing.first(), it) }
                missing.drop(1).forEach { time ->
                    val bmp = MediaProbe.frameBitmap(context, uri, path, time) ?: return@forEach
                    remember(uri, time, bmp)?.let { onFrame(time, it) }
                }
                return@withContext
            }
            if (!allowExo) return@withContext
            val grabbed = ExoFrameGrab.grabMany(context, uri, missing.take(4), timeoutMs = 5_000L)
            grabbed.forEach { (time, bmp) ->
                remember(uri, time, bmp)?.let { onFrame(time, it) }
            }
        }
    }

    private fun remember(uri: Uri, timeMs: Long, bitmap: android.graphics.Bitmap): ImageBitmap? {
        return try {
            val image = bitmap.asImageBitmap()
            memory.put(key(uri.toString(), timeMs), image)
            image
        } catch (_: Exception) {
            null
        }
    }
}

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
