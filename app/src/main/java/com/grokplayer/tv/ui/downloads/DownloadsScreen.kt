package com.grokplayer.tv.ui.downloads

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.grokplayer.tv.R
import com.grokplayer.tv.data.DownloadItem
import com.grokplayer.tv.data.DownloadStatus
import com.grokplayer.tv.data.DownloadStore
import com.grokplayer.tv.data.LibraryVideo
import com.grokplayer.tv.ui.components.EmptyState
import com.grokplayer.tv.ui.theme.GrokMuted
import com.grokplayer.tv.ui.theme.GrokPink
import com.grokplayer.tv.ui.theme.GrokSoft
import com.grokplayer.tv.ui.theme.GrokSurface
import com.grokplayer.tv.ui.theme.GrokType
import com.grokplayer.tv.ui.theme.GrokWhite
import com.grokplayer.tv.ui.theme.GrokYellow
import com.grokplayer.tv.ui.theme.InterceptBack
import com.grokplayer.tv.ui.theme.RememberFocusLock

@Composable
fun DownloadsScreen(
    firstFocus: FocusRequester,
    railFocus: FocusRequester,
    downloads: DownloadStore,
    onPlay: (List<LibraryVideo>, Int) -> Unit,
    onRemoved: (DownloadItem) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var optionsFor by remember { mutableStateOf<DownloadItem?>(null) }
    var lastFocusedId by rememberSaveable { mutableStateOf<String?>(null) }
    var restoreAfterModal by remember { mutableStateOf(false) }
    val rowFocus = remember { mutableMapOf<String, FocusRequester>() }
    fun requester(id: String) = rowFocus.getOrPut(id) { FocusRequester() }
    fun closeOptions() {
        optionsFor = null
        restoreAfterModal = true
    }
    fun dismiss(): Boolean {
        if (optionsFor == null) return false
        closeOptions()
        return true
    }
    InterceptBack(enabled = optionsFor != null) { dismiss() }
    BackHandler(enabled = optionsFor != null) { dismiss() }
    LaunchedEffect(restoreAfterModal, downloads.items.map { it.id }) {
        if (!restoreAfterModal) return@LaunchedEffect
        kotlinx.coroutines.delay(40)
        val items = downloads.items
        val target = items.firstOrNull { it.id == lastFocusedId } ?: items.firstOrNull()
        lastFocusedId = target?.id
        repeat(6) {
            val ok = if (target != null) {
                runCatching { requester(target.id).requestFocus() }.getOrDefault(false)
            } else {
                runCatching { firstFocus.requestFocus() }.getOrDefault(false)
            }
            if (ok) {
                restoreAfterModal = false
                return@LaunchedEffect
            }
            kotlinx.coroutines.delay(40)
        }
        restoreAfterModal = false
    }

    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = 28.dp, end = 28.dp, top = 18.dp, bottom = 10.dp)
                .focusProperties {
                    if (optionsFor != null) {
                        canFocus = false
                        onEnter = { FocusRequester.Cancel }
                    }
                },
        ) {
            Text(stringResource(R.string.nav_downloads), style = GrokType.pageTitle, color = GrokWhite)
            Text(
                text = stringResource(R.string.downloads_subtitle),
                style = GrokType.heroMeta,
                color = GrokMuted,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )
            if (downloads.items.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.empty_downloads_title),
                    body = stringResource(R.string.empty_downloads_body),
                    modifier = Modifier.focusRequester(firstFocus),
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) {
                    itemsIndexed(downloads.items, key = { index, item -> "${item.id}#$index" }) { _, item ->
                        val restoreId = lastFocusedId ?: downloads.items.firstOrNull()?.id
                        DownloadRow(
                            item = item,
                            modifier = Modifier
                                .then(
                                    if (item.id == restoreId) Modifier.focusRequester(firstFocus) else Modifier,
                                )
                                .focusRequester(requester(item.id))
                                .onFocusChanged { if (it.isFocused) lastFocusedId = item.id }
                                .focusProperties { left = railFocus },
                            onClick = {
                                when (item.status) {
                                    DownloadStatus.Done -> {
                                        val queue = downloads.items.filter { it.status == DownloadStatus.Done }.map { it.toVideo() }
                                        val playIndex = queue.indexOfFirst { it.id == item.toVideo().id }.coerceAtLeast(0)
                                        onPlay(queue, playIndex)
                                    }
                                    DownloadStatus.Failed -> downloads.enqueue(item.title, item.url)
                                    else -> Unit
                                }
                            },
                            onLongClick = { optionsFor = item },
                        )
                    }
                }
            }
        }
        optionsFor?.let { item ->
            DownloadOptions(
                item = item,
                onPlay = {
                    closeOptions()
                    if (item.status == DownloadStatus.Done) {
                        onPlay(listOf(item.toVideo()), 0)
                    }
                },
                onRetry = {
                    downloads.enqueue(item.title, item.url)
                    closeOptions()
                },
                onCancel = {
                    downloads.cancel(item.id)
                    closeOptions()
                },
                onDelete = {
                    val list = downloads.items
                    val idx = list.indexOfFirst { it.id == item.id }
                    lastFocusedId = list.getOrNull(idx + 1)?.id ?: list.getOrNull(idx - 1)?.id
                    downloads.remove(item.id)
                    onRemoved(item)
                    closeOptions()
                },
                onDismiss = { closeOptions() },
            )
        }
    }
}

