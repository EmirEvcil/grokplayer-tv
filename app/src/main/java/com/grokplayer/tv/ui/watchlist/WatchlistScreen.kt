package com.grokplayer.tv.ui.watchlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grokplayer.tv.R
import com.grokplayer.tv.data.CollectionStore
import com.grokplayer.tv.data.LibraryStore
import com.grokplayer.tv.data.LibraryVideo
import com.grokplayer.tv.data.PlaylistStore
import com.grokplayer.tv.data.WatchStatus
import com.grokplayer.tv.data.WatchlistEntry
import com.grokplayer.tv.data.WatchlistStore
import com.grokplayer.tv.data.formatClock
import com.grokplayer.tv.data.vodQueue
import com.grokplayer.tv.ui.components.EmptyState
import com.grokplayer.tv.ui.components.FocusableAction
import com.grokplayer.tv.ui.components.HintBar
import com.grokplayer.tv.ui.components.ModalAction
import com.grokplayer.tv.ui.components.VideoPoster
import com.grokplayer.tv.ui.lists.VideoMenuHost
import com.grokplayer.tv.ui.theme.GrokInk
import com.grokplayer.tv.ui.theme.GrokMuted
import com.grokplayer.tv.ui.theme.GrokType
import com.grokplayer.tv.ui.theme.GrokWhite
import kotlinx.coroutines.delay

@Composable
fun WatchlistScreen(
    firstFocus: FocusRequester,
    railFocus: FocusRequester,
    library: LibraryStore,
    watchlist: WatchlistStore,
    known: List<LibraryVideo>,
    playlists: PlaylistStore?,
    collections: CollectionStore?,
    onPlay: (List<LibraryVideo>, Int, Boolean, Boolean) -> Unit,
    onNotice: (String) -> Unit,
    playbackOpen: Boolean,
    focusItemId: String? = null,
    onFocusConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val entries = watchlist.entries(known)
    val videos = entries.map { it.video }
    val gridState = rememberLazyGridState()
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    var menu by remember { mutableStateOf<WatchlistEntry?>(null) }
    var menuIndex by remember { mutableStateOf(0) }
    var focusedKey by remember { mutableStateOf<String?>(null) }
    var focusedIndex by remember { mutableIntStateOf(0) }
    fun requester(index: Int, key: String): FocusRequester {
        if (index == 0) return firstFocus
        return requesters.getOrPut(key) { FocusRequester() }
    }

    LaunchedEffect(focusItemId, entries.map { it.video.id }) {
        val id = focusItemId ?: return@LaunchedEffect
        val index = entries.indexOfFirst { it.video.id == id || it.key == id }
        if (index >= 0) {
            gridState.scrollToItem(index)
            delay(32)
            repeat(4) {
                if (runCatching { requester(index, entries[index].key).requestFocus() }.getOrDefault(false)) return@repeat
                delay(40)
            }
        }
        onFocusConsumed()
    }
    LaunchedEffect(entries.map { it.key }, menu) {
        if (menu != null) return@LaunchedEffect
        val key = focusedKey ?: return@LaunchedEffect
        if (entries.any { it.key == key }) return@LaunchedEffect
        delay(48)
        if (entries.isEmpty()) {
            repeat(8) {
                if (runCatching { firstFocus.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
                delay(40)
            }
            return@LaunchedEffect
        }
        val index = focusedIndex.coerceAtMost(entries.lastIndex)
        gridState.scrollToItem(index)
        delay(32)
        val target = requester(index, entries[index].key)
        repeat(8) {
            if (runCatching { target.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
            delay(40)
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(GrokInk)
            .padding(start = 28.dp, end = 28.dp, top = 70.dp, bottom = 18.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            Text(stringResource(R.string.watchlist_title), style = GrokType.pageTitle, color = GrokWhite)
            Text(
                text = if (entries.size == 1) "1 video" else "${entries.size} video",
                style = GrokType.heroMeta,
                color = GrokMuted,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )
            if (entries.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.watchlist_empty_title),
                    body = stringResource(R.string.watchlist_empty_body),
                    modifier = Modifier.focusRequester(firstFocus).focusProperties { left = railFocus },
                )
            } else {
                BoxWithConstraints(Modifier.weight(1f)) {
                    val columns = 3
                    val lastRow = ((entries.size - 1) / columns) * columns
                    val tileReserve = (maxWidth - 14.dp * (columns - 1)) / columns * 9f / 16f + 58.dp
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        state = gridState,
                        contentPadding = PaddingValues(bottom = tileReserve),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        itemsIndexed(entries, key = { _, entry -> entry.key }) { index, entry ->
                            val onLastRow = index >= lastRow
                            WatchlistCard(
                                video = entry.video,
                                meta = watchlistMeta(entry.video, library),
                                progress = library.progressFraction(entry.video),
                                enablePreview = !playbackOpen && menu == null,
                                onClick = {
                                    val (queue, start) = vodQueue(videos, index)
                                    onPlay(queue, start, true, true)
                                },
                                onLongClick = {
                                    menu = entry
                                    menuIndex = index
                                },
                                modifier = Modifier
                                    .focusRequester(requester(index, entry.key))
                                    .onFocusChanged {
                                        if (it.isFocused) {
                                            focusedKey = entry.key
                                            focusedIndex = index
                                        }
                                    }
                                    .onPreviewKeyEvent { event ->
                                        event.type == KeyEventType.KeyDown &&
                                            event.key == Key.DirectionDown &&
                                            onLastRow
                                    }
                                    .focusProperties {
                                        left = if (index % columns == 0) railFocus else FocusRequester.Default
                                        down = if (onLastRow) FocusRequester.Cancel else FocusRequester.Default
                                    },
                            )
                        }
                    }
                }
            }
            HintBar(
                parts = listOf(stringResource(R.string.hint_play), stringResource(R.string.hint_options)),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        menu?.let { entry ->
            VideoMenuHost(
                video = entry.video,
                meta = watchlistMeta(entry.video, library),
                playlists = playlists,
                collections = collections,
                watch = library.watch,
                watchlist = watchlist,
                onNotice = onNotice,
                onDismiss = { menu = null },
                extraActions = listOf(
                    ModalAction(stringResource(R.string.resume), icon = Icons.Filled.PlayArrow) {
                        menu = null
                        val (queue, start) = vodQueue(videos, menuIndex)
                        onPlay(queue, start, true, false)
                    },
                    ModalAction(stringResource(R.string.play_from_start), icon = Icons.Outlined.Replay) {
                        menu = null
                        val (queue, start) = vodQueue(videos, menuIndex)
                        onPlay(queue, start, false, false)
                    },
                ),
            )
        }
    }
}

@Composable
fun WatchlistCard(
    video: LibraryVideo,
    meta: String,
    progress: Float?,
    enablePreview: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
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
                posterUrl = video.posterUrl,
                durationMs = video.durationMs,
                isLive = false,
                enablePreview = enablePreview && focused,
                originUrl = video.originUrl,
                referer = video.referer,
                userAgent = video.userAgent,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Text(
                video.title,
                style = GrokType.cardTitle,
                color = GrokWhite,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                meta,
                style = GrokType.cardMeta,
                color = GrokMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

fun watchlistMeta(video: LibraryVideo, library: LibraryStore): String {
    val duration = video.durationMs.takeIf { it > 0L }?.formatClock().orEmpty()
    val state = when (library.watch.status(video)) {
        WatchStatus.Watching -> "İzleniyor"
        WatchStatus.Watched -> "İzlendi"
        else -> ""
    }
    return listOf(duration, state).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { video.sourceLabel }
}
