package com.grokplayer.tv.ui.streams

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grokplayer.tv.R
import com.grokplayer.tv.data.LibraryVideo
import com.grokplayer.tv.data.StreamItem
import com.grokplayer.tv.data.StreamKind
import com.grokplayer.tv.data.StreamProbe
import com.grokplayer.tv.data.StreamStore
import com.grokplayer.tv.ui.components.EmptyState
import com.grokplayer.tv.ui.components.FilterChip
import com.grokplayer.tv.ui.components.FocusableAction
import com.grokplayer.tv.ui.components.HintBar
import com.grokplayer.tv.ui.components.OutlineButton
import com.grokplayer.tv.ui.components.VideoPoster
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.grokplayer.tv.ui.theme.GrokInk
import com.grokplayer.tv.ui.theme.GrokMuted
import com.grokplayer.tv.ui.theme.GrokPink
import com.grokplayer.tv.ui.theme.GrokSurface
import com.grokplayer.tv.ui.theme.GrokType
import com.grokplayer.tv.ui.theme.GrokWhite
import com.grokplayer.tv.ui.theme.GrokYellow
import com.grokplayer.tv.ui.theme.InterceptBack
import com.grokplayer.tv.ui.theme.RememberFocusLock

private enum class StreamFilter { All, Vod, Live, Favorites }

