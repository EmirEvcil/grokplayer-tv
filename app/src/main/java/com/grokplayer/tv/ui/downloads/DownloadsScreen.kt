package com.grokplayer.tv.ui.downloads

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.grokplayer.tv.R
import com.grokplayer.tv.ui.components.EmptyState
import com.grokplayer.tv.ui.theme.GrokMuted
import com.grokplayer.tv.ui.theme.GrokType
import com.grokplayer.tv.ui.theme.GrokWhite

@Composable
fun DownloadsScreen(
    firstFocus: FocusRequester,
    railFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .padding(start = 28.dp, end = 28.dp, top = 18.dp),
    ) {
        Text(stringResource(R.string.nav_downloads), style = GrokType.pageTitle, color = GrokWhite)
        Text(
            text = stringResource(R.string.downloads_subtitle),
            style = GrokType.heroMeta,
            color = GrokMuted,
            modifier = Modifier.padding(top = 4.dp),
        )
        EmptyState(
            title = stringResource(R.string.empty_downloads_title),
            body = stringResource(R.string.empty_downloads_body),
            modifier = Modifier.focusRequester(firstFocus),
        )
    }
}
