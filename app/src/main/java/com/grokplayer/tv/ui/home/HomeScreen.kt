package com.grokplayer.tv.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.grokplayer.tv.R
import com.grokplayer.tv.data.CollectionStore
import com.grokplayer.tv.data.LibraryStore
import com.grokplayer.tv.data.LibraryVideo
import com.grokplayer.tv.data.PlaylistStore
import com.grokplayer.tv.data.ThumbnailCache
import com.grokplayer.tv.data.WatchlistEntry
import com.grokplayer.tv.data.WatchlistStore
import com.grokplayer.tv.data.formatClock
import com.grokplayer.tv.ui.components.EmptyState
import com.grokplayer.tv.ui.components.FocusableAction
import com.grokplayer.tv.ui.components.HeroButton
import com.grokplayer.tv.ui.components.ModalAction
import com.grokplayer.tv.ui.components.VideoPoster
import com.grokplayer.tv.ui.lists.VideoMenuHost
import com.grokplayer.tv.ui.theme.GrokInk
import com.grokplayer.tv.ui.theme.GrokLine
import com.grokplayer.tv.ui.theme.GrokMuted
import com.grokplayer.tv.ui.theme.GrokSoft
import com.grokplayer.tv.ui.theme.GrokSurface
import com.grokplayer.tv.ui.theme.GrokType
import com.grokplayer.tv.ui.theme.GrokWhite
import com.grokplayer.tv.ui.theme.GrokYellow
import com.grokplayer.tv.ui.watchlist.WatchlistCard
import com.grokplayer.tv.ui.watchlist.watchlistMeta
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    resumeFocus: FocusRequester,
    railFocus: FocusRequester,
    library: LibraryStore,
    watchlist: WatchlistStore,
    known: List<LibraryVideo>,
    playlists: PlaylistStore?,
    collections: CollectionStore?,
    onPlay: (List<LibraryVideo>, Int, Boolean, Boolean) -> Unit,
    onNotice: (String) -> Unit,
    playerOpen: Boolean,
    focusItemId: String? = null,
    onFocusConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val hero = library.continueWatching() ?: library.recentVideos().firstOrNull()
    val recents = library.recentVideos().ifEmpty { library.videos.take(10) }
    val saved = watchlist.entries(known)
    var pane by remember { mutableStateOf(HomePane.Continue) }
    if (saved.isEmpty()) pane = HomePane.Continue
    val replayFocus = remember { FocusRequester() }
    val continueChip = remember { FocusRequester() }
    val listChip = remember { FocusRequester() }
    val watchFirst = remember { FocusRequester() }
    val watchPlay = remember { FocusRequester() }
    val watchReplay = remember { FocusRequester() }
    var watchIndex by remember { mutableIntStateOf(0) }
    val cardRequesters = remember(recents.size) { List(recents.size.coerceAtLeast(1)) { FocusRequester() } }
    val watchRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val recentState = rememberLazyListState()
    val watchState = rememberLazyListState()
    var lastLaunch by remember { mutableStateOf(HomeLaunch.Resume) }
    var lastCardId by remember { mutableStateOf<String?>(null) }
    var menuVideo by remember { mutableStateOf<LibraryVideo?>(null) }
    var menuQueue by remember { mutableStateOf<List<LibraryVideo>>(emptyList()) }
    var menuIndex by remember { mutableIntStateOf(0) }
    var paneEntered by remember { mutableStateOf(false) }
    fun watchRequester(index: Int, key: String): FocusRequester {
        if (index == 0) return watchFirst
        return watchRequesters.getOrPut(key) { FocusRequester() }
    }

    LaunchedEffect(focusItemId) {
        if (focusItemId == null) return@LaunchedEffect
        when (lastLaunch) {
            HomeLaunch.Resume -> {
                delay(32)
                repeat(3) {
                    runCatching { resumeFocus.requestFocus() }
                    delay(40)
                }
            }
            HomeLaunch.Replay -> {
                delay(32)
                repeat(3) {
                    runCatching { replayFocus.requestFocus() }
                    delay(40)
                }
            }
            HomeLaunch.Card -> {
                val index = recents.indexOfFirst { it.id == (lastCardId ?: focusItemId) }
                if (index >= 0) {
                    recentState.scrollToItem(index)
                    delay(32)
                    repeat(3) {
                        runCatching { cardRequesters.getOrNull(index)?.requestFocus() }
                        delay(40)
                    }
                } else {
                    runCatching { resumeFocus.requestFocus() }
                }
            }
            HomeLaunch.Watch -> {
                pane = HomePane.Watchlist
                val index = saved.indexOfFirst { it.video.id == (lastCardId ?: focusItemId) || it.key == focusItemId }
                if (index >= 0) {
                    watchState.scrollToItem(index)
                    delay(32)
                    repeat(3) {
                        runCatching { watchRequester(index, saved[index].key).requestFocus() }
                        delay(40)
                    }
                }
            }
        }
        onFocusConsumed()
    }
    LaunchedEffect(pane) {
        if (!paneEntered) {
            paneEntered = true
            return@LaunchedEffect
        }
        val target = if (pane == HomePane.Watchlist) watchFirst else resumeFocus
        repeat(8) {
            if (runCatching { target.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
            delay(40)
        }
    }

    if (hero == null && saved.isEmpty()) {
        Column(
            modifier
                .fillMaxSize()
                .background(GrokInk)
                .padding(start = 28.dp, end = 28.dp, top = 70.dp),
        ) {
            Text(
                text = stringResource(R.string.continue_watching).uppercase(),
                style = GrokType.eyebrow,
                color = GrokSoft,
            )
            EmptyState(
                title = stringResource(R.string.empty_home_title),
                body = stringResource(R.string.empty_home_body),
                modifier = Modifier.focusRequester(resumeFocus),
            )
        }
        return
    }

    if (saved.isNotEmpty() && watchIndex > saved.lastIndex) watchIndex = saved.lastIndex
    val shownHero = if (pane == HomePane.Watchlist) saved.getOrNull(watchIndex)?.video else hero
    val position = shownHero?.let { library.startPosition(it) } ?: 0L
    val previewBackdrop = pane == HomePane.Watchlist && !playerOpen && menuVideo == null

    Box(modifier.fillMaxSize().background(GrokInk)) {
        Crossfade(targetState = shownHero, animationSpec = tween(280), label = "home-backdrop") { video ->
            if (video != null) {
                VideoPoster(
                    uri = video.uri,
                    title = video.title,
                    focused = false,
                    path = video.path,
                    format = video.format,
                    timeMs = position.takeIf { it > 1_000L } ?: 1_000L,
                    maxWidth = ThumbnailCache.HERO_WIDTH,
                    enablePreview = previewBackdrop && video.id == shownHero?.id,
                    previewWithoutFocus = true,
                    posterUrl = video.posterUrl,
                    durationMs = video.durationMs,
                    originUrl = video.originUrl,
                    referer = video.referer,
                    userAgent = video.userAgent,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (shownHero != null) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.74f)
                    .background(
                        Brush.horizontalGradient(
                            0.00f to GrokInk.copy(alpha = 0.96f),
                            0.28f to GrokInk.copy(alpha = 0.88f),
                            0.58f to GrokInk.copy(alpha = 0.48f),
                            1.00f to Color.Transparent,
                        ),
                    ),
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.48f)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.35f to GrokInk.copy(alpha = 0.55f),
                            1f to GrokInk.copy(alpha = 0.96f),
                        ),
                    ),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 28.dp, end = 28.dp, top = 70.dp, bottom = 18.dp),
        ) {
            if (saved.isNotEmpty()) {
                val chipDown = if (pane == HomePane.Watchlist) watchPlay else resumeFocus
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    HomeModeChip(
                        label = stringResource(R.string.continue_watching),
                        selected = pane == HomePane.Continue,
                        onClick = { pane = HomePane.Continue },
                        modifier = Modifier
                            .focusRequester(continueChip)
                            .focusProperties {
                                left = railFocus
                                right = listChip
                                down = chipDown
                            },
                    )
                    HomeModeChip(
                        label = stringResource(R.string.watchlist_title),
                        selected = pane == HomePane.Watchlist,
                        onClick = { pane = HomePane.Watchlist },
                        modifier = Modifier
                            .focusRequester(listChip)
                            .focusProperties {
                                left = continueChip
                                down = chipDown
                            },
                    )
                }
            }
            AnimatedContent(
                targetState = pane,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                label = "home-pane",
                transitionSpec = {
                    val towardList = targetState == HomePane.Watchlist
                    val direction = if (towardList) 1 else -1
                    (fadeIn(tween(220)) + slideInHorizontally(tween(280)) { it / 8 * direction })
                        .togetherWith(fadeOut(tween(160)) + slideOutHorizontally(tween(220)) { -it / 10 * direction })
                },
            ) { current ->
                if (current == HomePane.Watchlist) {
                    HomeWatchlistRow(
                        entries = saved,
                        library = library,
                        railFocus = railFocus,
                        upFocus = continueChip,
                        playFocus = watchPlay,
                        replayFocus = watchReplay,
                        pageFocus = resumeFocus,
                        selected = watchIndex,
                        state = watchState,
                        requester = ::watchRequester,
                        onFocused = { index ->
                            if (index >= 0) {
                                watchIndex = index
                                lastLaunch = HomeLaunch.Watch
                                lastCardId = saved.getOrNull(index)?.video?.id
                            }
                        },
                        onResume = {
                            val index = watchIndex.coerceIn(0, saved.lastIndex)
                            lastLaunch = HomeLaunch.Watch
                            lastCardId = saved[index].video.id
                            val videos = saved.map { it.video }
                            val (q, i) = com.grokplayer.tv.data.vodQueue(videos, index)
                            onPlay(q, i, true, false)
                        },
                        onRestart = {
                            val index = watchIndex.coerceIn(0, saved.lastIndex)
                            lastLaunch = HomeLaunch.Watch
                            lastCardId = saved[index].video.id
                            val videos = saved.map { it.video }
                            val (q, i) = com.grokplayer.tv.data.vodQueue(videos, index)
                            onPlay(q, i, false, false)
                        },
                        onClick = { index ->
                            lastLaunch = HomeLaunch.Watch
                            lastCardId = saved[index].video.id
                            val videos = saved.map { it.video }
                            val (q, i) = com.grokplayer.tv.data.vodQueue(videos, index)
                            onPlay(q, i, true, true)
                        },
                        onLongClick = { index ->
                            menuVideo = saved[index].video
                            menuQueue = saved.map { it.video }
                            menuIndex = index
                        },
                    )
                } else if (hero == null) {
                    EmptyState(
                        title = stringResource(R.string.empty_home_title),
                        body = stringResource(R.string.empty_home_body),
                        modifier = Modifier.focusRequester(resumeFocus).focusProperties {
                            left = railFocus
                            up = if (saved.isNotEmpty()) continueChip else FocusRequester.Default
                        },
                    )
                } else {
                    ContinueHome(
                        hero = hero,
                        position = library.startPosition(hero),
                        recents = recents,
                        saved = saved.isNotEmpty(),
                        library = library,
                        resumeFocus = resumeFocus,
                        replayFocus = replayFocus,
                        railFocus = railFocus,
                        continueChip = continueChip,
                        listChip = listChip,
                        cardRequesters = cardRequesters,
                        recentState = recentState,
                        playbackOpen = !playerOpen && menuVideo == null,
                        onResume = {
                            lastLaunch = HomeLaunch.Resume
                            val (queue, heroIndex) = com.grokplayer.tv.data.vodQueue(recents.ifEmpty { listOf(hero) }, hero.id)
                            onPlay(queue, heroIndex, true, false)
                        },
                        onReplay = {
                            lastLaunch = HomeLaunch.Replay
                            val (queue, heroIndex) = com.grokplayer.tv.data.vodQueue(recents.ifEmpty { listOf(hero) }, hero.id)
                            onPlay(queue, heroIndex, false, false)
                        },
                        onCard = { index ->
                            lastLaunch = HomeLaunch.Card
                            lastCardId = recents[index].id
                            val (q, i) = com.grokplayer.tv.data.vodQueue(recents, index)
                            onPlay(q, i, true, true)
                        },
                        onLongClick = { index ->
                            menuVideo = recents[index]
                            menuQueue = recents
                            menuIndex = index
                        },
                    )
                }
            }
        }
        menuVideo?.let { video ->
            VideoMenuHost(
                video = video,
                meta = watchlistMeta(video, library),
                playlists = playlists,
                collections = collections,
                watch = library.watch,
                watchlist = watchlist,
                onNotice = onNotice,
                onDismiss = { menuVideo = null },
                extraActions = listOf(
                    ModalAction(stringResource(R.string.resume), icon = Icons.Filled.PlayArrow) {
                        menuVideo = null
                        val (q, i) = com.grokplayer.tv.data.vodQueue(menuQueue, menuIndex)
                        onPlay(q, i, true, false)
                    },
                    ModalAction(stringResource(R.string.play_from_start), icon = Icons.Outlined.Replay) {
                        menuVideo = null
                        val (q, i) = com.grokplayer.tv.data.vodQueue(menuQueue, menuIndex)
                        onPlay(q, i, false, false)
                    },
                ),
            )
        }
    }
}

