package com.grokplayer.tv.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import com.grokplayer.tv.ui.theme.GrokSurface
import com.grokplayer.tv.ui.theme.GrokType
import com.grokplayer.tv.ui.theme.GrokWhite
import com.grokplayer.tv.ui.theme.GrokYellow
import com.grokplayer.tv.ui.theme.InterceptBack
import com.grokplayer.tv.ui.theme.RememberFocusLock

data class ModalAction(val label: String, val onClick: () -> Unit)

@Composable
fun ModalMenu(
    title: String,
    onDismiss: () -> Unit,
    actions: List<ModalAction>,
    meta: String? = null,
    width: androidx.compose.ui.unit.Dp = 360.dp,
) {
    RememberFocusLock()
    InterceptBack { onDismiss(); true }
    BackHandler(onBack = onDismiss)
    val keys = remember(actions.size) { List(actions.size.coerceAtLeast(1)) { FocusRequester() } }
    AbsorbOpeningOk {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(GrokInk.copy(alpha = 0.55f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onDismiss,
                    )
                    .focusProperties {
                        canFocus = false
                        onEnter = { FocusRequester.Cancel }
                    },
            )
            Column(
                Modifier
                    .width(width)
                    .background(GrokSurface, RoundedCornerShape(12.dp))
                    .border(1.dp, GrokYellow.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                    .padding(16.dp),
            ) {
                Text(title, style = GrokType.section, color = GrokWhite)
                if (!meta.isNullOrBlank()) {
                    Text(
                        meta,
                        style = GrokType.cardMeta,
                        color = GrokMuted,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                    )
                } else {
                    androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
                }
                actions.forEachIndexed { index, action ->
                    val last = actions.lastIndex
                    val interaction = remember(action.label) { MutableInteractionSource() }
                    val focused = interaction.collectIsFocusedAsState().value
                    Text(
                        text = action.label,
                        style = GrokType.button,
                        color = if (focused) GrokInk else GrokWhite,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(keys[index])
                            .focusProperties {
                                left = FocusRequester.Cancel
                                right = FocusRequester.Cancel
                                up = keys[if (index == 0) 0 else index - 1]
                                down = keys[if (index == last) last else index + 1]
                            }
                            .background(if (focused) GrokYellow else Color.Transparent, RoundedCornerShape(6.dp))
                            .clickable(interactionSource = interaction, indication = null, onClick = action.onClick)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
    LaunchedEffect(title, actions.size) {
        repeat(8) {
            kotlinx.coroutines.delay(40)
            runCatching { keys.first().requestFocus() }
        }
    }
}

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
    var focused by remember { mutableStateOf(false) }
    var longFired by remember { mutableStateOf(false) }
    var suppressClick by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
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
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .wrapContentWidth()
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
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
