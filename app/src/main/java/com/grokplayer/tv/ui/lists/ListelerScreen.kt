package com.grokplayer.tv.ui.lists

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.grokplayer.tv.R
import com.grokplayer.tv.data.CollectionGrouper
import com.grokplayer.tv.data.CollectionStore
import com.grokplayer.tv.data.DownloadStatus
import com.grokplayer.tv.data.DownloadStore
import com.grokplayer.tv.data.FocusAnchor
import com.grokplayer.tv.data.FolderPlaylist
import com.grokplayer.tv.data.OfflineCollections
import com.grokplayer.tv.data.LibraryVideo
import com.grokplayer.tv.data.PlaylistCollectionSummary
import com.grokplayer.tv.data.PlaylistCollections
import com.grokplayer.tv.data.PlaylistStore
import com.grokplayer.tv.data.WatchLogic
import com.grokplayer.tv.data.WatchStore
import com.grokplayer.tv.data.formatClock
import com.grokplayer.tv.data.vodQueue
import com.grokplayer.tv.data.link.LinkController
import com.grokplayer.tv.ui.components.AnimatedOverlay
import com.grokplayer.tv.ui.components.EmptyState
import com.grokplayer.tv.ui.components.FilterChip
import com.grokplayer.tv.ui.components.FocusableAction
import com.grokplayer.tv.ui.components.ListResumeBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import com.grokplayer.tv.ui.components.ModalAction
import com.grokplayer.tv.ui.components.ModalMenu
import com.grokplayer.tv.ui.components.VideoPoster
import com.grokplayer.tv.ui.theme.GrokInk
import com.grokplayer.tv.ui.theme.GrokLine
import com.grokplayer.tv.ui.theme.GrokMuted
import com.grokplayer.tv.ui.theme.GrokSurface
import com.grokplayer.tv.ui.theme.GrokType
import com.grokplayer.tv.ui.theme.GrokWhite
import com.grokplayer.tv.ui.theme.GrokYellow
import com.grokplayer.tv.ui.theme.InterceptBack
import com.grokplayer.tv.ui.theme.RememberFocusLock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class ListsTab { Playlists, Collections }

private sealed class ListsBody {
    data object NeedPc : ListsBody()
    data object Loading : ListsBody()
    data object Playlists : ListsBody()
    data object CollectionPlaylists : ListsBody()
    data class CollectionGroups(val playlistId: String) : ListsBody()
    data class PlaylistDetail(val id: String) : ListsBody()
    data class CollectionDetail(val playlistId: String, val collectionId: String) : ListsBody()
}

