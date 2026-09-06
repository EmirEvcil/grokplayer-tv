package com.grokplayer.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.grokplayer.tv.R
import com.grokplayer.tv.data.LibraryStore
import com.grokplayer.tv.data.LibraryVideo
import com.grokplayer.tv.data.ThumbnailCache
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
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    resumeFocus: FocusRequester,
    railFocus: FocusRequester,
    library: LibraryStore,
    onPlay: (List<LibraryVideo>, Int, Boolean) -> Unit,
    focusItemId: String? = null,
    onFocusConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val hero = library.continueWatching() ?: library.recentVideos().firstOrNull()
    val recents = library.recentVideos().ifEmpty { library.videos.take(10) }
    val replayFocus = remember { FocusRequester() }
    val cardRequesters = remember(recents.size) { List(recents.size.coerceAtLeast(1)) { FocusRequester() } }
    val recentState = rememberLazyListState()
    var lastLaunch by remember { mutableStateOf(HomeLaunch.Resume) }
    var lastCardId by remember { mutableStateOf<String?>(null) }

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
        }
        onFocusConsumed()
    }

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
            path = hero.path,
            format = hero.format,
            timeMs = position.takeIf { it > 1_000L } ?: 1_000L,
            maxWidth = ThumbnailCache.HERO_WIDTH,
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
                        },
                    onClick = {
                        lastLaunch = HomeLaunch.Resume
                        onPlay(queue, heroIndex, true)
                    },
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
                    onClick = {
                        lastLaunch = HomeLaunch.Replay
                        onPlay(queue, heroIndex, false)
                    },
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
                                modifier = Modifier
                                    .width(cardWidth)
                                    .focusRequester(cardRequesters[index])
                                    .focusProperties {
                                        up = resumeFocus
                                        left = if (index == 0) railFocus else cardRequesters[index - 1]
                                        right = if (index == recents.lastIndex) {
                                            FocusRequester.Default
                                        } else {
                                            cardRequesters[index + 1]
                                        }
                                    },
                                onClick = {
                                    lastLaunch = HomeLaunch.Card
                                    lastCardId = item.id
                                    onPlay(recents, index, true)
                                },
                            )
                        }
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
            path = video.path,
            format = video.format,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        )
        Text(video.title, style = GrokType.cardTitle, color = GrokWhite, modifier = Modifier.padding(top = 8.dp))
        Text(sourceLabel, style = GrokType.cardMeta, color = GrokMuted, modifier = Modifier.padding(top = 2.dp))
    }
}

private enum class HomeLaunch { Resume, Replay, Card }
