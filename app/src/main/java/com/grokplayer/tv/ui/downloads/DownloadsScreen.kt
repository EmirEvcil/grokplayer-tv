package com.grokplayer.tv.ui.downloads

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.grokplayer.tv.R
import com.grokplayer.tv.ui.components.FilledFocusButton
import com.grokplayer.tv.ui.components.HintBar
import com.grokplayer.tv.ui.components.OutlineButton
import com.grokplayer.tv.ui.home.DownloadItem
import com.grokplayer.tv.ui.home.DownloadStatus
import com.grokplayer.tv.ui.home.HomeCatalog
import com.grokplayer.tv.ui.theme.GrokMuted
import com.grokplayer.tv.ui.theme.GrokPink
import com.grokplayer.tv.ui.theme.GrokSurface
import com.grokplayer.tv.ui.theme.GrokType
import com.grokplayer.tv.ui.theme.GrokWhite
import com.grokplayer.tv.ui.theme.LocalPlaceholderAction

@Composable
fun DownloadsScreen(
    firstFocus: FocusRequester,
    railFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    var items by remember { mutableStateOf(HomeCatalog.downloads) }
    val onPlaceholder = LocalPlaceholderAction.current
    val running = items.count { it.status == DownloadStatus.Running }
    val queued = items.count { it.status == DownloadStatus.Queued }
    val paused = items.count { it.status == DownloadStatus.Paused }
    val done = items.count { it.status == DownloadStatus.Done }

    Column(
        modifier
            .fillMaxSize()
            .padding(start = 28.dp, end = 28.dp, top = 18.dp, bottom = 10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 100.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column {
                Text(stringResource(R.string.nav_downloads), style = GrokType.pageTitle, color = GrokWhite)
                Text(
                    text = "$running indiriliyor · $queued bekliyor · $paused duraklatıldı · $done tamamlandı",
                    style = GrokType.heroMeta,
                    color = GrokMuted,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = stringResource(R.string.free_space),
                    style = GrokType.cardMeta,
                    color = GrokMuted,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            val downloadSettings = stringResource(R.string.download_settings)
            OutlineButton(
                label = downloadSettings,
                icon = Icons.Outlined.Settings,
                onClick = { onPlaceholder(downloadSettings) },
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items.forEachIndexed { index, item ->
                DownloadRow(
                    item = item,
                    modifier = Modifier.fillMaxWidth(),
                    primaryFocus = if (index == 0) firstFocus else null,
                    railFocus = railFocus,
                    onPrimary = {
                        items = items.map {
                            if (it.title != item.title) {
                                it
                            } else {
                                when (it.status) {
                                    DownloadStatus.Running -> it.copy(status = DownloadStatus.Paused)
                                    DownloadStatus.Paused, DownloadStatus.Queued -> it.copy(status = DownloadStatus.Running)
                                    DownloadStatus.Done -> it
                                }
                            }
                        }
                        if (item.status == DownloadStatus.Done) onPlaceholder(item.title)
                    },
                    onCancel = { onPlaceholder("İptal") },
                    onDelete = { onPlaceholder("Sil") },
                )
            }
        }

        HintBar(
            parts = listOf(
                stringResource(R.string.hint_choose),
                stringResource(R.string.hint_action),
                stringResource(R.string.hint_back_menu),
            ),
        )
    }
}

@Composable
private fun DownloadRow(
    item: DownloadItem,
    primaryFocus: FocusRequester?,
    railFocus: FocusRequester,
    onPrimary: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier
            .clip(shape)
            .background(GrokSurface.copy(alpha = 0.72f))
            .border(1.dp, Color.White.copy(alpha = 0.06f), shape)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Image(
            painter = painterResource(item.artwork),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(112.dp)
                .height(64.dp)
                .clip(RoundedCornerShape(6.dp)),
        )
        Column(Modifier.weight(1f)) {
            Text(item.title, style = GrokType.section, color = GrokWhite)
            val statusText = when (item.status) {
                DownloadStatus.Running -> "İndiriliyor · ${item.quality}"
                DownloadStatus.Queued -> "Sırada bekliyor · ${item.quality}"
                DownloadStatus.Paused -> "Duraklatıldı · ${item.quality}"
                DownloadStatus.Done -> "Tamamlandı · ${item.quality}" + (item.sizeLabel?.let { " · $it" } ?: "")
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 3.dp),
            ) {
                Text(statusText, style = GrokType.cardMeta, color = GrokMuted)
                if (item.status == DownloadStatus.Done) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = GrokPink,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            if (item.progress != null && item.status != DownloadStatus.Done) {
                Box(
                    Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.12f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(item.progress)
                            .background(GrokPink),
                    )
                }
                item.sizeLabel?.let {
                    Text(it, style = GrokType.cardMeta, color = GrokMuted, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val primaryLabel = when (item.status) {
                DownloadStatus.Running -> "Duraklat"
                DownloadStatus.Queued -> "Başlat"
                DownloadStatus.Paused -> "Devam et"
                DownloadStatus.Done -> "Oynat"
            }
            val primaryIcon = when (item.status) {
                DownloadStatus.Running -> Icons.Outlined.Pause
                else -> Icons.Outlined.PlayArrow
            }
            FilledFocusButton(
                label = primaryLabel,
                icon = primaryIcon,
                onClick = onPrimary,
                modifier = Modifier
                    .then(if (primaryFocus != null) Modifier.focusRequester(primaryFocus) else Modifier)
                    .focusProperties { left = railFocus },
            )
            if (item.status == DownloadStatus.Running) {
                OutlineButton(label = "İptal", icon = Icons.Outlined.Close, onClick = onCancel)
            }
            OutlineButton(label = "Sil", icon = Icons.Outlined.Delete, onClick = onDelete)
        }
    }
}
