package com.grokplayer.tv.ui.streams

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grokplayer.tv.R
import com.grokplayer.tv.data.LibraryVideo
import com.grokplayer.tv.data.StreamItem
import com.grokplayer.tv.data.StreamKind
import com.grokplayer.tv.data.StreamProbe
import com.grokplayer.tv.data.StreamStore
import com.grokplayer.tv.data.formatClock
import com.grokplayer.tv.ui.components.EmptyState
import com.grokplayer.tv.ui.components.FilterChip
import com.grokplayer.tv.ui.components.FocusableAction
import com.grokplayer.tv.ui.components.HintBar
import com.grokplayer.tv.ui.components.OutlineButton
import com.grokplayer.tv.ui.components.VideoPoster
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import android.net.Uri
import android.view.inputmethod.InputMethodManager
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
    canSend: Boolean = false,
    onSend: (LibraryVideo) -> Unit = {},
    focusStreamId: String? = null,
    onFocusConsumed: () -> Unit = {},
    initialScanUrl: String? = null,
    onScanConsumed: () -> Unit = {},
    playlists: com.grokplayer.tv.data.PlaylistStore? = null,
    collections: com.grokplayer.tv.data.CollectionStore? = null,
    watch: com.grokplayer.tv.data.WatchStore? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var filter by remember { mutableStateOf(StreamFilter.All) }
    var addOpen by remember { mutableStateOf(false) }
    var seedUrl by remember { mutableStateOf(initialScanUrl) }
    LaunchedEffect(initialScanUrl) {
        val seed = initialScanUrl?.trim().orEmpty()
        if (seed.isBlank()) return@LaunchedEffect
        seedUrl = seed
        addOpen = true
        onScanConsumed()
    }
    var optionsFor by remember { mutableStateOf<StreamItem?>(null) }
    var restoreAfterModal by remember { mutableStateOf(false) }
    var restoreStreamId by rememberSaveable { mutableStateOf<String?>(null) }
    val addFocus = remember { FocusRequester() }
    val firstTile = remember { FocusRequester() }
    val filterFocus = remember { List(4) { FocusRequester() } }
    val gridState = rememberLazyGridState()
    var savedScrollIndex by remember { mutableStateOf(0) }
    var savedScrollOffset by remember { mutableStateOf(0) }
    var lastFocusedIndex by rememberSaveable { mutableIntStateOf(0) }
    val tileFocus = remember(streams.items.size) { List(streams.items.size.coerceAtLeast(1)) { FocusRequester() } }

    fun dismissOverlay(): Boolean {
        return when {
            optionsFor != null -> {
                optionsFor = null
                restoreAfterModal = true
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

    LaunchedEffect(restoreAfterModal, restoreStreamId, lastFocusedIndex, visible.size, optionsFor, addOpen) {
        val overlay = optionsFor != null || addOpen
        if (!restoreAfterModal || overlay) return@LaunchedEffect
        if (visible.isEmpty()) {
            restoreAfterModal = false
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(32)
        val id = com.grokplayer.tv.data.OverlayRestore.targetId(
            overlayOpen = false,
            remembered = restoreStreamId ?: visible.getOrNull(lastFocusedIndex)?.id,
            ids = visible.map { it.id },
        )
        val index = visible.indexOfFirst { it.id == id }.let { if (it < 0) lastFocusedIndex.coerceIn(0, visible.lastIndex) else it }
        val requester = if (index == 0) firstTile else tileFocus.getOrNull(index)
        repeat(2) {
            if (runCatching { requester?.requestFocus() }.getOrDefault(false) == true) {
                lastFocusedIndex = index
                restoreAfterModal = false
                return@LaunchedEffect
            }
            kotlinx.coroutines.delay(40)
        }
        restoreAfterModal = false
    }

    LaunchedEffect(focusStreamId, visible) {
        val id = focusStreamId ?: return@LaunchedEffect
        val bare = id.removePrefix("stream:")
        val index = visible.indexOfFirst { it.id == id || it.id == bare }
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
                .padding(start = 28.dp, end = 28.dp, top = 18.dp, bottom = 10.dp)
                .focusProperties {
                    if (optionsFor != null || addOpen) {
                        canFocus = false
                        onEnter = { FocusRequester.Cancel }
                    }
                },
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
                    onClick = {
                        com.grokplayer.tv.data.KeyGate.arm()
                        addOpen = true
                    },
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
                            enablePreview = optionsFor == null && !addOpen,
                            progress = if (item.kind == StreamKind.Live) null else watch?.progressFraction(item.toVideo()),
                            modifier = Modifier
                                .then(
                                    if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                                )
                                .focusRequester(
                                    if (index == 0) firstTile else tileFocus[index.coerceAtMost(tileFocus.lastIndex)],
                                )
                                .onFocusChanged {
                                    if (it.isFocused && optionsFor == null && !addOpen && !restoreAfterModal) {
                                        lastFocusedIndex = index
                                        restoreStreamId = item.id
                                    }
                                }
                                .focusProperties {
                                    left = if (index % 3 == 0) railFocus else FocusRequester.Default
                                    up = if (index < 3) filterFocus[0] else FocusRequester.Default
                                },
                            onClick = {
                                savedScrollIndex = gridState.firstVisibleItemIndex
                                savedScrollOffset = gridState.firstVisibleItemScrollOffset
                                val videos = visible.map { it.toVideo() }
                                val (queue, start) = com.grokplayer.tv.data.vodQueue(videos, index)
                                onPlay(queue, start)
                            },
                            onLongClick = {
                                lastFocusedIndex = index
                                restoreStreamId = item.id
                                savedScrollIndex = gridState.firstVisibleItemIndex
                                savedScrollOffset = gridState.firstVisibleItemScrollOffset
                                optionsFor = item
                            },
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
                initialUrl = seedUrl,
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
                onPlayHit = { hit ->
                    streams.add(
                        title = hit.title,
                        url = hit.playUrl,
                        kind = hit.kind,
                        posterUrl = hit.thumbnailUrl,
                        referer = hit.referer,
                        userAgent = hit.userAgent,
                        durationMs = hit.durationMs,
                        pageUrl = hit.pageUrl,
                    )
                    addOpen = false
                    onPlay(listOf(hit.toVideo()), 0)
                },
                onSaveHit = { hit ->
                    streams.add(
                        title = hit.title,
                        url = hit.playUrl,
                        kind = hit.kind,
                        posterUrl = hit.thumbnailUrl,
                        referer = hit.referer,
                        userAgent = hit.userAgent,
                        durationMs = hit.durationMs,
                        pageUrl = hit.pageUrl,
                    )
                    addOpen = false
                    onNotice("Akış eklendi")
                },
                onDismiss = {
                    addOpen = false
                    seedUrl = null
                },
            )
        }
        optionsFor?.let { item ->
            val video = item.toVideo()
            val downloadStatus = downloads.statusOf(item.url)
            com.grokplayer.tv.ui.lists.VideoMenuHost(
                video = video,
                playlists = playlists,
                collections = collections,
                onNotice = onNotice,
                watch = watch,
                showAddToList = item.kind == StreamKind.Vod,
                onDismiss = {
                    optionsFor = null
                    restoreAfterModal = true
                },
                extraActions = listOf(
                    com.grokplayer.tv.ui.components.ModalAction(stringResource(R.string.resume)) {
                        optionsFor = null
                        savedScrollIndex = gridState.firstVisibleItemIndex
                        savedScrollOffset = gridState.firstVisibleItemScrollOffset
                        val videos = visible.map { it.toVideo() }
                        val index = visible.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
                        val (queue, start) = com.grokplayer.tv.data.vodQueue(videos, index)
                        onPlay(queue, start)
                    },
                ),
                trailingActions = buildList {
                    if (canSend) {
                        add(
                            com.grokplayer.tv.ui.components.ModalAction("PC’ye gönder") {
                                optionsFor = null
                                onSend(video)
                            },
                        )
                    }
                    if (item.kind == StreamKind.Vod) {
                        val label = when (downloadStatus) {
                            com.grokplayer.tv.data.DownloadStatus.Queued,
                            com.grokplayer.tv.data.DownloadStatus.Running,
                            -> "İndiriliyor…"
                            com.grokplayer.tv.data.DownloadStatus.Done -> "İndirildi"
                            else -> stringResource(R.string.download)
                        }
                        add(
                            com.grokplayer.tv.ui.components.ModalAction(label) {
                                optionsFor = null
                                val result = downloads.enqueueAll(
                                    listOf(item.title to item.url),
                                    downloadHeight,
                                )
                                onNotice(result.notice().ifBlank { "İndirme kuyruğu güncellendi" })
                            },
                        )
                    }
                    add(
                        com.grokplayer.tv.ui.components.ModalAction(
                            if (item.favorite) "Favorilerden çıkar" else "Favorilere ekle",
                        ) {
                            streams.toggleFavorite(item.id)
                            optionsFor = null
                            restoreAfterModal = true
                        },
                    )
                    add(
                        com.grokplayer.tv.ui.components.ModalAction("Sil") {
                            streams.remove(item.id)
                            optionsFor = null
                            restoreAfterModal = true
                        },
                    )
                },
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
    enablePreview: Boolean = true,
    progress: Float? = null,
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
                    posterUrl = item.posterUrl,
                    durationMs = item.durationMs,
                    isLive = item.kind == StreamKind.Live,
                    enablePreview = enablePreview,
                    originUrl = item.pageUrl ?: item.url,
                    referer = item.referer,
                    userAgent = item.userAgent,
                    progress = progress,
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
            }
            Text(item.title, style = GrokType.cardTitle, color = GrokWhite, modifier = Modifier.padding(top = 8.dp))
            Text(
                text = when {
                    item.kind == StreamKind.Live -> "CANLI"
                    item.durationMs > 0L -> "${item.durationMs.formatClock()} · VOD"
                    else -> "VOD"
                },
                style = GrokType.cardMeta,
                color = GrokMuted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddStreamDialog(
    initialUrl: String? = null,
    onSave: (String, String) -> Unit,
    onPlayHit: (com.grokplayer.tv.data.scan.ScanHit) -> Unit,
    onSaveHit: (com.grokplayer.tv.data.scan.ScanHit) -> Unit,
    onDismiss: () -> Unit,
) {
    RememberFocusLock()
    val keyboard = LocalSoftwareKeyboardController.current
    val view = LocalView.current
    val context = LocalContext.current
    val imeVisible = WindowInsets.isImeVisible
    var imeWasOpen by remember { mutableStateOf(true) }
    var hideImeOnce by remember { mutableStateOf(false) }
    LaunchedEffect(imeVisible) {
        if (imeVisible) {
            imeWasOpen = true
            hideImeOnce = false
        }
    }
    fun closeImeOrDismiss() {
        if (!hideImeOnce && (imeVisible || imeWasOpen)) {
            keyboard?.hide()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.hideSoftInputFromWindow(view.windowToken, 0)
            hideImeOnce = true
            imeWasOpen = false
            return
        }
        onDismiss()
    }
    InterceptBack { closeImeOrDismiss(); true }
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf(initialUrl.orEmpty()) }
    var autoScan by remember { mutableStateOf(!initialUrl.isNullOrBlank()) }
    var error by remember { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var hits by remember { mutableStateOf<List<com.grokplayer.tv.data.scan.ScanHit>>(emptyList()) }
    val nameFocus = remember { FocusRequester() }
    val urlFocus = remember { FocusRequester() }
    val saveFocus = remember { FocusRequester() }
    val scanFocus = remember { FocusRequester() }
    val firstHit = remember { FocusRequester() }
    val scanScope = rememberCoroutineScope()
    fun startScan(target: String) {
        val clean = target.trim()
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            error = "Geçerli bir http veya https adresi girin"
            return
        }
        error = null
        scanning = true
        hits = emptyList()
        scanScope.launch {
            val found = withContext(Dispatchers.IO) {
                runCatching { com.grokplayer.tv.data.scan.PageScanner.scan(context, clean) }
                    .onFailure { android.util.Log.e("GrokPlayer", "scan failed $clean", it) }
                    .getOrDefault(emptyList())
            }
            scanning = false
            if (found.isEmpty()) {
                error = context.getString(R.string.scan_empty)
            } else {
                hits = found
            }
        }
    }
    LaunchedEffect(autoScan) {
        if (autoScan) {
            autoScan = false
            startScan(url)
        }
    }
    BackHandler(onBack = { closeImeOrDismiss() })
    Box(
        Modifier
            .fillMaxSize()
            .background(GrokInk.copy(alpha = 0.62f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(520.dp)
                .background(GrokSurface, RoundedCornerShape(12.dp))
                .padding(18.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(stringResource(R.string.add_stream), style = GrokType.section, color = GrokWhite)
            DialogField(
                stringResource(R.string.stream_name),
                title,
                { title = it },
                Modifier.padding(top = 12.dp),
                fieldModifier = Modifier
                    .focusRequester(nameFocus)
                    .focusProperties {
                        up = nameFocus
                        down = urlFocus
                    },
                onFocused = {
                    imeWasOpen = true
                    hideImeOnce = false
                },
            )
            DialogField(
                stringResource(R.string.stream_url),
                url,
                { url = it },
                Modifier.padding(top = 8.dp),
                fieldModifier = Modifier
                    .focusRequester(urlFocus)
                    .focusProperties {
                        up = nameFocus
                        down = scanFocus
                    },
                onFocused = {
                    imeWasOpen = true
                    hideImeOnce = false
                },
            )
            error?.let {
                Text(it, style = GrokType.cardMeta, color = GrokPink, modifier = Modifier.padding(top = 8.dp))
            }
            if (scanning) {
                Text(
                    stringResource(R.string.scan_scanning),
                    style = GrokType.cardMeta,
                    color = GrokYellow,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            Row(
                Modifier.padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlineButton(
                    label = stringResource(R.string.scan_page),
                    onClick = { startScan(url) },
                    modifier = Modifier
                        .focusRequester(scanFocus)
                        .focusProperties {
                            up = urlFocus
                            right = saveFocus
                            down = if (hits.isNotEmpty()) firstHit else scanFocus
                        },
                )
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
                    modifier = Modifier
                        .focusRequester(saveFocus)
                        .focusProperties {
                            up = urlFocus
                            left = scanFocus
                        },
                )
            }
            if (hits.isNotEmpty()) {
                Text(
                    "${hits.size} yayın bulundu",
                    style = GrokType.cardMeta,
                    color = GrokMuted,
                    modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
                )
                hits.forEachIndexed { index, hit ->
                    ScanHitRow(
                        hit = hit,
                        onPlay = { onPlayHit(hit) },
                        onSave = { onSaveHit(hit) },
                        playModifier = Modifier.then(
                            if (index == 0) Modifier.focusRequester(firstHit) else Modifier,
                        ),
                    )
                }
                LaunchedEffect(hits) { runCatching { firstHit.requestFocus() } }
            }
        }
    }
    LaunchedEffect(Unit) {
        repeat(8) {
            kotlinx.coroutines.delay(40)
            runCatching { nameFocus.requestFocus() }
        }
    }
}

@Composable
private fun ScanHitRow(
    hit: com.grokplayer.tv.data.scan.ScanHit,
    onPlay: () -> Unit,
    onSave: () -> Unit,
    playModifier: Modifier = Modifier,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .background(GrokInk, RoundedCornerShape(8.dp))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        VideoPoster(
            uri = Uri.parse(hit.playUrl),
            title = hit.title,
            focused = false,
            posterUrl = hit.thumbnailUrl,
            modifier = Modifier
                .width(96.dp)
                .height(54.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = hit.title,
                style = GrokType.cardTitle,
                color = GrokWhite,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(if (hit.kind == StreamKind.Live) "CANLI" else "VOD")
                    if (hit.detail.isNotBlank()) {
                        append(" · ")
                        append(hit.detail)
                    }
                },
                style = GrokType.cardMeta,
                color = GrokMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        OutlineButton(
            label = stringResource(R.string.scan_play),
            onClick = onPlay,
            modifier = playModifier,
        )
        OutlineButton(
            label = stringResource(R.string.scan_save),
            onClick = onSave,
        )
    }
}

@Composable
private fun DialogField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
    onFocused: () -> Unit = {},
) {
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
                .then(fieldModifier)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .onFocusChanged { if (it.isFocused) onFocused() },
        )
    }
}
