package com.grokplayer.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val TvColors = darkColorScheme(
    primary = GrokYellow,
    onPrimary = GrokInk,
    secondary = GrokPink,
    background = GrokInk,
    onBackground = GrokWhite,
    surface = GrokSurface,
    onSurface = GrokWhite,
    border = GrokLine,
)

val LocalPlaceholderAction = staticCompositionLocalOf<(String) -> Unit> { {} }

@Composable
fun GrokPlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = TvColors) {
        CompositionLocalProvider(content = content)
    }
}
