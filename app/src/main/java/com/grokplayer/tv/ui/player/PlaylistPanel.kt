package com.grokplayer.tv.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.grokplayer.tv.data.LibraryVideo
import com.grokplayer.tv.data.formatClock
import com.grokplayer.tv.data.indexAfterInsert
import com.grokplayer.tv.data.moveItem
import com.grokplayer.tv.data.queuePlayNextIndex
import com.grokplayer.tv.ui.components.FocusableAction
import com.grokplayer.tv.ui.components.VideoPoster
import com.grokplayer.tv.ui.theme.GrokInk
import com.grokplayer.tv.ui.theme.GrokMuted
import com.grokplayer.tv.ui.theme.GrokPink
import com.grokplayer.tv.ui.theme.GrokSoft
import com.grokplayer.tv.ui.theme.GrokSurface
import com.grokplayer.tv.ui.theme.GrokType
import com.grokplayer.tv.ui.theme.GrokWhite
import com.grokplayer.tv.ui.theme.GrokYellow
import com.grokplayer.tv.ui.theme.InterceptBack
import kotlinx.coroutines.delay

private enum class PanelPage { List, Detail, Move, Collections }

private data class PanelAction(val label: String, val onClick: () -> Unit)

@Composable
fun PlaylistPanel(
    videos: List<LibraryVideo>,
    queue: List<LibraryVideo> = emptyList(),
    currentId: String,
    onSelectList: (Int) -> Unit,
    onSelectQueue: (Int) -> Unit = {},
    onClose: () -> Unit,
    onPlayNextInList: (LibraryVideo) -> Unit = {},
    onPlayNextInQueue: (LibraryVideo) -> Unit = {},
    onAddToQueue: (LibraryVideo) -> Unit = {},
    onRemove: (String) -> Unit = {},
    onMoveQueue: (Int, Int) -> Unit = { _, _ -> },
    onClear: () -> Unit = {},
    onWatchlist: (LibraryVideo) -> Unit = {},
    watchlistLabel: (LibraryVideo) -> String = { "İzleme listesine ekle" },
    onLike: (LibraryVideo) -> Unit = {},
    likeLabel: (LibraryVideo) -> String = { "Beğendim" },
    onMarkWatched: (LibraryVideo) -> Unit = {},
    watchedLabel: (LibraryVideo) -> String = { "İzlendi olarak işaretle" },
    collections: List<Triple<String, String, String>> = emptyList(),
    onAddCollection: (LibraryVideo, String, String) -> Unit = { _, _, _ -> },
    playingOnQueue: Boolean = false,
    playingIndex: Int = -1,
    modifier: Modifier = Modifier,
) {
    var showQueue by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(PanelPage.List) }
    var picked by remember { mutableStateOf<LibraryVideo?>(null) }
    var pickedIndex by remember { mutableIntStateOf(0) }
    var anchor by remember {
        val at = if (!playingOnQueue && playingIndex in videos.indices) {
            playingIndex
        } else {
            videos.indexOfFirst { it.id == currentId }
        }
        mutableIntStateOf(if (at >= 0) at else 0)
    }
    var actionFocus by remember { mutableIntStateOf(0) }
    var moving by remember { mutableStateOf<List<LibraryVideo>>(emptyList()) }
    var moveAt by remember { mutableIntStateOf(0) }
    var restoreTick by remember { mutableIntStateOf(1) }
    val listScroll = rememberLazyListState()
    val queueScroll = rememberLazyListState()
    val rowFocus = remember { mutableMapOf<String, FocusRequester>() }
    val actionRequesters = remember { mutableMapOf<Int, FocusRequester>() }
    val listTab = remember { FocusRequester() }
    val queueTab = remember { FocusRequester() }
    fun rowRequester(key: String) = rowFocus.getOrPut(key) { FocusRequester() }
    fun actionRequester(index: Int) = actionRequesters.getOrPut(index) { FocusRequester() }
    val liveRows = if (showQueue) queue else videos
    var shownRows by remember { mutableStateOf(liveRows) }
    var shownQueue by remember { mutableStateOf(showQueue) }

    fun showList() {
        page = PanelPage.List
        val count = liveRows.size
        if (count > 0 && anchor !in 0 until count) {
            anchor = anchor.coerceIn(0, count - 1)
        }
        restoreTick += 1
    }
    fun back() {
        when (page) {
            PanelPage.Collections -> page = PanelPage.Detail
            PanelPage.Move -> page = PanelPage.Detail
            PanelPage.Detail -> showList()
            PanelPage.List -> onClose()
        }
    }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(liveRows, showQueue, page) {
        val sameMode = showQueue == shownQueue
        val shrinking = sameMode && page == PanelPage.List && liveRows.size < shownRows.size
        if (shrinking) {
            // Drop focus before the focused row leaves the tree. Otherwise removing
            // the played "Sonra oynat" row crashes layout.
            focusManager.clearFocus(force = true)
            if (liveRows.isEmpty()) {
                runCatching { (if (showQueue) queueTab else listTab).requestFocus() }
            } else {
                anchor = anchor.coerceIn(0, liveRows.lastIndex)
                restoreTick += 1
            }
            delay(100)
        }
        shownQueue = showQueue
        shownRows = liveRows
    }
    LaunchedEffect(shownRows.size) {
        if (page != PanelPage.List) return@LaunchedEffect
        val count = shownRows.size
        if (count <= 0) {
            runCatching { (if (showQueue) queueTab else listTab).requestFocus() }
            return@LaunchedEffect
        }
        if (anchor !in 0 until count) anchor = count - 1
        restoreTick += 1
    }
    InterceptBack { back(); true }
    BackHandler(onBack = { back() })

    Column(
        modifier
            .fillMaxHeight()
            .width(380.dp)
            .background(GrokInk.copy(alpha = 0.96f))
            .padding(18.dp),
    ) {
        when (page) {
            PanelPage.List -> ListPage(
                showQueue = showQueue,
                rows = shownRows,
                currentId = currentId,
                playingIndex = playingIndex,
                listState = if (showQueue) queueScroll else listScroll,
                listTab = listTab,
                queueTab = queueTab,
                rowRequester = ::rowRequester,
                onShowQueue = { showQueue = it },
                onSelect = { index -> if (showQueue) onSelectQueue(index) else onSelectList(index) },
                onHold = { index, video ->
                    anchor = index
                    picked = video
                    pickedIndex = index
                    actionFocus = 0
                    page = PanelPage.Detail
                },
                anchor = anchor,
                restoreTick = restoreTick,
                onClear = onClear,
            )
            PanelPage.Detail -> picked?.let { video ->
                DetailPage(
                    video = video,
                    queuePosition = if (showQueue) "${pickedIndex + 1}/${queue.size}" else null,
                    actions = actionsFor(
                        queueMode = showQueue,
                        onPlayNext = {
                            if (showQueue) {
                                val currentIdInQueue = if (playingOnQueue) queue.getOrNull(playingIndex)?.id else currentId
                                pickedIndex = queuePlayNextIndex(queue.map { it.id }, currentIdInQueue, video.id)
                                anchor = pickedIndex
                                onPlayNextInQueue(video)
                            } else {
                                val insertAt = if (!playingOnQueue && playingIndex in videos.indices) {
                                    playingIndex + 1
                                } else {
                                    val at = videos.indexOfFirst { it.id == currentId }
                                    if (at >= 0) at + 1 else videos.size
                                }
                                if (insertAt <= pickedIndex) {
                                    pickedIndex = indexAfterInsert(pickedIndex, insertAt)
                                    anchor = pickedIndex
                                }
                                onPlayNextInList(video)
                            }
                        },
                        onAddQueue = { onAddToQueue(video) },
                        onRemove = {
                            onRemove(video.id)
                            anchor = anchor.coerceAtMost((liveRows.size - 2).coerceAtLeast(0))
                            showList()
                        },
                        onChangeOrder = {
                            actionFocus = 0
                            moving = queue
                            moveAt = pickedIndex
                            page = PanelPage.Move
                        },
                        onWatchlist = { onWatchlist(video) },
                        watchlistLabel = watchlistLabel(video),
                        onLike = { onLike(video) },
                        likeLabel = likeLabel(video),
                        onWatched = { onMarkWatched(video) },
                        watchedLabel = watchedLabel(video),
                        onCollections = { actionFocus = it; page = PanelPage.Collections },
                    ),
                    initialAction = actionFocus,
                    requester = ::actionRequester,
                    onFocusedAction = { actionFocus = it },
                )
            }
            PanelPage.Move -> MovePage(
                rows = moving,
                at = moveAt,
                onShift = { delta ->
                    val last = moving.lastIndex
                    if (last >= 0) {
                        val to = (moveAt + delta).coerceIn(0, last)
                        if (to != moveAt) {
                            moving = moveItem(moving, moveAt, to)
                            moveAt = to
                        }
                    }
                },
                onPlace = {
                    onMoveQueue(pickedIndex, moveAt)
                    pickedIndex = moveAt
                    anchor = moveAt
                    actionFocus = 0
                    page = PanelPage.Detail
                },
            )
            PanelPage.Collections -> picked?.let { video ->
                CollectionPage(
                    names = collections,
                    onPick = { playlistId, collectionId ->
                        onAddCollection(video, playlistId, collectionId)
                        page = PanelPage.Detail
                    },
                )
            }
        }
    }
    LaunchedEffect(restoreTick) {
        if (restoreTick == 0 || page != PanelPage.List) return@LaunchedEffect
        val state = if (showQueue) queueScroll else listScroll
        val count = shownRows.size
        if (count == 0) {
            runCatching { (if (showQueue) queueTab else listTab).requestFocus() }
            return@LaunchedEffect
        }
        val index = anchor.coerceIn(0, count - 1)
        repeat(8) {
            val info = state.layoutInfo
            if (info.totalItemsCount > 0) {
                val visible = info.visibleItemsInfo.any { it.index == index }
                if (!visible) state.scrollToItem(index)
                return@repeat
            }
            delay(16)
        }
    }
}

