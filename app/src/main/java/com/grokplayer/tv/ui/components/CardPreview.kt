package com.grokplayer.tv.ui.components

import android.net.Uri
import android.util.Log
import android.util.LruCache
import android.view.LayoutInflater
import android.view.TextureView
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.grokplayer.tv.R
import com.grokplayer.tv.data.StreamHttp
import com.grokplayer.tv.data.StreamProbe
import com.grokplayer.tv.data.scan.YouTubeResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
@Composable
fun CardPreview(
    uri: Uri,
    modifier: Modifier = Modifier,
    originUrl: String? = null,
    isLive: Boolean = false,
    format: String? = null,
    referer: String? = null,
    userAgent: String? = null,
) {
    val context = LocalContext.current
    var shown by remember(uri) { mutableStateOf(false) }
    val fade by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(180),
        label = "preview-fade",
    )
    val view = remember {
        (LayoutInflater.from(context).inflate(R.layout.player_view, FrameLayout(context), false) as PlayerView).apply {
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setKeepContentOnPlayerReset(true)
            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
            isFocusable = false
            isFocusableInTouchMode = false
            isClickable = false
            isLongClickable = false
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
            (videoSurfaceView as? TextureView)?.isOpaque = false
        }
    }
    DisposableEffect(uri, originUrl, isLive, referer, userAgent) {
        shown = false
        val player = CardPreviewPlayer.obtain(context)
        view.player = player
        (view.videoSurfaceView as? TextureView)?.isOpaque = false
        player.volume = 0f
        player.playWhenReady = true
        player.repeatMode = if (isLive) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                shown = true
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.w("GrokPlayer", "preview fail ${error.errorCodeName} ${error.message}")
            }
        }
        player.addListener(listener)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope.launch {
            val source = withContext(Dispatchers.IO) {
                previewSource(context, uri, originUrl, format, referer, userAgent)
            }
            if (!isActive) return@launch
            StreamHttp.applyPlayHeaders(
                CardPreviewPlayer.http(context),
                source.referer,
                source.userAgent,
            )
            player.setMediaItem(source.item)
            player.prepare()
        }
        onDispose {
            scope.cancel()
            player.removeListener(listener)
            shown = false
            view.player = null
            CardPreviewPlayer.park()
        }
    }
    AndroidView(
        factory = { view },
        update = { (it.videoSurfaceView as? TextureView)?.isOpaque = false },
        modifier = modifier
            .alpha(fade)
            .focusProperties { canFocus = false },
    )
}

@OptIn(UnstableApi::class)
private object CardPreviewPlayer {
    private val lock = Any()
    private var exo: ExoPlayer? = null
    private var httpFactory: androidx.media3.datasource.okhttp.OkHttpDataSource.Factory? = null

    fun http(context: android.content.Context): androidx.media3.datasource.okhttp.OkHttpDataSource.Factory {
        obtain(context)
        return httpFactory!!
    }

    fun obtain(context: android.content.Context): ExoPlayer {
        synchronized(lock) {
            exo?.let { return it }
            val app = context.applicationContext
            val http = StreamHttp.playerDataSourceFactory(app)
            httpFactory = http
            val tracks = DefaultTrackSelector(app).apply {
                setParameters(
                    buildUponParameters()
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .setMaxVideoSize(640, 360),
                )
            }
            val created = ExoPlayer.Builder(app)
                .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(app, http)))
                .setRenderersFactory(DefaultRenderersFactory(app).setEnableDecoderFallback(true))
                .setTrackSelector(tracks)
                .setLoadControl(
                    DefaultLoadControl.Builder()
                        .setBufferDurationsMs(500, 3_000, 250, 500)
                        .build(),
                )
                .build()
            exo = created
            return created
        }
    }

    fun park() {
        synchronized(lock) {
            exo?.apply {
                playWhenReady = false
                stop()
                clearMediaItems()
            }
        }
    }
}

private val previewItems = object : LruCache<String, PreviewSource>(16) {}

private data class PreviewSource(
    val item: MediaItem,
    val referer: String?,
    val userAgent: String?,
)

@OptIn(UnstableApi::class)
private fun previewSource(
    context: android.content.Context,
    uri: Uri,
    originUrl: String?,
    format: String?,
    referer: String?,
    userAgent: String?,
): PreviewSource {
    val raw = uri.toString()
    val lanFile = raw.contains("/v1/file")
    val page = if (lanFile) null else YouTubeResolver.watchUrl(raw, originUrl)
    if (page != null) {
        val id = YouTubeResolver.videoId(page) ?: YouTubeResolver.videoId(raw)
        val youtube = id?.let { YouTubeResolver.resolve(context, it, page) }
        if (youtube != null) {
            return PreviewSource(
                item = mediaItemOf(youtube.playUrl, format),
                referer = youtube.referer ?: "https://www.youtube.com/",
                userAgent = youtube.userAgent ?: YouTubeResolver.chromeUa,
            )
        }
    }
    previewItems.get(raw)?.let { return it }
    val resolved = when {
        lanFile || looksDirect(raw) -> raw
        raw.startsWith("http") -> StreamProbe.playUrl(raw)
        else -> raw
    }
    val source = PreviewSource(
        item = mediaItemOf(resolved, format),
        referer = referer,
        userAgent = userAgent,
    )
    if (page == null) previewItems.put(raw, source)
    return source
}

@OptIn(UnstableApi::class)
private fun mediaItemOf(url: String, format: String?): MediaItem {
    val builder = MediaItem.Builder().setUri(url)
    when {
        StreamProbe.isHls(url) -> builder.setMimeType(MimeTypes.APPLICATION_M3U8)
        StreamProbe.isDash(url) -> builder.setMimeType(MimeTypes.APPLICATION_MPD)
        url.endsWith(".ts", ignoreCase = true) || format.equals("TS", true) -> {
            builder.setMimeType(MimeTypes.VIDEO_MP2T)
        }
        url.endsWith(".avi", ignoreCase = true) || format.equals("AVI", true) -> {
            builder.setMimeType("video/x-msvideo")
        }
    }
    return builder.build()
}

private fun looksDirect(url: String): Boolean {
    val path = url.lowercase().substringBefore('?')
    return path.endsWith(".mp4") || path.endsWith(".mkv") || path.endsWith(".webm") ||
        path.endsWith(".mov") || path.endsWith(".m4v") || path.endsWith(".m3u8") ||
        path.endsWith(".mpd") || path.endsWith(".ts") || path.endsWith(".avi")
}
