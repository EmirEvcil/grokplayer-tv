package com.grokplayer.tv.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.grokplayer.tv.ui.theme.GrokInk
import com.grokplayer.tv.ui.theme.GrokLine
import com.grokplayer.tv.ui.theme.GrokMuted
import com.grokplayer.tv.ui.theme.GrokPink
import com.grokplayer.tv.ui.theme.GrokSoft
import com.grokplayer.tv.ui.theme.GrokType
import com.grokplayer.tv.ui.theme.GrokWhite
import com.grokplayer.tv.ui.theme.GrokYellow

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FocusableAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    shape: RoundedCornerShape = RoundedCornerShape(8.dp),
    content: @Composable RowScope.(focused: Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused = interaction.collectIsFocusedAsState().value
    var longFired by remember { mutableStateOf(false) }
    var suppressClick by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .clip(shape)
            .onPreviewKeyEvent { event ->
                if (onLongClick == null) return@onPreviewKeyEvent false
                val center = event.key == Key.DirectionCenter || event.key == Key.Enter
                if (!center) return@onPreviewKeyEvent false
                when (event.type) {
                    KeyEventType.KeyDown -> {
                        val repeat = event.nativeKeyEvent.repeatCount
                        if (repeat == 0) {
                            longFired = false
                            false
                        } else if (!longFired) {
                            longFired = true
                            suppressClick = true
                            onLongClick()
                            true
                        } else {
                            true
                        }
                    }
                    KeyEventType.KeyUp -> {
                        if (longFired || suppressClick) {
                            longFired = false
                            suppressClick = true
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = {
                    if (suppressClick) {
                        suppressClick = false
                    } else {
                        onClick()
                    }
                },
                onLongClick = {
                    suppressClick = true
                    onLongClick?.invoke()
                },
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = { content(focused) },
    )
}

@Composable
fun OutlineButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val shape = RoundedCornerShape(8.dp)
    FocusableAction(onClick = onClick, modifier = modifier, shape = shape) { focused ->
        val fill = if (focused) GrokYellow else Color.Transparent
        val stroke = if (focused) GrokYellow else GrokLine
        Row(
            modifier = Modifier
                .border(1.5.dp, stroke, shape)
                .background(fill, shape)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (focused) GrokInk else GrokWhite,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = label,
                style = GrokType.button,
                color = if (focused) GrokInk else GrokWhite,
            )
        }
    }
}

@Composable
fun FilledFocusButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val shape = RoundedCornerShape(8.dp)
    FocusableAction(onClick = onClick, modifier = modifier, shape = shape) { focused ->
        Row(
            modifier = Modifier
                .background(if (focused) GrokYellow else Color.Transparent, shape)
                .border(1.5.dp, if (focused) GrokYellow else GrokLine, shape)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (focused) GrokInk else GrokWhite,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = label,
                style = GrokType.button,
                color = if (focused) GrokInk else GrokWhite,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused = interaction.collectIsFocusedAsState().value
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .wrapContentWidth()
            .clip(shape)
            .then(if (focused) Modifier.border(1.5.dp, GrokYellow, shape) else Modifier)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = GrokType.button,
            color = if (selected || focused) GrokWhite else GrokMuted,
        )
        Box(
            Modifier
                .padding(top = 4.dp)
                .height(2.dp)
                .width(22.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(if (selected) GrokPink else Color.Transparent),
        )
    }
}

@Composable
fun MediaPoster(
    @DrawableRes artwork: Int,
    title: String,
    focused: Boolean,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .clip(shape)
            .then(if (focused) Modifier.border(2.dp, GrokYellow, shape) else Modifier),
    ) {
        Image(
            painter = painterResource(artwork),
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        overlay()
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

@Composable
fun AbsorbOpeningOk(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var armed by remember { mutableStateOf(true) }
    Box(
        modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
            val ok = event.key == Key.DirectionCenter || event.key == Key.Enter
            if (!armed || !ok) return@onPreviewKeyEvent false
            if (event.type == KeyEventType.KeyUp) armed = false
            true
        },
    ) {
        content()
    }
}

@Composable
fun HintBar(parts: List<String>, modifier: Modifier = Modifier) {
    Text(
        text = parts.joinToString("     "),
        style = GrokType.cardMeta,
        color = GrokSoft,
        modifier = modifier,
    )
}
