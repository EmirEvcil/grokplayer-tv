package com.grokplayer.tv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.grokplayer.tv.data.LibraryVideo
import com.grokplayer.tv.data.formatClock
import com.grokplayer.tv.ui.theme.GrokMuted
import com.grokplayer.tv.ui.theme.GrokType
import com.grokplayer.tv.ui.theme.GrokWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun VideoDetailsBody(video: LibraryVideo) {
    val context = LocalContext.current
    var details by remember { mutableStateOf<com.grokplayer.tv.data.MediaDetails?>(null) }
    LaunchedEffect(video.id) {
        details = withContext(Dispatchers.IO) {
            com.grokplayer.tv.data.MediaProbe.details(context, video.uri, video.path)
        }
    }
    val duration = (details?.durationMs?.takeIf { it > 0L } ?: video.durationMs)
    DetailRow("Süre", if (duration > 0L) duration.formatClock() else "—")
    DetailRow("Çözünürlük", details?.resolution ?: "…")
    DetailRow("Kare hızı", details?.fpsLabel ?: "…")
    video.path?.let { DetailRow("Dosya", it.substringAfterLast('/')) }
    val origin = video.originUrl
    if (origin != null && video.path == null) {
        DetailRow("Kaynak", origin.take(48) + if (origin.length > 48) "…" else "")
    }
    Spacer(Modifier.padding(top = 8.dp))
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = GrokType.cardMeta, color = GrokMuted)
        Text(value, style = GrokType.cardTitle, color = GrokWhite, modifier = Modifier.padding(start = 16.dp))
    }
}
