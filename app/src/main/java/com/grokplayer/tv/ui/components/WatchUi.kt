package com.grokplayer.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.grokplayer.tv.R
import com.grokplayer.tv.data.LibraryVideo
import com.grokplayer.tv.data.formatClock
import com.grokplayer.tv.ui.theme.GrokInk
import com.grokplayer.tv.ui.theme.GrokLine
import com.grokplayer.tv.ui.theme.GrokMuted
import com.grokplayer.tv.ui.theme.GrokSoft
import com.grokplayer.tv.ui.theme.GrokType
import com.grokplayer.tv.ui.theme.GrokWhite
import com.grokplayer.tv.ui.theme.GrokYellow

@Composable
fun HeroButton(
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
fun ListResumeBar(
    video: LibraryVideo,
    positionMs: Long,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    resumeFocus: FocusRequester,
    restartFocus: FocusRequester,
    up: FocusRequester,
    down: FocusRequester,
    progress: Float? = null,
    durationMs: Long = video.durationMs,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VideoPoster(
            uri = video.uri,
            title = video.title,
            focused = false,
            progress = progress,
            path = video.path,
            format = video.format,
            posterUrl = video.posterUrl,
            durationMs = video.durationMs,
            isLive = video.isLive,
            originUrl = video.originUrl,
            referer = video.referer,
            userAgent = video.userAgent,
            modifier = Modifier
                .width(220.dp)
                .aspectRatio(16f / 9f),
        )
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.continue_watching).uppercase(),
                style = GrokType.eyebrow,
                color = GrokSoft,
            )
            Text(
                video.title,
                style = GrokType.cardTitle,
                color = GrokWhite,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                text = run {
                    val total = durationMs.takeIf { it > 0L } ?: video.durationMs
                    val shown = total.coerceAtLeast(positionMs)
                    if (shown > 0L) {
                        "${positionMs.formatClock()} / ${shown.formatClock()}"
                    } else {
                        positionMs.formatClock()
                    }
                },
                style = GrokType.cardMeta,
                color = GrokMuted,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HeroButton(
                    label = stringResource(R.string.resume),
                    icon = Icons.Filled.PlayArrow,
                    onClick = onResume,
                    modifier = Modifier
                        .focusRequester(resumeFocus)
                        .focusProperties {
                            this.up = up
                            right = restartFocus
                            this.down = down
                        },
                )
                HeroButton(
                    label = stringResource(R.string.play_from_start),
                    icon = Icons.Outlined.Replay,
                    onClick = onRestart,
                    modifier = Modifier
                        .focusRequester(restartFocus)
                        .focusProperties {
                            this.up = up
                            left = resumeFocus
                            this.down = down
                        },
                )
            }
        }
    }
}
