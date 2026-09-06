package com.grokplayer.tv.ui.player

import android.graphics.Bitmap
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Forward5
import androidx.compose.material.icons.outlined.PlaylistPlay
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.Replay5
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.BehindLiveWindowException
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.grokplayer.tv.ui.theme.InterceptBack
import com.grokplayer.tv.R
import com.grokplayer.tv.data.LibraryStore
import com.grokplayer.tv.data.PlaySession
import com.grokplayer.tv.data.PlaybackSettings
import com.grokplayer.tv.data.PreviewGrabber
import com.grokplayer.tv.data.StreamProbe
import com.grokplayer.tv.data.ThumbnailCache
import com.grokplayer.tv.data.formatClock
import com.grokplayer.tv.data.formatSpeedLabel
import com.grokplayer.tv.data.seekTimes
import com.grokplayer.tv.ui.theme.GrokInk
import com.grokplayer.tv.ui.theme.GrokMuted
import com.grokplayer.tv.ui.theme.GrokPink
import com.grokplayer.tv.ui.theme.GrokSoft
import com.grokplayer.tv.ui.theme.GrokSurface
import com.grokplayer.tv.ui.theme.GrokType
import com.grokplayer.tv.ui.theme.GrokWhite
import com.grokplayer.tv.ui.theme.GrokYellow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    session: PlaySession,
    resume: Boolean,
    library: LibraryStore,
    settings: PlaybackSettings,
    onClose: (String) -> Unit,
) {
    val context = LocalContext.current
    var index by remember { mutableStateOf(session.startIndex.coerceIn(0, session.queue.lastIndex)) }
    var playlistOpen by remember { mutableStateOf(false) }
    var speedOpen by remember { mutableStateOf(false) }
    var subtitleOpen by remember { mutableStateOf(false) }
    var subtitleOptions by remember { mutableStateOf(listOf(SubtitleOption.Off)) }
    var selectedSubtitleKey by remember { mutableStateOf(SubtitleOption.Off.key) }
    var subtitlePicked by remember { mutableStateOf(false) }
    var subtitleCursor by remember { mutableIntStateOf(0) }
    var controls by remember { mutableStateOf(true) }
    var playing by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var muted by remember { mutableStateOf(false) }
    var speed by remember { mutableStateOf(PlaybackSettings.snapSpeed(settings.defaultSpeed)) }
    var toast by remember { mutableStateOf<String?>(null) }
    var seekBarFocused by remember { mutableStateOf(false) }
    var previewVisible by remember { mutableStateOf(false) }
    var previewPos by remember { mutableLongStateOf(0L) }
    var previewFrames by remember { mutableStateOf<Map<Long, ImageBitmap>>(emptyMap()) }
    var videoWidth by remember { mutableIntStateOf(0) }
    var videoHeight by remember { mutableIntStateOf(0) }
    val playFocus = remember { FocusRequester() }
    val seekFocus = remember { FocusRequester() }
    val liveFocus = remember { FocusRequester() }
    val playerRootFocus = remember { FocusRequester() }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    val video = session.queue[index]
    var live by remember { mutableStateOf(video.isLive) }
    var atLiveEdge by remember { mutableStateOf(true) }
    var liveOffsetMs by remember { mutableLongStateOf(0L) }
    var liveAnchorMs by remember { mutableLongStateOf(-1L) }
    var snapToLive by remember { mutableStateOf(false) }
    var pausedLiveAt by remember { mutableLongStateOf(0L) }
    var pausedLiveOffset by remember { mutableLongStateOf(0L) }
    var hideGen by remember { mutableIntStateOf(0) }
    var audioAnnounced by remember { mutableStateOf(false) }
    val seekStepMs = settings.seekStepSeconds * 1000L

    val player = remember {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(StreamProbe.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(20_000)
        val data = DefaultDataSource.Factory(context, http)
        val tracks = DefaultTrackSelector(context).apply {
            val params = buildUponParameters()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT or C.SELECTION_FLAG_FORCED)
                .setSelectUndeterminedTextLanguage(false)
                .setMaxAudioChannelCount(if (settings.stereoOnly) 2 else Integer.MAX_VALUE)
            if (settings.audioLang.isNotBlank()) {
                params.setPreferredAudioLanguage(settings.audioLang)
            }
            val cap = when {
                isEmulatorDevice() -> 540
                settings.maxHeight > 0 -> settings.maxHeight
                else -> 0
            }
            setParameters(if (cap > 0) params.setMaxVideoSize(1920, cap) else params)
        }
        val renderers = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setMediaCodecSelector(PreferStableDecoder)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(data))
            .setRenderersFactory(renderers)
            .setTrackSelector(tracks)
            .build()
            .apply {
                playWhenReady = true
                setPlaybackSpeed(PlaybackSettings.snapSpeed(settings.defaultSpeed))
            }
    }

    fun applyPendingSubtitles(target: androidx.media3.exoplayer.ExoPlayer) {
        val options = listSubtitleOptions(target.currentTracks).ifEmpty { subtitleOptions }
        subtitleOptions = options
        val desired = if (subtitlePicked) {
            options.firstOrNull { it.key == selectedSubtitleKey } ?: SubtitleOption.Off
        } else if (settings.captionsOn) {
            preferredSubtitle(options, settings.captionLang) ?: SubtitleOption.Off
        } else {
            SubtitleOption.Off
        }
        val currentKey = activeSubtitleKey(target.currentTracks, options)
        val needsOff = desired.isOff && target.currentTracks.groups.any { it.type == C.TRACK_TYPE_TEXT && it.isSelected }
        if (needsOff || (!desired.isOff && currentKey != desired.key)) {
            applySubtitleChoice(target, desired)
        }
        selectedSubtitleKey = desired.key
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    Log.i(
                        "GrokPlayer",
                        "open ${session.queue[index].format} ready pos=${player.currentPosition}",
                    )
                    applyPendingSubtitles(player)
                }
                if (playbackState == Player.STATE_ENDED && settings.autoNext && index < session.queue.lastIndex) {
                    index += 1
                } else if (playbackState == Player.STATE_ENDED) {
                    controls = true
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("GrokPlayer", "play error ${error.errorCodeName}: ${error.message}", error)
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW ||
                    error.cause is BehindLiveWindowException
                ) {
                    player.seekToDefaultPosition()
                    player.prepare()
                    player.play()
                    return
                }
                toast = "Oynatılamadı"
                controls = true
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                videoWidth = videoSize.width
                videoHeight = videoSize.height
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                live = session.queue[index].isLive
            }

            override fun onTracksChanged(tracks: Tracks) {
                val options = listSubtitleOptions(tracks)
                subtitleOptions = options
                subtitleCursor = subtitleCursor.coerceIn(0, options.lastIndex.coerceAtLeast(0))
                if (player.playbackState == Player.STATE_READY) {
                    applyPendingSubtitles(player)
                }
                if (!audioAnnounced) {
                    val selected = selectedAudioFormat(tracks)
                    if (selected != null) {
                        audioAnnounced = true
                        val lang = selected.language.orEmpty()
                        val name = PlaybackSettings.langLabel(lang, "")
                            .ifBlank { selected.label?.trim().orEmpty() }
                            .ifBlank { lang.ifBlank { "und" } }
                        android.util.Log.i(
                            "GrokPlayer",
                            "audio selected lang=${selected.language} label=${selected.label} id=${selected.id}",
                        )
                        toast = "Ses · $name"
                    }
                }
            }
        }
        player.addListener(listener)
        onDispose {
            library.markPlayed(session.queue[index].id, player.currentPosition)
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(index) {
        val item = session.queue[index]
        live = item.isLive
        atLiveEdge = !item.isLive
        liveOffsetMs = 0L
        liveAnchorMs = -1L
        snapToLive = false
        pausedLiveAt = 0L
        val openedAt = android.os.SystemClock.elapsedRealtime()
        val remote = item.uri.scheme == "http" || item.uri.scheme == "https"
        val mediaItem = if (remote) {
            withContext(Dispatchers.IO) { mediaItemFor(item) }
        } else {
            mediaItemFor(item)
        }
        Log.i("GrokPlayer", "open ${item.format} mediaItem ${android.os.SystemClock.elapsedRealtime() - openedAt}ms")
        player.setMediaItem(mediaItem)
        player.prepare()
        subtitlePicked = false
        audioAnnounced = false
        selectedSubtitleKey = SubtitleOption.Off.key
        subtitleOptions = listOf(SubtitleOption.Off)
        val start = if (resume && settings.resumeEnabled && !item.isLive) library.progressOf(item.id) else 0L
        library.touch(item, start)
        val nearEnd = item.durationMs > 0L && start >= item.durationMs - 2_000L
        val avi = item.format.equals("AVI", true)
        if (!item.isLive && start > 1_000L && !nearEnd && !avi) player.seekTo(start)
        player.setPlaybackSpeed(speed)
        player.play()
        if (!item.isLive && start > 1_000L && !nearEnd && avi) {
            player.seekTo(start)
        }
        Log.i("GrokPlayer", "open ${item.format} prepare ${android.os.SystemClock.elapsedRealtime() - openedAt}ms")
        controls = true
        previewVisible = false
        previewFrames = emptyMap()
        if (item.isStream) {
            delay(1_600)
            capturePlayerBitmap(playerView)?.let { frame ->
                withContext(Dispatchers.IO) {
                    val key = item.path ?: item.uri.toString()
                    ThumbnailCache.save(context, key, frame, maxWidth = ThumbnailCache.HERO_WIDTH)
                }
            }
        }
    }

    LaunchedEffect(player) {
        while (true) {
            val item = session.queue[index]
            val windowDuration = player.duration
            val liveNow = item.isLive
            live = liveNow
            if (liveNow) {
                val rawOffset = player.currentLiveOffset
                val windowOffset = if (windowDuration > 0 && windowDuration != C.TIME_UNSET) {
                    (windowDuration - player.currentPosition).coerceAtLeast(0L)
                } else {
                    0L
                }
                val measured = when {
                    rawOffset != C.TIME_UNSET && rawOffset >= 0L -> rawOffset
                    else -> windowOffset
                }
                if (snapToLive && player.playbackState == Player.STATE_READY) {
                    liveAnchorMs = measured
                    snapToLive = false
                    pausedLiveAt = 0L
                    pausedLiveOffset = 0L
                    liveOffsetMs = 0L
                    atLiveEdge = true
                } else if (!player.playWhenReady) {
                    val now = android.os.SystemClock.elapsedRealtime()
                    if (pausedLiveAt == 0L) {
                        pausedLiveAt = now
                        val behind = if (liveAnchorMs >= 0L) {
                            (measured - liveAnchorMs).coerceAtLeast(0L)
                        } else {
                            0L
                        }
                        pausedLiveOffset = behind
                    }
                    liveOffsetMs = pausedLiveOffset + (now - pausedLiveAt)
                    atLiveEdge = liveOffsetMs < 2_500L
                } else {
                    pausedLiveAt = 0L
                    pausedLiveOffset = 0L
                    if (liveAnchorMs < 0L || measured + 400L < liveAnchorMs) {
                        liveAnchorMs = measured
                    }
                    liveOffsetMs = (measured - liveAnchorMs).coerceAtLeast(0L)
                    atLiveEdge = liveOffsetMs < 2_500L
                }
                if (windowDuration > 0 && windowDuration != C.TIME_UNSET) {
                    position = player.currentPosition.coerceIn(0L, windowDuration)
                    duration = windowDuration
                } else {
                    position = 0L
                    duration = 0L
                }
            } else {
                atLiveEdge = true
                liveOffsetMs = 0L
                pausedLiveAt = 0L
                position = player.currentPosition
                duration = windowDuration.takeIf { it > 0 && it != C.TIME_UNSET } ?: item.durationMs
            }
            delay(250)
        }
    }

    LaunchedEffect(controls, hideGen, playlistOpen, speedOpen, subtitleOpen) {
        if (!controls || playlistOpen || speedOpen || subtitleOpen) return@LaunchedEffect
        delay(settings.hideControlsSeconds.coerceAtLeast(1) * 1000L)
        controls = false
        previewVisible = false
    }

    LaunchedEffect(toast) {
        if (toast != null) {
            delay(1600)
            toast = null
        }
    }

    LaunchedEffect(previewPos, video.uri, previewVisible, seekBarFocused, duration, seekStepMs) {
        if (!previewVisible || !seekBarFocused) return@LaunchedEffect
        val times = seekTimes(duration, seekStepMs)
        val selected = times.minByOrNull { kotlin.math.abs(it - previewPos) } ?: previewPos
        val index = times.indexOf(selected).coerceAtLeast(0)
        val start = (index - 3).coerceAtLeast(0)
        val end = (index + 4).coerceAtMost(times.size)
        val window = times.subList(start, end)
        window.forEach { time ->
            PreviewGrabber.cached(video.uri, time)?.let {
                previewFrames = previewFrames + (time to it)
            }
        }
        delay(80)
        capturePlayerFrame(playerView)?.let { frame ->
            previewFrames = previewFrames + (previewPos to frame)
        }
        delay(280)
        val stillMissing = window.filter { it !in previewFrames && PreviewGrabber.cached(video.uri, it) == null }
        if (stillMissing.isNotEmpty()) {
            PreviewGrabber.loadWindow(
                context,
                video.uri,
                video.path,
                stillMissing,
                allowExo = false,
            ) { time, frame ->
                previewFrames = previewFrames + (time to frame)
            }
        }
    }

    fun closePlayer() {
        val key = video.path ?: video.uri.toString()
        capturePlayerBitmap(playerView)?.let { frame ->
            ThumbnailCache.save(context, key, frame, maxWidth = ThumbnailCache.HERO_WIDTH)
            ThumbnailCache.save(context, key, frame, maxWidth = ThumbnailCache.TILE_WIDTH)
        }
        library.markPlayed(video.id, player.currentPosition)
        onClose(video.id)
    }

    fun showControls() {
        controls = true
        hideGen += 1
    }

    fun seekTo(ms: Long) {
        val total = duration.takeIf { it > 0 } ?: 0L
        val next = ms.coerceIn(0L, if (total > 0) total else Long.MAX_VALUE)
        player.seekTo(next)
        position = next
        previewPos = next
        previewVisible = true
        showControls()
    }

    fun seekBy(delta: Long, steps: Int = 1) {
        val times = seekTimes(duration, seekStepMs)
        if (times.isEmpty()) return
        val current = times.minByOrNull { kotlin.math.abs(it - previewPos.coerceAtLeast(position)) } ?: times.first()
        val index = times.indexOf(current).coerceAtLeast(0)
        val nextIndex = if (delta >= 0) {
            (index + steps).coerceAtMost(times.lastIndex)
        } else {
            (index - steps).coerceAtLeast(0)
        }
        seekTo(times[nextIndex])
    }

    fun playIndex(newIndex: Int) {
        library.markPlayed(session.queue[index].id, player.currentPosition)
        index = newIndex
    }

    fun openSubtitleMenu() {
        val options = listSubtitleOptions(player.currentTracks)
        subtitleOptions = options
        subtitleCursor = options.indexOfFirst { it.key == selectedSubtitleKey }.coerceAtLeast(0)
        subtitleOpen = true
        showControls()
    }

    fun pickSubtitle(option: SubtitleOption) {
        subtitlePicked = true
        selectedSubtitleKey = option.key
        applySubtitleChoice(player, option)
        subtitleOpen = false
        toast = if (option.isOff) "Altyazı kapalı" else option.label
        showControls()
    }

    fun handleSubtitleKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        if (!subtitleOpen) return false
        return when (event.key) {
            Key.DirectionDown -> {
                if (subtitleOptions.isNotEmpty()) {
                    subtitleCursor = (subtitleCursor + 1).coerceAtMost(subtitleOptions.lastIndex)
                }
                true
            }
            Key.DirectionUp -> {
                subtitleCursor = (subtitleCursor - 1).coerceAtLeast(0)
                true
            }
            Key.DirectionCenter, Key.Enter -> {
                subtitleOptions.getOrNull(subtitleCursor)?.let { pickSubtitle(it) }
                true
            }
            Key.DirectionLeft, Key.DirectionRight -> true
            else -> false
        }
    }

    fun handleBack(): Boolean {
        return when {
            speedOpen -> {
                speedOpen = false
                true
            }
            subtitleOpen -> {
                subtitleOpen = false
                true
            }
            playlistOpen -> {
                playlistOpen = false
                true
            }
            else -> {
                closePlayer()
                true
            }
        }
    }

    InterceptBack { handleBack() }
    BackHandler { handleBack() }

    LaunchedEffect(subtitleOpen) {
        if (subtitleOpen) {
            delay(16)
            runCatching { playerRootFocus.requestFocus() }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(playerRootFocus)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (event.key == Key.Back || event.key == Key.Escape) {
                    return@onPreviewKeyEvent handleBack()
                }
                if (handleSubtitleKey(event)) return@onPreviewKeyEvent true
                if (playlistOpen || speedOpen) return@onPreviewKeyEvent false
                val left = event.key == Key.DirectionLeft
                val right = event.key == Key.DirectionRight
                val repeats = event.nativeKeyEvent.repeatCount
                val seekNow = (left || right) && (
                    seekBarFocused ||
                        !controls ||
                        (!live && repeats > 0)
                    )
                if (seekNow) {
                    val steps = when {
                        repeats > 10 -> 4
                        repeats > 5 -> 2
                        else -> 1
                    }
                    seekBy(if (left) -seekStepMs else seekStepMs, steps)
                    if (!controls || repeats > 0 || live) runCatching { seekFocus.requestFocus() }
                    true
                } else {
                    false
                }
            }
            .focusProperties { canFocus = !speedOpen && !playlistOpen }
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                controls = !controls
            },
    ) {
        AndroidView(
            factory = { ctx ->
                (LayoutInflater.from(ctx).inflate(R.layout.player_view, FrameLayout(ctx), false) as PlayerView).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    this.player = player
                    playerView = this
                    isFocusable = false
                    isFocusableInTouchMode = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    resizeMode = fitResizeMode(settings.fitMode)
                    styleSubtitleView(this, settings.captionSp)
                }
            },
            update = {
                it.player = player
                it.resizeMode = fitResizeMode(settings.fitMode)
                styleSubtitleView(it, settings.captionSp)
                playerView = it
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (controls || playlistOpen) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(0.65f), Color.Transparent))),
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(280.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.88f)))),
            )
            TopBar(
                title = video.title,
                width = videoWidth,
                height = videoHeight,
                live = live,
                atLiveEdge = atLiveEdge,
                liveOffsetMs = liveOffsetMs,
            )
            if (!playlistOpen && !speedOpen && !subtitleOpen) {
                BottomControls(
                    playing = playing,
                    position = position,
                    duration = duration,
                    live = live,
                    atLiveEdge = atLiveEdge,
                    liveOffsetMs = liveOffsetMs,
                    playFocus = playFocus,
                    seekFocus = seekFocus,
                    liveFocus = liveFocus,
                    seekStep = settings.seekStepSeconds,
                    previewVisible = previewVisible || seekBarFocused,
                    previewPos = if (previewVisible) previewPos else position,
                    previewFrames = previewFrames,
                    seekTimes = seekTimes(duration, seekStepMs),
                    onSeekFocused = { focused ->
                        seekBarFocused = focused
                        if (focused) {
                            previewPos = position
                            previewVisible = true
                        }
                    },
                    onPlayPause = {
                        if (player.isPlaying) player.pause() else player.play()
                        showControls()
                    },
                    onGoLive = {
                        snapToLive = true
                        pausedLiveAt = 0L
                        pausedLiveOffset = 0L
                        liveOffsetMs = 0L
                        atLiveEdge = true
                        player.seekToDefaultPosition()
                        player.play()
                        showControls()
                    },
                    onSeek = { seekBy(it) },
                    onPrev = { if (index > 0) playIndex(index - 1) },
                    onNext = { if (index < session.queue.lastIndex) playIndex(index + 1) },
                    onAudio = {
                        muted = !muted
                        player.volume = if (muted) 0f else 1f
                        toast = if (muted) "Ses kapalı" else "Ses açık"
                        showControls()
                    },
                    onCaption = { openSubtitleMenu() },
                    onSpeed = { speedOpen = true },
                    onList = { playlistOpen = true },
                )
            }
        }

        if (speedOpen) {
            SpeedMenu(
                selected = speed,
                onSelect = { value ->
                    speed = value
                    player.setPlaybackSpeed(value)
                    speedOpen = false
                    toast = "Hız ${formatSpeedLabel(value)}"
                },
                onDismiss = { speedOpen = false },
            )
        }

        if (subtitleOpen) {
            SubtitleMenu(
                options = subtitleOptions,
                selectedKey = selectedSubtitleKey,
                cursor = subtitleCursor,
                onSelect = { pickSubtitle(it) },
                onDismiss = { subtitleOpen = false },
            )
        }

        if (playlistOpen) {
            PlaylistPanel(
                videos = session.queue,
                currentId = video.id,
                onSelect = { i ->
                    playIndex(i)
                    playlistOpen = false
                },
                onClose = { playlistOpen = false },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }

        toast?.let {
            Text(
                text = it,
                style = GrokType.cardTitle,
                color = GrokInk,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp)
                    .background(GrokYellow, RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }

    LaunchedEffect(controls, playlistOpen, speedOpen, subtitleOpen) {
        if (controls && !playlistOpen && !speedOpen && !subtitleOpen) {
            delay(30)
            runCatching { seekFocus.requestFocus() }
        }
    }
}

@Composable
private fun TopBar(
    title: String,
    width: Int,
    height: Int,
    live: Boolean,
    atLiveEdge: Boolean = true,
    liveOffsetMs: Long = 0L,
) {
    var clock by remember { mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())) }
    LaunchedEffect(Unit) {
        while (true) {
            clock = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            delay(15_000)
        }
    }
    val badge = when {
        height >= 2160 || width >= 3840 -> "4K"
        height >= 1080 || width >= 1920 -> "FHD"
        height >= 720 || width >= 1280 -> "HD"
        width > 0 || height > 0 -> "SD"
        else -> "HD"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 28.dp, top = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.logo_mark),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(20.dp),
        )
        Text("GrokPlayer", style = GrokType.wordmark, color = GrokWhite, modifier = Modifier.padding(start = 8.dp))
        Text("  |  $title", style = GrokType.section, color = GrokWhite, modifier = Modifier.padding(start = 8.dp))
        if (live) {
            LiveBadge(
                Modifier.padding(start = 12.dp),
                atLiveEdge = atLiveEdge,
                offsetMs = liveOffsetMs,
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = badge,
            style = GrokType.cardMeta,
            color = GrokWhite,
            modifier = Modifier
                .border(1.dp, GrokSoft.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
        Text(clock, style = GrokType.clock, color = GrokSoft, modifier = Modifier.padding(start = 14.dp))
    }
}

@Composable
private fun BottomControls(
    playing: Boolean,
    position: Long,
    duration: Long,
    live: Boolean,
    atLiveEdge: Boolean,
    liveOffsetMs: Long,
    playFocus: FocusRequester,
    seekFocus: FocusRequester,
    liveFocus: FocusRequester,
    seekStep: Int,
    previewVisible: Boolean,
    previewPos: Long,
    previewFrames: Map<Long, ImageBitmap>,
    seekTimes: List<Long>,
    onSeekFocused: (Boolean) -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onGoLive: () -> Unit = {},
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onAudio: () -> Unit,
    onCaption: () -> Unit,
    onSpeed: () -> Unit,
    onList: () -> Unit,
) {
    val progress = if (live) {
        if (atLiveEdge) 1f else if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 0.97f) else 0.7f
    } else if (duration > 0) {
        (position.toFloat() / duration).coerceIn(0f, 1f)
    } else {
        0f
    }
    val backIcon = if (seekStep <= 5) Icons.Outlined.Replay5 else Icons.Outlined.Replay10
    val fwdIcon = if (seekStep <= 5) Icons.Outlined.Forward5 else Icons.Outlined.Forward10
    Column(
        Modifier
            .fillMaxSize()
            .padding(start = 36.dp, end = 36.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (previewVisible) {
            SeekStrip(times = seekTimes, current = previewPos, frames = previewFrames)
            Spacer(Modifier.height(12.dp))
        } else {
            Spacer(Modifier.height(118.dp))
        }
        if (!live || duration > 0L) {
            SeekBar(
                progress = progress,
                focusedRequester = seekFocus,
                playFocus = playFocus,
                live = live,
                onFocused = onSeekFocused,
                onPlayPause = onPlayPause,
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (live) {
                Text(
                    text = if (duration > 0L) position.formatClock() else "",
                    style = GrokType.cardMeta,
                    color = GrokSoft,
                )
                LiveBadge(atLiveEdge = atLiveEdge, offsetMs = liveOffsetMs)
            } else {
                Text(position.formatClock(), style = GrokType.cardMeta, color = GrokSoft)
                Text(duration.formatClock(), style = GrokType.cardMeta, color = GrokSoft)
            }
        }
        Row(
            Modifier.padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (!live) {
                Transport(Icons.Outlined.SkipPrevious, onPrev)
                Transport(backIcon, { onSeek(-seekStep * 1000L) })
            }
            Transport(
                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                onPlayPause,
                primary = true,
                modifier = Modifier
                    .focusRequester(playFocus)
                    .focusProperties {
                        up = seekFocus
                        right = if (live) liveFocus else FocusRequester.Default
                    },
            )
            if (live) {
                LiveGoButton(
                    onClick = onGoLive,
                    atLiveEdge = atLiveEdge,
                    modifier = Modifier
                        .focusRequester(liveFocus)
                        .focusProperties { left = playFocus },
                )
            } else {
                Transport(fwdIcon, { onSeek(seekStep * 1000L) })
                Transport(Icons.Outlined.SkipNext, onNext)
            }
            Box(
                Modifier
                    .padding(horizontal = 18.dp)
                    .width(1.dp)
                    .height(36.dp)
                    .background(Color.White.copy(0.22f)),
            )
            ActionChip(Icons.Outlined.VolumeUp, "Ses", onAudio)
            ActionChip(Icons.Outlined.ClosedCaption, "Altyazı", onCaption)
            ActionChip(Icons.Outlined.Speed, "Hız", onSpeed)
            ActionChip(Icons.Outlined.PlaylistPlay, "Liste", onList)
        }
        HintRow(Modifier.padding(top = 14.dp))
    }
}

@Composable
private fun SeekStrip(
    times: List<Long>,
    current: Long,
    frames: Map<Long, ImageBitmap>,
) {
    val selected = times.minByOrNull { kotlin.math.abs(it - current) } ?: current
    val selectedIndex = times.indexOf(selected).coerceAtLeast(0)
    val window = 7
    val start = (selectedIndex - window / 2).coerceAtLeast(0)
    val end = (start + window).coerceAtMost(times.size)
    val visible = if (times.isEmpty()) emptyList() else times.subList(start, end)
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Bottom,
    ) {
        visible.forEach { time ->
            val focused = time == selected
            val width = if (focused) 168.dp else 108.dp
            val height = if (focused) 94.dp else 60.dp
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 4.dp),
            ) {
                Box(
                    Modifier
                        .width(width)
                        .height(height)
                        .border(
                            if (focused) 2.5.dp else 1.dp,
                            if (focused) Color(0xFFF0E8D4) else Color.White.copy(0.22f),
                            RoundedCornerShape(8.dp),
                        )
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    val frame = frames[time]
                    if (frame != null) {
                        Image(frame, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                }
                Text(
                    text = time.formatClock(),
                    style = GrokType.cardMeta,
                    color = if (focused) GrokWhite else GrokMuted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

private fun capturePlayerBitmap(view: PlayerView?): Bitmap? {
    if (view == null || view.width < 8 || view.height < 8) return null
    val texture = view.videoSurfaceView as? TextureView
    if (texture != null) {
        return runCatching { texture.bitmap }.getOrNull()
    }
    return null
}

private suspend fun capturePlayerFrame(view: PlayerView?): ImageBitmap? {
    if (view == null || view.width < 8 || view.height < 8) return null
    val texture = view.videoSurfaceView as? TextureView
    if (texture != null) {
        return runCatching { texture.bitmap?.asImageBitmap() }.getOrNull()
    }
    val surfaceView = view.videoSurfaceView as? SurfaceView ?: return null
    val bmp = Bitmap.createBitmap(
        (view.width / 3).coerceAtLeast(160),
        (view.height / 3).coerceAtLeast(90),
        Bitmap.Config.ARGB_8888,
    )
    return try {
        suspendCancellableCoroutine { cont ->
            PixelCopy.request(surfaceView, bmp, { result ->
                if (result == PixelCopy.SUCCESS) {
                    cont.resume(bmp.asImageBitmap())
                } else {
                    if (!bmp.isRecycled) bmp.recycle()
                    cont.resume(null)
                }
            }, Handler(Looper.getMainLooper()))
        }
    } catch (_: Exception) {
        if (!bmp.isRecycled) bmp.recycle()
        null
    }
}

private fun selectedAudioFormat(tracks: Tracks): androidx.media3.common.Format? {
    tracks.groups.forEach { group ->
        if (group.type != C.TRACK_TYPE_AUDIO) return@forEach
        for (index in 0 until group.length) {
            if (group.isTrackSelected(index)) return group.getTrackFormat(index)
        }
    }
    return null
}

private fun mediaItemFor(video: com.grokplayer.tv.data.LibraryVideo): MediaItem {
    val raw = video.uri.toString()
    val resolved = if (raw.startsWith("http")) StreamProbe.resolveFinalUrl(raw) else raw
    val builder = MediaItem.Builder().setUri(resolved)
    when {
        StreamProbe.mimeForUrl(resolved) != null -> {
            val mime = StreamProbe.mimeForUrl(resolved)
            builder.setMimeType(
                if (mime!!.contains("mpegURL")) MimeTypes.APPLICATION_M3U8 else MimeTypes.APPLICATION_MPD,
            )
        }
        resolved.endsWith(".ts", ignoreCase = true) || video.format.equals("TS", true) -> {
            builder.setMimeType(MimeTypes.VIDEO_MP2T)
        }
        resolved.endsWith(".avi", ignoreCase = true) || video.format.equals("AVI", true) -> {
            builder.setMimeType("video/x-msvideo")
        }
    }
    val sidecars = sidecarSubtitleConfigs(video)
    if (sidecars.isNotEmpty()) {
        builder.setSubtitleConfigurations(sidecars)
    }
    return builder.build()
}

private fun fitResizeMode(mode: Int): Int = when (mode) {
    1 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
    2 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
}

private fun styleSubtitleView(view: PlayerView, captionSp: Float) {
    view.subtitleView?.apply {
        setApplyEmbeddedStyles(true)
        setApplyEmbeddedFontSizes(false)
        setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, captionSp)
        setBottomPaddingFraction(0.08f)
        setStyle(
            CaptionStyleCompat(
                android.graphics.Color.WHITE,
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
                CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                android.graphics.Color.BLACK,
                Typeface.DEFAULT_BOLD,
            ),
        )
    }
}

private object PreferStableDecoder : MediaCodecSelector {
    override fun getDecoderInfos(
        mimeType: String,
        requiresSecureDecoder: Boolean,
        requiresTunnelingDecoder: Boolean,
    ): List<androidx.media3.exoplayer.mediacodec.MediaCodecInfo> {
        val infos = MediaCodecSelector.DEFAULT.getDecoderInfos(
            mimeType,
            requiresSecureDecoder,
            requiresTunnelingDecoder,
        )
        val stable = infos.filterNot { info ->
            val name = info.name.lowercase()
            name.contains("goldfish") || name.contains("ranchu")
        }
        return stable.ifEmpty { infos }
    }
}

private fun isEmulatorDevice(): Boolean {
    val hardware = Build.HARDWARE.lowercase()
    val fingerprint = Build.FINGERPRINT.lowercase()
    val product = Build.PRODUCT.lowercase()
    val model = Build.MODEL.lowercase()
    return hardware.contains("goldfish") ||
        hardware.contains("ranchu") ||
        fingerprint.contains("generic") ||
        fingerprint.contains("emulator") ||
        product.contains("sdk") ||
        model.contains("sdk") ||
        model.contains("emulator")
}

@Composable
private fun LiveBadge(
    modifier: Modifier = Modifier,
    atLiveEdge: Boolean = true,
    offsetMs: Long = 0L,
) {
    val behind = !atLiveEdge && offsetMs > 0L
    Row(
        modifier
            .background(if (behind) Color(0xFF2A2412) else Color(0xFF2A1216), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(if (behind) GrokYellow else Color(0xFFE24A4A), CircleShape),
        )
        Text(
            text = if (behind) "GERİDE −${offsetMs.formatClock()}" else "CANLI",
            style = GrokType.cardMeta,
            color = GrokWhite,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
private fun LiveGoButton(
    onClick: () -> Unit,
    atLiveEdge: Boolean,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused = interaction.collectIsFocusedAsState().value
    val behind = !atLiveEdge
    Row(
        modifier
            .padding(start = 10.dp)
            .background(
                when {
                    focused -> GrokYellow
                    behind -> Color(0xFFE24A4A)
                    else -> Color.White.copy(0.10f)
                },
                RoundedCornerShape(20.dp),
            )
            .then(
                if (focused) Modifier else Modifier.border(
                    1.dp,
                    Color(0xFFE24A4A).copy(0.7f),
                    RoundedCornerShape(20.dp),
                ),
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(
                    when {
                        focused -> GrokInk
                        behind -> GrokWhite
                        else -> Color(0xFFE24A4A)
                    },
                    CircleShape,
                ),
        )
        Text(
            text = if (behind) "CANLI'ya git" else "CANLI",
            style = GrokType.button,
            color = if (focused) GrokInk else GrokWhite,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun SeekBar(
    progress: Float,
    focusedRequester: FocusRequester,
    playFocus: FocusRequester,
    live: Boolean,
    onFocused: (Boolean) -> Unit,
    onPlayPause: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused = interaction.collectIsFocusedAsState().value
    val knob = if (focused) 18.dp else 16.dp
    val barHeight = if (focused) 7.dp else 6.dp
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(22.dp)
            .focusRequester(focusedRequester)
            .focusProperties {
                down = playFocus
            }
            .onFocusChanged { onFocused(it.isFocused) }
            .focusable(interactionSource = interaction)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (event.key == Key.DirectionCenter || event.key == Key.Enter) {
                    onPlayPause()
                    true
                } else {
                    false
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(barHeight)
                .background(Color.White.copy(0.22f), RoundedCornerShape(50)),
        )
        Box(
            Modifier
                .fillMaxWidth(progress)
                .height(barHeight)
                .background(GrokPink, RoundedCornerShape(50)),
        )
        Box(
            Modifier
                .offset(x = (maxWidth - knob) * progress)
                .size(knob)
                .background(GrokPink, CircleShape)
                .then(if (focused) Modifier.border(2.dp, Color.White, CircleShape) else Modifier),
        )
    }
}

@Composable
private fun HintRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HintKey("OK")
        Text("Oynat / Duraklat", style = GrokType.cardMeta, color = GrokMuted)
        Text("|", style = GrokType.cardMeta, color = GrokMuted.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 4.dp))
        Text("←  →", style = GrokType.cardMeta, color = GrokSoft)
        Text(": İleri / Geri", style = GrokType.cardMeta, color = GrokMuted)
        Text("|", style = GrokType.cardMeta, color = GrokMuted.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 4.dp))
        HintKey("Geri")
        Text("Kapat", style = GrokType.cardMeta, color = GrokMuted)
    }
}

@Composable
private fun HintKey(label: String) {
    Text(
        text = label,
        style = GrokType.cardMeta,
        color = GrokWhite,
        modifier = Modifier
            .border(1.dp, Color.White.copy(0.28f), RoundedCornerShape(4.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

@Composable
private fun Transport(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused = interaction.collectIsFocusedAsState().value
    val size = if (primary) 64.dp else 46.dp
    val fill = when {
        primary && focused -> GrokYellow
        primary -> Color.Black.copy(alpha = 0.45f)
        focused -> Color.White.copy(0.18f)
        else -> Color.White.copy(0.10f)
    }
    val ring = when {
        primary && focused -> Modifier.border(3.dp, Color.White, CircleShape)
        primary -> Modifier.border(2.dp, GrokYellow, CircleShape)
        focused -> Modifier.border(1.5.dp, Color.White.copy(0.35f), CircleShape)
        else -> Modifier
    }
    Box(
        modifier
            .padding(end = 10.dp)
            .size(size)
            .background(fill, CircleShape)
            .then(ring)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            null,
            tint = when {
                primary && focused -> GrokInk
                primary -> GrokYellow
                else -> GrokWhite
            },
            modifier = Modifier.size(if (primary) 30.dp else 22.dp),
        )
    }
}

@Composable
private fun ActionChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused = interaction.collectIsFocusedAsState().value
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(end = 16.dp)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Box(
            Modifier
                .size(46.dp)
                .background(if (focused) GrokYellow else Color.White.copy(0.10f), CircleShape)
                .then(if (focused) Modifier else Modifier.border(1.dp, Color.White.copy(0.16f), CircleShape)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (focused) GrokInk else GrokWhite, modifier = Modifier.size(22.dp))
        }
        Text(label, style = GrokType.cardMeta, color = if (focused) GrokWhite else GrokSoft, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun SpeedMenu(
    selected: Float,
    onSelect: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    val selectedIndex = PlaybackSettings.SPEEDS.indexOfFirst { kotlin.math.abs(it - selected) < 0.01f }
        .coerceAtLeast(0)
    com.grokplayer.tv.ui.theme.RememberFocusLock()
    InterceptBack { onDismiss(); true }
    BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(GrokInk.copy(alpha = 0.55f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(220.dp)
                .background(GrokSurface, RoundedCornerShape(12.dp))
                .border(1.dp, GrokYellow.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                .padding(10.dp),
        ) {
            Text("Oynatma hızı", style = GrokType.section, color = GrokWhite, modifier = Modifier.padding(8.dp))
            PlaybackSettings.SPEEDS.forEachIndexed { index, value ->
                val interaction = remember { MutableInteractionSource() }
                val focused = interaction.collectIsFocusedAsState().value
                val isSelected = kotlin.math.abs(value - selected) < 0.01f
                Text(
                    text = formatSpeedLabel(value),
                    style = GrokType.button,
                    color = if (focused || isSelected) GrokInk else GrokWhite,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (index == selectedIndex) Modifier.focusRequester(first) else Modifier)
                        .background(
                            when {
                                focused -> GrokYellow
                                isSelected -> GrokYellow.copy(alpha = 0.35f)
                                else -> Color.Transparent
                            },
                            RoundedCornerShape(6.dp),
                        )
                        .clickable(interactionSource = interaction, indication = null) { onSelect(value) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

@Composable
private fun SubtitleMenu(
    options: List<SubtitleOption>,
    selectedKey: String,
    cursor: Int,
    onSelect: (SubtitleOption) -> Unit,
    onDismiss: () -> Unit,
) {
    com.grokplayer.tv.ui.theme.RememberFocusLock()
    InterceptBack { onDismiss(); true }
    BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(GrokInk.copy(alpha = 0.55f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() }
            .focusProperties { canFocus = false },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(300.dp)
                .background(GrokSurface, RoundedCornerShape(12.dp))
                .border(1.dp, GrokYellow.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                .padding(10.dp)
                .focusProperties { canFocus = false },
        ) {
            Text("Altyazı", style = GrokType.section, color = GrokWhite, modifier = Modifier.padding(8.dp))
            options.forEachIndexed { index, option ->
                val isCursor = index == cursor
                val isSelected = option.key == selectedKey
                Text(
                    text = option.label,
                    style = GrokType.button,
                    color = if (isCursor || isSelected) GrokInk else GrokWhite,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            when {
                                isCursor -> GrokYellow
                                isSelected -> GrokYellow.copy(alpha = 0.35f)
                                else -> Color.Transparent
                            },
                            RoundedCornerShape(6.dp),
                        )
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { onSelect(option) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
    }
}
