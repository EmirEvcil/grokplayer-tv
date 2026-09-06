package com.grokplayer.tv.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

object MediaProbe {
    fun durationMs(context: Context, uri: Uri, path: String?): Long {
        retrieverDuration(context, uri, path).takeIf { it > 0L }?.let { return it }
        extractorDuration(context, uri, path).takeIf { it > 0L }?.let { return it }
        return containerDuration(context, uri, path)
    }

    fun frameBitmap(context: Context, uri: Uri, path: String?, timeMs: Long, maxWidth: Int = 640): Bitmap? {
        val fromRetriever = withRetriever(context, uri, path) { retriever ->
            val timeUs = timeMs.coerceAtLeast(0L) * 1000L
            retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(-1)
                ?: retriever.frameAtTime
        }
        if (fromRetriever != null) return scale(fromRetriever, maxWidth)
        return scale(thumbnailUtils(path), maxWidth)
    }

    fun frameImage(context: Context, uri: Uri, path: String?, timeMs: Long): ImageBitmap? =
        frameBitmap(context, uri, path, timeMs)?.asImageBitmap()

    private fun retrieverDuration(context: Context, uri: Uri, path: String?): Long {
        return withRetriever(context, uri, path) { retriever ->
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } ?: 0L
    }

    private fun extractorDuration(context: Context, uri: Uri, path: String?): Long {
        val extractor = MediaExtractor()
        return try {
            setExtractorDataSource(extractor, context, uri, path)
            var best = 0L
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                if (format.containsKey(MediaFormat.KEY_DURATION)) {
                    val micros = format.getLong(MediaFormat.KEY_DURATION)
                    if (micros > best) best = micros
                }
            }
            if (best > 0L) best / 1000L else 0L
        } catch (_: Exception) {
            0L
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun thumbnailUtils(path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.canRead()) return null
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                ThumbnailUtils.createVideoThumbnail(file, Size(640, 360), null)
            } else {
                @Suppress("DEPRECATION")
                ThumbnailUtils.createVideoThumbnail(path, MediaStore.Images.Thumbnails.MINI_KIND)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun <T> withRetriever(
        context: Context,
        uri: Uri,
        path: String?,
        block: (MediaMetadataRetriever) -> T?,
    ): T? {
        val retriever = MediaMetadataRetriever()
        return try {
            if (!setRetrieverDataSource(retriever, context, uri, path)) return null
            block(retriever)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun setRetrieverDataSource(
        retriever: MediaMetadataRetriever,
        context: Context,
        uri: Uri,
        path: String?,
    ): Boolean {
        if (!path.isNullOrBlank()) {
            val file = File(path)
            if (file.canRead()) {
                runCatching {
                    retriever.setDataSource(path)
                    return true
                }
                runCatching {
                    file.inputStream().use { stream ->
                        retriever.setDataSource(stream.fd)
                    }
                    return true
                }
            }
        }
        runCatching {
            retriever.setDataSource(context, uri)
            return true
        }
        runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                retriever.setDataSource(pfd.fileDescriptor)
            } ?: return false
            return true
        }
        return false
    }

    private fun setExtractorDataSource(
        extractor: MediaExtractor,
        context: Context,
        uri: Uri,
        path: String?,
    ) {
        if (!path.isNullOrBlank() && File(path).canRead()) {
            extractor.setDataSource(path)
            return
        }
        extractor.setDataSource(context, uri, null)
    }

    private fun containerDuration(context: Context, uri: Uri, path: String?): Long {
        val header = readPrefix(context, uri, path, 256 * 1024) ?: return 0L
        aviDurationMs(header).takeIf { it > 0L }?.let { return it }
        return 0L
    }

    private fun aviDurationMs(bytes: ByteArray): Long {
        if (bytes.size < 16) return 0L
        if (!asciiEquals(bytes, 0, "RIFF") || !asciiEquals(bytes, 8, "AVI ")) return 0L
        val avih = indexOfAscii(bytes, "avih")
        if (avih < 0 || avih + 28 >= bytes.size) return 0L
        val microsPerFrame = readU32Le(bytes, avih + 8)
        val totalFrames = readU32Le(bytes, avih + 24)
        if (microsPerFrame == 0L || totalFrames == 0L) return 0L
        return (totalFrames * microsPerFrame) / 1000L
    }

    private fun readPrefix(context: Context, uri: Uri, path: String?, size: Int): ByteArray? {
        return try {
            val stream = if (!path.isNullOrBlank() && File(path).canRead()) {
                File(path).inputStream()
            } else {
                context.contentResolver.openInputStream(uri)
            } ?: return null
            stream.use { input ->
                val buffer = ByteArray(size)
                var offset = 0
                while (offset < buffer.size) {
                    val read = input.read(buffer, offset, buffer.size - offset)
                    if (read <= 0) break
                    offset += read
                }
                if (offset == buffer.size) buffer else buffer.copyOf(offset)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun asciiEquals(bytes: ByteArray, offset: Int, token: String): Boolean {
        if (offset + token.length > bytes.size) return false
        for (i in token.indices) {
            if (bytes[offset + i].toInt().toChar() != token[i]) return false
        }
        return true
    }

    private fun indexOfAscii(bytes: ByteArray, token: String): Int {
        val first = token[0].code.toByte()
        val last = bytes.size - token.length
        var i = 0
        while (i <= last) {
            if (bytes[i] == first && asciiEquals(bytes, i, token)) return i
            i++
        }
        return -1
    }

    private fun readU32Le(bytes: ByteArray, offset: Int): Long {
        if (offset + 3 >= bytes.size) return 0L
        return (bytes[offset].toLong() and 0xFF) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 8) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 3].toLong() and 0xFF) shl 24)
    }

    private fun scale(source: Bitmap?, maxWidth: Int): Bitmap? {
        if (source == null) return null
        if (source.width <= maxWidth) return source
        val height = (source.height.toFloat() / source.width * maxWidth).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(source, maxWidth, height, true)
        if (scaled != source) source.recycle()
        return scaled
    }
}
