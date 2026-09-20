package com.grokplayer.tv.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
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
    startIndex: Int = 0,
    absorbOpeningOk: Boolean = true,
    focusNonce: Any? = null,
    trapFocus: Boolean = true,
    header: (@Composable (firstAction: FocusRequester) -> Unit)? = null,
    headerFocus: FocusRequester? = null,
    focusHeader: Boolean = false,
    onFocusedIndex: (Int) -> Unit = {},
    onBack: () -> Unit = onDismiss,
) {
    RememberFocusLock()
    InterceptBack {
        onBack()
        true
    }
    BackHandler(onBack = onBack)
    val keys = remember(focusNonce, actions.size) { List(actions.size.coerceAtLeast(1)) { FocusRequester() } }
    AbsorbOpeningOk(enabled = absorbOpeningOk) {
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
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .background(GrokSurface, RoundedCornerShape(12.dp))
                    .border(1.dp, GrokYellow.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                    .padding(16.dp)
                    .focusProperties {
                        if (trapFocus) {
                            left = FocusRequester.Cancel
                            right = FocusRequester.Cancel
                        }
                    },
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
                if (header != null && keys.isNotEmpty()) header(keys.first())
                actions.forEachIndexed { index, action ->
                    key(focusNonce, index, action.label) {
                    val last = actions.lastIndex
                    val interaction = remember(focusNonce, action.label, index) { MutableInteractionSource() }
                    val focused = interaction.collectIsFocusedAsState().value
                    val once = remember(action.label, index, focusNonce) { com.grokplayer.tv.data.OkAction() }
                    fun fire() {
                        once.fire { action.onClick() }
                    }
                    Text(
                        text = action.label,
                        style = GrokType.button,
                        color = if (focused) GrokInk else GrokWhite,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(keys[index])
                            .onFocusChanged { if (it.isFocused) onFocusedIndex(index) }
                            .focusProperties {
                                canFocus = trapFocus
                                if (!trapFocus) onEnter = { FocusRequester.Cancel }
                                left = FocusRequester.Cancel
                                right = FocusRequester.Cancel
                                up = when {
                                    index == 0 && headerFocus != null -> headerFocus
                                    index == 0 -> keys[0]
                                    else -> keys[index - 1]
                                }
                                down = keys[if (index == last) last else index + 1]
                            }
                            .background(if (focused) GrokYellow else Color.Transparent, RoundedCornerShape(6.dp))
                            .clickable(interactionSource = interaction, indication = null, onClick = { fire() })
                            .onPreviewKeyEvent { event ->
                                val ok = event.key == Key.DirectionCenter || event.key == Key.Enter
                                if (!ok) return@onPreviewKeyEvent false
                                if (event.type == KeyEventType.KeyDown) {
                                    if (com.grokplayer.tv.data.ModalOk.shouldFire(
                                            down = true,
                                            repeatCount = event.nativeKeyEvent.repeatCount,
                                        )
                                    ) {
                                        fire()
                                    }
                                }
                                true
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                    }
                }
            }
        }
    }
    LaunchedEffect(focusNonce, focusHeader) {
        if (!focusHeader || headerFocus == null) return@LaunchedEffect
        repeat(2) {
            if (runCatching { headerFocus.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
            kotlinx.coroutines.delay(40)
        }
    }
    LaunchedEffect(title, actions.size, startIndex, focusNonce) {
        if (focusHeader) return@LaunchedEffect
        val target = keys.getOrNull(startIndex.coerceIn(0, keys.lastIndex)) ?: keys.first()
        repeat(2) {
            if (runCatching { target.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
            kotlinx.coroutines.delay(40)
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
    val scope = rememberCoroutineScope()
    var focused by remember { mutableStateOf(false) }
    var longFired by remember { mutableStateOf(false) }
    var suppressClick by remember { mutableStateOf(false) }
    var holdJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun cancelHold() {
        holdJob?.cancel()
        holdJob = null
    }
    fun fireLong() {
        if (longFired) return
        cancelHold()
        longFired = true
        suppressClick = true
        onLongClick?.invoke()
    }
    Row(
        modifier = modifier
            .onFocusChanged { state ->
                focused = state.isFocused || state.hasFocus
                if (!com.grokplayer.tv.data.HoldOk.keepLongFired(focused, longFired) && (longFired || suppressClick || holdJob != null)) {
                    cancelHold()
                    longFired = false
                    suppressClick = false
                }
            }
            .clip(shape)
            .onPreviewKeyEvent { event ->
                val center = event.key == Key.DirectionCenter || event.key == Key.Enter
                if (center && com.grokplayer.tv.data.KeyGate.onOk(
                        down = event.type == KeyEventType.KeyDown,
                        up = event.type == KeyEventType.KeyUp,
                        repeatCount = event.nativeKeyEvent.repeatCount,
                    )
                ) {
                    return@onPreviewKeyEvent true
                }
                if (onLongClick == null) return@onPreviewKeyEvent false
                if (!center) return@onPreviewKeyEvent false
                val native = event.nativeKeyEvent
                val flaggedLong = native.repeatCount >= 1 &&
                    (native.flags and android.view.KeyEvent.FLAG_LONG_PRESS) != 0
                when (event.type) {
                    KeyEventType.KeyDown -> {
                        if (native.repeatCount == 0) {
                            if (com.grokplayer.tv.data.HoldOk.resetLongOnRepeat0(longFired)) {
                                cancelHold()
                                longFired = false
                                suppressClick = false
                                val timeout = android.view.ViewConfiguration.getLongPressTimeout().toLong()
                                    .coerceAtLeast(350L)
                                holdJob = scope.launch {
                                    kotlinx.coroutines.delay(timeout)
                                    fireLong()
                                }
                            }
                            return@onPreviewKeyEvent true
                        }
                        if ((flaggedLong || native.repeatCount >= 1) &&
                            com.grokplayer.tv.data.HoldOk.fireLongFromRepeat(holdJob != null || longFired)
                        ) {
                            fireLong()
                        }
                        true
                    }
                    KeyEventType.KeyUp -> {
                        val click = com.grokplayer.tv.data.HoldOk.clickOnUp(longFired, suppressClick)
                        cancelHold()
                        longFired = false
                        suppressClick = false
                        if (click) onClick()
                        true
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
                onLongClick = if (onLongClick == null) {
                    null
                } else {
                    {
                        if (!longFired) {
                            cancelHold()
                            longFired = true
                            suppressClick = true
                            onLongClick()
                        }
                    }
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
fun AbsorbOpeningOk(
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onConsumed: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    var armed by remember {
        mutableStateOf(
            com.grokplayer.tv.data.AbsorbOk.startArmed(
                enabled = enabled,
                gateArmed = com.grokplayer.tv.data.KeyGate.armed(),
            ),
        )
    }
    LaunchedEffect(enabled) {
        armed = com.grokplayer.tv.data.AbsorbOk.startArmed(
            enabled = enabled,
            gateArmed = com.grokplayer.tv.data.KeyGate.armed(),
        )
    }
    Box(
        modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (!enabled || !armed) return@onPreviewKeyEvent false
                val ok = event.key == Key.DirectionCenter || event.key == Key.Enter
                if (!ok) return@onPreviewKeyEvent false
                val down = event.type == KeyEventType.KeyDown
                val up = event.type == KeyEventType.KeyUp
                if (com.grokplayer.tv.data.AbsorbOk.passThroughNewPress(
                        down = down,
                        repeatCount = event.nativeKeyEvent.repeatCount,
                    )
                ) {
                    armed = false
                    return@onPreviewKeyEvent false
                }
                if (up) {
                    armed = false
                    onConsumed()
                }
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