@Composable
private fun DownloadRow(
    item: DownloadItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    com.grokplayer.tv.ui.components.FocusableAction(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
    ) { focused ->
        Column(
            Modifier
                .fillMaxWidth()
                .background(if (focused) GrokSurface else GrokSurface.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                .then(if (focused) Modifier.border(2.dp, GrokYellow, RoundedCornerShape(8.dp)) else Modifier)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(item.title, style = GrokType.cardTitle, color = GrokWhite)
            Text(
                text = statusLabel(item),
                style = GrokType.cardMeta,
                color = if (item.status == DownloadStatus.Failed) GrokPink else GrokMuted,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (item.status == DownloadStatus.Running || item.status == DownloadStatus.Queued) {
                Box(
                    Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(GrokMuted.copy(0.35f), RoundedCornerShape(2.dp)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(item.progress.coerceIn(0f, 1f))
                            .height(4.dp)
                            .background(GrokPink, RoundedCornerShape(2.dp)),
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadOptions(
    item: DownloadItem,
    onPlay: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    com.grokplayer.tv.ui.components.ModalMenu(
        title = item.title,
        meta = statusLabel(item),
        onDismiss = onDismiss,
        width = 320.dp,
        actions = buildList {
            if (item.status == DownloadStatus.Done) {
                add(com.grokplayer.tv.ui.components.ModalAction("Oynat", onPlay))
            }
            if (item.status == DownloadStatus.Failed) {
                add(com.grokplayer.tv.ui.components.ModalAction("Tekrar dene", onRetry))
            }
            if (item.status == DownloadStatus.Running || item.status == DownloadStatus.Queued) {
                add(com.grokplayer.tv.ui.components.ModalAction("İptal et", onCancel))
            }
            add(com.grokplayer.tv.ui.components.ModalAction("Sil", onDelete))
            add(com.grokplayer.tv.ui.components.ModalAction(stringResource(R.string.close), onDismiss))
        },
    )
}

private fun statusLabel(item: DownloadItem): String = when (item.status) {
    DownloadStatus.Queued -> "Sırada"
    DownloadStatus.Running -> {
        val pct = (item.progress * 100).toInt().coerceIn(0, 99)
        val shown = if (item.progress > 0f && pct == 0) 1 else pct
        "İndiriliyor · %$shown"
    }
    DownloadStatus.Done -> "İndirildi"
    DownloadStatus.Failed -> item.error ?: "İndirilemedi"
}