@Composable
fun StreamsScreen(
    firstFocus: FocusRequester,
    railFocus: FocusRequester,
    streams: StreamStore,
    downloads: com.grokplayer.tv.data.DownloadStore,
    downloadHeight: Int,
    onPlay: (List<LibraryVideo>, Int) -> Unit,
    onNotice: (String) -> Unit,
    focusStreamId: String? = null,
    onFocusConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var filter by remember { mutableStateOf(StreamFilter.All) }
    var addOpen by remember { mutableStateOf(false) }
    var optionsFor by remember { mutableStateOf<StreamItem?>(null) }
    val addFocus = remember { FocusRequester() }
    val firstTile = remember { FocusRequester() }
    val filterFocus = remember { List(4) { FocusRequester() } }
    val gridState = rememberLazyGridState()
    var savedScrollIndex by remember { mutableStateOf(0) }
    var savedScrollOffset by remember { mutableStateOf(0) }
    val tileFocus = remember(streams.items.size) { List(streams.items.size.coerceAtLeast(1)) { FocusRequester() } }

    fun dismissOverlay(): Boolean {
        return when {
            optionsFor != null -> {
                optionsFor = null
                true
            }
            addOpen -> {
                addOpen = false
                true
            }
            else -> false
        }
    }
    InterceptBack(enabled = addOpen || optionsFor != null) { dismissOverlay() }
    BackHandler(enabled = addOpen || optionsFor != null) { dismissOverlay() }
    val visible = remember(streams.items, filter) {
        streams.items.filter {
            when (filter) {
                StreamFilter.All -> true
                StreamFilter.Vod -> it.kind == StreamKind.Vod
                StreamFilter.Live -> it.kind == StreamKind.Live
                StreamFilter.Favorites -> it.favorite
            }
        }
    }

    LaunchedEffect(focusStreamId, visible) {
        val id = focusStreamId ?: return@LaunchedEffect
        val index = visible.indexOfFirst { it.id == id }
        if (index >= 0) {
            gridState.scrollToItem(savedScrollIndex, savedScrollOffset)
            kotlinx.coroutines.delay(32)
            val requester = if (index == 0) firstTile else tileFocus.getOrNull(index)
            repeat(3) {
                runCatching { requester?.requestFocus() }
                kotlinx.coroutines.delay(40)
            }
            gridState.scrollToItem(savedScrollIndex, savedScrollOffset)
            onFocusConsumed()
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = 28.dp, end = 28.dp, top = 18.dp, bottom = 10.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(end = 100.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column {
                    Text(stringResource(R.string.nav_streams), style = GrokType.pageTitle, color = GrokWhite)
                    Text(
                        text = stringResource(R.string.streams_subtitle),
                        style = GrokType.heroMeta,
                        color = GrokMuted,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                OutlineButton(
                    label = stringResource(R.string.add_stream),
                    icon = Icons.Outlined.Add,
                    onClick = { addOpen = true },
                    modifier = Modifier
                        .then(if (visible.isEmpty()) Modifier.focusRequester(firstFocus) else Modifier)
                        .focusRequester(addFocus)
                        .focusProperties {
                            left = railFocus
                            down = filterFocus[0]
                        },
                )
            }
            Row(
                Modifier.padding(top = 16.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FilterChip(
                    stringResource(R.string.filter_all),
                    filter == StreamFilter.All,
                    { filter = StreamFilter.All },
                    Modifier
                        .focusRequester(filterFocus[0])
                        .focusProperties {
                            left = railFocus
                            right = filterFocus[1]
                            up = addFocus
                            down = if (visible.isNotEmpty()) firstTile else FocusRequester.Default
                        },
                )
                FilterChip(
                    stringResource(R.string.filter_vod),
                    filter == StreamFilter.Vod,
                    { filter = StreamFilter.Vod },
                    Modifier
                        .focusRequester(filterFocus[1])
                        .focusProperties {
                            left = filterFocus[0]
                            right = filterFocus[2]
                            up = addFocus
                            down = if (visible.isNotEmpty()) firstTile else FocusRequester.Default
                        },
                )
                FilterChip(
                    stringResource(R.string.filter_live),
                    filter == StreamFilter.Live,
                    { filter = StreamFilter.Live },
                    Modifier
                        .focusRequester(filterFocus[2])
                        .focusProperties {
                            left = filterFocus[1]
                            right = filterFocus[3]
                            up = addFocus
                            down = if (visible.isNotEmpty()) firstTile else FocusRequester.Default
                        },
                )
                FilterChip(
                    stringResource(R.string.filter_favorites),
                    filter == StreamFilter.Favorites,
                    { filter = StreamFilter.Favorites },
                    Modifier
                        .focusRequester(filterFocus[3])
                        .focusProperties {
                            left = filterFocus[2]
                            up = addFocus
                            down = if (visible.isNotEmpty()) firstTile else FocusRequester.Default
                        },
                )
            }
            if (visible.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.empty_streams_title),
                    body = stringResource(R.string.empty_streams_body),
                    modifier = if (streams.items.isEmpty()) Modifier else Modifier,
                )
                Spacer(Modifier.weight(1f))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = gridState,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    itemsIndexed(visible, key = { _, item -> item.id }) { index, item ->
                        StreamTile(
                            item = item,
                            modifier = Modifier
                                .then(
                                    if (index == 0) {
                                        Modifier.focusRequester(firstFocus).focusRequester(firstTile)
                                    } else {
                                        Modifier.focusRequester(tileFocus[index.coerceAtMost(tileFocus.lastIndex)])
                                    },
                                )
                                .focusProperties {
                                    left = if (index % 3 == 0) railFocus else FocusRequester.Default
                                    up = filterFocus[0]
                                },
                            onClick = {
                                savedScrollIndex = gridState.firstVisibleItemIndex
                                savedScrollOffset = gridState.firstVisibleItemScrollOffset
                                val queue = visible.map { it.toVideo() }
                                onPlay(queue, index)
                            },
                            onLongClick = { optionsFor = item },
                        )
                    }
                }
            }
            HintBar(
                parts = listOf(stringResource(R.string.hint_play), stringResource(R.string.hint_options)),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (addOpen) {
            AddStreamDialog(
                onSave = { title, url ->
                    streams.add(title, url, StreamKind.Vod)
                    addOpen = false
                    val id = streams.items.firstOrNull { it.url == url }?.id
                    scope.launch(Dispatchers.IO) {
                        val kind = StreamProbe.detectKind(url)
                        if (id != null) {
                            withContext(Dispatchers.Main) { streams.updateKind(id, kind) }
                        }
                    }
                },
                onDismiss = { addOpen = false },
            )
        }
        optionsFor?.let { item ->
            StreamOptions(
                item = item,
                downloadStatus = downloads.statusOf(item.url),
                onPlay = {
                    optionsFor = null
                    savedScrollIndex = gridState.firstVisibleItemIndex
                    savedScrollOffset = gridState.firstVisibleItemScrollOffset
                    val queue = visible.map { it.toVideo() }
                    val index = visible.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
                    onPlay(queue, index)
                },
                onDownload = {
                    optionsFor = null
                    if (item.kind != StreamKind.Vod) {
                        onNotice("Canlı yayın indirilemez")
                    } else {
                        val status = downloads.statusOf(item.url)
                        when (status) {
                            com.grokplayer.tv.data.DownloadStatus.Queued,
                            com.grokplayer.tv.data.DownloadStatus.Running,
                            -> onNotice("İndirme zaten sürüyor")
                            com.grokplayer.tv.data.DownloadStatus.Done -> onNotice("Bu yayın zaten indirildi")
                            else -> {
                                downloads.enqueue(item.title, item.url, downloadHeight)
                                onNotice("İndirme başladı")
                            }
                        }
                    }
                },
                onFavorite = {
                    streams.toggleFavorite(item.id)
                    optionsFor = null
                },
                onDelete = {
                    streams.remove(item.id)
                    optionsFor = null
                },
                onDismiss = { optionsFor = null },
            )
        }
    }
}

@Composable
private fun StreamTile(
    item: StreamItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FocusableAction(onClick = onClick, onLongClick = onLongClick, modifier = modifier, shape = RoundedCornerShape(8.dp)) { focused ->
        Column(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            ) {
                VideoPoster(
                    uri = Uri.parse(item.url),
                    title = item.title,
                    focused = focused,
                    path = null,
                    format = if (item.kind == StreamKind.Live) "CANLI" else "VOD",
                    modifier = Modifier.fillMaxSize(),
                )
                Icon(
                    imageVector = if (item.favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = null,
                    tint = if (item.favorite) GrokPink else GrokMuted,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(18.dp),
                )
                if (item.kind == StreamKind.Live) {
                    Row(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(7.dp).background(GrokPink, CircleShape))
                        Text("  CANLI", style = GrokType.cardMeta, color = GrokWhite)
                    }
                }
            }
            Text(item.title, style = GrokType.cardTitle, color = GrokWhite, modifier = Modifier.padding(top = 8.dp))
            Text(
                text = if (item.kind == StreamKind.Live) "CANLI" else "VOD",
                style = GrokType.cardMeta,
                color = GrokMuted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun AddStreamDialog(
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    RememberFocusLock()
    InterceptBack { onDismiss(); true }
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }
    BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(GrokInk.copy(alpha = 0.62f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(420.dp)
                .background(GrokSurface, RoundedCornerShape(12.dp))
                .padding(18.dp),
        ) {
            Text(stringResource(R.string.add_stream), style = GrokType.section, color = GrokWhite)
            DialogField(stringResource(R.string.stream_name), title, { title = it }, Modifier.focusRequester(first).padding(top = 12.dp))
            DialogField(stringResource(R.string.stream_url), url, { url = it }, Modifier.padding(top = 8.dp))
            error?.let {
                Text(it, style = GrokType.cardMeta, color = GrokPink, modifier = Modifier.padding(top = 8.dp))
            }
            OutlineButton(
                label = stringResource(R.string.stream_save),
                onClick = {
                    val clean = url.trim()
                    if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
                        error = "Geçerli bir http veya https adresi girin"
                    } else {
                        onSave(title, clean)
                    }
                },
                modifier = Modifier.padding(top = 14.dp),
            )
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

@Composable
private fun DialogField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Text(label, style = GrokType.cardMeta, color = GrokMuted)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = GrokWhite, fontSize = 15.sp),
            cursorBrush = SolidColor(GrokYellow),
            modifier = Modifier
                .padding(top = 4.dp)
                .fillMaxWidth()
                .background(GrokInk, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun StreamOptions(
    item: StreamItem,
    downloadStatus: com.grokplayer.tv.data.DownloadStatus?,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onFavorite: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    RememberFocusLock()
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
                .width(320.dp)
                .background(GrokSurface, RoundedCornerShape(12.dp))
                .padding(16.dp)
                .focusProperties { left = FocusRequester.Cancel; right = FocusRequester.Cancel },
        ) {
            Text(item.title, style = GrokType.section, color = GrokWhite)
            OptionLine(stringResource(R.string.resume), onPlay, Modifier.focusRequester(first))
            if (item.kind == StreamKind.Vod) {
                val label = when (downloadStatus) {
                    com.grokplayer.tv.data.DownloadStatus.Queued,
                    com.grokplayer.tv.data.DownloadStatus.Running,
                    -> "İndiriliyor…"
                    com.grokplayer.tv.data.DownloadStatus.Done -> "İndirildi"
                    else -> stringResource(R.string.download)
                }
                OptionLine(label, onDownload)
            }
            OptionLine(if (item.favorite) "Favorilerden çıkar" else "Favorilere ekle", onFavorite)
            OptionLine("Sil", onDelete)
            OptionLine(stringResource(R.string.close), onDismiss)
        }
    }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

@Composable
private fun OptionLine(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused = interaction.collectIsFocusedAsState().value
    Text(
        text = label,
        style = GrokType.button,
        color = if (focused) GrokInk else GrokWhite,
        modifier = modifier
            .fillMaxWidth()
            .background(if (focused) GrokYellow else Color.Transparent, RoundedCornerShape(6.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}
