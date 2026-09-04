package com.grokplayer.tv.ui.videos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.automirrored.outlined.Sort
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
import com.grokplayer.tv.ui.home.LibraryVideo
import com.grokplayer.tv.ui.home.StorageSource
import com.grokplayer.tv.ui.theme.GrokMuted
import com.grokplayer.tv.ui.theme.GrokType
import com.grokplayer.tv.ui.theme.GrokWhite
import com.grokplayer.tv.ui.theme.LocalPlaceholderAction

private enum class VideoFilter { All, Internal, Usb }

@Composable
fun VideosScreen(
    firstFocus: FocusRequester,
    railFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    var filter by remember { mutableStateOf(VideoFilter.All) }
    val onPlaceholder = LocalPlaceholderAction.current
    val videos = HomeCatalog.videos.filter {
        when (filter) {
            VideoFilter.All -> true
            VideoFilter.Internal -> it.source == StorageSource.Internal
            VideoFilter.Usb -> it.source == StorageSource.Usb
        }
    }
    val tileFocus = remember(videos.size) { List(videos.size) { FocusRequester() } }

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
                Text(stringResource(R.string.nav_videos), style = GrokType.pageTitle, color = GrokWhite)
                Text(
                    text = "${videos.size} video · 2 kaynak",
                    style = GrokType.heroMeta,
                    color = GrokMuted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            val addFolder = stringResource(R.string.add_folder)
            val sortRecent = stringResource(R.string.sort_recent)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlineButton(
                    label = addFolder,
                    icon = Icons.Outlined.CreateNewFolder,
                    onClick = { onPlaceholder(addFolder) },
                    modifier = Modifier.focusProperties { left = railFocus },
                )
                OutlineButton(
                    label = sortRecent,
                    icon = Icons.AutoMirrored.Outlined.Sort,
                    onClick = { onPlaceholder(sortRecent) },
                )
            }
        }

        Row(
            modifier = Modifier.padding(top = 16.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FilterChip(
                label = stringResource(R.string.filter_all),
                selected = filter == VideoFilter.All,
                onClick = { filter = VideoFilter.All },
                modifier = Modifier.focusProperties { left = railFocus },
            )
            FilterChip(
                label = stringResource(R.string.filter_internal),
                selected = filter == VideoFilter.Internal,
                onClick = { filter = VideoFilter.Internal },
            )
            FilterChip(
                label = stringResource(R.string.filter_usb),
                selected = filter == VideoFilter.Usb,
                onClick = { filter = VideoFilter.Usb },
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            videos.chunked(3).forEachIndexed { rowIndex, row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    row.forEachIndexed { col, video ->
                        val index = rowIndex * 3 + col
                        val requester = if (index == 0) firstFocus else tileFocus[index]
                        VideoTile(
                            video = video,
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(requester)
                                .focusProperties {
                                    left = if (col == 0) railFocus else FocusRequester.Default
                                },
                            onClick = { onPlaceholder(video.title) },
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
private fun VideoTile(
    video: LibraryVideo,
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
                artwork = video.artwork,
                title = video.title,
                focused = focused,
                progress = video.progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            )
            Text(
                text = video.title,
                style = GrokType.cardTitle,
                color = GrokWhite,
                modifier = Modifier.padding(top = 8.dp),
            )
            val source = if (video.source == StorageSource.Usb) "USB" else "Dahili"
            Text(
                text = "${video.duration} · ${video.format} · $source",
                style = GrokType.cardMeta,
                color = GrokMuted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
