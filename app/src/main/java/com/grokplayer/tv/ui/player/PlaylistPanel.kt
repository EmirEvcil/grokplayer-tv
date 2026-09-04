package com.grokplayer.tv.ui.player

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.grokplayer.tv.R
import com.grokplayer.tv.data.LibraryVideo
import com.grokplayer.tv.data.StorageSource
import com.grokplayer.tv.data.formatClock
import com.grokplayer.tv.ui.components.VideoPoster
import com.grokplayer.tv.ui.theme.GrokInk
import com.grokplayer.tv.ui.theme.GrokMuted
import com.grokplayer.tv.ui.theme.GrokPink
import com.grokplayer.tv.ui.theme.GrokSoft
import com.grokplayer.tv.ui.theme.GrokSurface
import com.grokplayer.tv.ui.theme.GrokType
import com.grokplayer.tv.ui.theme.GrokWhite
import com.grokplayer.tv.ui.theme.GrokYellow

@Composable
fun PlaylistPanel(
    videos: List<LibraryVideo>,
    currentId: String,
    onSelect: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val first = remember { FocusRequester() }
    BackHandler(onBack = onClose)
    Column(
        modifier
            .fillMaxHeight()
            .width(360.dp)
            .background(GrokInk.copy(alpha = 0.96f))
            .padding(18.dp),
    ) {
        Text(stringResource(R.string.playlist_title), style = GrokType.pageTitle, color = GrokWhite)
        Text(
            text = "${videos.size} video",
            style = GrokType.cardMeta,
            color = GrokMuted,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        Row(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(stringResource(R.string.source_local), style = GrokType.button, color = GrokWhite)
            Column {
                Text(stringResource(R.string.nav_streams), style = GrokType.button, color = GrokMuted)
                Box(
                    Modifier
                        .padding(top = 4.dp)
                        .width(28.dp)
                        .height(2.dp)
                        .background(GrokPink),
                )
            }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(videos, key = { _, item -> item.id }) { index, item ->
                val interaction = remember { MutableInteractionSource() }
                val focused = interaction.collectIsFocusedAsState().value
                val current = item.id == currentId
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(if (index == 0) Modifier.focusRequester(first) else Modifier)
                        .background(if (focused) GrokSurface else GrokSurface.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                        .then(if (focused) Modifier.border(2.dp, GrokYellow, RoundedCornerShape(8.dp)) else Modifier)
                        .clickable(interactionSource = interaction, indication = null) { onSelect(index) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${index + 1}", style = GrokType.cardMeta, color = GrokMuted, modifier = Modifier.width(20.dp))
                    VideoPoster(
                        uri = item.uri,
                        title = item.title,
                        focused = false,
                        modifier = Modifier
                            .width(88.dp)
                            .height(50.dp),
                    )
                    Column(Modifier.padding(start = 10.dp)) {
                        Text(item.title, style = GrokType.cardTitle, color = GrokWhite)
                        Text(
                            text = if (current) {
                                "${item.durationMs.formatClock()} · ${stringResource(R.string.now_playing)}"
                            } else {
                                val src = if (item.source == StorageSource.Usb) "USB" else "Yerel"
                                "${item.durationMs.formatClock()} · $src"
                            },
                            style = GrokType.cardMeta,
                            color = if (current) GrokPink else GrokMuted,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "OK: Oynat     Geri: Kapat",
            style = GrokType.cardMeta,
            color = GrokSoft,
            modifier = Modifier.align(Alignment.End),
        )
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}
