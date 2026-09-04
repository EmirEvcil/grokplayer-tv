package com.grokplayer.tv.ui.home

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.grokplayer.tv.R
import com.grokplayer.tv.data.LibraryStore
import com.grokplayer.tv.data.LibraryVideo
import com.grokplayer.tv.data.StorageSource
import com.grokplayer.tv.data.formatClock
import com.grokplayer.tv.ui.components.EmptyState
import com.grokplayer.tv.ui.components.VideoPoster
import com.grokplayer.tv.ui.theme.GrokInk
import com.grokplayer.tv.ui.theme.GrokLine
import com.grokplayer.tv.ui.theme.GrokMuted
import com.grokplayer.tv.ui.theme.GrokSoft
import com.grokplayer.tv.ui.theme.GrokType
import com.grokplayer.tv.ui.theme.GrokWhite
import com.grokplayer.tv.ui.theme.GrokYellow

@Composable
fun HomeScreen(
    resumeFocus: FocusRequester,
    railFocus: FocusRequester,
    library: LibraryStore,
    onPlay: (List<LibraryVideo>, Int, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hero = library.continueWatching() ?: library.recentVideos().firstOrNull()
    val recents = library.recentVideos().ifEmpty { library.videos.take(4) }
    val replayFocus = remember { FocusRequester() }
    val cardRequesters = remember(recents.size) { List(recents.size.coerceAtLeast(1)) { FocusRequester() } }

    if (hero == null) {
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

    val queue = recents.ifEmpty { listOf(hero) }
    val heroIndex = queue.indexOfFirst { it.id == hero.id }.coerceAtLeast(0)
    val position = library.progressOf(hero.id)

    Box(modifier.fillMaxSize()) {
        VideoPoster(
            uri = hero.uri,
            title = hero.title,
            focused = false,
            modifier = Modifier.fillMaxSize(),
        )
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 28.dp, end = 28.dp, top = 70.dp, bottom = 18.dp),
        ) {
            Text(
                text = stringResource(R.string.continue_watching).uppercase(),
                style = GrokType.eyebrow,
                color = GrokSoft,
            )
            Text(hero.title, style = GrokType.heroTitle, color = GrokWhite, modifier = Modifier.padding(top = 22.dp))
            Text(
                text = "${if (hero.source == StorageSource.Usb) "USB" else "Yerel"}  ·  ${position.formatClock()} / ${hero.durationMs.formatClock()}",
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
                        },
                    onClick = { onPlay(queue, heroIndex, true) },
                )
                HeroButton(
                    label = stringResource(R.string.play_from_start),
                    icon = Icons.Outlined.Replay,
                    modifier = Modifier
                        .focusRequester(replayFocus)
                        .focusProperties {
                            down = if (recents.isNotEmpty()) cardRequesters.first() else FocusRequester.Default
                            left = resumeFocus
                        },
                    onClick = { onPlay(queue, heroIndex, false) },
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    recents.take(4).forEachIndexed { index, item ->
                        RecentCard(
                            video = item,
                            sourceLabel = if (item.source == StorageSource.Usb) {
                                "USB"
                            } else {
                                stringResource(R.string.source_local)
                            },
                            progress = library.progressFraction(item),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(cardRequesters[index])
                                .focusProperties {
                                    up = resumeFocus
                                    left = if (index == 0) railFocus else cardRequesters[index - 1]
                                    right = if (index == recents.lastIndex.coerceAtMost(3)) {
                                        FocusRequester.Default
                                    } else {
                                        cardRequesters[index + 1]
                                    }
                                },
                            onClick = { onPlay(recents, index, true) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused = interaction.collectIsFocusedAsState().value
    val shape = RoundedCornerShape(8.dp)
    val fill = if (focused) GrokYellow else Color.Black.copy(alpha = 0.28f)
    Row(
        modifier = modifier
            .clip(shape)
            .background(fill)
            .then(if (focused) Modifier else Modifier.border(1.5.dp, GrokLine, shape))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, tint = if (focused) GrokInk else GrokWhite, modifier = Modifier.size(18.dp))
        Text(label, style = GrokType.button, color = if (focused) GrokInk else GrokWhite)
    }
}

@Composable
private fun RecentCard(
    video: LibraryVideo,
    sourceLabel: String,
    progress: Float?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused = interaction.collectIsFocusedAsState().value
    Column(modifier = modifier) {
        VideoPoster(
            uri = video.uri,
            title = video.title,
            focused = focused,
            progress = progress,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        )
        Text(video.title, style = GrokType.cardTitle, color = GrokWhite, modifier = Modifier.padding(top = 8.dp))
        Text(sourceLabel, style = GrokType.cardMeta, color = GrokMuted, modifier = Modifier.padding(top = 2.dp))
    }
}
