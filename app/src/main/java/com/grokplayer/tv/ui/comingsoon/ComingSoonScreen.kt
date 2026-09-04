package com.grokplayer.tv.ui.comingsoon

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.grokplayer.tv.R
import com.grokplayer.tv.ui.theme.GrokMuted
import com.grokplayer.tv.ui.theme.GrokType
import com.grokplayer.tv.ui.theme.GrokWhite

@Composable
fun ComingSoonScreen(
    title: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 36.dp, end = 48.dp, top = 72.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(text = title, style = GrokType.comingTitle, color = GrokWhite)
        Text(
            text = stringResource(R.string.coming_soon),
            style = GrokType.section,
            color = GrokMuted,
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            text = stringResource(R.string.coming_soon_body),
            style = GrokType.comingBody,
            color = GrokMuted,
            modifier = Modifier
                .padding(top = 14.dp)
                .widthIn(max = 420.dp),
        )
    }
}
