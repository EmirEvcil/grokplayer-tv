package com.grokplayer.tv.ui.videos

import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.grokplayer.tv.R
import com.grokplayer.tv.data.LibraryStore
import com.grokplayer.tv.data.LibraryVideo
import com.grokplayer.tv.data.StorageSource
import com.grokplayer.tv.data.VideoSort
import com.grokplayer.tv.data.formatClock
import com.grokplayer.tv.data.link.RemoteState
import com.grokplayer.tv.ui.components.EmptyState
import com.grokplayer.tv.ui.components.FilterChip
import com.grokplayer.tv.ui.components.FocusableAction
import com.grokplayer.tv.ui.components.HintBar
import com.grokplayer.tv.ui.components.OutlineButton
import com.grokplayer.tv.ui.components.VideoPoster
import com.grokplayer.tv.ui.theme.InterceptBack
import com.grokplayer.tv.ui.theme.GrokInk
import com.grokplayer.tv.ui.theme.GrokMuted
import com.grokplayer.tv.ui.theme.GrokPink
import com.grokplayer.tv.ui.theme.GrokSurface
import com.grokplayer.tv.ui.theme.GrokType
import com.grokplayer.tv.ui.theme.GrokWhite
import com.grokplayer.tv.ui.theme.GrokYellow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class VideoFilter { All, Internal, Usb }

