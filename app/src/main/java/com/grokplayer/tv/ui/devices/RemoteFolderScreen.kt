package com.grokplayer.tv.ui.devices

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.grokplayer.tv.data.BrowseListing
import com.grokplayer.tv.data.BrowseVideo
import com.grokplayer.tv.data.LibraryVideo
import com.grokplayer.tv.data.link.LinkController
import com.grokplayer.tv.data.link.PairedPc
import com.grokplayer.tv.ui.components.FocusableAction
import com.grokplayer.tv.ui.theme.GrokInk
import com.grokplayer.tv.ui.theme.GrokMuted
import com.grokplayer.tv.ui.theme.GrokSurface
import com.grokplayer.tv.ui.theme.GrokType
import com.grokplayer.tv.ui.theme.GrokWhite
import com.grokplayer.tv.ui.theme.GrokYellow
import com.grokplayer.tv.ui.theme.InterceptBack
import com.grokplayer.tv.ui.theme.RememberFocusLock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun RemoteFolderScreen(
    link: LinkController,
    pc: PairedPc,
    onPlay: (List<LibraryVideo>, Int) -> Unit,
    onDownload: (List<LibraryVideo>) -> Unit,
    onAddPlaylist: (path: String, title: String) -> Unit = { _, _ -> },
    onClose: () -> Unit,
    watch: com.grokplayer.tv.data.WatchStore? = null,
    interactive: Boolean = true,
) {
    val scope = rememberCoroutineScope()
    var path by remember { mutableStateOf("") }
    var listing by remember { mutableStateOf<BrowseListing?>(null) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var optionsFor by remember { mutableStateOf<BrowseVideo?>(null) }
    var folderOptions by remember { mutableStateOf<com.grokplayer.tv.data.BrowseDir?>(null) }
    val first = remember { FocusRequester() }
    if (interactive) RememberFocusLock()

    fun load(next: String) {
        busy = true
        scope.launch {
            listing = link.browse(pc, next)
            path = next
            busy = false
            notice = if (listing == null) "Klasör okunamadı. PC’de klasör izni verin." else null
        }
    }

    LaunchedEffect(pc.id) { load("") }

    fun goBack() {
        if (optionsFor != null) {
            optionsFor = null
            return
        }
        if (folderOptions != null) {
            folderOptions = null
            return
        }
        val parent = listing?.parent
        if (parent == null || path.isBlank()) onClose() else load(parent)
    }

    InterceptBack(enabled = interactive) { goBack(); true }
    BackHandler(enabled = interactive) { goBack() }

    Box(
        Modifier
            .fillMaxSize()
            .background(GrokInk)
            .padding(start = 28.dp, end = 28.dp, top = 22.dp, bottom = 16.dp)
            .focusProperties {
                if (!interactive) {
                    canFocus = false
                    onEnter = { FocusRequester.Cancel }
                }
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            Text("PC klasörleri", style = GrokType.pageTitle, color = GrokWhite)
            Text(
                listing?.path?.ifBlank { pc.name } ?: pc.name,
                style = GrokType.heroMeta,
                color = GrokMuted,
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
            )
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (busy) {
                    Text("Yükleniyor…", style = GrokType.cardMeta, color = GrokMuted)
                }
                notice?.let { Text(it, style = GrokType.cardMeta, color = GrokYellow) }
                listing?.dirs?.forEachIndexed { index, dir ->
                    FolderRow(
                        title = dir.name,
                        meta = "Klasör",
                        modifier = if (index == 0) Modifier.focusRequester(first) else Modifier,
                        onClick = { load(dir.path) },
                        onLongClick = { folderOptions = dir },
                    )
                }
                listing?.videos?.forEachIndexed { index, video ->
                    FolderRow(
                        title = video.title,
                        meta = "VOD · oynat",
                        modifier = if (listing?.dirs.isNullOrEmpty() && index == 0) {
                            Modifier.focusRequester(first)
                        } else {
                            Modifier
                        },
                        onClick = {
                            val queue = link.videosFrom(pc, listing?.videos.orEmpty())
                            val index = queue.indexOfFirst { it.title == video.title }.coerceAtLeast(0)
                            onPlay(queue, index)
                        },
                        onLongClick = { optionsFor = video },
                    )
                }
            }
            Text(
                "Tamam · oynat   Uzun bas · klasörü liste yap / indir   Geri",
                style = GrokType.cardMeta,
                color = GrokMuted,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        folderOptions?.let { dir ->
            com.grokplayer.tv.ui.components.ModalMenu(
                title = dir.name,
                meta = "PC klasörü",
                onDismiss = { folderOptions = null },
                actions = listOf(
                    com.grokplayer.tv.ui.components.ModalAction("Tümünü oynat", icon = Icons.AutoMirrored.Outlined.PlaylistPlay) {
                        folderOptions = null
                        scope.launch {
                            val inside = link.browse(pc, dir.path, deep = true) ?: return@launch
                            val queue = link.videosFrom(pc, inside.videos)
                            if (queue.isEmpty()) notice = "Bu klasörde video yok" else onPlay(queue, 0)
                        }
                    },
                    com.grokplayer.tv.ui.components.ModalAction("Oynatma listesine ekle", icon = Icons.AutoMirrored.Outlined.PlaylistAdd) {
                        onAddPlaylist(dir.path, dir.name)
                        folderOptions = null
                    },
                    com.grokplayer.tv.ui.components.ModalAction("Klasörü indir", icon = Icons.Outlined.Download) {
                        folderOptions = null
                        scope.launch {
                            val inside = link.browse(pc, dir.path, deep = true) ?: return@launch
                            onDownload(link.videosFrom(pc, inside.videos))
                        }
                    },
                    com.grokplayer.tv.ui.components.ModalAction("Kapat", icon = Icons.Outlined.Close) { folderOptions = null },
                ),
            )
        }
        optionsFor?.let { video ->
            val asVideo = link.videosFrom(pc, listOf(video)).firstOrNull()
            if (asVideo != null) {
                com.grokplayer.tv.ui.lists.VideoMenuHost(
                    video = asVideo,
                    meta = "PC’den VOD",
                    showAddToList = false,
                    watch = watch,
                    onDismiss = { optionsFor = null },
                    extraActions = listOf(
                        com.grokplayer.tv.ui.components.ModalAction("Oynat", icon = Icons.Filled.PlayArrow) {
                            val queue = link.videosFrom(pc, listing?.videos.orEmpty())
                            val index = queue.indexOfFirst { it.title == video.title }.coerceAtLeast(0)
                            optionsFor = null
                            onPlay(queue, index)
                        },
                    ),
                    trailingActions = listOf(
                        com.grokplayer.tv.ui.components.ModalAction("İndir", icon = Icons.Outlined.Download) {
                            onDownload(link.videosFrom(pc, listOf(video)))
                            optionsFor = null
                        },
                        com.grokplayer.tv.ui.components.ModalAction("Klasörü indir", icon = Icons.Outlined.Download) {
                            onDownload(link.videosFrom(pc, listing?.videos.orEmpty()))
                            optionsFor = null
                        },
                    ),
                )
            }
        }
    }
    LaunchedEffect(listing?.path, listing?.dirs?.size, listing?.videos?.size, busy, interactive) {
        if (!interactive || busy || listing == null) return@LaunchedEffect
        if (listing?.dirs.isNullOrEmpty() && listing?.videos.isNullOrEmpty()) return@LaunchedEffect
        repeat(16) {
            val ok = runCatching { first.requestFocus() }.getOrDefault(false)
            if (ok) return@LaunchedEffect
            delay(40)
        }
    }
}

@Composable
private fun FolderRow(
    title: String,
    meta: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FocusableAction(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.focusProperties { left = FocusRequester.Cancel },
        shape = RoundedCornerShape(8.dp),
    ) { focused ->
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (focused) GrokSurface else GrokSurface.copy(0.7f), RoundedCornerShape(8.dp))
                .then(if (focused) Modifier.border(2.dp, GrokYellow, RoundedCornerShape(8.dp)) else Modifier)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = GrokType.cardTitle, color = GrokWhite)
                Text(meta, style = GrokType.cardMeta, color = GrokMuted, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}
