package com.grokplayer.tv.ui.home

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.grokplayer.tv.R
import com.grokplayer.tv.ui.theme.GrokInk
import com.grokplayer.tv.ui.theme.GrokLine
import com.grokplayer.tv.ui.theme.GrokMuted
import com.grokplayer.tv.ui.theme.GrokSoft
import com.grokplayer.tv.ui.theme.GrokType
import com.grokplayer.tv.ui.theme.GrokWhite
import com.grokplayer.tv.ui.theme.GrokYellow
import com.grokplayer.tv.ui.theme.LocalPlaceholderAction

@Composable
fun HomeScreen(
    resumeFocus: FocusRequester,
    railFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val hero = HomeCatalog.hero
    val onPlaceholder = LocalPlaceholderAction.current
    val replayFocus = remember { FocusRequester() }
    val cardRequesters = remember { HomeCatalog.recent.map { FocusRequester() } }

    Box(modifier.fillMaxSize()) {
        Image(
            painter = painterResource(hero.artwork),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.CenterEnd,
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
            Text(
                text = hero.title,
                style = GrokType.heroTitle,
                color = GrokWhite,
                modifier = Modifier.padding(top = 22.dp),
            )
            Text(
                text = "${hero.genre}  ·  ${hero.position} / ${hero.duration}",
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
                            down = cardRequesters.first()
                            left = railFocus
                            right = replayFocus
                        },
                    onClick = { onPlaceholder(hero.title) },
                )
                HeroButton(
                    label = stringResource(R.string.play_from_start),
                    icon = Icons.Outlined.Replay,
                    modifier = Modifier
                        .focusRequester(replayFocus)
                        .focusProperties {
                            down = cardRequesters.first()
                            left = resumeFocus
                        },
                    onClick = { onPlaceholder(hero.title) },
                )
            }

            Spacer(Modifier.weight(1f))

            Text(
                text = stringResource(R.string.recently_opened),
                style = GrokType.section,
                color = GrokWhite,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                HomeCatalog.recent.forEachIndexed { index, item ->
                    RecentCard(
                        item = item,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(cardRequesters[index])
                            .focusProperties {
                                up = resumeFocus
                                left = if (index == 0) railFocus else cardRequesters[index - 1]
                                right = if (index == cardRequesters.lastIndex) {
                                    FocusRequester.Default
                                } else {
                                    cardRequesters[index + 1]
                                }
                            },
                        onClick = { onPlaceholder(item.title) },
                    )
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
    val active = focused
    val shape = RoundedCornerShape(8.dp)
    val fill = when {
        active -> GrokYellow
        else -> Color.Black.copy(alpha = 0.28f)
    }
    Row(
        modifier = modifier
            .clip(shape)
            .background(fill)
            .then(
                if (active) {
                    Modifier
                } else {
                    Modifier.border(1.5.dp, GrokLine, shape)
                },
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) GrokInk else GrokWhite,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            style = GrokType.button,
            color = if (active) GrokInk else GrokWhite,
        )
    }
}

@Composable
private fun RecentCard(
    item: RecentItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused = interaction.collectIsFocusedAsState().value
    val shape = RoundedCornerShape(8.dp)

    Column(modifier = modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(shape)
                .then(
                    if (focused) {
                        Modifier.border(2.dp, GrokYellow, shape)
                    } else {
                        Modifier
                    },
                )
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick,
                ),
        ) {
            Image(
                painter = painterResource(item.artwork),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (item.progress != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.White.copy(alpha = 0.18f)),
                )
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(item.progress.coerceIn(0.08f, 1f))
                        .height(3.dp)
                        .background(item.progressColor),
                )
            }
        }
        Text(
            text = item.title,
            style = GrokType.cardTitle,
            color = GrokWhite,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = stringResource(item.sourceRes),
            style = GrokType.cardMeta,
            color = GrokMuted,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