@Composable
fun VideosScreen(
    firstFocus: FocusRequester,
    railFocus: FocusRequester,
    library: LibraryStore,
    onPlay: (List<LibraryVideo>, Int, Boolean) -> Unit,
    onSend: (LibraryVideo) -> Unit = {},
    canSend: Boolean = false,
    remote: RemoteState? = null,
    focusVideoId: String? = null,
    onFocusConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var filter by remember { mutableStateOf(VideoFilter.All) }
    var sort by remember { mutableStateOf(VideoSort.RecentlyAdded) }
    var sortOpen by remember { mutableStateOf(false) }
    var folderOpen by remember { mutableStateOf(false) }
    var optionsFor by remember { mutableStateOf<LibraryVideo?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var pendingGridFocus by remember { mutableStateOf(false) }
    val addFolderFocus = remember { FocusRequester() }
    val firstVideoFocus = remember { FocusRequester() }
    val gridState = rememberLazyGridState()
    var savedScrollIndex by remember { mutableStateOf(0) }
    var savedScrollOffset by remember { mutableStateOf(0) }
    var lastWasAddFolder by remember { mutableStateOf(false) }
    var scanFinished by remember { mutableStateOf(library.videos.isNotEmpty()) }

    val videos = remember(library.videos, filter, sort) {
        library.videos
            .filter {
                when (filter) {
                    VideoFilter.All -> true
                    VideoFilter.Internal -> it.source == StorageSource.Internal
                    VideoFilter.Usb -> it.source == StorageSource.Usb
                }
            }
            .sortedWith(
                when (sort) {
                    VideoSort.RecentlyAdded -> compareByDescending { it.dateAdded }
                    VideoSort.Oldest -> compareBy { it.dateAdded }
                    VideoSort.Alphabetical -> compareBy { it.title.lowercase() }
                    VideoSort.Newest -> compareByDescending { it.lastModified }
                },
            )
    }
    fun dismissOverlay(): Boolean {
        return when {
            optionsFor != null -> {
                runCatching { firstVideoFocus.requestFocus() }
                optionsFor = null
                true
            }
            sortOpen -> {
                runCatching { addFolderFocus.requestFocus() }
                sortOpen = false
                true
            }
            else -> false
        }
    }
    InterceptBack(enabled = sortOpen || optionsFor != null) { dismissOverlay() }
    BackHandler(enabled = sortOpen || optionsFor != null) { dismissOverlay() }

    val sources = library.videos.map { it.source }.toSet().size
    val tileFocus = remember(videos.size) { List(videos.size.coerceAtLeast(1)) { FocusRequester() } }

    LaunchedEffect(pendingGridFocus, videos) {
        if (pendingGridFocus && videos.isNotEmpty()) {
            gridState.scrollToItem(0)
            delay(16)
            runCatching { firstVideoFocus.requestFocus() }
            gridState.animateScrollToItem(0)
            pendingGridFocus = false
        } else if (pendingGridFocus) {
            pendingGridFocus = false
        }
    }

    LaunchedEffect(focusVideoId, videos) {
        val id = focusVideoId ?: return@LaunchedEffect
        val index = videos.indexOfFirst { it.id == id }
        if (index < 0) return@LaunchedEffect
        gridState.scrollToItem(savedScrollIndex, savedScrollOffset)
        delay(32)
        val requester = if (index == 0) firstVideoFocus else tileFocus.getOrNull(index)
        repeat(3) {
            runCatching { requester?.requestFocus() }
            delay(40)
        }
        gridState.scrollToItem(savedScrollIndex, savedScrollOffset)
        onFocusConsumed()
    }

    LaunchedEffect(Unit) {
        val start = android.os.SystemClock.elapsedRealtime()
        while (library.videos.isEmpty() && android.os.SystemClock.elapsedRealtime() - start < 2500) {
            delay(50)
        }
        scanFinished = true
    }

    var filterReady by remember { mutableStateOf(false) }
    LaunchedEffect(filter) {
        if (filterReady && videos.isNotEmpty() && !pendingGridFocus) {
            gridState.animateScrollToItem(0)
        }
        filterReady = true
    }

    LaunchedEffect(notice) {
        if (notice != null) {
            delay(2200)
            notice = null
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = 28.dp, end = 28.dp, top = 18.dp, bottom = 10.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 100.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column {
                    Text(stringResource(R.string.nav_videos), style = GrokType.pageTitle, color = GrokWhite)
                    Text(
                        text = if (library.videos.isEmpty()) {
                            stringResource(R.string.videos_empty_meta)
                        } else {
                            "${videos.size} video · $sources kaynak"
                        },
                        style = GrokType.heroMeta,
                        color = GrokMuted,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlineButton(
                        label = stringResource(R.string.add_folder),
                        icon = Icons.Outlined.CreateNewFolder,
                        onClick = { folderOpen = true },
                        modifier = Modifier
                            .then(
                                if ((scanFinished && videos.isEmpty()) || lastWasAddFolder) {
                                    Modifier.focusRequester(firstFocus)
                                } else {
                                    Modifier
                                },
                            )
                            .focusRequester(addFolderFocus)
                            .onFocusChanged { if (it.isFocused) lastWasAddFolder = true }
                            .focusProperties { left = railFocus },
                    )
                    OutlineButton(
                        label = stringResource(sort.labelRes),
                        icon = Icons.AutoMirrored.Outlined.Sort,
                        onClick = { sortOpen = true },
                    )
                }
            }

            Row(
                modifier = Modifier.padding(top = 16.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FilterChip(
                    stringResource(R.string.filter_all),
                    filter == VideoFilter.All,
                    {
                        filter = VideoFilter.All
                        lastWasAddFolder = false
                    },
                    Modifier.focusProperties {
                        left = railFocus
                        canFocus = !pendingGridFocus
                    },
                )
                FilterChip(
                    stringResource(R.string.filter_internal),
                    filter == VideoFilter.Internal,
                    {
                        filter = VideoFilter.Internal
                        lastWasAddFolder = false
                    },
                    Modifier.focusProperties { canFocus = !pendingGridFocus },
                )
                FilterChip(
                    stringResource(R.string.filter_usb),
                    filter == VideoFilter.Usb,
                    {
                        filter = VideoFilter.Usb
                        lastWasAddFolder = false
                    },
                    Modifier.focusProperties { canFocus = !pendingGridFocus },
                )
            }

            if (videos.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.empty_videos_title),
                    body = stringResource(R.string.empty_videos_body),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = gridState,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    itemsIndexed(videos, key = { _, item -> item.id }) { index, video ->
                        VideoTile(
                            video = video,
                            progress = library.progressFraction(video),
                            onPc = remote?.hasVideo(video.title) == true,
                            pcPlaying = remote?.isCurrent(video.title) == true,
                            pcPaused = remote?.paused == true,
                            modifier = Modifier
                                .then(
                                    if (index == 0 && !lastWasAddFolder) {
                                        Modifier.focusRequester(firstFocus)
                                    } else {
                                        Modifier
                                    },
                                )
                                .focusRequester(
                                    when (index) {
                                        0 -> firstVideoFocus
                                        else -> tileFocus[index]
                                    },
                                )
                                .onFocusChanged {
                                    if (it.isFocused) lastWasAddFolder = false
                                }
                                .focusProperties {
                                    left = if (index % 3 == 0) railFocus else FocusRequester.Default
                                },
                            onClick = {
                                savedScrollIndex = gridState.firstVisibleItemIndex
                                savedScrollOffset = gridState.firstVisibleItemScrollOffset
                                onPlay(videos, index, true)
                            },
                            onLongClick = { optionsFor = video },
                        )
                    }
                }
            }
            if (videos.isEmpty()) Spacer(Modifier.weight(1f))
            HintBar(
                parts = listOf(stringResource(R.string.hint_play), stringResource(R.string.hint_options)),
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (sortOpen) {
            SortMenu(
                selected = sort,
                onSelect = {
                    sort = it
                    sortOpen = false
                    lastWasAddFolder = false
                    pendingGridFocus = true
                },
                onDismiss = {
                    runCatching { addFolderFocus.requestFocus() }
                    sortOpen = false
                },
            )
        }
        if (folderOpen) {
            FolderBrowser(
                restoreFocus = addFolderFocus,
                onPick = { dir ->
                    runCatching { addFolderFocus.requestFocus() }
                    folderOpen = false
                    val before = library.videos.map { it.id }.toSet()
                    library.addFolder(Uri.fromFile(dir))
                    scope.launch {
                        library.refresh()
                        val added = library.videos.count { it.id !in before }
                        notice = if (added > 0) {
                            context.getString(R.string.folder_added_count, added)
                        } else {
                            context.getString(R.string.folder_added_none)
                        }
                    }
                },
                onDismiss = {
                    runCatching { addFolderFocus.requestFocus() }
                    folderOpen = false
                },
            )
        }
        optionsFor?.let { video ->
            val optionIndex = videos.indexOfFirst { it.id == video.id }.coerceAtLeast(0)
            VideoOptions(
                video = video,
                onPlay = {
                    optionsFor = null
                    savedScrollIndex = gridState.firstVisibleItemIndex
                    savedScrollOffset = gridState.firstVisibleItemScrollOffset
                    onPlay(videos, optionIndex, true)
                },
                onPlayFromStart = {
                    optionsFor = null
                    savedScrollIndex = gridState.firstVisibleItemIndex
                    savedScrollOffset = gridState.firstVisibleItemScrollOffset
                    onPlay(videos, optionIndex, false)
                },
                canSend = canSend,
                onSend = {
                    optionsFor = null
                    onSend(video)
                },
                onDismiss = {
                    runCatching { firstVideoFocus.requestFocus() }
                    optionsFor = null
                },
            )
        }
        notice?.let { message ->
            Text(
                text = message,
                style = GrokType.cardTitle,
                color = GrokInk,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
                    .background(GrokYellow, RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun VideoTile(
    video: LibraryVideo,
    progress: Float?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    onPc: Boolean = false,
    pcPlaying: Boolean = false,
    pcPaused: Boolean = false,
) {
    FocusableAction(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
    ) { focused ->
        Column(Modifier.fillMaxWidth()) {
            VideoPoster(
                uri = video.uri,
                title = video.title,
                focused = focused,
                progress = progress,
                path = video.path,
                format = video.format,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                overlay = {
                    if (onPc || pcPlaying) {
                        Box(Modifier.fillMaxSize()) {
                            val label = when {
                                pcPlaying && pcPaused -> "PC DURAKLATILDI"
                                pcPlaying -> "PC OYNATIYOR"
                                else -> "PC’DE"
                            }
                            val fill = if (pcPlaying) GrokYellow else GrokPink
                            Text(
                                label,
                                style = GrokType.cardMeta,
                                color = GrokInk,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(6.dp)
                                    .background(fill, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                },
            )
            Text(video.title, style = GrokType.cardTitle, color = GrokWhite, modifier = Modifier.padding(top = 8.dp))
            Text(
                text = "${video.durationMs.formatClock()} · ${video.format} · ${video.sourceLabel}",
                style = GrokType.cardMeta,
                color = GrokMuted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun VideoOptions(
    video: LibraryVideo,
    onPlay: () -> Unit,
    onPlayFromStart: () -> Unit,
    onSend: () -> Unit,
    canSend: Boolean,
    onDismiss: () -> Unit,
) {
    com.grokplayer.tv.ui.theme.RememberFocusLock()
    InterceptBack { onDismiss(); true }
    val first = remember { FocusRequester() }
    BackHandler(onBack = onDismiss)
    com.grokplayer.tv.ui.components.AbsorbOpeningOk {
    Box(
        Modifier
            .fillMaxSize()
            .background(GrokInk.copy(alpha = 0.55f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(360.dp)
                .background(GrokSurface, RoundedCornerShape(12.dp))
                .border(1.dp, GrokYellow.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                .padding(16.dp)
                .focusProperties {
                    left = FocusRequester.Cancel
                    right = FocusRequester.Cancel
                },
        ) {
            Text(video.title, style = GrokType.section, color = GrokWhite)
            Text(
                text = "${video.durationMs.formatClock()} · ${video.format} · ${video.sourceLabel}",
                style = GrokType.cardMeta,
                color = GrokMuted,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            OptionRow(stringResource(R.string.resume), onPlay, Modifier.focusRequester(first))
            OptionRow(stringResource(R.string.play_from_start), onPlayFromStart)
            if (canSend) {
                OptionRow("PC’ye gönder", onSend)
            }
            OptionRow(stringResource(R.string.close), onDismiss)
        }
    }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

@Composable
private fun OptionRow(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused = interaction.collectIsFocusedAsState().value
    Text(
        text = label,
        style = GrokType.button,
        color = if (focused) GrokInk else GrokWhite,
        modifier = modifier
            .fillMaxWidth()
            .background(if (focused) GrokYellow else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(6.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

@Composable
private fun SortMenu(
    selected: VideoSort,
    onSelect: (VideoSort) -> Unit,
    onDismiss: () -> Unit,
) {
    com.grokplayer.tv.ui.theme.RememberFocusLock()
    InterceptBack { onDismiss(); true }
    val first = remember { FocusRequester() }
    BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(GrokInk.copy(alpha = 0.55f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() },
        contentAlignment = Alignment.TopEnd,
    ) {
        Column(
            Modifier
                .padding(top = 72.dp, end = 130.dp)
                .width(220.dp)
                .background(GrokSurface, RoundedCornerShape(10.dp))
                .border(1.dp, GrokYellow.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                .padding(8.dp)
                .focusProperties {
                    left = FocusRequester.Cancel
                    right = FocusRequester.Cancel
                },
        ) {
            VideoSort.entries.forEachIndexed { index, option ->
                val interaction = remember { MutableInteractionSource() }
                val focused = interaction.collectIsFocusedAsState().value
                Text(
                    text = stringResource(option.labelRes),
                    style = GrokType.button,
                    color = if (focused || option == selected) GrokInk else GrokWhite,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (index == 0) Modifier.focusRequester(first) else Modifier)
                        .background(
                            when {
                                focused -> GrokYellow
                                option == selected -> GrokYellow.copy(alpha = 0.35f)
                                else -> androidx.compose.ui.graphics.Color.Transparent
                            },
                            RoundedCornerShape(6.dp),
                        )
                        .clickable(interactionSource = interaction, indication = null) { onSelect(option) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

private val VideoSort.labelRes: Int
    get() = when (this) {
        VideoSort.RecentlyAdded -> R.string.sort_recent
        VideoSort.Oldest -> R.string.sort_oldest
        VideoSort.Alphabetical -> R.string.sort_az
        VideoSort.Newest -> R.string.sort_newest
    }
