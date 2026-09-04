package com.grokplayer.tv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.grokplayer.tv.ui.theme.GrokMuted
import com.grokplayer.tv.ui.theme.GrokType
import com.grokplayer.tv.ui.theme.GrokWhite

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = title, style = GrokType.section, color = GrokWhite)
        Text(
            text = body,
            style = GrokType.comingBody,
            color = GrokMuted,
            modifier = Modifier.widthIn(max = 460.dp),
        )
    }
}
