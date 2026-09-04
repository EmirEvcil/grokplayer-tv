package com.grokplayer.tv.ui.streams

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.grokplayer.tv.R
import com.grokplayer.tv.ui.components.FilterChip
import com.grokplayer.tv.ui.components.FocusableAction
import com.grokplayer.tv.ui.components.HintBar
import com.grokplayer.tv.ui.components.MediaPoster
import com.grokplayer.tv.ui.components.OutlineButton
import com.grokplayer.tv.ui.home.HomeCatalog
import com.grokplayer.tv.ui.home.SavedStream
import com.grokplayer.tv.ui.theme.GrokMuted
import com.grokplayer.tv.ui.theme.GrokPink
import com.grokplayer.tv.ui.theme.GrokType
import com.grokplayer.tv.ui.theme.GrokWhite
import com.grokplayer.tv.ui.theme.LocalPlaceholderAction

private enum class StreamFilter { All, Vod, Live, Favorites }

@Composable
fun StreamsScreen(
    firstFocus: FocusRequester,
    railFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    var filter by remember { mutableStateOf(StreamFilter.All) }
    val onPlaceholder = LocalPlaceholderAction.current
    val streams = HomeCatalog.streams.filter {
        when (filter) {
            StreamFilter.All -> true
            StreamFilter.Vod -> !it.live
            StreamFilter.Live -> it.live
            StreamFilter.Favorites -> it.favorite
        }
    }

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
                Text(stringResource(R.string.nav_streams), style = GrokType.pageTitle, color = GrokWhite)
                Text(
                    text = stringResource(R.string.streams_subtitle),
                    style = GrokType.heroMeta,
                    color = GrokMuted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            val addStream = stringResource(R.string.add_stream)
            OutlineButton(
                label = addStream,
                icon = Icons.Outlined.Add,
                onClick = { onPlaceholder(addStream) },
            )
        }

        Row(
            modifier = Modifier.padding(top = 16.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilterChip(stringResource(R.string.filter_all), filter == StreamFilter.All, { filter = StreamFilter.All }, Modifier.focusProperties { left = railFocus })
            FilterChip(stringResource(R.string.filter_vod), filter == StreamFilter.Vod, { filter = StreamFilter.Vod })
            FilterChip(stringResource(R.string.filter_live), filter == StreamFilter.Live, { filter = StreamFilter.Live })
            FilterChip(stringResource(R.string.filter_favorites), filter == StreamFilter.Favorites, { filter = StreamFilter.Favorites })
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            streams.chunked(3).forEachIndexed { rowIndex, row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    row.forEachIndexed { col, stream ->
                        val index = rowIndex * 3 + col
                        StreamTile(
                            stream = stream,
                            modifier = Modifier
                                .weight(1f)
                                .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                                .focusProperties {
                                    left = if (col == 0) railFocus else FocusRequester.Default
                                },
                            onClick = { onPlaceholder(stream.title) },
                            onLongClick = { onPlaceholder("Seçenekler") },
                        )
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        HintBar(
            parts = listOf(stringResource(R.string.hint_play), stringResource(R.string.hint_options)),
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun StreamTile(
    stream: SavedStream,
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
            MediaPoster(
                artwork = stream.artwork,
                title = stream.title,
                focused = focused,
                progress = if (stream.live) null else stream.progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            ) {
                if (stream.favorite) {
                    Icon(
                        imageVector = Icons.Outlined.FavoriteBorder,
                        contentDescription = null,
                        tint = GrokWhite.copy(alpha = 0.85f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(18.dp),
                    )
                }
            }
            Text(
                text = stream.title,
                style = GrokType.cardTitle,
                color = GrokWhite,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (stream.live) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 3.dp),
                ) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .background(GrokPink, CircleShape),
                    )
                    Text(
                        text = stringResource(R.string.live_badge),
                        style = GrokType.cardMeta,
                        color = GrokPink,
                    )
                }
            } else {
                Text(
                    text = "VOD · ${stream.duration}",
                    style = GrokType.cardMeta,
                    color = GrokMuted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
