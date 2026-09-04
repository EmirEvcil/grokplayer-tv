package com.grokplayer.tv.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.grokplayer.tv.R
import com.grokplayer.tv.data.LibraryStore
import com.grokplayer.tv.data.PlaySession
import com.grokplayer.tv.data.PlaybackSettings
import com.grokplayer.tv.ui.Destination
import com.grokplayer.tv.ui.player.PlayerScreen
import com.grokplayer.tv.ui.downloads.DownloadsScreen
import com.grokplayer.tv.ui.home.HomeScreen
import com.grokplayer.tv.ui.settings.SettingsCategory
import com.grokplayer.tv.ui.settings.SettingsScreen
import com.grokplayer.tv.ui.streams.StreamsScreen
import com.grokplayer.tv.ui.theme.GrokInk
import com.grokplayer.tv.ui.theme.GrokMuted
import com.grokplayer.tv.ui.theme.GrokSidebar
import com.grokplayer.tv.ui.theme.GrokSoft
import com.grokplayer.tv.ui.theme.GrokType
import com.grokplayer.tv.ui.theme.GrokWhite
import com.grokplayer.tv.ui.theme.GrokYellow
import com.grokplayer.tv.ui.theme.LocalPlaceholderAction
import com.grokplayer.tv.ui.videos.VideosScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TvShell() {
    val context = LocalContext.current
    val library = remember { LibraryStore(context) }
    val settings = remember { PlaybackSettings(context) }
    val scope = rememberCoroutineScope()
    var destination by remember { mutableStateOf(settings.startScreen) }
    var notice by remember { mutableStateOf<String?>(null) }
    var lastSettingsCategory by rememberSaveable { mutableStateOf(SettingsCategory.Playback.name) }
    var session by remember { mutableStateOf<PlaySession?>(null) }
    var resumePlayback by remember { mutableStateOf(true) }
    val navFocus = remember { Destination.entries.associateWith { FocusRequester() } }
    val pageFocus = remember { Destination.entries.associateWith { FocusRequester() } }
    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { scope.launch { library.refresh() } }

    LaunchedEffect(Unit) {
        val needed = if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permission.launch(needed)
        library.refresh()
    }

    LaunchedEffect(destination) {
        delay(50)
        runCatching { pageFocus.getValue(destination).requestFocus() }
    }

    LaunchedEffect(notice) {
        if (notice != null) {
            delay(2200)
            notice = null
        }
    }

    BackHandler(enabled = session == null && destination != Destination.Home) {
        destination = Destination.Home
    }

    CompositionLocalProvider(
        LocalPlaceholderAction provides { title ->
            notice = "$title · oynatma yakında"
        },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(GrokInk),
        ) {
            val playing = session
            if (playing != null) {
                PlayerScreen(
                    session = playing,
                    resume = resumePlayback,
                    library = library,
                    settings = settings,
                    onClose = { session = null },
                )
            } else {
            Row(Modifier.fillMaxSize()) {
                SideRail(
                    selected = destination,
                    navFocus = navFocus,
                    contentFocus = pageFocus.getValue(destination),
                    onSelect = { destination = it },
                )
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    val rail = navFocus.getValue(destination)
                    AnimatedContent(targetState = destination, label = "page") { page ->
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
                                modifier = Modifier.fillMaxSize(),
                            )
                            Destination.Videos -> VideosScreen(
                                firstFocus = focus,
                                railFocus = rail,
                                library = library,
                                onPlay = { queue, index ->
                                    resumePlayback = true
                                    session = PlaySession(queue, index)
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                            Destination.Streams -> StreamsScreen(
                                firstFocus = focus,
                                railFocus = rail,
                                modifier = Modifier.fillMaxSize(),
                            )
                            Destination.Downloads -> DownloadsScreen(
                                firstFocus = focus,
                                railFocus = rail,
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
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    TopChrome(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 18.dp, end = 28.dp),
                        downFocus = pageFocus.getValue(destination),
                        onSearch = { notice = "Arama yakında" },
                    )
                }
            }
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
    contentFocus: FocusRequester,
    onSelect: (Destination) -> Unit,
) {
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
                onSelect = { onSelect(item) },
                modifier = Modifier
                    .focusRequester(navFocus.getValue(item))
                    .focusProperties { right = contentFocus },
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
