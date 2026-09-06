package com.grokplayer.tv.ui.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.grokplayer.tv.R
import com.grokplayer.tv.data.DownloadStore
import com.grokplayer.tv.data.LibraryStore
import com.grokplayer.tv.data.LibraryVideo
import com.grokplayer.tv.data.PlaySession
import com.grokplayer.tv.data.PlaybackSettings
import com.grokplayer.tv.data.StreamStore
import com.grokplayer.tv.data.link.LinkController
import com.grokplayer.tv.data.link.PairedPc
import com.grokplayer.tv.ui.devices.DeviceHub
import com.grokplayer.tv.ui.devices.DeviceOptions
import com.grokplayer.tv.ui.devices.PairPinOverlay
import com.grokplayer.tv.ui.devices.SendToPcSheet
import com.grokplayer.tv.ui.devices.TransferOverlay
import com.grokplayer.tv.data.ThumbnailCache
import com.grokplayer.tv.ui.Destination
import com.grokplayer.tv.ui.player.PlayerScreen
import com.grokplayer.tv.ui.downloads.DownloadsScreen
import com.grokplayer.tv.ui.home.HomeScreen
import com.grokplayer.tv.ui.settings.SettingsCategory
import com.grokplayer.tv.ui.settings.SettingsScreen
import com.grokplayer.tv.ui.streams.StreamsScreen
import com.grokplayer.tv.ui.search.SearchOverlay
import com.grokplayer.tv.ui.search.SearchTarget
import com.grokplayer.tv.ui.theme.GrokInk
import com.grokplayer.tv.ui.theme.GrokMuted
import com.grokplayer.tv.ui.theme.GrokSidebar
import com.grokplayer.tv.ui.theme.GrokSoft
import com.grokplayer.tv.ui.theme.GrokType
import com.grokplayer.tv.ui.theme.GrokWhite
import com.grokplayer.tv.ui.theme.GrokYellow
import androidx.compose.runtime.DisposableEffect
import com.grokplayer.tv.ui.theme.AppBack
import com.grokplayer.tv.ui.theme.BackHub
import com.grokplayer.tv.ui.theme.LocalBackHub
import com.grokplayer.tv.ui.theme.LocalFocusLock
import com.grokplayer.tv.ui.theme.LocalPlaceholderAction
import com.grokplayer.tv.ui.videos.VideosScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TvShell() {
    val context = LocalContext.current
    val library = remember { LibraryStore(context) }
    val settings = remember { PlaybackSettings(context) }
    val streams = remember { StreamStore(context) }
    val downloads = remember { DownloadStore(context, settings) }
    val link = remember { LinkController(context) }
    val linkUi by link.ui.collectAsState()
    var hubPc by remember { mutableStateOf<PairedPc?>(null) }
    var deviceMenuPc by remember { mutableStateOf<PairedPc?>(null) }
    var sendVideo by remember { mutableStateOf<LibraryVideo?>(null) }
    var transferTitle by remember { mutableStateOf<String?>(null) }
    var transferDone by remember { mutableStateOf(0L) }
    var transferTotal by remember { mutableStateOf(0L) }
    val scope = rememberCoroutineScope()
    var destination by remember { mutableStateOf(settings.startScreen) }
    var notice by remember { mutableStateOf<String?>(null) }
    var lastSettingsCategory by rememberSaveable { mutableStateOf(SettingsCategory.Playback.name) }
    var session by remember { mutableStateOf<PlaySession?>(null) }
    var resumePlayback by remember { mutableStateOf(true) }
    var searchOpen by remember { mutableStateOf(false) }
    var focusVideoId by remember { mutableStateOf<String?>(null) }
    var focusStreamId by remember { mutableStateOf<String?>(null) }
    var focusHomeId by remember { mutableStateOf<String?>(null) }
    var focusSettingKey by remember { mutableStateOf<String?>(null) }
    val focusLock = remember { mutableStateOf(false) }
    val backHub = remember { BackHub() }
    val navFocus = remember { Destination.entries.associateWith { FocusRequester() } }
    val pageFocus = remember { Destination.entries.associateWith { FocusRequester() } }
    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { scope.launch { library.refresh() } }

    DisposableEffect(Unit) {
        link.start()
        onDispose { link.stop() }
    }

    LaunchedEffect(Unit) {
        val needed = if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permission.launch(needed)
        library.refresh()
    }

    var lastDownloadDone by remember { mutableStateOf(-1) }
    LaunchedEffect(downloads.items.count { it.status == com.grokplayer.tv.data.DownloadStatus.Done }) {
        val done = downloads.items.count { it.status == com.grokplayer.tv.data.DownloadStatus.Done }
        if (lastDownloadDone < 0) {
            lastDownloadDone = done
            return@LaunchedEffect
        }
        if (done != lastDownloadDone) {
            lastDownloadDone = done
            library.refresh()
        }
    }

    LaunchedEffect(Unit) {
        delay(50)
        runCatching { pageFocus.getValue(destination).requestFocus() }
    }

    LaunchedEffect(notice) {
        if (notice != null) {
            delay(2200)
            notice = null
        }
    }

    val backState = remember { AppBackState(backHub) }
    backState.playing = session != null
    backState.onHome = destination == Destination.Home
    backState.closePlayer = { session = null }
    backState.goHome = { destination = Destination.Home }
    AppBack.handler = {
        when {
            backState.hub.dispatch() -> true
            backState.playing -> {
                backState.closePlayer()
                true
            }
            !backState.onHome -> {
                backState.goHome()
                true
            }
            else -> false
        }
    }
    DisposableEffect(Unit) {
        onDispose { AppBack.handler = null }
    }

    CompositionLocalProvider(
        LocalPlaceholderAction provides { title ->
            notice = "$title · oynatma yakında"
        },
        LocalFocusLock provides focusLock,
        LocalBackHub provides backHub,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(GrokInk),
        ) {
            val playing = session
            Row(
                Modifier
                    .fillMaxSize()
                    .focusProperties {
                        if (session != null) {
                            canFocus = false
                            onEnter = { FocusRequester.Cancel }
                        }
                    },
            ) {
                SideRail(
                    selected = destination,
                    navFocus = navFocus,
                    locked = focusLock.value || session != null,
                    onSelect = { destination = it },
                    onEnter = { dest ->
                        destination = dest
                        scope.launch {
                            delay(40)
                            runCatching { pageFocus.getValue(dest).requestFocus() }
                        }
                    },
                )
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    val rail = navFocus.getValue(destination)
                    AnimatedContent(
                        targetState = destination,
                        label = "page",
                        transitionSpec = {
                            (fadeIn(tween(240)) + slideInHorizontally(tween(240)) { it / 18 })
                                .togetherWith(fadeOut(tween(180)) + slideOutHorizontally(tween(180)) { -it / 18 })
                        },
                    ) { page ->
                        val focus = pageFocus.getValue(page)
                        when (page) {
                            Destination.Home -> HomeScreen(
                                resumeFocus = focus,
                                railFocus = rail,
                                library = library,
                                onPlay = { queue, index, resume ->
                                    resumePlayback = resume
                                    session = PlaySession(queue, index)
                                },
                                focusItemId = focusHomeId,
                                onFocusConsumed = { focusHomeId = null },
                                modifier = Modifier.fillMaxSize(),
                            )
                            Destination.Videos -> VideosScreen(
                                firstFocus = focus,
                                railFocus = rail,
                                library = library,
                                onPlay = { queue, index, resume ->
                                    resumePlayback = resume
                                    session = PlaySession(queue, index)
                                },
                                canSend = linkUi.paired.isNotEmpty(),
                                onSend = { sendVideo = it },
                                focusVideoId = focusVideoId,
                                onFocusConsumed = { focusVideoId = null },
                                modifier = Modifier.fillMaxSize(),
                            )
                            Destination.Streams -> StreamsScreen(
                                firstFocus = focus,
                                railFocus = rail,
                                streams = streams,
                                downloads = downloads,
                                downloadHeight = settings.downloadHeight,
                                onPlay = { queue, index ->
                                    resumePlayback = true
                                    session = PlaySession(queue, index)
                                },
                                onNotice = { notice = it },
                                focusStreamId = focusStreamId,
                                onFocusConsumed = { focusStreamId = null },
                                modifier = Modifier.fillMaxSize(),
                            )
                            Destination.Downloads -> DownloadsScreen(
                                firstFocus = focus,
                                railFocus = rail,
                                downloads = downloads,
                                onPlay = { queue, index ->
                                    resumePlayback = true
                                    session = PlaySession(queue, index)
                                },
                                onRemoved = { item ->
                                    library.forget("download:${item.id}")
                                    item.localPath?.let { library.forgetPath(it) }
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                            Destination.Settings -> SettingsScreen(
                                firstFocus = focus,
                                railFocus = rail,
                                initialCategory = runCatching {
                                    SettingsCategory.valueOf(lastSettingsCategory)
                                }.getOrDefault(SettingsCategory.Playback),
                                onCategoryChanged = { lastSettingsCategory = it.name },
                                settings = settings,
                                link = link,
                                onOpenDevice = { hubPc = it },
                                onDeviceMenu = { deviceMenuPc = it },
                                focusSettingKey = focusSettingKey,
                                onFocusConsumed = { focusSettingKey = null },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    TopChrome(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 18.dp, end = 28.dp),
                        downFocus = pageFocus.getValue(destination),
                        onSearch = { if (!focusLock.value) searchOpen = true },
                    )
                }
            }
            DisposableEffect(session != null) {
                ThumbnailCache.playbackActive = session != null
                onDispose { ThumbnailCache.playbackActive = false }
            }
            if (playing != null) {
                PlayerScreen(
                    session = playing,
                    resume = resumePlayback,
                    library = library,
                    settings = settings,
                    onClose = { lastId ->
                        session = null
                        when (destination) {
                            Destination.Videos -> focusVideoId = lastId
                            Destination.Streams -> focusStreamId = lastId
                            Destination.Home -> focusHomeId = lastId
                            else -> Unit
                        }
                    },
                )
            }
            if (searchOpen) {
                SearchOverlay(
                    library = library,
                    streams = streams,
                    onDismiss = { searchOpen = false },
                    onOpen = { target ->
                        searchOpen = false
                        when (target) {
                            is SearchTarget.Video -> {
                                destination = Destination.Videos
                                focusVideoId = target.id
                            }
                            is SearchTarget.Stream -> {
                                destination = Destination.Streams
                                focusStreamId = target.id
                            }
                            is SearchTarget.Setting -> {
                                lastSettingsCategory = target.category.name
                                focusSettingKey = target.key
                                destination = Destination.Settings
                            }
                        }
                    },
                )
            }
            if (linkUi.visible) {
                linkUi.pin?.let { pin ->
                    PairPinOverlay(pin = pin, onCancel = { link.cancelPair() })
                }
            }
            LaunchedEffect(linkUi.paired, hubPc) {
                val open = hubPc ?: return@LaunchedEffect
                if (linkUi.paired.none { it.id == open.id }) hubPc = null
            }
            sendVideo?.let { video ->
                val pcs = linkUi.paired
                if (pcs.isEmpty()) {
                    sendVideo = null
                } else {
                    SendToPcSheet(
                        video = video,
                        pcs = pcs,
                        onDismiss = { sendVideo = null },
                        onSend = { pc, mode, whenPlay ->
                            sendVideo = null
                            transferTitle = video.title
                            transferDone = 0
                            transferTotal = 0
                            link.send(pc, video, mode, whenPlay) { done, total ->
                                scope.launch(Dispatchers.Main.immediate) {
                                    transferDone = done
                                    transferTotal = total
                                    if (total > 0 && done >= total) transferTitle = null
                                }
                            }
                        },
                    )
                }
            }
            transferTitle?.let { title ->
                TransferOverlay(
                    title = title,
                    done = transferDone,
                    total = transferTotal,
                    onBackground = { transferTitle = null },
                    onCancel = { transferTitle = null },
                )
            }
            hubPc?.let { pc ->
                DeviceHub(link = link, pc = pc, onClose = { hubPc = null })
            }
            deviceMenuPc?.let { pc ->
                val connected = linkUi.nearby.any { it.id == pc.id }
                DeviceOptions(
                    pc = pc,
                    connected = connected,
                    onManage = {
                        deviceMenuPc = null
                        hubPc = pc
                    },
                    onDisconnect = {
                        link.disconnect(pc.id)
                        deviceMenuPc = null
                    },
                    onRemove = {
                        link.forget(pc.id)
                        deviceMenuPc = null
                        if (hubPc?.id == pc.id) hubPc = null
                    },
                    onDismiss = { deviceMenuPc = null },
                )
            }
            notice?.let { message ->
                Text(
                    text = message,
                    style = GrokType.cardTitle,
                    color = GrokInk,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(GrokYellow)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SideRail(
    selected: Destination,
    navFocus: Map<Destination, FocusRequester>,
    locked: Boolean,
    onSelect: (Destination) -> Unit,
    onEnter: (Destination) -> Unit,
) {
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier
            .width(156.dp)
            .fillMaxHeight()
            .background(GrokSidebar)
            .padding(top = 22.dp, bottom = 24.dp),
    ) {
        BrandMark(Modifier.padding(start = 18.dp, end = 12.dp))
        Spacer(Modifier.height(88.dp))
        Destination.entries.forEach { item ->
            NavRow(
                destination = item,
                selected = item == selected,
                onSelect = {
                    onSelect(item)
                    scope.launch {
                        delay(80)
                        runCatching { navFocus.getValue(item).requestFocus() }
                    }
                },
                onEnter = { onEnter(item) },
                modifier = Modifier
                    .focusRequester(navFocus.getValue(item))
                    .focusProperties {
                        canFocus = !locked
                        right = FocusRequester.Cancel
                    },
            )
        }
    }
}

@Composable
private fun BrandMark(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.logo_mark),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = "GrokPlayer",
            style = GrokType.wordmark,
            color = GrokWhite,
        )
    }
}

@Composable
private fun NavRow(
    destination: Destination,
    selected: Boolean,
    onSelect: () -> Unit,
    onEnter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused = interaction.collectIsFocusedAsState().value
    val iconTint = when {
        selected -> GrokYellow
        focused -> GrokSoft
        else -> GrokMuted
    }
    val labelColor = if (focused) GrokWhite else GrokMuted

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (event.key == Key.DirectionRight) {
                    onEnter()
                    true
                } else {
                    false
                }
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onSelect,
            )
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (selected) GrokYellow else Color.Transparent),
        )
        Icon(
            imageVector = destination.icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier
                .padding(start = 16.dp)
                .size(20.dp),
        )
        Text(
            text = stringResource(destination.labelRes),
            style = GrokType.nav,
            color = labelColor,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

@Composable
private fun TopChrome(
    onSearch: () -> Unit,
    downFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    var clock by remember { mutableStateOf(currentClock()) }
    LaunchedEffect(Unit) {
        while (true) {
            clock = currentClock()
            delay(15_000)
        }
    }
    val interaction = remember { MutableInteractionSource() }
    val focused = interaction.collectIsFocusedAsState().value

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = stringResource(R.string.search),
            tint = if (focused) GrokYellow else GrokSoft,
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .focusProperties { down = downFocus }
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onSearch,
                )
                .padding(1.dp),
        )
        Text(text = clock, style = GrokType.clock, color = GrokSoft)
    }
}

private val clockFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

private fun currentClock(): String = clockFormat.format(Date())

private class AppBackState(val hub: BackHub) {
    var playing: Boolean = false
    var onHome: Boolean = false
    var closePlayer: () -> Unit = {}
    var goHome: () -> Unit = {}
}