@Composable
fun ListelerScreen(
    firstFocus: FocusRequester,
    railFocus: FocusRequester,
    link: LinkController,
    playlists: PlaylistStore,
    collections: CollectionStore,
    downloads: DownloadStore,
    localVideos: List<LibraryVideo> = emptyList(),
    onPlay: (List<LibraryVideo>, Int, Boolean, Boolean, String?) -> Unit,
    watch: WatchStore,
    onNotice: (String) -> Unit,
    playerOpen: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val linkUi by link.ui.collectAsState()
    val pc = linkUi.paired.firstOrNull { it.id == linkUi.connectedId }
        ?: linkUi.paired.firstOrNull()
    var tab by remember { mutableStateOf(ListsTab.Playlists) }
    var openPlaylist by remember { mutableStateOf<FolderPlaylist?>(null) }
    var openCollectionsPlaylist by remember { mutableStateOf<FolderPlaylist?>(null) }
    var offlineBrowse by remember { mutableStateOf(false) }
    var openCollection by remember { mutableStateOf<CollectionGrouper.Bucket<LibraryVideo>?>(null) }
    var playlistVideos by remember { mutableStateOf<List<LibraryVideo>>(emptyList()) }
    var videosByPlaylist by remember { mutableStateOf<Map<String, List<LibraryVideo>>>(emptyMap()) }
    var loading by remember { mutableStateOf(false) }
    var playlistMenu by remember { mutableStateOf<FolderPlaylist?>(null) }
    var collectionMenu by remember { mutableStateOf<CollectionGrouper.Bucket<LibraryVideo>?>(null) }
    var videoMenu by remember { mutableStateOf<LibraryVideo?>(null) }
    var renameTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var addFolderOpen by remember { mutableStateOf(false) }
    var createPlaylistOpen by remember { mutableStateOf(false) }
    var createCollectionOpen by remember { mutableStateOf(false) }
    var resetOpen by remember { mutableStateOf(false) }
    var grantedPaths by remember { mutableStateOf<List<String>>(emptyList()) }
    val playlistsFocus = remember { FocusRequester() }
    val collectionsFocus = remember { FocusRequester() }
    var playlistAnchor by remember { mutableStateOf(FocusAnchor()) }
    var collectionAnchor by remember { mutableStateOf(FocusAnchor()) }
    var videoAnchor by remember { mutableStateOf(FocusAnchor()) }
    var restoreGen by remember { mutableIntStateOf(0) }
    var restoreLock by remember { mutableStateOf(false) }
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    fun requester(key: String): FocusRequester = requesters.getOrPut(key) { FocusRequester() }

    fun enqueue(videos: List<LibraryVideo>) {
        if (offlineBrowse) {
            onNotice("Bu koleksiyon zaten cihazda")
            return
        }
        if (videos.isEmpty()) {
            onNotice("İndirilecek video yok")
            return
        }
        val result = downloads.enqueueAll(
            videos.map { it.title to (it.originUrl ?: it.uri.toString()) },
        )
        onNotice(result.notice().ifBlank { "İndirme kuyruğu güncellendi" })
    }

    fun loadPlaylist(item: FolderPlaylist) {
        if (item.custom) {
            playlistVideos = playlists.videosOf(item.id)
            restoreGen += 1
            return
        }
        val host = pc ?: return
        loading = true
        scope.launch {
            val listing = link.browse(host, item.path, deep = true)
            playlistVideos = listing?.let { link.videosFrom(host, it.videos) }.orEmpty()
            loading = false
            if (listing == null) onNotice("Klasör okunamadı. PC’de klasör izni verin.")
            restoreGen += 1
        }
    }

    fun customMap(): Map<String, List<LibraryVideo>> =
        playlists.items.filter { it.custom }.associate { it.id to playlists.videosOf(it.id) }

    fun refreshAll() {
        val custom = customMap()
        val host = pc
        if (host == null) {
            videosByPlaylist = custom
            restoreLock = true
            restoreGen += 1
            return
        }
        loading = true
        scope.launch {
            val root = link.browse(host, "")
            grantedPaths = root?.granted.orEmpty()
            playlists.syncGranted(host.id, grantedPaths)
            val map = linkedMapOf<String, List<LibraryVideo>>()
            map.putAll(customMap())
            playlists.items.filter { it.pcId == host.id }.forEach { item ->
                val listing = link.browse(host, item.path, deep = true)
                map[item.id] = listing?.let { link.videosFrom(host, it.videos) }.orEmpty()
            }
            videosByPlaylist = map
            loading = false
            restoreLock = true
            restoreGen += 1
        }
    }

    playlists.tick()
    LaunchedEffect(pc?.id, tab, playlists.tick()) {
        videosByPlaylist = videosByPlaylist + customMap()
        if (pc == null) return@LaunchedEffect
        val root = link.browse(pc, "")
        grantedPaths = root?.granted.orEmpty()
        if (tab == ListsTab.Playlists) {
            playlists.syncGranted(pc.id, grantedPaths)
        } else {
            refreshAll()
        }
    }

    val visiblePlaylists = playlists.items.filter { it.custom || (pc != null && it.pcId == pc.id) }
    collections.tick()
    val offlineVideos = OfflineCollections.mergeByKey(
        preferred = downloads.items
            .filter { it.status == DownloadStatus.Done && it.localPath != null }
            .map { it.toVideo() },
        extra = localVideos.filter { OfflineCollections.isOfflineVideo(it) },
        keyOf = OfflineCollections::fileKey,
    )
    fun bucketsFor(playlistId: String) =
        if (playlistId == OfflineCollections.SCOPE || offlineBrowse) {
            collections.apply(offlineVideos, OfflineCollections.SCOPE)
        } else {
            collections.apply(videosByPlaylist[playlistId].orEmpty(), playlistId)
        }
    val offlineBuckets = collections.apply(offlineVideos, OfflineCollections.SCOPE)
    val collectionSummaries = PlaylistCollections.summarize(
        visiblePlaylists,
        { videosByPlaylist[it].orEmpty() },
        { videos, scope -> collections.apply(videos, scope) },
    )
    val activePlaylistId = when {
        offlineBrowse -> OfflineCollections.SCOPE
        else -> openCollectionsPlaylist?.id ?: openPlaylist?.id.orEmpty()
    }
    val scopedBuckets = if (activePlaylistId.isBlank()) emptyList() else bucketsFor(activePlaylistId)
    val playlistKeys = if (tab == ListsTab.Collections) {
        buildList {
            if (offlineVideos.isNotEmpty()) add(OfflineCollections.SOURCE_ID)
            addAll(collectionSummaries.map { it.playlistId })
        }
    } else {
        visiblePlaylists.map { it.id }
    }
    val collectionKeys = scopedBuckets.map { it.id }
    val detailVideos = openCollection?.items ?: playlistVideos
    val videoKeys = detailVideos.map { it.id }

    fun rememberPlaylist(key: String) {
        playlistAnchor = playlistAnchor.remember(key, playlistKeys)
    }

    fun rememberCollection(key: String) {
        collectionAnchor = collectionAnchor.remember(key, collectionKeys)
    }

    fun rememberVideo(key: String) {
        videoAnchor = videoAnchor.remember(key, videoKeys)
    }

    fun rememberList(key: String) {
        if ((openCollectionsPlaylist != null || offlineBrowse) && openCollection == null) {
            rememberCollection(key)
        } else {
            rememberPlaylist(key)
        }
    }

    fun syncOpenCollection() {
        val playlistId = if (offlineBrowse) OfflineCollections.SCOPE else openCollectionsPlaylist?.id ?: return
        val current = openCollection ?: return
        openCollection = bucketsFor(playlistId).firstOrNull { it.id == current.id }
        restoreLock = true
        restoreGen += 1
    }

    val firstBodyKey = when {
        openPlaylist != null -> playlistVideos.firstOrNull()?.id ?: "action:playall"
        openCollection != null -> openCollection?.items?.firstOrNull()?.id ?: "action:playall"
        offlineBrowse || openCollectionsPlaylist != null -> "action:createcollection"
        tab == ListsTab.Playlists -> "action:createplaylist"
        else -> if (offlineVideos.isNotEmpty()) OfflineCollections.SOURCE_ID else collectionSummaries.firstOrNull()?.playlistId
    }
    val firstBodyFocus = firstBodyKey?.let { requester(it) } ?: FocusRequester.Cancel

    val modalOpen = playlistMenu != null || collectionMenu != null || videoMenu != null ||
        renameTarget != null || addFolderOpen ||
        createPlaylistOpen || createCollectionOpen || resetOpen
    val detailOpen = openPlaylist != null || openCollection != null
    val collectionBrowseOpen = openCollectionsPlaylist != null || offlineBrowse

    fun playQueue(
        queue: List<LibraryVideo>,
        index: Int,
        resume: Boolean = true,
        listId: String? = null,
        ask: Boolean = false,
    ) {
        val (vods, start) = vodQueue(queue, index)
        if (vods.isEmpty()) return
        vods.getOrNull(start)?.id?.let { rememberVideo(it) }
        onPlay(vods, start, resume, ask, listId)
    }

    fun goBack(): Boolean {
        when {
            addFolderOpen -> addFolderOpen = false
            createPlaylistOpen -> createPlaylistOpen = false
            createCollectionOpen -> createCollectionOpen = false
            resetOpen -> resetOpen = false
            renameTarget != null -> renameTarget = null
            videoMenu != null -> videoMenu = null
            playlistMenu != null -> playlistMenu = null
            collectionMenu != null -> collectionMenu = null
            openCollection != null -> {
                restoreLock = true
                openCollection = null
                restoreGen += 1
            }
            offlineBrowse -> {
                restoreLock = true
                offlineBrowse = false
                restoreGen += 1
            }
            openCollectionsPlaylist != null -> {
                restoreLock = true
                openCollectionsPlaylist = null
                restoreGen += 1
            }
            openPlaylist != null -> {
                restoreLock = true
                openPlaylist = null
                playlistVideos = emptyList()
                restoreGen += 1
            }
            else -> return false
        }
        return true
    }

    InterceptBack { goBack() }
    BackHandler(enabled = modalOpen || detailOpen || collectionBrowseOpen) { goBack() }

    LaunchedEffect(
        modalOpen,
        playerOpen,
        detailOpen,
        collectionBrowseOpen,
        restoreGen,
        loading,
        tab,
        playlistKeys,
        collectionKeys,
        videoKeys,
    ) {
        if (modalOpen || playerOpen || loading) return@LaunchedEffect
        val key = when {
            detailOpen -> videoAnchor.resolve(videoKeys) ?: videoKeys.firstOrNull()
            offlineBrowse || openCollectionsPlaylist != null ->
                collectionAnchor.resolve(collectionKeys) ?: collectionKeys.firstOrNull()
            else -> playlistAnchor.resolve(playlistKeys) ?: playlistKeys.firstOrNull()
        }
        if (key == null) {
            restoreLock = false
            return@LaunchedEffect
        }
        val target = requesters[key]
        if (target == null) {
            restoreLock = false
            return@LaunchedEffect
        }
        repeat(3) {
            if (runCatching { target.requestFocus() }.getOrDefault(false)) {
                restoreLock = false
                return@LaunchedEffect
            }
            delay(32)
        }
        restoreLock = false
    }

    val body = when {
        loading && tab == ListsTab.Playlists && !detailOpen -> ListsBody.Loading
        openPlaylist != null -> ListsBody.PlaylistDetail(openPlaylist!!.id)
        openCollection != null -> ListsBody.CollectionDetail(
            if (offlineBrowse) OfflineCollections.SCOPE else openCollectionsPlaylist?.id.orEmpty(),
            openCollection!!.id,
        )
        offlineBrowse -> ListsBody.CollectionGroups(OfflineCollections.SCOPE)
        openCollectionsPlaylist != null -> ListsBody.CollectionGroups(openCollectionsPlaylist!!.id)
        tab == ListsTab.Playlists -> ListsBody.Playlists
        else -> ListsBody.CollectionPlaylists
    }

    Box(modifier.fillMaxSize().background(GrokInk)) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = 28.dp, end = 28.dp, top = 18.dp, bottom = 10.dp)
                .focusProperties {
                    if (modalOpen || playerOpen) {
                        canFocus = false
                        onEnter = { FocusRequester.Cancel }
                    }
                },
        ) {
            Text(stringResource(R.string.nav_lists), style = GrokType.pageTitle, color = GrokWhite)
            Text(
                stringResource(R.string.lists_subtitle),
                style = GrokType.heroMeta,
                color = GrokMuted,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    stringResource(R.string.tab_playlists),
                    tab == ListsTab.Playlists,
                    {
                        tab = ListsTab.Playlists
                        openCollection = null
                        openCollectionsPlaylist = null
                        offlineBrowse = false
                    },
                    Modifier
                        .focusRequester(firstFocus)
                        .focusRequester(playlistsFocus)
                        .focusProperties {
                            canFocus = !restoreLock && !modalOpen && !playerOpen
                            left = railFocus
                            right = collectionsFocus
                            down = firstBodyFocus
                        },
                )
                FilterChip(
                    stringResource(R.string.tab_collections),
                    tab == ListsTab.Collections,
                    {
                        tab = ListsTab.Collections
                        openPlaylist = null
                        offlineBrowse = false
                    },
                    Modifier
                        .focusRequester(collectionsFocus)
                        .focusProperties {
                            canFocus = !restoreLock && !modalOpen && !playerOpen
                            left = playlistsFocus
                            down = firstBodyFocus
                        },
                )
            }
            AnimatedContent(
                targetState = body,
                modifier = Modifier.weight(1f),
                label = "lists-body",
                transitionSpec = {
                    fun depth(state: ListsBody) = when (state) {
                        is ListsBody.CollectionDetail, is ListsBody.PlaylistDetail -> 2
                        is ListsBody.CollectionGroups -> 1
                        else -> 0
                    }
                    val spec = tween<Float>(240, easing = FastOutSlowInEasing)
                    when {
                        depth(targetState) > depth(initialState) ->
                            (fadeIn(spec) + slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) { it / 10 })
                                .togetherWith(fadeOut(tween(160)) + slideOutHorizontally(tween(200)) { -it / 14 })
                        depth(targetState) < depth(initialState) ->
                            (fadeIn(spec) + slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) { -it / 14 })
                                .togetherWith(fadeOut(tween(160)) + slideOutHorizontally(tween(200)) { it / 10 })
                        else -> fadeIn(tween(180)).togetherWith(fadeOut(tween(140)))
                    }
                },
            ) { page ->
                when (page) {
                    ListsBody.NeedPc -> EmptyState(
                        title = stringResource(R.string.lists_need_pc),
                        body = stringResource(R.string.lists_need_pc_body),
                    )
                    ListsBody.Loading -> Text(
                        "Yükleniyor…",
                        style = GrokType.cardMeta,
                        color = GrokMuted,
                        modifier = Modifier.padding(top = 20.dp),
                    )
                    ListsBody.Playlists -> PlaylistList(
                        items = visiblePlaylists,
                        extras = pc?.let { playlists.availableFolders(it.id, grantedPaths) }.orEmpty(),
                        requester = ::requester,
                        onFocused = { rememberList(it) },
                        onOpen = { item ->
                            rememberList(item.id)
                            videoAnchor = FocusAnchor()
                            openPlaylist = item
                            loadPlaylist(item)
                        },
                        onMenu = { item ->
                            rememberList(item.id)
                            playlistMenu = item
                        },
                        onCreate = { createPlaylistOpen = true },
                        onAddFolder = {
                            addFolderOpen = true
                            val host = pc
                            if (host != null) {
                                scope.launch {
                                    val root = link.browse(host, "")
                                    grantedPaths = root?.granted.orEmpty()
                                }
                            }
                        },
                        tabFocus = playlistsFocus,
                    )
                    ListsBody.CollectionPlaylists -> CollectionPlaylistList(
                        summaries = collectionSummaries,
                        playlists = visiblePlaylists,
                        offlineCount = offlineBuckets.size,
                        offlineVideos = offlineVideos.size,
                        requester = ::requester,
                        onFocused = { rememberList(it) },
                        onOpenOffline = {
                            rememberList(OfflineCollections.SOURCE_ID)
                            offlineBrowse = true
                            openCollectionsPlaylist = null
                            restoreLock = true
                            restoreGen += 1
                        },
                        onOpen = { item ->
                            rememberList(item.id)
                            offlineBrowse = false
                            openCollectionsPlaylist = item
                            restoreLock = true
                            restoreGen += 1
                        },
                        onMenu = { item ->
                            rememberList(item.id)
                            playlistMenu = item
                        },
                        tabFocus = playlistsFocus,
                    )
                    is ListsBody.CollectionGroups -> CollectionList(
                        watch = watch,
                        heading = if (offlineBrowse) "Çevrimdışı" else openCollectionsPlaylist?.title,
                        headingMeta = if (offlineBrowse) {
                            "Cihazındaki videolar · ${offlineVideos.size}"
                        } else {
                            null
                        },
                        buckets = scopedBuckets,
                        onCreate = { createCollectionOpen = true },
                        onReset = { resetOpen = true },
                        requester = ::requester,
                        onFocused = { rememberList(it) },
                        onOpen = { bucket ->
                            rememberList(bucket.id)
                            videoAnchor = FocusAnchor()
                            openCollection = bucket
                            restoreLock = true
                            restoreGen += 1
                        },
                        onMenu = { bucket ->
                            rememberList(bucket.id)
                            collectionMenu = bucket
                        },
                        tabFocus = playlistsFocus,
                    )
                    is ListsBody.PlaylistDetail -> {
                        val item = openPlaylist
                        if (item != null) {
                            val listId = WatchLogic.playlistId(item.id)
                            val stats = watch.stats(playlistVideos)
                            val watchedLine = WatchLogic.listWatchedLine(stats)
                            VideoList(
                                title = item.title,
                                subtitle = buildString {
                                    append("${playlistVideos.size} video · VOD")
                                    if (watchedLine != null) append(" · ").append(watchedLine)
                                },
                                videos = playlistVideos,
                                requester = ::requester,
                                onFocused = { rememberVideo(it) },
                                onPlay = { video ->
                                    rememberVideo(video.id)
                                    val index = playlistVideos.indexOfFirst { it.id == video.id }.coerceAtLeast(0)
                                    playQueue(playlistVideos, index, resume = true, listId = listId, ask = true)
                                },
                                onMenu = { video ->
                                    rememberVideo(video.id)
                                    videoMenu = video
                                },
                                onPlayAll = { if (playlistVideos.isNotEmpty()) playQueue(playlistVideos, 0, resume = false, listId = listId) },
                                onDownloadAll = { enqueue(playlistVideos) },
                                tabFocus = playlistsFocus,
                                progressOf = { watch.progressFraction(it) },
                                resumeVideo = watch.resumeIn(playlistVideos, listId),
                                resumePosition = { watch.positionMs(it) },
                                resumeDuration = { watch.durationMs(it) },
                                enablePreview = !playerOpen,
                                onResume = { video ->
                                    val index = playlistVideos.indexOfFirst { it.id == video.id }.coerceAtLeast(0)
                                    playQueue(playlistVideos, index, resume = true, listId = listId, ask = false)
                                },
                                onRestart = { video ->
                                    val index = playlistVideos.indexOfFirst { it.id == video.id }.coerceAtLeast(0)
                                    playQueue(playlistVideos, index, resume = false, listId = listId)
                                },
                            )
                        }
                    }
                    is ListsBody.CollectionDetail -> {
                        val bucket = openCollection
                        if (bucket != null) {
                            val listId = WatchLogic.collectionId(bucket.id)
                            val stats = watch.stats(bucket.items)
                            val watchedLine = WatchLogic.listWatchedLine(stats)
                            VideoList(
                                title = bucket.name,
                                subtitle = buildString {
                                    if (offlineBrowse) append("Çevrimdışı · ")
                                    if (bucket.isGeneral) {
                                        append("Tekil videolar · ${bucket.items.size}")
                                    } else {
                                        append("${bucket.items.size} ilgili video")
                                    }
                                    if (watchedLine != null) append(" · ").append(watchedLine)
                                },
                                videos = bucket.items,
                                requester = ::requester,
                                onFocused = { rememberVideo(it) },
                                onPlay = { video ->
                                    rememberVideo(video.id)
                                    val index = bucket.items.indexOfFirst { it.id == video.id }.coerceAtLeast(0)
                                    playQueue(bucket.items, index, resume = true, listId = listId, ask = true)
                                },
                                onMenu = { video ->
                                    rememberVideo(video.id)
                                    videoMenu = video
                                },
                                onPlayAll = { if (bucket.items.isNotEmpty()) playQueue(bucket.items, 0, resume = false, listId = listId) },
                                onDownloadAll = { enqueue(bucket.items) },
                                showDownload = !offlineBrowse,
                                tabFocus = playlistsFocus,
                                progressOf = { watch.progressFraction(it) },
                                resumeVideo = watch.resumeIn(bucket.items, listId),
                                resumePosition = { watch.positionMs(it) },
                                resumeDuration = { watch.durationMs(it) },
                                enablePreview = !playerOpen,
                                onResume = { video ->
                                    val index = bucket.items.indexOfFirst { it.id == video.id }.coerceAtLeast(0)
                                    playQueue(bucket.items, index, resume = true, listId = listId, ask = false)
                                },
                                onRestart = { video ->
                                    val index = bucket.items.indexOfFirst { it.id == video.id }.coerceAtLeast(0)
                                    playQueue(bucket.items, index, resume = false, listId = listId)
                                },
                            )
                        }
                    }
                }
            }
        }

        AnimatedOverlay(playlistMenu) { item ->
            ModalMenu(
                title = item.title,
                meta = "Oynatma listesi",
                onDismiss = { playlistMenu = null },
                actions = listOf(
                    ModalAction("Tümünü oynat", icon = Icons.AutoMirrored.Outlined.PlaylistPlay) {
                        playlistMenu = null
                        rememberList(item.id)
                        videoAnchor = FocusAnchor()
                        openPlaylist = item
                        loadPlaylist(item)
                        if (item.custom) {
                            val queue = playlists.videosOf(item.id)
                            if (queue.isNotEmpty()) playQueue(queue, 0, resume = false, listId = WatchLogic.playlistId(item.id))
                        } else {
                            scope.launch {
                                val host = pc ?: return@launch
                                val listing = link.browse(host, item.path, deep = true)
                                val queue = listing?.let { link.videosFrom(host, it.videos) }.orEmpty()
                                if (queue.isNotEmpty()) playQueue(queue, 0, resume = false, listId = WatchLogic.playlistId(item.id))
                            }
                        }
                    },
                    ModalAction("Tümünü indir", icon = Icons.Outlined.Download) {
                        playlistMenu = null
                        if (item.custom) {
                            enqueue(playlists.videosOf(item.id))
                        } else {
                            scope.launch {
                                val host = pc ?: return@launch
                                val listing = link.browse(host, item.path, deep = true)
                                enqueue(listing?.let { link.videosFrom(host, it.videos) }.orEmpty())
                            }
                        }
                    },
                    ModalAction("Listeden kaldır", icon = Icons.Outlined.Delete) {
                        playlists.remove(item.id)
                        playlistMenu = null
                    },
                    ModalAction("Kapat", icon = Icons.Outlined.Close) { playlistMenu = null },
                ),
            )
        }

        AnimatedOverlay(collectionMenu) { bucket ->
            ModalMenu(
                title = bucket.name,
                meta = "Koleksiyon",
                onDismiss = { collectionMenu = null },
                actions = buildList {
                    add(
                        ModalAction("Tümünü oynat", icon = Icons.AutoMirrored.Outlined.PlaylistPlay) {
                            collectionMenu = null
                            if (bucket.items.isNotEmpty()) playQueue(bucket.items, 0, resume = false, listId = WatchLogic.collectionId(bucket.id))
                        },
                    )
                    if (!offlineBrowse) {
                        add(
                            ModalAction("Tümünü indir", icon = Icons.Outlined.Download) {
                                collectionMenu = null
                                enqueue(bucket.items)
                            },
                        )
                    }
                    add(
                        ModalAction("Yeniden adlandır", icon = Icons.Outlined.Edit) {
                            collectionMenu = null
                            renameTarget = bucket.id to bucket.name
                        },
                    )
                    if (!bucket.isGeneral) {
                        add(
                            ModalAction("Sil", icon = Icons.Outlined.Delete) {
                                val playlistId = activePlaylistId
                                collections.delete(bucket.id, playlistId, bucket.items.map { it.id })
                                collectionMenu = null
                                if (openCollection?.id == bucket.id) openCollection = null
                                syncOpenCollection()
                            },
                        )
                    }
                    add(ModalAction("Kapat", icon = Icons.Outlined.Close) { collectionMenu = null })
                },
            )
        }

        AnimatedOverlay(videoMenu) { video ->
            val playlistId = activePlaylistId
            val move = PlaylistCollections.moveTargets(playlistId, scopedBuckets).map { it.id to it.name }
            VideoMenuHost(
                video = video,
                meta = if (offlineBrowse) "Çevrimdışı" else "VOD",
                showAddToList = false,
                playlists = playlists,
                collections = collections,
                moveTargets = move,
                onMoveTo = { id ->
                    collections.move(video.id, id, playlistId)
                    videoMenu = null
                    syncOpenCollection()
                },
                onCreateAndMove = { name ->
                    val id = collections.create(name, playlistId)
                    collections.move(video.id, id, playlistId)
                    videoMenu = null
                    syncOpenCollection()
                },
                onDismiss = { videoMenu = null },
                watch = watch,
                extraActions = buildList {
                    add(
                        ModalAction("Oynat", icon = Icons.Filled.PlayArrow) {
                            val queue = openCollection?.items ?: playlistVideos
                            val index = queue.indexOfFirst { it.id == video.id }.coerceAtLeast(0)
                            videoMenu = null
                            if (queue.isNotEmpty()) {
                                val listId = openCollection?.let { WatchLogic.collectionId(it.id) }
                                    ?: openPlaylist?.let { WatchLogic.playlistId(it.id) }
                                playQueue(queue, index, resume = true, listId = listId, ask = true)
                            }
                        },
                    )
                    if (!offlineBrowse) {
                        add(
                            ModalAction("İndir", icon = Icons.Outlined.Download) {
                                enqueue(listOf(video))
                                videoMenu = null
                            },
                        )
                    }
                },
                trailingActions = buildList {
                    if (openPlaylist?.custom == true) {
                        add(
                            ModalAction("Listeden çıkar", icon = Icons.Outlined.Delete) {
                                playlists.removeVideo(openPlaylist!!.id, video.id)
                                playlistVideos = playlists.videosOf(openPlaylist!!.id)
                                videosByPlaylist = videosByPlaylist + (openPlaylist!!.id to playlistVideos)
                                videoMenu = null
                            },
                        )
                    }
                },
            )
        }

        AnimatedOverlay(if (addFolderOpen) pc else null) { host ->
            val extras = playlists.availableFolders(host.id, grantedPaths)
            ModalMenu(
                title = stringResource(R.string.add_playlist_folder),
                meta = "PC’de izin verilen klasörler",
                absorbOpeningOk = false,
                onDismiss = { addFolderOpen = false },
                actions = extras.map { (path, title) ->
                    ModalAction(title, icon = Icons.Outlined.Folder) {
                        playlists.add(host.id, path, title)
                        addFolderOpen = false
                        onNotice("Oynatma listesine eklendi")
                    }
                } + listOf(ModalAction("Kapat", icon = Icons.Outlined.Close) { addFolderOpen = false }),
            )
        }

        AnimatedOverlay(renameTarget) { (id, current) ->
            NameDialog(
                title = "Koleksiyonu adlandır",
                initial = current,
                onSave = { name ->
                    collections.rename(id, name)
                    renameTarget = null
                    syncOpenCollection()
                },
                onDismiss = { renameTarget = null },
            )
        }

        if (createPlaylistOpen) {
            NameDialog(
                title = stringResource(R.string.create_playlist),
                initial = "",
                onSave = { name ->
                    val created = playlists.createCustom(name)
                    videosByPlaylist = videosByPlaylist + (created.id to playlists.videosOf(created.id))
                    createPlaylistOpen = false
                    onNotice("Oynatma listesi oluşturuldu")
                },
                onDismiss = { createPlaylistOpen = false },
            )
        }

        if (createCollectionOpen) {
            NameDialog(
                title = stringResource(R.string.create_collection),
                initial = "",
                onSave = { name ->
                    collections.create(name, activePlaylistId)
                    createCollectionOpen = false
                    syncOpenCollection()
                },
                onDismiss = { createCollectionOpen = false },
            )
        }

        if (resetOpen) {
            ModalMenu(
                title = stringResource(R.string.reset_collections),
                meta = "Otomatik gruplar yeniden kurulur",
                onDismiss = { resetOpen = false },
                actions = listOf(
                    ModalAction(stringResource(R.string.reset_full), icon = Icons.Outlined.Delete) {
                        collections.resetFull(activePlaylistId)
                        resetOpen = false
                        openCollection = null
                        syncOpenCollection()
                        onNotice("Koleksiyonlar sıfırlandı")
                    },
                    ModalAction(stringResource(R.string.reset_keep_custom), icon = Icons.Outlined.Folder) {
                        collections.resetKeepCustom(activePlaylistId)
                        resetOpen = false
                        openCollection = null
                        syncOpenCollection()
                        onNotice("Otomatik koleksiyonlar sıfırlandı")
                    },
                    ModalAction("Kapat", icon = Icons.Outlined.Close) { resetOpen = false },
                ),
            )
        }
    }
}

