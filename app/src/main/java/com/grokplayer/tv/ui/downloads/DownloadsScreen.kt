package com.grokplayer.tv.ui.downloads

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.grokplayer.tv.R
import com.grokplayer.tv.data.DownloadItem
import com.grokplayer.tv.data.DownloadStatus
import com.grokplayer.tv.data.DownloadStore
import com.grokplayer.tv.data.LibraryVideo
import com.grokplayer.tv.ui.components.EmptyState
import com.grokplayer.tv.ui.theme.GrokInk
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
    val rowFocus = remember(downloads.items.size) {
        List(downloads.items.size.coerceAtLeast(1)) { FocusRequester() }
    }
    fun dismiss(): Boolean {
        if (optionsFor == null) return false
        optionsFor = null
        return true
    }
    InterceptBack(enabled = optionsFor != null) { dismiss() }
    BackHandler(enabled = optionsFor != null) { dismiss() }

    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = 28.dp, end = 28.dp, top = 18.dp, bottom = 10.dp),
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
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(downloads.items, key = { _, item -> item.id }) { index, item ->
                        DownloadRow(
                            item = item,
                            modifier = Modifier
                                .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                                .focusRequester(rowFocus[index])
                                .focusProperties { left = railFocus },
                            onClick = {
                                if (item.status == DownloadStatus.Done) {
                                    val queue = downloads.items.filter { it.status == DownloadStatus.Done }.map { it.toVideo() }
                                    val playIndex = queue.indexOfFirst { it.id == item.toVideo().id }.coerceAtLeast(0)
                                    onPlay(queue, playIndex)
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
                    optionsFor = null
                    if (item.status == DownloadStatus.Done) {
                        onPlay(listOf(item.toVideo()), 0)
                    }
                },
                onCancel = {
                    downloads.cancel(item.id)
                    optionsFor = null
                },
                onDelete = {
                    downloads.remove(item.id)
                    onRemoved(item)
                    optionsFor = null
                },
                onDismiss = { optionsFor = null },
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
    onCancel: () -> Unit,
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
                    .padding(16.dp),
            ) {
                Text(item.title, style = GrokType.section, color = GrokWhite)
                Text(statusLabel(item), style = GrokType.cardMeta, color = GrokSoft, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                if (item.status == DownloadStatus.Done) {
                    OptionLine("Oynat", onPlay, Modifier.focusRequester(first))
                }
                if (item.status == DownloadStatus.Running || item.status == DownloadStatus.Queued) {
                    OptionLine("İptal et", onCancel, Modifier.focusRequester(first))
                }
                OptionLine("Sil", onDelete, if (item.status == DownloadStatus.Failed) Modifier.focusRequester(first) else Modifier)
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
            .background(if (focused) GrokYellow else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(6.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
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
