package com.grokplayer.tv.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.grokplayer.tv.data.MediaProbe
import com.grokplayer.tv.data.posterUsesNetwork
import com.grokplayer.tv.data.ThumbnailCache
import com.grokplayer.tv.data.formatClock
import com.grokplayer.tv.data.scan.YouTubeResolver
import com.grokplayer.tv.ui.theme.GrokInk
import com.grokplayer.tv.ui.theme.GrokMuted
import com.grokplayer.tv.ui.theme.GrokPink
import com.grokplayer.tv.ui.theme.GrokSurface
import com.grokplayer.tv.ui.theme.GrokType
import com.grokplayer.tv.ui.theme.GrokWhite
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
    posterUrl: String? = null,
    timeMs: Long = 1_000L,
    maxWidth: Int = ThumbnailCache.TILE_WIDTH,
    durationMs: Long = 0L,
    isLive: Boolean = false,
    enablePreview: Boolean = false,
    previewWithoutFocus: Boolean = false,
    originUrl: String? = null,
    referer: String? = null,
    userAgent: String? = null,
    captionOverlay: Boolean = false,
    overlay: @Composable (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(8.dp)
    val cacheKey = path ?: uri.toString()
    var poster by remember(cacheKey, maxWidth) { mutableStateOf<File?>(null) }
    var failed by remember(cacheKey, maxWidth) { mutableStateOf(false) }
    var duration by remember(cacheKey) { mutableLongStateOf(durationMs) }
    var preview by remember { mutableStateOf(false) }
    val ytId = YouTubeResolver.videoId(originUrl.orEmpty()) ?: YouTubeResolver.videoId(uri.toString())
    val artwork = if (posterUsesNetwork(path)) {
        posterUrl?.takeIf { it.startsWith("http") } ?: ytId?.let { YouTubeResolver.posterUrl(it) }
    } else {
        null
    }

    LaunchedEffect(cacheKey, maxWidth, timeMs, artwork) {
        if (artwork != null) return@LaunchedEffect
        var attempt = 0
        while (isActive && poster == null) {
            val cached = withContext(Dispatchers.IO) { ThumbnailCache.existing(context, cacheKey, maxWidth) }
            if (cached != null) {
                poster = cached
                failed = false
                return@LaunchedEffect
            }
            if (ThumbnailCache.playbackActive) {
                attempt++
                delay(400)
                continue
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

    LaunchedEffect(cacheKey, durationMs, isLive) {
        if (durationMs > 0L) {
            duration = durationMs
            return@LaunchedEffect
        }
        if (isLive) return@LaunchedEffect
        val raw = uri.toString()
        if (YouTubeResolver.videoId(raw) != null || YouTubeResolver.videoId(originUrl.orEmpty()) != null) {
            return@LaunchedEffect
        }
        duration = withContext(Dispatchers.IO) { MediaProbe.durationMs(context, uri, path) }
    }

    LaunchedEffect(focused, enablePreview, previewWithoutFocus, cacheKey) {
        preview = false
        if (!enablePreview || (!focused && !previewWithoutFocus)) return@LaunchedEffect
        delay(if (previewWithoutFocus) 800 else 500)
        preview = true
    }

    Box(
        modifier
            .clip(shape)
            .background(GrokSurface)
            .then(if (focused) Modifier.border(2.dp, GrokYellow, shape) else Modifier),
    ) {
        if (artwork != null || poster != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(artwork ?: poster)
                    .crossfade(false)
                    .build(),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onSuccess = { failed = false },
                onError = { failed = true },
            )
        }
        if (artwork == null && (failed || poster == null)) {
            Text(
                text = format?.ifBlank { null } ?: title.take(1).uppercase().ifBlank { "V" },
                style = GrokType.section,
                color = GrokMuted,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        if (preview) {
            CardPreview(
                uri = uri,
                originUrl = originUrl,
                isLive = isLive,
                format = format,
                referer = referer,
                userAgent = userAgent,
                modifier = Modifier.fillMaxSize(),
            )
        }
        overlay?.invoke()
        if (captionOverlay && title.isNotBlank()) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.35f to GrokInk.copy(alpha = 0.55f),
                            1f to GrokInk.copy(alpha = 0.92f),
                        ),
                    )
                    .padding(start = 8.dp, end = 8.dp, top = 28.dp, bottom = 8.dp),
            ) {
                Text(
                    title,
                    style = GrokType.cardTitle,
                    color = GrokWhite,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 52.dp),
                )
            }
        }
        if (isLive) {
            Text(
                "CANLI",
                style = GrokType.cardMeta,
                color = GrokWhite,
                modifier = Modifier
                    .align(if (captionOverlay) Alignment.TopStart else Alignment.BottomStart)
                    .padding(8.dp)
                    .background(GrokPink, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        } else if (duration > 0L) {
            Text(
                duration.formatClock(),
                style = GrokType.cardMeta,
                color = GrokWhite,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(GrokInk.copy(alpha = 0.72f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        if (progress != null && !isLive) {
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
                    .background(GrokPink)
                    .testTag("watch-progress"),
            )
        }
    }
}