@Composable
private fun PlaylistList(
    items: List<FolderPlaylist>,
    extras: List<Pair<String, String>>,
    requester: (String) -> FocusRequester,
    onFocused: (String) -> Unit,
    onOpen: (FolderPlaylist) -> Unit,
    onMenu: (FolderPlaylist) -> Unit,
    onCreate: () -> Unit,
    onAddFolder: () -> Unit,
    tabFocus: FocusRequester,
) {
    Column(Modifier.fillMaxSize().padding(top = 16.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 12.dp),
        ) {
            com.grokplayer.tv.ui.components.OutlineButton(
                label = stringResource(R.string.create_playlist),
                onClick = onCreate,
                modifier = Modifier
                    .focusRequester(requester("action:createplaylist"))
                    .focusProperties {
                        up = tabFocus
                        down = items.firstOrNull()?.let { requester(it.id) } ?: FocusRequester.Cancel
                    },
            )
            com.grokplayer.tv.ui.components.OutlineButton(
                label = stringResource(R.string.add_playlist_folder),
                onClick = onAddFolder,
                modifier = Modifier
                    .focusRequester(requester("action:addfolder"))
                    .focusProperties { up = tabFocus },
            )
        }
        if (items.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.empty_playlists_title),
                body = stringResource(R.string.empty_playlists_body),
            )
        } else {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items.forEachIndexed { index, item ->
                    ListRow(
                        title = item.title,
                        meta = if (item.custom) "Oynatma listesi · özel" else "Oynatma listesi · PC klasörü",
                        modifier = Modifier
                            .focusRequester(requester(item.id))
                            .focusProperties {
                                if (index == items.lastIndex) down = FocusRequester.Cancel
                                if (index == 0) up = tabFocus
                            }
                            .onFocusChanged { if (it.isFocused) onFocused(item.id) },
                        onClick = { onOpen(item) },
                        onLongClick = { onMenu(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CollectionPlaylistList(
    summaries: List<PlaylistCollectionSummary>,
    playlists: List<FolderPlaylist>,
    offlineCount: Int,
    offlineVideos: Int,
    requester: (String) -> FocusRequester,
    onFocused: (String) -> Unit,
    onOpenOffline: () -> Unit,
    onOpen: (FolderPlaylist) -> Unit,
    onMenu: (FolderPlaylist) -> Unit,
    tabFocus: FocusRequester,
) {
    val showOffline = offlineVideos > 0
    if (!showOffline && summaries.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.empty_collections_title),
            body = stringResource(R.string.empty_collections_body),
        )
        return
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(top = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showOffline) {
            ListRow(
                title = "Çevrimdışı",
                meta = "Cihazındaki videolar · $offlineCount koleksiyon · $offlineVideos video",
                modifier = Modifier
                    .focusRequester(requester(OfflineCollections.SOURCE_ID))
                    .focusProperties {
                        up = tabFocus
                        if (summaries.isEmpty()) down = FocusRequester.Cancel
                    }
                    .onFocusChanged { if (it.isFocused) onFocused(OfflineCollections.SOURCE_ID) },
                onClick = onOpenOffline,
                onLongClick = onOpenOffline,
            )
        }
        summaries.forEachIndexed { index, summary ->
            val item = playlists.firstOrNull { it.id == summary.playlistId } ?: return@forEachIndexed
            ListRow(
                title = item.title,
                meta = "PC · ${summary.collectionCount} koleksiyon · ${summary.videoCount} video",
                modifier = Modifier
                    .focusRequester(requester(item.id))
                    .focusProperties {
                        if (index == summaries.lastIndex) down = FocusRequester.Cancel
                        if (index == 0 && !showOffline) up = tabFocus
                    }
                    .onFocusChanged { if (it.isFocused) onFocused(item.id) },
                onClick = { onOpen(item) },
                onLongClick = { onMenu(item) },
            )
        }
    }
}

@Composable
private fun CollectionList(
    buckets: List<CollectionGrouper.Bucket<LibraryVideo>>,
    watch: WatchStore,
    requester: (String) -> FocusRequester,
    onFocused: (String) -> Unit,
    onOpen: (CollectionGrouper.Bucket<LibraryVideo>) -> Unit,
    onMenu: (CollectionGrouper.Bucket<LibraryVideo>) -> Unit,
    tabFocus: FocusRequester,
    heading: String? = null,
    headingMeta: String? = null,
    onCreate: (() -> Unit)? = null,
    onReset: (() -> Unit)? = null,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(top = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!heading.isNullOrBlank()) {
            Text(heading, style = GrokType.section, color = GrokWhite)
            if (!headingMeta.isNullOrBlank()) {
                Text(
                    headingMeta,
                    style = GrokType.cardMeta,
                    color = GrokMuted,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
            }
        }
        if (onCreate != null || onReset != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                if (onCreate != null) {
                    com.grokplayer.tv.ui.components.OutlineButton(
                        stringResource(R.string.create_collection),
                        onCreate,
                        Modifier
                            .focusRequester(requester("action:createcollection"))
                            .focusProperties {
                                up = tabFocus
                                down = buckets.firstOrNull()?.let { requester(it.id) } ?: FocusRequester.Cancel
                                right = if (onReset != null) requester("action:resetcollections") else FocusRequester.Default
                            },
                    )
                }
                if (onReset != null) {
                    com.grokplayer.tv.ui.components.OutlineButton(
                        stringResource(R.string.reset_collections),
                        onReset,
                        Modifier
                            .focusRequester(requester("action:resetcollections"))
                            .focusProperties {
                                up = tabFocus
                                left = requester("action:createcollection")
                                down = buckets.firstOrNull()?.let { requester(it.id) } ?: FocusRequester.Cancel
                            },
                    )
                }
            }
        }
        if (buckets.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.empty_collections_title),
                body = stringResource(R.string.empty_collections_body),
            )
            return@Column
        }
        buckets.forEachIndexed { index, bucket ->
            ListRow(
                title = bucket.name,
                meta = WatchLogic.collectionMeta(
                    bucket.isGeneral,
                    bucket.items.size,
                    watch.stats(bucket.items),
                ),
                modifier = Modifier
                    .focusRequester(requester(bucket.id))
                    .focusProperties {
                        if (index == buckets.lastIndex) down = FocusRequester.Cancel
                        if (index == 0) {
                            up = if (onCreate != null) requester("action:createcollection") else tabFocus
                        }
                    }
                    .onFocusChanged { if (it.isFocused) onFocused(bucket.id) },
                onClick = { onOpen(bucket) },
                onLongClick = { onMenu(bucket) },
            )
        }
    }
}