@Composable
private fun ColumnScope.ListPage(
    showQueue: Boolean,
    rows: List<LibraryVideo>,
    currentId: String,
    playingIndex: Int,
    listState: androidx.compose.foundation.lazy.LazyListState,
    listTab: FocusRequester,
    queueTab: FocusRequester,
    rowRequester: (String) -> FocusRequester,
    onShowQueue: (Boolean) -> Unit,
    onSelect: (Int) -> Unit,
    onHold: (Int, LibraryVideo) -> Unit,
    onClear: () -> Unit,
    anchor: Int,
    restoreTick: Int,
) {
    val clearFocus = remember { FocusRequester() }
    val rowKeys = buildList {
        val seen = mutableMapOf<String, Int>()
        rows.forEach { item ->
            val n = seen.getOrDefault(item.id, 0)
            seen[item.id] = n + 1
            add("${item.id}#$n")
        }
    }
    val firstRow = if (rowKeys.isNotEmpty()) rowRequester(rowKeys.first()) else FocusRequester.Cancel
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TabChip(
            "Liste",
            !showQueue,
            Modifier.focusRequester(listTab).focusProperties {
                left = FocusRequester.Cancel
                right = queueTab
                down = if (showQueue) FocusRequester.Default else firstRow
            },
            { onShowQueue(false) },
        )
        TabChip(
            "Sıra",
            showQueue,
            Modifier.focusRequester(queueTab).focusProperties {
                left = listTab
                down = if (showQueue) clearFocus else FocusRequester.Default
            },
            { onShowQueue(true) },
        )
    }
    Text("${rows.size} video", style = GrokType.cardMeta, color = GrokMuted, modifier = Modifier.padding(top = 8.dp))
    if (showQueue) {
        FocusableAction(
            onClick = onClear,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .focusRequester(clearFocus)
                .focusProperties {
                    left = FocusRequester.Cancel
                    up = queueTab
                    down = firstRow
                },
            shape = RoundedCornerShape(8.dp),
        ) { focused ->
            Text(
                "Sırayı temizle",
                style = GrokType.button,
                color = if (focused) GrokInk else GrokWhite,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (focused) GrokYellow else GrokSurface, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    } else {
        Spacer(Modifier.height(8.dp))
    }
    LazyColumn(
        Modifier.weight(1f).padding(top = 8.dp).testTag(if (showQueue) "panel-queue" else "panel-list"),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(rows, key = { index, _ -> rowKeys[index] }) { index, item ->
            val rowKey = rowKeys[index]
            val current = if (showQueue) {
                item.id == currentId
            } else if (playingIndex in rows.indices) {
                index == playingIndex
            } else {
                false
            }
            val lastRow = rows.lastIndex
            val restoreHere = lastRow >= 0 && restoreTick > 0 && index == anchor.coerceIn(0, lastRow)
            var cardFocused by remember { mutableStateOf(false) }
            if (restoreHere) {
                LaunchedEffect(restoreTick) {
                    repeat(8) {
                        if (runCatching { rowRequester(rowKey).requestFocus() }.getOrDefault(false)) return@LaunchedEffect
                        delay(32)
                    }
                }
            }
            FocusableAction(
                onClick = { onSelect(index) },
                onLongClick = { onHold(index, item) },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { cardFocused = it.isFocused }
                    .border(2.dp, if (cardFocused) GrokYellow else GrokSurface, RoundedCornerShape(8.dp))
                    .background(GrokSurface, RoundedCornerShape(8.dp))
                    .focusRequester(rowRequester(rowKey))
                    .focusProperties {
                        left = FocusRequester.Cancel
                        up = if (index == 0) (if (showQueue) clearFocus else listTab) else FocusRequester.Default
                        down = if (index == rows.lastIndex) FocusRequester.Cancel else FocusRequester.Default
                    },
                shape = RoundedCornerShape(8.dp),
            ) { focused ->
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${index + 1}", style = GrokType.cardMeta, color = GrokMuted, modifier = Modifier.width(28.dp))
                    VideoPoster(
                        uri = item.uri,
                        title = item.title,
                        focused = false,
                        path = item.path,
                        format = item.format,
                        modifier = Modifier.width(88.dp).height(50.dp),
                    )
                    Column(Modifier.padding(start = 10.dp)) {
                        Text(item.title, style = GrokType.cardTitle, color = GrokWhite, maxLines = 2)
                        Text(
                            item.durationMs.formatClock() + " · " + if (current) "Şu an oynuyor" else item.sourceLabel,
                            style = GrokType.cardMeta,
                            color = if (current) GrokPink else GrokMuted,
                        )
                    }
                }
            }
        }
    }
    Text("OK: Oynat    Basılı tut: Seçenekler", style = GrokType.cardMeta, color = GrokSoft, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun ColumnScope.DetailPage(
    video: LibraryVideo,
    queuePosition: String?,
    actions: List<PanelAction>,
    initialAction: Int,
    requester: (Int) -> FocusRequester,
    onFocusedAction: (Int) -> Unit,
) {
    if (queuePosition != null) {
        Text(queuePosition, style = GrokType.cardMeta, color = GrokYellow)
    }
    Text(video.title, style = GrokType.section, color = GrokWhite, modifier = Modifier.padding(top = 4.dp))
    Text(
        video.durationMs.formatClock() + " · " + video.sourceLabel,
        style = GrokType.cardMeta,
        color = GrokMuted,
        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
    )
    val labels = actions.map { it.label }
    val scroll = rememberScrollState()
    Column(
        Modifier.weight(1f).verticalScroll(scroll).testTag("panel-actions"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        actions.forEachIndexed { index, action ->
            if (action.label == "—") {
                Text("—", style = GrokType.cardMeta, color = GrokMuted, modifier = Modifier.padding(vertical = 4.dp))
            } else {
                val above = actionNeighbor(labels, index, -1)
                val below = actionNeighbor(labels, index, 1)
                var rowFocused by remember(index) { mutableStateOf(false) }
                if (index == initialAction) {
                    LaunchedEffect(initialAction) {
                        repeat(12) {
                            if (rowFocused) return@LaunchedEffect
                            runCatching { requester(index).requestFocus() }
                            delay(32)
                        }
                    }
                }
                FocusableAction(
                    onClick = action.onClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { rowFocused = it.isFocused }
                        .focusRequester(requester(index))
                        .focusProperties {
                            left = FocusRequester.Cancel
                            up = if (above == null) FocusRequester.Cancel else requester(above)
                            down = if (below == null) FocusRequester.Cancel else requester(below)
                        },
                    shape = RoundedCornerShape(8.dp),
                ) { focused ->
                    if (focused) SideEffect { onFocusedAction(index) }
                    Text(
                        action.label,
                        style = GrokType.button,
                        color = if (focused) GrokInk else GrokWhite,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (focused) GrokYellow else GrokSurface, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.MovePage(
    rows: List<LibraryVideo>,
    at: Int,
    onShift: (Int) -> Unit,
    onPlace: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    Text("Sırayı değiştir", style = GrokType.section, color = GrokWhite)
    Text("Yukarı / aşağı taşı, Tamam ile bırak", style = GrokType.cardMeta, color = GrokMuted, modifier = Modifier.padding(bottom = 8.dp))
    LazyColumn(Modifier.weight(1f).testTag("panel-move"), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(rows, key = { _, item -> item.id }) { index, item ->
            val moving = index == at
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (moving) {
                            Modifier
                                .focusRequester(focus)
                                .focusable()
                                .onPreviewKeyEvent { event ->
                                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    when (event.key) {
                                        Key.DirectionUp -> {
                                            onShift(-1)
                                            true
                                        }
                                        Key.DirectionDown -> {
                                            onShift(1)
                                            true
                                        }
                                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                            onPlace()
                                            true
                                        }
                                        else -> false
                                    }
                                }
                        } else {
                            Modifier
                        },
                    )
                    .background(if (moving) GrokYellow.copy(alpha = 0.18f) else GrokSurface, RoundedCornerShape(8.dp))
                    .border(2.dp, if (moving) GrokYellow else GrokSurface, RoundedCornerShape(8.dp))
                    .padding(8.dp)
                    .testTag(if (moving) "queue-moving" else "queue-slot"),
            ) {
                Text("${index + 1}", color = GrokMuted, modifier = Modifier.width(28.dp))
                Text(if (moving) "Taşınıyor · ${item.title}" else item.title, color = GrokWhite)
            }
        }
    }
    LaunchedEffect(at) {
        if (at in rows.indices) listState.scrollToItem(at)
        runCatching { focus.requestFocus() }
    }
}

@Composable
private fun ColumnScope.CollectionPage(
    names: List<Triple<String, String, String>>,
    onPick: (String, String) -> Unit,
) {
    val first = remember { FocusRequester() }
    Text("Koleksiyon", style = GrokType.section, color = GrokWhite, modifier = Modifier.padding(bottom = 8.dp))
    if (names.isEmpty()) {
        Text("Koleksiyon yok", style = GrokType.cardMeta, color = GrokMuted)
        return
    }
    LazyColumn(Modifier.weight(1f).testTag("panel-collections"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        itemsIndexed(names, key = { _, item -> "${item.first}:${item.second}" }) { index, item ->
            var rowFocused by remember(item.second) { mutableStateOf(false) }
            if (index == 0) {
                LaunchedEffect(item.second) {
                    repeat(12) {
                        if (rowFocused) return@LaunchedEffect
                        runCatching { first.requestFocus() }
                        delay(32)
                    }
                }
            }
            FocusableAction(
                onClick = { onPick(item.first, item.second) },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { rowFocused = it.isFocused }
                    .then(if (index == 0) Modifier.focusRequester(first) else Modifier)
                    .focusProperties {
                        left = FocusRequester.Cancel
                        up = if (index == 0) FocusRequester.Cancel else FocusRequester.Default
                        down = if (index == names.lastIndex) FocusRequester.Cancel else FocusRequester.Default
                    },
                shape = RoundedCornerShape(8.dp),
            ) { focused ->
                Text(
                    item.third,
                    color = if (focused) GrokInk else GrokWhite,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (focused) GrokYellow else GrokSurface, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
    }
}

internal fun actionNeighbor(labels: List<String>, index: Int, delta: Int): Int? {
    val focusable = labels.indices.filter { labels[it] != "—" }
    val pos = focusable.indexOf(index)
    if (pos < 0) return null
    return focusable.getOrNull(pos + delta)
}

@Composable
private fun TabChip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    FocusableAction(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(8.dp)) { focused ->
        Text(
            label,
            style = GrokType.button,
            color = if (focused) GrokInk else if (selected) GrokWhite else GrokMuted,
            modifier = Modifier
                .background(
                    when {
                        focused -> GrokYellow
                        selected -> GrokSurface
                        else -> Color.Transparent
                    },
                    RoundedCornerShape(8.dp),
                )
                .border(
                    width = 1.dp,
                    color = if (selected && !focused) GrokYellow else Color.Transparent,
                    shape = RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

private fun actionsFor(
    queueMode: Boolean,
    onPlayNext: () -> Unit,
    onAddQueue: () -> Unit,
    onRemove: () -> Unit,
    onChangeOrder: () -> Unit,
    onWatchlist: () -> Unit,
    watchlistLabel: String,
    onLike: () -> Unit,
    likeLabel: String,
    onWatched: () -> Unit,
    watchedLabel: String,
    onCollections: (Int) -> Unit,
): List<PanelAction> {
    val main = if (queueMode) {
        listOf(
            PanelAction("Sırayı değiştir", onChangeOrder),
            PanelAction("Sonra oynat", onPlayNext),
            PanelAction("Sıradan çıkar", onRemove),
        )
    } else {
        listOf(
            PanelAction("Sonra oynat", onPlayNext),
            PanelAction("Sıraya ekle", onAddQueue),
        )
    }
    val rest = listOf(
        PanelAction("—") {},
        PanelAction(watchlistLabel, onWatchlist),
        PanelAction(likeLabel, onLike),
        PanelAction(watchedLabel, onWatched),
        PanelAction("Koleksiyona ekle") { onCollections(main.size + 4) },
    )
    return main + rest
}
