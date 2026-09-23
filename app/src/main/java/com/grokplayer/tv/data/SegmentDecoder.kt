package com.grokplayer.tv.data

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.media.Image
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal object SegmentDecoder {
    fun frameAt(file: File, offsetMs: Long, maxWidth: Int): Bitmap? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var reader: ImageReader? = null
        var thread: HandlerThread? = null
        return try {
            extractor.setDataSource(file.absolutePath)
            var track = -1
            var format: MediaFormat? = null
            for (index in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(index)
                val mime = candidate.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/")) {
                    track = index
                    format = candidate
                    break
                }
            }
            val video = format ?: return null
            if (track < 0) return null
            extractor.selectTrack(track)
            val width = video.getInteger(MediaFormat.KEY_WIDTH)
            val height = video.getInteger(MediaFormat.KEY_HEIGHT)
            val mime = video.getString(MediaFormat.KEY_MIME) ?: return null
            val images = ImageReader.newInstance(width, height, ImageFormat.YUV_420_888, 2)
            reader = images
            val worker = HandlerThread("segment-frame")
            worker.start()
            thread = worker
            val ready = CountDownLatch(1)
            val holder = arrayOfNulls<Bitmap>(1)
            images.setOnImageAvailableListener({ source ->
                val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    holder[0] = yuvToBitmap(image, maxWidth)
                } finally {
                    image.close()
                    ready.countDown()
                }
            }, Handler(worker.looper))
            val decoder = MediaCodec.createDecoderByType(mime)
            codec = decoder
            decoder.configure(video, images.surface, null, 0)
            decoder.start()
            val info = MediaCodec.BufferInfo()
            val targetUs = offsetMs.coerceAtLeast(0L) * 1_000L
            var inputDone = false
            val started = android.os.SystemClock.elapsedRealtime()
            while (android.os.SystemClock.elapsedRealtime() - started < 3_000L) {
                if (!inputDone) {
                    val inputIndex = decoder.dequeueInputBuffer(5_000)
                    if (inputIndex >= 0) {
                        val buffer = decoder.getInputBuffer(inputIndex)
                        val size = if (buffer == null) -1 else extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val sampleUs = extractor.sampleTime.coerceAtLeast(0L)
                            decoder.queueInputBuffer(inputIndex, 0, size, sampleUs, 0)
                            extractor.advance()
                        }
                    }
                }
                val outputIndex = decoder.dequeueOutputBuffer(info, 5_000)
                if (outputIndex >= 0) {
                    val end = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    val render = info.presentationTimeUs + 40_000L >= targetUs || end
                    decoder.releaseOutputBuffer(outputIndex, render)
                    if (render) {
                        ready.await(700, TimeUnit.MILLISECONDS)
                        break
                    }
                } else if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER && inputDone) {
                    break
                }
            }
            holder[0]
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { reader?.close() }
            runCatching { extractor.release() }
            thread?.quitSafely()
        }
    }

    private fun yuvToBitmap(image: Image, maxWidth: Int): Bitmap {
        val width = image.width
        val height = image.height
        val outWidth = width.coerceAtMost(maxWidth.coerceAtLeast(1))
        val outHeight = (height.toFloat() * outWidth / width).toInt().coerceAtLeast(1)
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val y = yPlane.buffer
        val u = uPlane.buffer
        val v = vPlane.buffer
        val yRow = yPlane.rowStride
        val uvRow = uPlane.rowStride
        val uvPixel = uPlane.pixelStride.coerceAtLeast(1)
        val pixels = IntArray(outWidth * outHeight)
        for (row in 0 until outHeight) {
            val srcRow = row * height / outHeight
            for (col in 0 until outWidth) {
                val srcCol = col * width / outWidth
                val yValue = y.get(srcRow * yRow + srcCol).toInt() and 0xFF
                val uvIndex = (srcRow / 2) * uvRow + (srcCol / 2) * uvPixel
                val uValue = (u.get(uvIndex).toInt() and 0xFF) - 128
                val vValue = (v.get(uvIndex).toInt() and 0xFF) - 128
                val c = yValue - 16
                val red = (298 * c + 409 * vValue + 128) shr 8
                val green = (298 * c - 100 * uValue - 208 * vValue + 128) shr 8
                val blue = (298 * c + 516 * uValue + 128) shr 8
                pixels[row * outWidth + col] =
                    (0xFF shl 24) or (red.coerceIn(0, 255) shl 16) or (green.coerceIn(0, 255) shl 8) or blue.coerceIn(0, 255)
            }
        }
        return Bitmap.createBitmap(pixels, outWidth, outHeight, Bitmap.Config.ARGB_8888)
    }
}
