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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import kotlinx.coroutines.withContext

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
    var detailsFor by remember { mutableStateOf<LibraryVideo?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var pendingGridFocus by remember { mutableStateOf(false) }
    var restoreAfterModal by remember { mutableStateOf(false) }
    var restoreVideoId by rememberSaveable { mutableStateOf<String?>(null) }
    var pinFocus by remember { mutableStateOf(false) }
    val addFolderFocus = remember { FocusRequester() }
    val sortFocus = remember { FocusRequester() }
    val filterFocus = remember { List(3) { FocusRequester() } }
    val firstVideoFocus = remember { FocusRequester() }
    val tileById = remember { mutableMapOf<String, FocusRequester>() }
    fun tileRequester(id: String) = tileById.getOrPut(id) { FocusRequester() }
    fun overlayOpen() = optionsFor != null || detailsFor != null || sortOpen || folderOpen
    val gridState = rememberLazyGridState()
    var savedScrollIndex by remember { mutableStateOf(0) }
    var savedScrollOffset by remember { mutableStateOf(0) }
    var lastFocusedIndex by rememberSaveable { mutableIntStateOf(0) }
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
                    VideoSort.Episode -> Comparator { a, b ->
                        com.grokplayer.tv.data.MediaOrder.compare(a.title, b.title)
                    }
                },
            )
    }
    fun pinVideo(video: LibraryVideo, index: Int) {
        pinFocus = true
        restoreVideoId = video.id
        lastFocusedIndex = index
        savedScrollIndex = gridState.firstVisibleItemIndex
        savedScrollOffset = gridState.firstVisibleItemScrollOffset
    }
    fun closeOverlays() {
        pinFocus = true
        if (restoreVideoId == null) {
            restoreVideoId = videos.getOrNull(lastFocusedIndex)?.id
        }
        detailsFor = null
        optionsFor = null
        sortOpen = false
        restoreAfterModal = true
    }
    fun dismissOverlay(): Boolean {
        if (!overlayOpen()) return false
        closeOverlays()
        return true
    }
    InterceptBack(enabled = sortOpen || optionsFor != null || detailsFor != null) { dismissOverlay() }
    BackHandler(enabled = sortOpen || optionsFor != null || detailsFor != null) { dismissOverlay() }

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

    suspend fun restoreSavedScroll() {
        runCatching { gridState.scrollToItem(savedScrollIndex, savedScrollOffset) }
    }

    LaunchedEffect(restoreAfterModal, restoreVideoId) {
        if (!restoreAfterModal) return@LaunchedEffect
        if (videos.isEmpty()) {
            runCatching { addFolderFocus.requestFocus() }
            restoreAfterModal = false
            pinFocus = false
            return@LaunchedEffect
        }
        restoreSavedScroll()
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
                .padding(start = 28.dp, end = 28.dp, top = 18.dp, bottom = 10.dp)
                .focusProperties {
                    if (optionsFor != null || sortOpen || folderOpen || detailsFor != null) {
                        canFocus = false
                        onEnter = { FocusRequester.Cancel }
                    }
                },
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
                                if (scanFinished && videos.isEmpty()) {
                                    Modifier.focusRequester(firstFocus)
                                } else {
                                    Modifier
                                },
                            )
                            .focusRequester(addFolderFocus)
                            .focusProperties {
                                left = railFocus
                                right = sortFocus
                                down = filterFocus[0]
                            },
                    )
                    OutlineButton(
                        label = stringResource(sort.labelRes),
                        icon = Icons.AutoMirrored.Outlined.Sort,
                        onClick = { sortOpen = true },
                        modifier = Modifier
                            .focusRequester(sortFocus)
                            .focusProperties {
                                left = addFolderFocus
                                down = filterFocus[0]
                            },
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
                    },
                    Modifier
                        .then(
                            if (!(scanFinished && videos.isEmpty())) {
                                Modifier.focusRequester(firstFocus)
                            } else {
                                Modifier
                            },
                        )
                        .focusRequester(filterFocus[0])
                        .focusProperties {
                            left = railFocus
                            right = filterFocus[1]
                            up = addFolderFocus
                            down = if (videos.isNotEmpty()) firstVideoFocus else FocusRequester.Default
                            if (pendingGridFocus) canFocus = false
                        },
                )
                FilterChip(
                    stringResource(R.string.filter_internal),
                    filter == VideoFilter.Internal,
                    {
                        filter = VideoFilter.Internal
                    },
                    Modifier
                        .focusRequester(filterFocus[1])
                        .focusProperties {
                            left = filterFocus[0]
                            right = filterFocus[2]
                            up = addFolderFocus
                            down = if (videos.isNotEmpty()) firstVideoFocus else FocusRequester.Default
                            if (pendingGridFocus) canFocus = false
                        },
                )
                FilterChip(
                    stringResource(R.string.filter_usb),
                    filter == VideoFilter.Usb,
                    {
                        filter = VideoFilter.Usb
                    },
                    Modifier
                        .focusRequester(filterFocus[2])
                        .focusProperties {
                            left = filterFocus[1]
                            up = addFolderFocus
                            down = if (videos.isNotEmpty()) firstVideoFocus else FocusRequester.Default
                            if (pendingGridFocus) canFocus = false
                        },
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
                        if (restoreAfterModal && video.id == restoreVideoId) {
                            LaunchedEffect(video.id, restoreAfterModal) {
                                restoreSavedScroll()
                                repeat(16) {
                                    val ok = runCatching { tileRequester(video.id).requestFocus() }.getOrDefault(false)
                                    restoreSavedScroll()
                                    if (ok) {
                                        lastFocusedIndex = index
                                        restoreAfterModal = false
                                        delay(16)
                                        restoreSavedScroll()
                                        pinFocus = false
                                        return@LaunchedEffect
                                    }
                                    delay(50)
                                }
                                restoreAfterModal = false
                                pinFocus = false
                            }
                        }
                        VideoTile(
                            video = video,
                            progress = library.progressFraction(video),
                            onPc = remote?.hasVideo(video.title) == true,
                            pcPlaying = remote?.isCurrent(video.title) == true,
                            pcPaused = remote?.paused == true,
                            modifier = Modifier
                                .then(
                                    if (index == 0) Modifier.focusRequester(firstVideoFocus) else Modifier,
                                )
                                .focusRequester(tileRequester(video.id))
                                .onFocusChanged {
                                    if (it.isFocused && !overlayOpen() && !pinFocus) {
                                        lastFocusedIndex = index
                                        restoreVideoId = video.id
                                    }
                                }
                                .focusProperties {
                                    if (overlayOpen()) canFocus = false
                                    left = if (index % 3 == 0) railFocus else FocusRequester.Default
                                    up = if (index < 3) filterFocus[0] else FocusRequester.Default
                                },
                            onClick = {
                                savedScrollIndex = gridState.firstVisibleItemIndex
                                savedScrollOffset = gridState.firstVisibleItemScrollOffset
                                onPlay(videos, index, true)
                            },
                            onLongClick = {
                                pinVideo(video, index)
                                optionsFor = video
                            },
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
                    pendingGridFocus = true
                },
                onDismiss = { closeOverlays() },
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
                onDetails = { detailsFor = video },
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
                onDismiss = { closeOverlays() },
            )
        }
        detailsFor?.let { video ->
            VideoDetailsSheet(
                video = video,
                onDismiss = { closeOverlays() },
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
    onDetails: () -> Unit,
    onSend: () -> Unit,
    canSend: Boolean,
    onDismiss: () -> Unit,
) {
    com.grokplayer.tv.ui.components.ModalMenu(
        title = video.title,
        meta = "${video.durationMs.formatClock()} · ${video.format} · ${video.sourceLabel}",
        onDismiss = onDismiss,
        actions = buildList {
            add(com.grokplayer.tv.ui.components.ModalAction(stringResource(R.string.resume), onPlay))
            add(com.grokplayer.tv.ui.components.ModalAction(stringResource(R.string.play_from_start), onPlayFromStart))
            add(com.grokplayer.tv.ui.components.ModalAction("Ayrıntılar", onDetails))
            if (canSend) add(com.grokplayer.tv.ui.components.ModalAction("PC’ye gönder", onSend))
            add(com.grokplayer.tv.ui.components.ModalAction(stringResource(R.string.close), onDismiss))
        },
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
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
        Box(
            Modifier
                .fillMaxSize()
                .background(GrokInk.copy(alpha = 0.55f))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() }
                .focusProperties {
                    canFocus = false
                    onEnter = { FocusRequester.Cancel }
                },
        )
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
                    onEnter = { first }
                },
        ) {
            val keys = remember { VideoSort.entries.map { FocusRequester() } }
            VideoSort.entries.forEachIndexed { index, option ->
                val interaction = remember { MutableInteractionSource() }
                val focused = interaction.collectIsFocusedAsState().value
                val last = VideoSort.entries.lastIndex
                Text(
                    text = stringResource(option.labelRes),
                    style = GrokType.button,
                    color = if (focused || option == selected) GrokInk else GrokWhite,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(keys[index])
                        .then(if (index == 0) Modifier.focusRequester(first) else Modifier)
                        .focusProperties {
                            left = FocusRequester.Cancel
                            right = FocusRequester.Cancel
                            up = keys[if (index == 0) 0 else index - 1]
                            down = keys[if (index == last) last else index + 1]
                        }
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

@Composable
private fun VideoDetailsSheet(video: LibraryVideo, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var details by remember { mutableStateOf<com.grokplayer.tv.data.MediaDetails?>(null) }
    LaunchedEffect(video.id) {
        details = withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.grokplayer.tv.data.MediaProbe.details(context, video.uri, video.path)
        }
    }
    val close = remember { FocusRequester() }
    com.grokplayer.tv.ui.theme.RememberFocusLock()
    InterceptBack { onDismiss(); true }
    BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .background(GrokInk.copy(alpha = 0.55f))
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onDismiss)
                .focusProperties {
                    canFocus = false
                    onEnter = { FocusRequester.Cancel }
                },
        )
        Column(
            Modifier
                .width(420.dp)
                .background(GrokSurface, RoundedCornerShape(12.dp))
                .border(1.dp, GrokYellow.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                .padding(18.dp),
        ) {
            Text(video.title, style = GrokType.section, color = GrokWhite)
            Text(
                "${video.format} · ${video.sourceLabel}",
                style = GrokType.cardMeta,
                color = GrokMuted,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            val duration = (details?.durationMs?.takeIf { it > 0L } ?: video.durationMs)
            DetailRow("Süre", if (duration > 0L) duration.formatClock() else "—")
            DetailRow("Çözünürlük", details?.resolution ?: "…")
            DetailRow("Kare hızı", details?.fpsLabel ?: "…")
            video.path?.let { DetailRow("Dosya", it.substringAfterLast('/')) }
            Spacer(Modifier.padding(top = 8.dp))
            com.grokplayer.tv.ui.components.FocusableAction(
                onClick = onDismiss,
                modifier = Modifier.focusRequester(close),
            ) { focused ->
                Text(
                    stringResource(R.string.close),
                    style = GrokType.button,
                    color = if (focused) GrokInk else GrokWhite,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (focused) GrokYellow else GrokSurface, RoundedCornerShape(6.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { close.requestFocus() } }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = GrokType.cardMeta, color = GrokMuted)
        Text(value, style = GrokType.cardTitle, color = GrokWhite, modifier = Modifier.padding(start = 16.dp))
    }
}

private val VideoSort.labelRes: Int
    get() = when (this) {
        VideoSort.RecentlyAdded -> R.string.sort_recent
        VideoSort.Oldest -> R.string.sort_oldest
        VideoSort.Alphabetical -> R.string.sort_az
        VideoSort.Newest -> R.string.sort_newest
        VideoSort.Episode -> R.string.sort_episode
    }
