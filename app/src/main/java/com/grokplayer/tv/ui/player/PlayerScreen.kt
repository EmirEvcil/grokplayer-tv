package com.grokplayer.tv.ui.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material.icons.outlined.PlaylistPlay
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.grokplayer.tv.R
import com.grokplayer.tv.data.LibraryStore
import com.grokplayer.tv.data.LibraryVideo
import com.grokplayer.tv.data.PlaySession
import com.grokplayer.tv.data.PlaybackSettings
import com.grokplayer.tv.data.formatClock
import com.grokplayer.tv.ui.theme.GrokInk
import com.grokplayer.tv.ui.theme.GrokMuted
import com.grokplayer.tv.ui.theme.GrokPink
import com.grokplayer.tv.ui.theme.GrokSoft
import com.grokplayer.tv.ui.theme.GrokType
import com.grokplayer.tv.ui.theme.GrokWhite
import com.grokplayer.tv.ui.theme.GrokYellow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    session: PlaySession,
    resume: Boolean,
    library: LibraryStore,
    settings: PlaybackSettings,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var index by remember { mutableStateOf(session.startIndex.coerceIn(0, session.queue.lastIndex)) }
    var playlistOpen by remember { mutableStateOf(false) }
    var controls by remember { mutableStateOf(true) }
    var playing by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var muted by remember { mutableStateOf(false) }
    var speed by remember { mutableStateOf(settings.defaultSpeed) }
    var toast by remember { mutableStateOf<String?>(null) }
    val playFocus = remember { FocusRequester() }
    val video = session.queue[index]

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            setPlaybackSpeed(settings.defaultSpeed)
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && settings.autoNext && index < session.queue.lastIndex) {
                    index += 1
                } else if (playbackState == Player.STATE_ENDED) {
                    controls = true
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
        player.setMediaItem(MediaItem.fromUri(item.uri))
        player.prepare()
        val start = if (resume && settings.resumeEnabled) library.progressOf(item.id) else 0L
        if (start > 1_000L) player.seekTo(start)
        player.play()
        player.setPlaybackSpeed(speed)
        controls = true
    }

    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition
            duration = player.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: video.durationMs
            delay(250)
        }
    }

    LaunchedEffect(controls, playing, playlistOpen) {
        if (controls && playing && !playlistOpen) {
            delay(settings.hideControlsSeconds * 1000L)
            controls = false
        }
    }

    LaunchedEffect(toast) {
        if (toast != null) {
            delay(1600)
            toast = null
        }
    }

    fun seekBy(delta: Long) {
        val next = (player.currentPosition + delta).coerceAtLeast(0)
        player.seekTo(next)
        controls = true
    }

    fun playIndex(newIndex: Int, fromStart: Boolean) {
        library.markPlayed(session.queue[index].id, player.currentPosition)
        index = newIndex
        if (!fromStart) {
            // resume handled in LaunchedEffect via resume flag only for first
        }
    }

    BackHandler {
        when {
            playlistOpen -> playlistOpen = false
            controls -> controls = false
            else -> {
                library.markPlayed(video.id, player.currentPosition)
                onClose()
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                controls = !controls
            },
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    this.player = player
                }
            },
            update = { it.player = player },
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
                    .height(220.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.82f)))),
            )
            TopBar(title = video.title, durationMs = duration)
            if (!playlistOpen) {
                BottomControls(
                    playing = playing,
                    position = position,
                    duration = duration,
                    playFocus = playFocus,
                    seekStep = settings.seekStepSeconds,
                    onPlayPause = {
                        if (player.isPlaying) player.pause() else player.play()
                        controls = true
                    },
                    onSeek = { seekBy(it) },
                    onPrev = { if (index > 0) playIndex(index - 1, false) },
                    onNext = { if (index < session.queue.lastIndex) playIndex(index + 1, false) },
                    onAudio = {
                        muted = !muted
                        player.volume = if (muted) 0f else 1f
                        toast = if (muted) "Ses kapalı" else "Ses açık"
                    },
                    onCaption = { toast = "Altyazı yakında" },
                    onSpeed = {
                        speed = when {
                            speed < 1f -> 1f
                            speed < 1.2f -> 1.25f
                            speed < 1.6f -> 1.5f
                            else -> 0.75f
                        }
                        player.setPlaybackSpeed(speed)
                        toast = "Hız ${speed}×"
                    },
                    onList = { playlistOpen = true },
                )
            }
        }

        if (playlistOpen) {
            PlaylistPanel(
                videos = session.queue,
                currentId = video.id,
                onSelect = { i ->
                    playIndex(i, false)
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

    LaunchedEffect(controls, playlistOpen) {
        if (controls && !playlistOpen) runCatching { playFocus.requestFocus() }
    }
}

@Composable
private fun TopBar(title: String, durationMs: Long) {
    val clock = remember { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()) }
    val badge = if (durationMs > 0) "FHD" else "HD"
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 28.dp, end = 28.dp, top = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(R.drawable.logo_mark),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(20.dp),
        )
        Text("GrokPlayer", style = GrokType.wordmark, color = GrokWhite, modifier = Modifier.padding(start = 8.dp))
        Text("  |  $title", style = GrokType.section, color = GrokWhite, modifier = Modifier.padding(start = 8.dp))
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
    playFocus: FocusRequester,
    seekStep: Int,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onAudio: () -> Unit,
    onCaption: () -> Unit,
    onSpeed: () -> Unit,
    onList: () -> Unit,
) {
    val progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Column(
        Modifier
            .fillMaxSize()
            .padding(start = 36.dp, end = 36.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(Color.White.copy(0.2f), RoundedCornerShape(2.dp)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .height(4.dp)
                    .background(GrokPink, RoundedCornerShape(2.dp)),
            )
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = ((progress * 1000).toInt()).dp / 1000)
                    .size(10.dp)
                    .background(GrokPink, CircleShape),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(position.formatClock(), style = GrokType.cardMeta, color = GrokSoft)
            Text(duration.formatClock(), style = GrokType.cardMeta, color = GrokSoft)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Transport(Icons.Outlined.SkipPrevious, onPrev)
            Transport(Icons.Outlined.FastRewind, { onSeek(-seekStep * 1000L) })
            Transport(
                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                onPlayPause,
                primary = true,
                modifier = Modifier.focusRequester(playFocus),
            )
            Transport(Icons.Outlined.FastForward, { onSeek(seekStep * 1000L) })
            Transport(Icons.Outlined.SkipNext, onNext)
            Spacer(Modifier.width(28.dp))
            ActionChip(Icons.Outlined.VolumeUp, "Ses", onAudio)
            ActionChip(Icons.Outlined.ClosedCaption, "Altyazı", onCaption)
            ActionChip(Icons.Outlined.Speed, "Hız", onSpeed)
            ActionChip(Icons.Outlined.PlaylistPlay, "Liste", onList)
        }
        Text(
            text = "OK: Oynat / Duraklat     ← →: İleri / Geri     Geri: Kapat",
            style = GrokType.cardMeta,
            color = GrokMuted,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 12.dp),
        )
    }
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
    val size = if (primary) 58.dp else 44.dp
    Box(
        modifier
            .padding(end = 10.dp)
            .size(size)
            .background(
                when {
                    focused && primary -> GrokYellow
                    focused -> Color.White.copy(0.16f)
                    primary -> GrokYellow.copy(alpha = 0.92f)
                    else -> Color.White.copy(0.10f)
                },
                CircleShape,
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            null,
            tint = if (primary) GrokInk else GrokWhite,
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
                .size(44.dp)
                .background(if (focused) GrokYellow else Color.White.copy(0.10f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (focused) GrokInk else GrokWhite, modifier = Modifier.size(22.dp))
        }
        Text(label, style = GrokType.cardMeta, color = if (focused) GrokWhite else GrokSoft, modifier = Modifier.padding(top = 4.dp))
    }
}
