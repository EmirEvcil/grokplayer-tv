package com.grokplayer.tv.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.grokplayer.tv.data.ThumbnailCache
import com.grokplayer.tv.ui.theme.GrokMuted
import com.grokplayer.tv.ui.theme.GrokPink
import com.grokplayer.tv.ui.theme.GrokSurface
import com.grokplayer.tv.ui.theme.GrokType
import com.grokplayer.tv.ui.theme.GrokYellow
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@Composable
fun VideoPoster(
    uri: Uri,
    title: String,
    focused: Boolean,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    path: String? = null,
    format: String? = null,
    timeMs: Long = 1_000L,
    maxWidth: Int = ThumbnailCache.TILE_WIDTH,
    overlay: @Composable (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(8.dp)
    val cacheKey = path ?: uri.toString()
    var poster by remember(cacheKey, maxWidth) { mutableStateOf<File?>(null) }
    var failed by remember(cacheKey, maxWidth) { mutableStateOf(false) }

    LaunchedEffect(cacheKey, maxWidth, timeMs) {
        var attempt = 0
        while (isActive && poster == null) {
            while (ThumbnailCache.playbackActive && isActive) delay(400)
            val cached = withContext(Dispatchers.IO) { ThumbnailCache.existing(context, cacheKey, maxWidth) }
            if (cached != null) {
                poster = cached
                failed = false
                return@LaunchedEffect
            }
            val extracted = withContext(Dispatchers.IO) {
                ThumbnailCache.extract(context, cacheKey, uri, path, timeMs = timeMs, maxWidth = maxWidth)
            }
            if (extracted != null) {
                poster = extracted
                failed = false
                return@LaunchedEffect
            }
            attempt++
            delay(if (attempt < 8) 400L else 1_200L)
        }
        failed = poster == null
    }

    Box(
        modifier
            .clip(shape)
            .background(GrokSurface)
            .then(if (focused) Modifier.border(2.dp, GrokYellow, shape) else Modifier),
    ) {
        if (poster != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(poster)
                    .crossfade(false)
                    .build(),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onSuccess = { failed = false },
                onError = { failed = true },
            )
        }
        if (failed || poster == null) {
            Text(
                text = format?.ifBlank { null } ?: title.take(1).uppercase().ifBlank { "V" },
                style = GrokType.section,
                color = GrokMuted,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        overlay?.invoke()
        if (progress != null) {
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
                    .fillMaxWidth(progress.coerceIn(0.08f, 1f))
                    .height(3.dp)
                    .background(GrokPink),
            )
        }
    }
}
