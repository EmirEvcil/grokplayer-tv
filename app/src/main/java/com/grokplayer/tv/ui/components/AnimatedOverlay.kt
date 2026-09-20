package com.grokplayer.tv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.runtime.Composable

@Composable
fun <T> AnimatedOverlay(
    value: T?,
    content: @Composable (T) -> Unit,
) {
    AnimatedVisibility(
        visible = value != null,
        enter = fadeIn(tween(160, easing = FastOutSlowInEasing)) +
            scaleIn(initialScale = 0.96f, animationSpec = tween(180, easing = FastOutSlowInEasing)),
        exit = fadeOut(tween(80, easing = FastOutSlowInEasing)),
    ) {
        value?.let { content(it) }
    }
}