@Composable
private fun VideoList(
    title: String,
    subtitle: String,
    videos: List<LibraryVideo>,
    requester: (String) -> FocusRequester,
    onFocused: (String) -> Unit,
    onPlay: (LibraryVideo) -> Unit,
    onMenu: (LibraryVideo) -> Unit,
    onPlayAll: () -> Unit,
    onDownloadAll: () -> Unit,
    tabFocus: FocusRequester,
    showDownload: Boolean = true,
    progressOf: (LibraryVideo) -> Float? = { null },
    resumeVideo: LibraryVideo? = null,
    resumePosition: (LibraryVideo) -> Long = { 0L },
    resumeDuration: (LibraryVideo) -> Long = { it.durationMs },
    onResume: (LibraryVideo) -> Unit = {},
    onRestart: (LibraryVideo) -> Unit = {},
    enablePreview: Boolean = true,
) {
    Column(Modifier.fillMaxSize().padding(top = 16.dp)) {
        Text(title, style = GrokType.section, color = GrokWhite)
        Text(
            subtitle,
            style = GrokType.cardMeta,
            color = GrokMuted,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        if (resumeVideo != null) {
            ListResumeBar(
                video = resumeVideo,
                positionMs = resumePosition(resumeVideo),
                durationMs = resumeDuration(resumeVideo),
                onResume = { onResume(resumeVideo) },
                onRestart = { onRestart(resumeVideo) },
                resumeFocus = requester("action:resume"),
                restartFocus = requester("action:restart"),
                up = tabFocus,
                down = requester("action:playall"),
                progress = progressOf(resumeVideo),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
            com.grokplayer.tv.ui.components.OutlineButton(
                "Tümünü oynat",
                onPlayAll,
                Modifier
                    .focusRequester(requester("action:playall"))
                    .focusProperties {
                        up = if (resumeVideo != null) requester("action:resume") else tabFocus
                    },
            )
            if (showDownload) {
                com.grokplayer.tv.ui.components.OutlineButton("Tümünü indir", onDownloadAll)
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 28.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            itemsIndexed(videos, key = { _, item -> item.id }) { index, video ->
                val lastRow = videos.size - ((videos.size - 1) % 3 + 1)
                FocusableAction(
                    onClick = { onPlay(video) },
                    onLongClick = { onMenu(video) },
                    modifier = Modifier
                        .focusRequester(requester(video.id))
                        .focusProperties {
                            if (index < 3) up = requester("action:playall")
                            if (index >= lastRow) down = FocusRequester.Cancel
                        }
                        .onFocusChanged { if (it.isFocused) onFocused(video.id) },
                    shape = RoundedCornerShape(8.dp),
                ) { focused ->
                    VideoPoster(
                        uri = video.uri,
                        title = video.title,
                        focused = focused,
                        path = video.path,
                        format = video.format,
                        posterUrl = video.posterUrl,
                        durationMs = video.durationMs,
                        isLive = video.isLive,
                        enablePreview = enablePreview,
                        originUrl = video.originUrl,
                        referer = video.referer,
                        userAgent = video.userAgent,
                        progress = progressOf(video),
                        captionOverlay = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ListRow(
    title: String,
    meta: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FocusableAction(onClick = onClick, onLongClick = onLongClick, modifier = modifier, shape = RoundedCornerShape(8.dp)) { focused ->
        val ring by animateColorAsState(
            if (focused) GrokYellow else Color.Transparent,
            animationSpec = tween(160, easing = FastOutSlowInEasing),
            label = "row-focus",
        )
        val fill by animateColorAsState(
            if (focused) GrokSurface else GrokSurface.copy(alpha = 0.86f),
            animationSpec = tween(160),
            label = "row-fill",
        )
        Row(
            Modifier
                .fillMaxWidth()
                .background(fill, RoundedCornerShape(8.dp))
                .border(2.dp, ring, RoundedCornerShape(8.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = GrokType.cardTitle, color = GrokWhite)
                Text(meta, style = GrokType.cardMeta, color = GrokMuted, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NameDialog(
    title: String,
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    val field = remember { FocusRequester() }
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
    val saveKey = remember { FocusRequester() }
    val cancelKey = remember { FocusRequester() }
    RememberFocusLock()
    InterceptBack { closeImeOrDismiss(); true }
    BackHandler(onBack = { closeImeOrDismiss() })
    Box(
        Modifier
            .fillMaxSize()
            .background(GrokInk.copy(alpha = 0.62f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .matchParentSize()
                .focusProperties {
                    canFocus = false
                    onEnter = { FocusRequester.Cancel }
                },
        )
        Column(
            Modifier
                .fillMaxWidth(0.46f)
                .background(GrokSurface, RoundedCornerShape(12.dp))
                .border(1.dp, GrokLine, RoundedCornerShape(12.dp))
                .padding(18.dp)
                .focusProperties {
                    onExit = { FocusRequester.Cancel }
                },
        ) {
            Text(title, style = GrokType.section, color = GrokWhite)
            BasicTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                textStyle = TextStyle(color = GrokWhite, fontSize = 16.sp),
                cursorBrush = SolidColor(GrokYellow),
                modifier = Modifier
                    .padding(top = 14.dp)
                    .fillMaxWidth()
                    .focusRequester(field)
                    .focusProperties {
                        left = FocusRequester.Cancel
                        right = FocusRequester.Cancel
                        up = FocusRequester.Cancel
                        down = saveKey
                    }
                    .onKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                        if (event.key == Key.DirectionDown) {
                            runCatching { saveKey.requestFocus() }
                            true
                        } else {
                            false
                        }
                    }
                    .background(GrokInk, RoundedCornerShape(8.dp))
                    .padding(12.dp),
            )
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.grokplayer.tv.ui.components.OutlineButton(
                    "Kaydet",
                    { onSave(value) },
                    Modifier
                        .focusRequester(saveKey)
                        .focusProperties {
                            left = FocusRequester.Cancel
                            right = cancelKey
                            up = field
                            down = FocusRequester.Cancel
                        }
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                                runCatching { cancelKey.requestFocus() }
                                true
                            } else {
                                false
                            }
                        },
                )
                com.grokplayer.tv.ui.components.OutlineButton(
                    "Vazgeç",
                    onDismiss,
                    Modifier
                        .focusRequester(cancelKey)
                        .focusProperties {
                            left = saveKey
                            right = FocusRequester.Cancel
                            up = field
                            down = FocusRequester.Cancel
                        },
                )
            }
        }
    }
    LaunchedEffect(Unit) {
        repeat(6) {
            delay(40)
            runCatching { field.requestFocus() }
        }
    }
}
