package com.grokplayer.tv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.grokplayer.tv.R
import com.grokplayer.tv.data.LibraryVideo
import com.grokplayer.tv.data.formatClock
import com.grokplayer.tv.ui.theme.GrokMuted
import com.grokplayer.tv.ui.theme.GrokType
import com.grokplayer.tv.ui.theme.GrokWhite
import androidx.compose.material3.Text

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
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Text(stringResource(R.string.list_resume_label), style = GrokType.cardMeta, color = GrokMuted)
        Text(video.title, style = GrokType.cardTitle, color = GrokWhite, modifier = Modifier.padding(top = 2.dp))
        Text(
            text = if (video.durationMs > 0L) {
                "${positionMs.formatClock()} / ${video.durationMs.formatClock()}"
            } else {
                positionMs.formatClock()
            },
            style = GrokType.cardMeta,
            color = GrokMuted,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlineButton(
                stringResource(R.string.resume),
                onResume,
                Modifier
                    .focusRequester(resumeFocus)
                    .focusProperties {
                        this.up = up
                        right = restartFocus
                        this.down = down
                    },
            )
            OutlineButton(
                stringResource(R.string.play_from_start),
                onRestart,
                Modifier
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
