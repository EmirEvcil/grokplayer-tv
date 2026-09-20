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
import android.view.View
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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.BehindLiveWindowException
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.grokplayer.tv.ui.components.ModalAction
import com.grokplayer.tv.ui.components.ModalMenu
import com.grokplayer.tv.ui.theme.InterceptBack
import com.grokplayer.tv.R
import com.grokplayer.tv.data.LibraryStore
import com.grokplayer.tv.data.LibraryVideo
import com.grokplayer.tv.data.PlaySession
import com.grokplayer.tv.data.PlaybackSettings
import com.grokplayer.tv.data.PreviewGrabber
import com.grokplayer.tv.data.SeekPreviewPlan
import com.grokplayer.tv.data.StreamHttp
import com.grokplayer.tv.data.StreamProbe
import com.grokplayer.tv.data.scan.YouTubeAudio
import com.grokplayer.tv.data.scan.YouTubeCaptions
import com.grokplayer.tv.data.scan.YouTubeResolver
import com.grokplayer.tv.data.scan.YtAudioTrack
import com.grokplayer.tv.data.scan.YtCaptionLine
import com.grokplayer.tv.data.scan.YtCaptionTrack
import com.grokplayer.tv.data.ThumbnailCache
import com.grokplayer.tv.data.formatClock
import com.grokplayer.tv.data.formatSpeedLabel
import com.grokplayer.tv.data.isVod
import com.grokplayer.tv.data.localPlaybackFile
import com.grokplayer.tv.data.seekCursor
import com.grokplayer.tv.data.seekTimes
import com.grokplayer.tv.data.sniffContainer
import com.grokplayer.tv.ui.components.VideoPoster
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
    askResume: Boolean = false,
    remotePosition: (LibraryVideo) -> Long = { 0L },
) {
    val context = LocalContext.current
    var index by remember { mutableStateOf(session.startIndex.coerceIn(0, session.queue.lastIndex)) }
    var playlistOpen by remember { mutableStateOf(false) }
    var speedOpen by remember { mutableStateOf(false) }
    var subtitleOpen by remember { mutableStateOf(false) }
    var subtitleOptions by remember { mutableStateOf(listOf(SubtitleOption.Off)) }
    var selectedSubtitleKey by remember { mutableStateOf(SubtitleOption.Off.key) }
    var selectedSubtitleLang by remember { mutableStateOf<String?>(null) }
    var subtitlePicked by remember { mutableStateOf(false) }
    var subtitleCursor by remember { mutableIntStateOf(0) }
    var audioOpen by remember { mutableStateOf(false) }
    var audioOptions by remember { mutableStateOf<List<AudioOption>>(emptyList()) }
    var selectedAudioKey by remember { mutableStateOf<String?>(null) }
    var audioCursor by remember { mutableIntStateOf(0) }
    var ytCaptions by remember { mutableStateOf<List<YtCaptionTrack>>(emptyList()) }
    var ytAudios by remember { mutableStateOf<List<YtAudioTrack>>(emptyList()) }
    var lastPrepared by remember { mutableStateOf<PreparedPlay?>(null) }
    var activeDubId by remember { mutableStateOf<String?>(null) }
    var ytLines by remember { mutableStateOf<List<YtCaptionLine>>(emptyList()) }
    var captionPos by remember { mutableLongStateOf(0L) }
    var controls by remember { mutableStateOf(true) }
    var playing by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var muted by remember { mutableStateOf(false) }
    var speed by remember { mutableStateOf(PlaybackSettings.snapSpeed(settings.defaultSpeed)) }
    var toast by remember { mutableStateOf<String?>(null) }
    var seekBarFocused by remember { mutableStateOf(false) }
    var barFocused by remember { mutableStateOf(false) }
    var previewVisible by remember { mutableStateOf(false) }
    var previewPos by remember { mutableLongStateOf(0L) }
    var previewFrames by remember { mutableStateOf<Map<Long, ImageBitmap>>(emptyMap()) }
    var pendingSeek by remember { mutableStateOf<Long?>(null) }
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
    var resumeOffer by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var audioAnnounced by remember { mutableStateOf(false) }
    var skipNextUp by remember { mutableStateOf(true) }
    val skipNextUpRef = remember { java.util.concurrent.atomic.AtomicBoolean(true) }
    var nextUpVisible by remember { mutableStateOf(false) }
    var nextUpFocused by remember { mutableStateOf(false) }
    val nextUpFocus = remember { FocusRequester() }
    val seekStepMs = settings.seekStepSeconds * 1000L

    val httpFactory = remember { StreamHttp.playerDataSourceFactory(context) }
    val mediaSourceFactory = remember {
        DefaultMediaSourceFactory(DefaultDataSource.Factory(context, httpFactory))
    }
    val player = remember {
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
            .setMediaSourceFactory(mediaSourceFactory)
            .setRenderersFactory(renderers)
            .setTrackSelector(tracks)
            .build()
            .apply {
                playWhenReady = true
                setPlaybackSpeed(PlaybackSettings.snapSpeed(settings.defaultSpeed))
            }
    }

    fun applyPendingSubtitles(target: androidx.media3.exoplayer.ExoPlayer) {
        val options = mergeSubtitleOptions(listSubtitleOptions(target.currentTracks), ytCaptions)
            .ifEmpty { subtitleOptions }
        subtitleOptions = options
        val desired = resolveSubtitleOption(
            options = options,
            selectedKey = selectedSubtitleKey,
            language = selectedSubtitleLang,
            picked = subtitlePicked,
            captionsOn = settings.captionsOn,
            captionLang = settings.captionLang,
        )
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
                Log.i(
                    "GrokPlayer",
                    "open ${session.queue[index].format} state=$playbackState pos=${player.currentPosition}",
                )
                if (playbackState == Player.STATE_READY) {
                    val wanted = PlaybackSettings.snapSpeed(speed)
                    if (kotlin.math.abs(player.playbackParameters.speed - wanted) > 0.01f) {
                        player.setPlaybackSpeed(wanted)
                    }
                    applyPendingSubtitles(player)
                }
                if (playbackState == Player.STATE_ENDED) {
                    val item = session.queue.getOrNull(index)
                    if (item != null && item.isVod()) {
                        val pos = player.currentPosition.coerceAtLeast(0L)
                        val reported = player.duration.takeIf { it > 0L } ?: item.durationMs
                        val dur = if (pos in 1L until reported) pos else reported
                        library.watch.markWatched(item)
                        library.markPlayed(item, dur.coerceAtLeast(pos), dur)
                        library.noteListPlay(session.listId, item)
                    }
                    val canNext = settings.autoNext &&
                        skipNextUpRef.get() &&
                        item != null &&
                        item.isVod() &&
                        index < session.queue.lastIndex &&
                        session.queue[index + 1].isVod()
                    if (canNext) {
                        index += 1
                    } else {
                        controls = true
                    }
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
                toast = when (error.errorCode) {
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                    -> "Bağlantı kurulamadı"
                    else -> "Oynatılamadı"
                }
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
                val options = mergeSubtitleOptions(listSubtitleOptions(tracks), ytCaptions)
                subtitleOptions = options
                subtitleCursor = subtitleCursor.coerceIn(0, options.lastIndex.coerceAtLeast(0))
                val audios = mergeAudioOptions(listAudioOptions(tracks), ytAudios)
                audioOptions = audios
                selectedAudioKey = activeAudioKey(tracks, audios) ?: selectedAudioKey
                audioCursor = audios.indexOfFirst { it.key == selectedAudioKey }.coerceAtLeast(0)
                if (activeDubId != null) {
                    preferSidecarAudio(player, ytAudios.firstOrNull { it.id == activeDubId }?.language)
                }
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
            val item = session.queue.getOrNull(index)
            if (item != null) {
                val (pos, dur) = playClock(player, item.durationMs)
                library.markPlayed(item, pos, dur)
                library.noteListPlay(session.listId, item)
            }
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(index) {
        pendingSeek = null
        val item = session.queue[index]
        live = item.isLive
        atLiveEdge = !item.isLive
        liveOffsetMs = 0L
        liveAnchorMs = -1L
        snapToLive = false
        pausedLiveAt = 0L
        val openedAt = android.os.SystemClock.elapsedRealtime()
        val remote = item.uri.scheme == "http" || item.uri.scheme == "https"
        val prepared = withContext(Dispatchers.IO) { preparePlay(context, item) }
        StreamHttp.applyPlayHeaders(httpFactory, prepared.referer, prepared.userAgent)
        ytCaptions = prepared.captions
        ytAudios = prepared.audios
        lastPrepared = prepared
        activeDubId = null
        ytLines = emptyList()
        Log.i(
            "GrokPlayer",
            "open ${item.format} mediaItem ${android.os.SystemClock.elapsedRealtime() - openedAt}ms caps=${prepared.captions.size} dubs=${prepared.audios.size}",
        )
        val sidecar = prepared.audioSidecar
        if (sidecar != null && sidecar.isFile && sidecar.length() > 32L) {
            val audioItem = MediaItem.Builder().setUri(android.net.Uri.fromFile(sidecar)).build()
            player.setMediaSource(
                MergingMediaSource(
                    true,
                    mediaSourceFactory.createMediaSource(prepared.mediaItem),
                    mediaSourceFactory.createMediaSource(audioItem),
                ),
            )
        } else {
            player.setMediaItem(prepared.mediaItem)
        }
        player.prepare()
        subtitlePicked = false
        audioAnnounced = false
        selectedSubtitleKey = SubtitleOption.Off.key
        selectedSubtitleLang = null
        subtitleOptions = listOf(SubtitleOption.Off)
        val start = if (resume && settings.resumeEnabled && !item.isLive) {
            library.startPosition(item, remotePosition(item))
        } else {
            0L
        }
        library.touch(item, start)
        library.noteListPlay(session.listId, item)
        val total = library.watch.durationMs(item).takeIf { it > 0L } ?: item.durationMs
        val nearEnd = total > 0L && start >= total - 2_000L
        val avi = item.format.equals("AVI", true)
        val offerResume = askResume && resume && settings.resumeEnabled &&
            !item.isLive && start >= 1_000L && !nearEnd
        player.setPlaybackSpeed(PlaybackSettings.snapSpeed(speed))
        if (offerResume) {
            resumeOffer = start to total
            player.pause()
        } else {
            resumeOffer = null
            if (!item.isLive && start > 1_000L && !nearEnd && !avi) player.seekTo(start)
            player.play()
            if (!item.isLive && start > 1_000L && !nearEnd && avi) {
                player.seekTo(start)
            }
        }
        Log.i("GrokPlayer", "open ${item.format} prepare ${android.os.SystemClock.elapsedRealtime() - openedAt}ms")
        controls = true
        previewVisible = false
        previewFrames = SeekPreviewPlan.times(item.durationMs.coerceAtLeast(total), seekStepMs)
            .mapNotNull { time -> PreviewGrabber.cached(item.uri, time)?.let { time to it } }
            .toMap()
        skipNextUp = true
        skipNextUpRef.set(true)
        nextUpVisible = false
        nextUpFocused = false
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
                if (pendingSeek == null) {
                    position = player.currentPosition
                    if (!previewVisible) previewPos = position
                }
                duration = windowDuration.takeIf { it > 0 && it != C.TIME_UNSET } ?: item.durationMs
                val remain = duration - position
                nextUpVisible = settings.autoNext &&
                    skipNextUp &&
                    !item.isLive &&
                    index < session.queue.lastIndex &&
                    session.queue[index + 1].isVod() &&
                    duration > 20_000L &&
                    remain in 1L..10_000L
            }
            delay(250)
        }
    }

    LaunchedEffect(selectedSubtitleKey, ytCaptions, index) {
        val option = subtitleOptions.firstOrNull { it.key == selectedSubtitleKey }
        val track = if (option == null || option.isOff) {
            null
        } else {
            YouTubeCaptions.match(ytCaptions, option.language, option.label)
                ?: ytCaptions.firstOrNull { option.key.startsWith("yt:${it.language}") }
        }
        if (track == null) {
            ytLines = emptyList()
            return@LaunchedEffect
        }
        val lines = track.lines.ifEmpty {
            withContext(Dispatchers.IO) {
                YouTubeCaptions.fetchLines(context, track, video.referer ?: "https://www.youtube.com/", video.userAgent)
            }
        }
        ytLines = lines
        Log.i("GrokPlayer", "caption overlay lang=${track.language} lines=${lines.size}")
        if (lines.isEmpty()) {
            toast = "Altyazı yüklenemedi"
        }
    }

    LaunchedEffect(ytLines) {
        if (ytLines.isEmpty()) return@LaunchedEffect
        while (true) {
            captionPos = player.currentPosition
            delay(50)
        }
    }

    LaunchedEffect(ytLines, playerView) {
        val hide = ytLines.isNotEmpty()
        playerView?.subtitleView?.visibility = if (hide) View.GONE else View.VISIBLE
    }

    LaunchedEffect(controls, hideGen, playlistOpen, speedOpen, subtitleOpen, audioOpen) {
        if (!controls || playlistOpen || speedOpen || subtitleOpen || audioOpen) {
            return@LaunchedEffect
        }
        val wait = settings.hideControlsSeconds.coerceAtLeast(2) * 1000L
        delay(wait)
        if (playlistOpen || speedOpen || subtitleOpen || audioOpen) return@LaunchedEffect
        controls = false
        previewVisible = false
    }

    LaunchedEffect(toast) {
        if (toast != null) {
            delay(1600)
            toast = null
        }
    }

    LaunchedEffect(pendingSeek) {
        val target = pendingSeek ?: return@LaunchedEffect
        delay(80)
        if (pendingSeek != target) return@LaunchedEffect
        player.seekTo(target)
        if (video.isLive) {
            pendingSeek = null
            return@LaunchedEffect
        }
        var waited = 0
        while (waited < 900 && player.playbackState != androidx.media3.common.Player.STATE_READY) {
            delay(40)
            waited += 40
        }
        delay(80)
        capturePlayerFrame(playerView)?.let { frame ->
            PreviewGrabber.put(video.uri, target, frame)
            previewFrames = previewFrames + (target to frame)
        }
        pendingSeek = null
    }

    LaunchedEffect(video.id, duration, seekStepMs) {
        if (video.isLive || duration <= 0L) return@LaunchedEffect
        val times = SeekPreviewPlan.times(duration, seekStepMs)
        times.forEach { time ->
            PreviewGrabber.cached(video.uri, time)?.let {
                previewFrames = previewFrames + (time to it)
            }
        }
        val around = seekCursor(previewVisible, previewPos, position)
        PreviewGrabber.preload(
            context = context,
            uri = video.uri,
            path = video.path,
            timesMs = times,
            aroundMs = around,
        ) { time, frame ->
            previewFrames = previewFrames + (time to frame)
        }
    }

    LaunchedEffect(previewPos, video.uri, previewVisible, duration, seekStepMs) {
        if (!previewVisible) return@LaunchedEffect
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
    }

    fun closePlayer() {
        val key = video.path ?: video.uri.toString()
        capturePlayerBitmap(playerView)?.let { frame ->
            ThumbnailCache.save(context, key, frame, maxWidth = ThumbnailCache.HERO_WIDTH)
            ThumbnailCache.save(context, key, frame, maxWidth = ThumbnailCache.TILE_WIDTH)
        }
        val (pos, dur) = playClock(player, video.durationMs)
        library.markPlayed(video, pos, dur)
        library.noteListPlay(session.listId, video)
        onClose(video.id)
    }

    fun showControls() {
        controls = true
        hideGen += 1
    }

    fun seekTo(ms: Long) {
        val total = duration.takeIf { it > 0 } ?: 0L
        val next = ms.coerceIn(0L, if (total > 0) total else Long.MAX_VALUE)
        position = next
        previewPos = next
        previewVisible = !live
        if (live) {
            pendingSeek = null
            player.seekTo(next)
        } else {
            pendingSeek = next
        }
        showControls()
    }

    fun seekBy(delta: Long, steps: Int = 1) {
        if (live) {
            val jump = seekStepMs * steps.coerceAtLeast(1)
            val from = player.currentPosition.takeIf { it > 0L } ?: position
            seekTo(from + if (delta >= 0) jump else -jump)
            return
        }
        val times = seekTimes(duration, seekStepMs)
        if (times.isEmpty()) return
        val cursor = seekCursor(previewVisible, previewPos, position)
        val current = times.minByOrNull { kotlin.math.abs(it - cursor) } ?: times.first()
        val index = times.indexOf(current).coerceAtLeast(0)
        val nextIndex = if (delta >= 0) {
            (index + steps).coerceAtMost(times.lastIndex)
        } else {
            (index - steps).coerceAtLeast(0)
        }
        seekTo(times[nextIndex])
    }

    fun playIndex(newIndex: Int) {
        val current = session.queue[index]
        val (pos, dur) = playClock(player, current.durationMs)
        library.markPlayed(current, pos, dur)
        library.noteListPlay(session.listId, current)
        index = newIndex
    }

    fun openSubtitleMenu() {
        val options = mergeSubtitleOptions(listSubtitleOptions(player.currentTracks), ytCaptions)
        subtitleOptions = options
        subtitleCursor = options.indexOfFirst { it.key == selectedSubtitleKey }.coerceAtLeast(0)
        subtitleOpen = true
        showControls()
    }

    fun pickSubtitle(option: SubtitleOption) {
        subtitlePicked = true
        selectedSubtitleKey = option.key
        selectedSubtitleLang = option.language
        applySubtitleChoice(player, option)
        subtitleOpen = false
        toast = if (option.isOff) "Altyazı kapalı" else option.label
        showControls()
    }

    fun openAudioMenu() {
        val options = mergeAudioOptions(listAudioOptions(player.currentTracks), ytAudios)
        audioOptions = options
        if (options.isEmpty()) {
            muted = !muted
            player.volume = if (muted) 0f else 1f
            toast = if (muted) "Ses kapalı" else "Ses açık"
            showControls()
            return
        }
        selectedAudioKey = when {
            activeDubId != null -> "yt-a:$activeDubId"
            else -> activeAudioKey(player.currentTracks, options) ?: selectedAudioKey
        }
        audioCursor = options.indexOfFirst { it.key == selectedAudioKey }.let { idx ->
            if (idx >= 0) idx else options.indexOfFirst { it.language == settings.audioLang }.coerceAtLeast(0)
        }
        audioOpen = true
        showControls()
    }

    fun switchToDub(track: YtAudioTrack) {
        val prepared = lastPrepared ?: return
        val position = player.currentPosition
        val resume = player.playWhenReady
        activeDubId = track.id
        StreamHttp.applyPlayHeaders(httpFactory, prepared.referer, prepared.userAgent)
        val audioItem = MediaItem.Builder()
            .setUri(track.url)
            .setMimeType(YouTubeAudio.mimeType(track))
            .build()
        val videoSource = mediaSourceFactory.createMediaSource(prepared.mediaItem)
        val audioSource = mediaSourceFactory.createMediaSource(audioItem)
        player.setMediaSource(MergingMediaSource(true, videoSource, audioSource))
        player.prepare()
        player.seekTo(position)
        player.playWhenReady = resume
        Log.i("GrokPlayer", "dub ${track.id} ${track.menuLabel()} mime=${track.mime.take(32)}")
    }

    fun restoreDefaultAudio() {
        val prepared = lastPrepared ?: return
        val position = player.currentPosition
        val resume = player.playWhenReady
        activeDubId = null
        StreamHttp.applyPlayHeaders(httpFactory, prepared.referer, prepared.userAgent)
        player.setMediaItem(prepared.mediaItem)
        player.prepare()
        player.seekTo(position)
        player.playWhenReady = resume
    }

    fun pickAudio(option: AudioOption) {
        selectedAudioKey = option.key
        audioOpen = false
        if (muted) {
            muted = false
            player.volume = 1f
        }
        val yt = ytAudios.firstOrNull { option.key == "yt-a:${it.id}" }
        when {
            yt != null && !yt.isDefault -> switchToDub(yt)
            yt != null && yt.isDefault -> restoreDefaultAudio()
            else -> applyAudioChoice(player, option)
        }
        toast = "Ses · ${option.label}"
        showControls()
    }

    fun handleBack(): Boolean {
        return when {
            resumeOffer != null -> {
                resumeOffer = null
                player.seekTo(0)
                player.play()
                true
            }
            speedOpen -> {
                speedOpen = false
                true
            }
            audioOpen -> {
                audioOpen = false
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
            nextUpFocused -> {
                nextUpFocused = false
                nextUpVisible = false
                skipNextUp = false
                skipNextUpRef.set(false)
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

    LaunchedEffect(subtitleOpen, audioOpen) {
        if (subtitleOpen || audioOpen) return@LaunchedEffect
        delay(16)
        runCatching { playerRootFocus.requestFocus() }
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
                if (playlistOpen || speedOpen || audioOpen || subtitleOpen) return@onPreviewKeyEvent false
                val dpad = event.key == Key.DirectionCenter ||
                    event.key == Key.Enter ||
                    event.key == Key.DirectionDown ||
                    event.key == Key.DirectionUp ||
                    event.key == Key.DirectionLeft ||
                    event.key == Key.DirectionRight
                if (controls && dpad) {
                    showControls()
                }
                if (nextUpVisible && !nextUpFocused && event.key == Key.DirectionDown) {
                    nextUpFocused = true
                    showControls()
                    return@onPreviewKeyEvent true
                }
                if (!controls) {
                    val openChrome = event.key == Key.DirectionCenter ||
                        event.key == Key.Enter ||
                        event.key == Key.DirectionDown ||
                        event.key == Key.DirectionUp ||
                        event.key == Key.DirectionLeft ||
                        event.key == Key.DirectionRight
                    if (openChrome) {
                        showControls()
                        return@onPreviewKeyEvent true
                    }
                }
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
            .focusProperties { canFocus = !speedOpen && !playlistOpen && !audioOpen && !subtitleOpen }
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

        if (ytLines.isNotEmpty() && selectedSubtitleKey != SubtitleOption.Off.key) {
            YtCaptionOverlay(
                lines = ytLines,
                positionMs = captionPos,
                fontSp = settings.captionSp,
                lifted = controls || playlistOpen,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 72.dp),
            )
        }

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
            if (!playlistOpen && !speedOpen && !subtitleOpen && !audioOpen) {
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
                    onPrev = {
                        if (index > 0 && session.queue[index - 1].isVod()) playIndex(index - 1)
                    },
                    onNext = {
                        if (index < session.queue.lastIndex && session.queue[index + 1].isVod()) playIndex(index + 1)
                    },
                    onAudio = { openAudioMenu() },
                    onCaption = { openSubtitleMenu() },
                    onSpeed = { speedOpen = true },
                    onList = { playlistOpen = true },
                    onBarFocus = { barFocused = it },
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

        if (audioOpen) {
            ModalMenu(
                title = "Ses",
                onDismiss = { audioOpen = false },
                startIndex = audioCursor,
                width = 300.dp,
                absorbOpeningOk = false,
                actions = audioOptions.map { option ->
                    ModalAction(option.label, icon = Icons.AutoMirrored.Outlined.VolumeUp) { pickAudio(option) }
                },
            )
        }

        if (subtitleOpen) {
            ModalMenu(
                title = "Altyazı",
                onDismiss = { subtitleOpen = false },
                startIndex = subtitleCursor,
                width = 300.dp,
                absorbOpeningOk = false,
                actions = subtitleOptions.map { option ->
                    ModalAction(option.label, icon = Icons.Outlined.ClosedCaption) { pickSubtitle(option) }
                },
            )
        }

        resumeOffer?.let { (startMs, durationMs) ->
            com.grokplayer.tv.ui.devices.PcResumeOverlay(
                offer = com.grokplayer.tv.data.link.ResumeOffer(
                    title = video.title,
                    seconds = startMs / 1000.0,
                    duration = (durationMs.takeIf { it > startMs } ?: startMs).coerceAtLeast(startMs) / 1000.0,
                ),
                onContinue = {
                    val avi = video.format.equals("AVI", true)
                    resumeOffer = null
                    if (!avi) player.seekTo(startMs)
                    player.play()
                    if (avi) player.seekTo(startMs)
                },
                onStartOver = {
                    resumeOffer = null
                    player.seekTo(0)
                    player.play()
                },
                onDismiss = {
                    resumeOffer = null
                    closePlayer()
                },
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

        if (nextUpVisible && index < session.queue.lastIndex) {
            val upcoming = session.queue[index + 1]
            NextUpCard(
                video = upcoming,
                remainingMs = (duration - position).coerceAtLeast(0L),
                focused = nextUpFocused,
                playFocus = nextUpFocus,
                onPlayNow = {
                    nextUpVisible = false
                    nextUpFocused = false
                    index += 1
                },
                onCancel = {
                    nextUpVisible = false
                    nextUpFocused = false
                    skipNextUp = false
                    skipNextUpRef.set(false)
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 28.dp, bottom = if (controls) 168.dp else 36.dp),
            )
            LaunchedEffect(nextUpFocused) {
                if (nextUpFocused) {
                    delay(40)
                    runCatching { nextUpFocus.requestFocus() }
                }
            }
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

    LaunchedEffect(controls, playlistOpen, speedOpen, subtitleOpen, audioOpen) {
        if (playlistOpen || speedOpen || subtitleOpen || audioOpen) return@LaunchedEffect
        delay(30)
        if (!controls) {
            runCatching { playerRootFocus.requestFocus() }
        } else {
            val target = if (live && duration <= 0L) playFocus else seekFocus
            runCatching { target.requestFocus() }
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
private fun NextUpCard(
    video: LibraryVideo,
    remainingMs: Long,
    focused: Boolean,
    playFocus: FocusRequester,
    onPlayNow: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cancelFocus = remember { FocusRequester() }
    Column(
        modifier
            .width(300.dp)
            .background(GrokSurface.copy(alpha = 0.92f), RoundedCornerShape(12.dp))
            .border(1.dp, GrokYellow.copy(alpha = if (focused) 0.7f else 0.22f), RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Text("Sıradaki", style = GrokType.eyebrow, color = GrokYellow)
        VideoPoster(
            uri = video.uri,
            title = video.title,
            focused = false,
            path = video.path,
            format = video.format,
            posterUrl = video.posterUrl,
            durationMs = video.durationMs,
            originUrl = video.originUrl,
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
        )
        Text(video.title, style = GrokType.cardTitle, color = GrokWhite, modifier = Modifier.padding(top = 8.dp), maxLines = 2)
        Text(
            "${remainingMs.formatClock()} · Aşağı ile seç",
            style = GrokType.cardMeta,
            color = GrokMuted,
            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.grokplayer.tv.ui.components.FocusableAction(
                onClick = onPlayNow,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(playFocus)
                    .focusProperties { right = cancelFocus },
            ) { on ->
                Text(
                    "Şimdi oynat",
                    style = GrokType.button,
                    color = if (on) GrokInk else GrokWhite,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (on) GrokYellow else GrokInk.copy(0.35f), RoundedCornerShape(6.dp))
                        .padding(vertical = 8.dp)
                        .padding(horizontal = 8.dp),
                )
            }
            com.grokplayer.tv.ui.components.FocusableAction(
                onClick = onCancel,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(cancelFocus)
                    .focusProperties { left = playFocus },
            ) { on ->
                Text(
                    "İptal",
                    style = GrokType.button,
                    color = if (on) GrokInk else GrokWhite,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (on) GrokYellow else GrokInk.copy(0.35f), RoundedCornerShape(6.dp))
                        .padding(vertical = 8.dp)
                        .padding(horizontal = 8.dp),
                )
            }
        }
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
    onBarFocus: (Boolean) -> Unit = {},
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
            .onFocusChanged { onBarFocus(it.hasFocus) }
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
        SeekBar(
            progress = progress,
            focusedRequester = seekFocus,
            playFocus = playFocus,
            live = live,
            onFocused = onSeekFocused,
            onPlayPause = onPlayPause,
        )
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
                    val frame = frames[time] ?: frames.minByOrNull { kotlin.math.abs(it.key - time) }
                        ?.takeIf { kotlin.math.abs(it.key - time) <= 1_500L }
                        ?.value
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

private fun playClock(player: androidx.media3.common.Player, fallback: Long): Pair<Long, Long> {
    val pos = player.currentPosition.coerceAtLeast(0L)
    val reported = player.duration.takeIf { it > 0L && it != C.TIME_UNSET } ?: fallback
    val dur = if (player.playbackState == androidx.media3.common.Player.STATE_ENDED && pos in 1L until reported) {
        pos
    } else {
        reported
    }
    return pos to dur.coerceAtLeast(pos)
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

private data class PreparedPlay(
    val mediaItem: MediaItem,
    val referer: String?,
    val userAgent: String?,
    val captions: List<YtCaptionTrack> = emptyList(),
    val audios: List<YtAudioTrack> = emptyList(),
    val audioSidecar: java.io.File? = null,
)

private fun preparePlay(context: android.content.Context, video: com.grokplayer.tv.data.LibraryVideo): PreparedPlay {
    val raw = video.uri.toString()
    val local = localPlaybackFile(video.path, video.uri.takeIf { it.scheme == "file" }?.path)
    if (local != null) {
        val kind = sniffContainer(local)
        val playUri = android.net.Uri.fromFile(local)
        val builder = MediaItem.Builder().setUri(playUri)
        Log.i("GrokPlayer", "open local ${local.name} kind=$kind bytes=${local.length()}")
        when (kind) {
            "ts", "m2ts" -> builder.setMimeType(MimeTypes.VIDEO_MP2T)
            "avi" -> builder.setMimeType("video/x-msvideo")
            "m3u8" -> builder.setMimeType(MimeTypes.APPLICATION_M3U8)
            "mpd" -> builder.setMimeType(MimeTypes.APPLICATION_MPD)
        }
        val sidecars = sidecarSubtitleConfigs(video)
        if (sidecars.isNotEmpty()) builder.setSubtitleConfigurations(sidecars)
        return PreparedPlay(
            mediaItem = builder.build(),
            referer = video.referer,
            userAgent = video.userAgent,
            audioSidecar = if (kind == "m3u8") null else sidecarAudioFile(video, local),
        )
    }
    val source = video.originUrl?.takeIf { it.startsWith("http") }
        ?: raw.takeIf { it.startsWith("http") }
        ?: raw
    val lanFile = source.contains("/v1/file")
    val youtubeId = YouTubeResolver.videoId(source)
        ?: video.originUrl?.let { YouTubeResolver.videoId(it) }
    val youtube = if (!lanFile && youtubeId != null) {
        val page = video.originUrl?.takeIf { YouTubeResolver.videoId(it) != null } ?: source
        YouTubeResolver.resolve(context, youtubeId, page)
    } else {
        null
    }
    val resolved = when {
        lanFile -> source
        youtube != null -> youtube.playUrl
        source.startsWith("http") -> StreamProbe.playUrl(source)
        else -> source
    }
    val builder = MediaItem.Builder().setUri(resolved)
    Log.i(
        "GrokPlayer",
        "open mime hls=${StreamProbe.isHls(resolved)} dash=${StreamProbe.isDash(resolved)} url=${resolved.take(96)}",
    )
    when {
        StreamProbe.isHls(resolved) -> builder.setMimeType(MimeTypes.APPLICATION_M3U8)
        StreamProbe.isDash(resolved) -> builder.setMimeType(MimeTypes.APPLICATION_MPD)
        resolved.endsWith(".ts", ignoreCase = true) || video.format.equals("TS", true) -> {
            builder.setMimeType(MimeTypes.VIDEO_MP2T)
        }
        resolved.endsWith(".avi", ignoreCase = true) || video.format.equals("AVI", true) -> {
            builder.setMimeType("video/x-msvideo")
        }
    }
    val sidecars = sidecarSubtitleConfigs(video)
    val youtubeSubs = youtube?.captions.orEmpty().mapNotNull { track ->
        val file = YouTubeCaptions.writeVtt(context, track) ?: return@mapNotNull null
        MediaItem.SubtitleConfiguration.Builder(android.net.Uri.fromFile(file))
            .setMimeType(MimeTypes.TEXT_VTT)
            .setLanguage(track.language)
            .setLabel(track.displayLabel())
            .setId("yt:${track.language}:${if (track.auto) "asr" else "manual"}")
            .setSelectionFlags(0)
            .build()
    }
    val subs = sidecars + youtubeSubs
    if (subs.isNotEmpty()) {
        builder.setSubtitleConfigurations(subs)
    }
    return PreparedPlay(
        mediaItem = builder.build(),
        referer = youtube?.referer ?: video.referer,
        userAgent = youtube?.userAgent ?: video.userAgent,
        captions = youtube?.captions.orEmpty(),
        audios = youtube?.audios.orEmpty(),
    )
}

private fun preferSidecarAudio(player: ExoPlayer, language: String?) {
    val groups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
    if (groups.size < 2) return
    val match = groups.indexOfLast { group ->
        val lang = group.getTrackFormat(0).language
        !language.isNullOrBlank() && !lang.isNullOrBlank() &&
            lang.startsWith(language.take(2), ignoreCase = true)
    }.let { if (it >= 0) it else groups.lastIndex }
    val chosen = groups[match]
    if (chosen.isTrackSelected(0)) return
    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
        .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
        .setOverrideForType(TrackSelectionOverride(chosen.mediaTrackGroup, listOf(0)))
        .build()
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
private fun YtCaptionOverlay(
    lines: List<YtCaptionLine>,
    positionMs: Long,
    fontSp: Float,
    lifted: Boolean,
    modifier: Modifier = Modifier,
) {
    val visible = YouTubeCaptions.visibleLines(lines, positionMs)
    if (visible.isEmpty()) return
    Column(
        modifier.padding(bottom = if (lifted) 196.dp else 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        visible.forEach { line ->
            val spoken = line.words.filter { positionMs + 40L >= it.startMs }
            if (spoken.isEmpty()) return@forEach
            val currentStart = spoken.last().startMs
            Row(
                Modifier
                    .padding(vertical = 3.dp)
                    .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 14.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                spoken.forEachIndexed { index, word ->
                    val current = word.startMs == currentStart
                    Text(
                        text = word.text + if (index == spoken.lastIndex) "" else " ",
                        style = TextStyle(
                            fontSize = fontSp.sp,
                            fontWeight = if (current) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (current) GrokYellow else GrokWhite,
                            shadow = Shadow(color = Color.Black, offset = Offset(0f, 2f), blurRadius = 6f),
                        ),
                    )
                }
            }
        }
    }
}