@Composable
private fun HomeModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FocusableAction(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
    ) { focused ->
        val fill = when {
            focused -> GrokYellow
            selected -> GrokSurface
            else -> Color.Transparent
        }
        val border = if (focused || selected) GrokYellow else GrokLine
        Text(
            text = label,
            style = GrokType.button,
            color = if (focused) GrokInk else if (selected) GrokWhite else GrokMuted,
            modifier = Modifier
                .background(fill, RoundedCornerShape(20.dp))
                .border(1.5.dp, border, RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun ContinueHome(
    hero: LibraryVideo,
    position: Long,
    recents: List<LibraryVideo>,
    saved: Boolean,
    library: LibraryStore,
    resumeFocus: FocusRequester,
    replayFocus: FocusRequester,
    railFocus: FocusRequester,
    continueChip: FocusRequester,
    listChip: FocusRequester,
    cardRequesters: List<FocusRequester>,
    recentState: androidx.compose.foundation.lazy.LazyListState,
    playbackOpen: Boolean,
    onResume: () -> Unit,
    onReplay: () -> Unit,
    onCard: (Int) -> Unit,
    onLongClick: (Int) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        if (!saved) {
            Text(
                text = stringResource(R.string.continue_watching).uppercase(),
                style = GrokType.eyebrow,
                color = GrokSoft,
            )
        }
        Text(hero.title, style = GrokType.heroTitle, color = GrokWhite, modifier = Modifier.padding(top = 22.dp))
        Text(
            text = when {
                hero.isLive -> "${hero.sourceLabel}  ·  CANLI"
                hero.durationMs <= 0L -> "${hero.sourceLabel}  ·  ${position.formatClock()}"
                else -> "${hero.sourceLabel}  ·  ${position.formatClock()} / ${hero.durationMs.formatClock()}"
            },
            style = GrokType.heroMeta,
            color = GrokMuted,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(
            modifier = Modifier.padding(top = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeroButton(
                label = stringResource(R.string.resume),
                icon = Icons.Filled.PlayArrow,
                modifier = Modifier
                    .focusRequester(resumeFocus)
                    .focusProperties {
                        down = if (recents.isNotEmpty()) cardRequesters.first() else FocusRequester.Default
                        left = railFocus
                        right = replayFocus
                        up = if (saved) continueChip else FocusRequester.Default
                    },
                onClick = onResume,
            )
            HeroButton(
                label = stringResource(R.string.play_from_start),
                icon = Icons.Outlined.Replay,
                modifier = Modifier
                    .focusRequester(replayFocus)
                    .focusProperties {
                        down = if (recents.isNotEmpty()) cardRequesters.first() else FocusRequester.Default
                        left = resumeFocus
                        up = if (saved) listChip else FocusRequester.Default
                    },
                onClick = onReplay,
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = stringResource(R.string.recently_opened),
            style = GrokType.section,
            color = GrokWhite,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        if (recents.isEmpty()) {
            Text(stringResource(R.string.empty_recents), style = GrokType.cardMeta, color = GrokMuted)
        } else {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val cardWidth = (maxWidth - 14.dp * 3) / 4
                LazyRow(
                    state = recentState,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    itemsIndexed(recents, key = { _, item -> item.id }) { index, item ->
                        RecentCard(
                            video = item,
                            sourceLabel = item.sourceLabel,
                            progress = library.progressFraction(item),
                            enablePreview = playbackOpen,
                            modifier = Modifier
                                .width(cardWidth)
                                .focusRequester(cardRequesters[index])
                                .focusProperties {
                                    up = resumeFocus
                                    left = if (index == 0) railFocus else cardRequesters[index - 1]
                                    right = if (index == recents.lastIndex) {
                                        FocusRequester.Cancel
                                    } else {
                                        cardRequesters[index + 1]
                                    }
                                },
                            onClick = { onCard(index) },
                            onLongClick = { onLongClick(index) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeWatchlistRow(
    entries: List<WatchlistEntry>,
    library: LibraryStore,
    railFocus: FocusRequester,
    upFocus: FocusRequester,
    playFocus: FocusRequester,
    replayFocus: FocusRequester,
    pageFocus: FocusRequester,
    selected: Int,
    state: androidx.compose.foundation.lazy.LazyListState,
    requester: (Int, String) -> FocusRequester,
    onFocused: (Int) -> Unit,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onClick: (Int) -> Unit,
    onLongClick: (Int) -> Unit,
) {
    val video = entries.getOrNull(selected)?.video
    LaunchedEffect(selected, entries.size) {
        if (entries.isEmpty()) return@LaunchedEffect
        state.animateScrollToItem((selected - 1).coerceAtLeast(0))
    }
    Column(Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.watchlist_title).uppercase(),
            style = GrokType.eyebrow,
            color = GrokSoft,
        )
        if (video != null) {
            Text(video.title, style = GrokType.heroTitle, color = GrokWhite, modifier = Modifier.padding(top = 22.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                text = watchlistMeta(video, library).ifBlank { video.sourceLabel },
                style = GrokType.heroMeta,
                color = GrokMuted,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(
                modifier = Modifier.padding(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeroButton(
                    label = stringResource(R.string.resume),
                    icon = Icons.Filled.PlayArrow,
                    modifier = Modifier
                        .focusRequester(playFocus)
                        .focusProperties {
                            left = railFocus
                            right = replayFocus
                            up = upFocus
                            down = if (entries.isNotEmpty()) requester(selected, entries[selected.coerceIn(0, entries.lastIndex)].key) else FocusRequester.Cancel
                        },
                    onClick = onResume,
                )
                HeroButton(
                    label = stringResource(R.string.play_from_start),
                    icon = Icons.Outlined.Replay,
                    modifier = Modifier
                        .focusRequester(replayFocus)
                        .focusProperties {
                            left = playFocus
                            up = upFocus
                            down = if (entries.isNotEmpty()) requester(selected, entries[selected.coerceIn(0, entries.lastIndex)].key) else FocusRequester.Cancel
                        },
                    onClick = onRestart,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        if (entries.isNotEmpty()) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val cardWidth = (maxWidth - 14.dp * 3) / 4
                LazyRow(
                    state = state,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    itemsIndexed(entries, key = { _, entry -> entry.key }) { index, entry ->
                        val own = requester(index, entry.key)
                        WatchlistCard(
                            video = entry.video,
                            meta = watchlistMeta(entry.video, library),
                            progress = library.progressFraction(entry.video),
                            enablePreview = false,
                            onClick = { onClick(index) },
                            onLongClick = { onLongClick(index) },
                            modifier = Modifier
                                .width(cardWidth)
                                .focusRequester(own)
                                .then(if (index == selected) Modifier.focusRequester(pageFocus) else Modifier)
                                .onFocusChanged { if (it.isFocused) onFocused(index) }
                                .focusProperties {
                                    up = playFocus
                                    down = FocusRequester.Cancel
                                    left = if (index == 0) railFocus else requester(index - 1, entries[index - 1].key)
                                    right = if (index == entries.lastIndex) FocusRequester.Cancel else requester(index + 1, entries[index + 1].key)
                                },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentCard(
    video: LibraryVideo,
    sourceLabel: String,
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
                isLive = video.isLive,
                enablePreview = enablePreview,
                originUrl = video.originUrl,
                referer = video.referer,
                userAgent = video.userAgent,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            )
            Text(
                video.title,
                style = GrokType.cardTitle,
                color = GrokWhite,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(sourceLabel, style = GrokType.cardMeta, color = GrokMuted, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

private enum class HomePane { Continue, Watchlist }

private enum class HomeLaunch { Resume, Replay, Card, Watch }
