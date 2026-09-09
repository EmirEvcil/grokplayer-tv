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

data class MediaDetails(
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val fps: Float,
) {
    val resolution: String
        get() = if (width > 0 && height > 0) "${width}×$height" else "—"
    val fpsLabel: String
        get() = if (fps > 0f) {
            val rounded = if (kotlin.math.abs(fps - fps.toInt()) < 0.08f) fps.toInt().toString() else "%.2f".format(fps)
            "$rounded fps"
        } else {
            "—"
        }
}

object MediaProbe {
    fun details(context: Context, uri: Uri, path: String?): MediaDetails {
        val fromRetriever = withRetriever(context, uri, path) { retriever ->
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val fps = parseFps(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE))
            MediaDetails(duration, width, height, fps)
        } ?: MediaDetails(0L, 0, 0, 0f)
        val fromExtractor = extractorDetails(context, uri, path)
        val fromAvi = aviDetails(context, uri, path)
        val duration = fromRetriever.durationMs.takeIf { it > 0L }
            ?: fromExtractor.durationMs.takeIf { it > 0L }
            ?: fromAvi.durationMs
        val width = fromRetriever.width.takeIf { it > 0 } ?: fromExtractor.width.takeIf { it > 0 } ?: fromAvi.width
        val height = fromRetriever.height.takeIf { it > 0 } ?: fromExtractor.height.takeIf { it > 0 } ?: fromAvi.height
        val fps = fromRetriever.fps.takeIf { it > 0f } ?: fromExtractor.fps.takeIf { it > 0f } ?: fromAvi.fps
        if (duration > 0L || width > 0 || fps > 0f) {
            return MediaDetails(duration, width, height, fps)
        }
        return MediaDetails(durationMs(context, uri, path), 0, 0, 0f)
    }

    internal fun parseFps(raw: String?): Float {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return 0f
        text.toFloatOrNull()?.let { if (it > 0f && it < 240f) return it }
        if ('/' in text) {
            val left = text.substringBefore('/').toFloatOrNull() ?: return 0f
            val right = text.substringAfter('/').toFloatOrNull() ?: return 0f
            if (right > 0f) {
                val value = left / right
                if (value > 0f && value < 240f) return value
            }
        }
        return 0f
    }

    internal fun saneFps(value: Float): Float =
        if (value > 0.5f && value < 240f) value else 0f

    private fun extractorDetails(context: Context, uri: Uri, path: String?): MediaDetails {
        val extractor = MediaExtractor()
        return try {
            setExtractorDataSource(extractor, context, uri, path)
            var duration = 0L
            var width = 0
            var height = 0
            var fps = 0f
            var videoTrack = -1
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (format.containsKey(MediaFormat.KEY_DURATION)) {
                    val micros = format.getLong(MediaFormat.KEY_DURATION)
                    if (micros > duration) duration = micros
                }
                if (!mime.startsWith("video/")) continue
                videoTrack = index
                if (format.containsKey(MediaFormat.KEY_WIDTH)) {
                    width = format.getInteger(MediaFormat.KEY_WIDTH)
                }
                if (format.containsKey(MediaFormat.KEY_HEIGHT)) {
                    height = format.getInteger(MediaFormat.KEY_HEIGHT)
                }
                fps = fpsFromFormat(format)
            }
            if (fps <= 0f && videoTrack >= 0) {
                fps = sampleFps(extractor, videoTrack)
            }
            MediaDetails(if (duration > 0L) duration / 1000L else 0L, width, height, fps)
        } catch (_: Exception) {
            MediaDetails(0L, 0, 0, 0f)
        } finally {
            runCatching { extractor.release() }
        }
    }

    internal fun fpsFromFormat(format: MediaFormat): Float {
        if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
            val fromInt = runCatching { format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat() }.getOrDefault(0f)
            saneFps(fromInt).takeIf { it > 0f }?.let { return it }
            val fromFloat = runCatching { format.getFloat(MediaFormat.KEY_FRAME_RATE) }.getOrDefault(0f)
            saneFps(fromFloat).takeIf { it > 0f }?.let { return it }
        }
        if (Build.VERSION.SDK_INT >= 29 && format.containsKey(MediaFormat.KEY_CAPTURE_RATE)) {
            val capture = runCatching { format.getInteger(MediaFormat.KEY_CAPTURE_RATE).toFloat() }.getOrDefault(0f)
            saneFps(capture).takeIf { it > 0f }?.let { return it }
            val captureF = runCatching { format.getFloat(MediaFormat.KEY_CAPTURE_RATE) }.getOrDefault(0f)
            saneFps(captureF).takeIf { it > 0f }?.let { return it }
        }
        return 0f
    }

    private fun sampleFps(extractor: MediaExtractor, track: Int): Float {
        return try {
            extractor.selectTrack(track)
            extractor.seekTo(0L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            var first = -1L
            var last = -1L
            var count = 0
            while (count < 24) {
                val time = extractor.sampleTime
                if (time < 0L) break
                if (first < 0L) first = time
                last = time
                count++
                if (!extractor.advance()) break
            }
            if (count >= 2 && last > first) {
                saneFps(((count - 1) * 1_000_000.0 / (last - first)).toFloat())
            } else {
                0f
            }
        } catch (_: Exception) {
            0f
        }
    }

    private fun aviDetails(context: Context, uri: Uri, path: String?): MediaDetails {
        val header = readPrefix(context, uri, path, 256 * 1024) ?: return MediaDetails(0L, 0, 0, 0f)
        return aviHeaderDetails(header)
    }

    internal fun aviHeaderDetails(bytes: ByteArray): MediaDetails {
        if (bytes.size < 16) return MediaDetails(0L, 0, 0, 0f)
        if (!asciiEquals(bytes, 0, "RIFF") || !asciiEquals(bytes, 8, "AVI ")) return MediaDetails(0L, 0, 0, 0f)
        val avih = indexOfAscii(bytes, "avih")
        if (avih < 0 || avih + 40 >= bytes.size) return MediaDetails(0L, 0, 0, 0f)
        val microsPerFrame = readU32Le(bytes, avih + 8)
        val totalFrames = readU32Le(bytes, avih + 24)
        val width = readU32Le(bytes, avih + 32).toInt()
        val height = readU32Le(bytes, avih + 36).toInt()
        val fps = if (microsPerFrame > 0L) saneFps(1_000_000f / microsPerFrame) else 0f
        val duration = if (microsPerFrame > 0L && totalFrames > 0L) (totalFrames * microsPerFrame) / 1000L else 0L
        return MediaDetails(duration, width, height, fps)
    }

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
