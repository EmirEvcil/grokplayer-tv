package com.grokplayer.tv.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.io.File

object ThumbnailCache {
    const val TILE_WIDTH = 320
    const val HERO_WIDTH = 1280

    @Volatile
    var playbackActive: Boolean = false

    private val lock = Any()

    fun existing(context: Context, key: String, maxWidth: Int = TILE_WIDTH): File? {
        val file = fileFor(context, key, maxWidth)
        return file.takeIf { it.exists() && it.length() > 32L }
    }

    fun extract(
        context: Context,
        key: String,
        uri: Uri,
        path: String?,
        timeMs: Long = 1_000L,
        maxWidth: Int = TILE_WIDTH,
    ): File? {
        existing(context, key, maxWidth)?.let { return it }
        if (playbackActive) return null
        val scheme = uri.scheme?.lowercase()
        val remote = scheme == "http" || scheme == "https"
        synchronized(lock) {
            existing(context, key, maxWidth)?.let { return it }
            if (playbackActive) return null
            val at = timeMs.coerceAtLeast(0L)
            val bitmap = try {
                if (remote && path?.let { File(it).isFile } != true) {
                    ExoFrameGrab.grab(context, uri, at, timeoutMs = 8_000L)
                } else {
                    localPreviewBitmap(context, path, uri, at, maxWidth)
                        ?: MediaProbe.frameBitmap(context, uri, path, at, maxWidth)
                        ?: ExoFrameGrab.grab(context, uri, at, timeoutMs = 8_000L, rejectBlack = false)
                }
            } catch (_: Throwable) {
                null
            } ?: return null
            return write(context, key, bitmap, recycle = true, maxWidth = maxWidth)
        }
    }

    private fun localPreviewBitmap(
        context: Context,
        path: String?,
        uri: Uri,
        timeMs: Long,
        maxWidth: Int,
    ): Bitmap? {
        val file = path?.let { File(it) }?.takeIf { it.isFile && it.canRead() } ?: return null
        if (file.extension.equals("m3u8", true)) {
            val slice = hlsPreviewSlice(file, timeMs) ?: return null
            return MediaProbe.frameInSegment(context, slice.file, slice.offsetMs, maxWidth)
        }
        return MediaProbe.frameBitmap(context, uri, path, timeMs, maxWidth, exact = true)
    }

    fun save(context: Context, key: String, bitmap: Bitmap, maxWidth: Int = TILE_WIDTH): File? {
        if (ExoFrameGrab.isMostlyBlack(bitmap)) return existing(context, key, maxWidth)
        return write(context, key, bitmap, recycle = false, maxWidth = maxWidth)
    }

    private fun write(
        context: Context,
        key: String,
        bitmap: Bitmap,
        recycle: Boolean,
        maxWidth: Int,
    ): File? {
        val dest = fileFor(context, key, maxWidth)
        val scaled = scaleTo(bitmap, maxWidth)
        return try {
            dest.outputStream().use { stream ->
                val quality = if (maxWidth >= HERO_WIDTH) 90 else 78
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            }
            dest.takeIf { it.exists() && it.length() > 32L }
        } catch (_: Exception) {
            null
        } finally {
            if (scaled !== bitmap && !scaled.isRecycled) scaled.recycle()
            if (recycle && !bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun scaleTo(source: Bitmap, maxWidth: Int): Bitmap {
        if (source.width <= maxWidth) return source
        val height = (source.height.toFloat() / source.width * maxWidth).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, maxWidth, height, true)
    }

    private fun fileFor(context: Context, key: String, maxWidth: Int): File {
        val dir = File(context.cacheDir, "thumbs").apply { mkdirs() }
        val safe = key.hashCode().toUInt().toString()
        val name = if (maxWidth <= TILE_WIDTH) "$safe.jpg" else "${safe}_w$maxWidth.jpg"
        return File(dir, name)
    }
}
