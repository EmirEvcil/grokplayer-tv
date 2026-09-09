package com.grokplayer.tv.data

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
object ExoFrameGrab {
    private val lock = Any()
    private val main = Handler(Looper.getMainLooper())

    fun grab(
        context: Context,
        uri: android.net.Uri,
        timeMs: Long,
        timeoutMs: Long = 4_000L,
        rejectBlack: Boolean = true,
    ): Bitmap? {
        if (ThumbnailCache.playbackActive) return null
        return try {
            synchronized(lock) { grabLocked(context, uri, timeMs, timeoutMs, rejectBlack) }
        } catch (_: Throwable) {
            null
        }
    }

    fun grabMany(
        context: Context,
        uri: android.net.Uri,
        timesMs: List<Long>,
        timeoutMs: Long = 8_000L,
    ): Map<Long, Bitmap> {
        if (timesMs.isEmpty()) return emptyMap()
        val first = timesMs.first()
        val frame = grab(context, uri, first, timeoutMs) ?: return emptyMap()
        return mapOf(first to frame)
    }

    private fun grabLocked(
        context: Context,
        uri: android.net.Uri,
        timeMs: Long,
        timeoutMs: Long,
        rejectBlack: Boolean,
    ): Bitmap? {
        val activity = activityOf(context) ?: return null
        val ready = CountDownLatch(1)
        val framed = CountDownLatch(1)
        var player: ExoPlayer? = null
        var texture: TextureView? = null
        var root: ViewGroup? = null
        val posted = CountDownLatch(1)
        main.post {
            try {
                val tv = TextureView(activity)
                val params = FrameLayout.LayoutParams(480, 270)
                params.leftMargin = -800
                params.topMargin = 0
                val host = activity.window.decorView as ViewGroup
                host.addView(tv, params)
                texture = tv
                root = host
                val exo = ExoPlayer.Builder(activity.applicationContext)
                    .setMediaSourceFactory(
                        DefaultMediaSourceFactory(
                            DefaultDataSource.Factory(
                                activity.applicationContext,
                                StreamHttp.dataSourceFactory(activity.applicationContext),
                            ),
                        ),
                    )
                    .setRenderersFactory(
                        DefaultRenderersFactory(activity.applicationContext)
                            .setEnableDecoderFallback(true)
                            .setMediaCodecSelector { mime, secure, tunnel ->
                                val infos = MediaCodecSelector.DEFAULT.getDecoderInfos(mime, secure, tunnel)
                                val stable = infos.filterNot {
                                    val name = it.name.lowercase()
                                    name.contains("goldfish") || name.contains("ranchu")
                                }
                                stable.ifEmpty { infos }
                            },
                    )
                    .build()
                player = exo
                exo.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_READY) ready.countDown()
                    }

                    override fun onRenderedFirstFrame() {
                        framed.countDown()
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        ready.countDown()
                        framed.countDown()
                    }
                })
                exo.setVideoTextureView(tv)
                exo.setMediaItem(mediaItemFor(uri))
                exo.volume = 0f
                exo.playWhenReady = true
                exo.prepare()
            } catch (_: Exception) {
                ready.countDown()
                framed.countDown()
            } finally {
                posted.countDown()
            }
        }
        posted.await(2_000, TimeUnit.MILLISECONDS)
        if (ThumbnailCache.playbackActive) {
            main.post {
                runCatching { player?.release() }
                runCatching { texture?.let { root?.removeView(it) } }
            }
            return null
        }
        val wait = timeoutMs.coerceAtLeast(5_000L)
        ready.await(wait - 2_000L, TimeUnit.MILLISECONDS)
        val remote = uri.scheme == "http" || uri.scheme == "https"
        if (!remote && timeMs > 0L) {
            main.post { player?.seekTo(timeMs) }
        }
        framed.await(3_000, TimeUnit.MILLISECONDS)
        Thread.sleep(120)
        val bitmap = arrayOfNulls<Bitmap>(1)
        val copied = CountDownLatch(1)
        main.post {
            bitmap[0] = runCatching { texture?.getBitmap(480, 270) }.getOrNull()
            copied.countDown()
        }
        copied.await(1_000, TimeUnit.MILLISECONDS)
        val result = bitmap[0]
        main.post {
            runCatching { player?.release() }
            runCatching { texture?.let { root?.removeView(it) } }
        }
        return if (rejectBlack) result?.takeUnless { isMostlyBlack(it) } else result
    }

    private fun activityOf(context: Context): Activity? {
        var current: Context? = context
        repeat(6) {
            when (current) {
                is Activity -> return current
                is ContextWrapper -> current = current.baseContext
                else -> return null
            }
        }
        return null
    }

    private fun mediaItemFor(uri: android.net.Uri): MediaItem {
        val builder = MediaItem.Builder().setUri(uri)
        val lower = uri.toString().lowercase()
        when {
            ".m3u8" in lower -> builder.setMimeType(MimeTypes.APPLICATION_M3U8)
            ".mpd" in lower -> builder.setMimeType(MimeTypes.APPLICATION_MPD)
            lower.endsWith(".avi") -> builder.setMimeType("video/x-msvideo")
        }
        return builder.build()
    }

    fun isMostlyBlack(bitmap: Bitmap): Boolean {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 4 || h < 4) return true
        var bright = 0
        val stepX = (w / 6).coerceAtLeast(1)
        val stepY = (h / 6).coerceAtLeast(1)
        var samples = 0
        var y = stepY
        while (y < h) {
            var x = stepX
            while (x < w) {
                val c = bitmap.getPixel(x, y)
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                if (r + g + b > 40) bright += 1
                samples += 1
                x += stepX
            }
            y += stepY
        }
        return samples == 0 || bright * 5 < samples
    }
}
