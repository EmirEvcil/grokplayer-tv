package com.grokplayer.tv.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.grokplayer.tv.ui.theme.GrokPink
import com.grokplayer.tv.ui.theme.GrokSurface
import com.grokplayer.tv.ui.theme.GrokYellow

@Composable
fun VideoPoster(
    uri: Uri,
    title: String,
    focused: Boolean,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    overlay: @Composable (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .clip(shape)
            .background(GrokSurface)
            .then(if (focused) Modifier.border(2.dp, GrokYellow, shape) else Modifier),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(uri)
                .decoderFactory(VideoFrameDecoder.Factory())
                .videoFrameMillis(200)
                .crossfade(true)
                .build(),
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
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
