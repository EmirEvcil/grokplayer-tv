package com.grokplayer.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
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

val LocalFocusLock = staticCompositionLocalOf<MutableState<Boolean>> {
    mutableStateOf(false)
}

class BackHub {
    private val handlers = mutableListOf<() -> Boolean>()

    fun push(handler: () -> Boolean): () -> Unit {
        handlers.add(handler)
        return { handlers.remove(handler) }
    }

    fun dispatch(): Boolean {
        for (index in handlers.lastIndex downTo 0) {
            if (handlers[index].invoke()) return true
        }
        return false
    }
}

object AppBack {
    @Volatile
    var handler: (() -> Boolean)? = null
    @Volatile
    private var lastAt = 0L

    fun handle(): Boolean {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastAt < 280L) return true
        lastAt = now
        val current = handler
        if (current == null) {
            android.util.Log.e("GrokPlayer", "back ignored: handler missing")
            return true
        }
        return current.invoke()
    }
}

val LocalBackHub = staticCompositionLocalOf { BackHub() }

@Composable
fun InterceptBack(enabled: Boolean = true, onBack: () -> Boolean) {
    val hub = LocalBackHub.current
    val latest = rememberUpdatedState(onBack)
    DisposableEffect(enabled) {
        if (!enabled) return@DisposableEffect onDispose { }
        val pop = hub.push { latest.value.invoke() }
        onDispose(pop)
    }
}

@Composable
fun RememberFocusLock() {
    val lock = LocalFocusLock.current
    DisposableEffect(Unit) {
        lock.value = true
        onDispose { lock.value = false }
    }
}

@Composable
fun GrokPlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = TvColors) {
        CompositionLocalProvider(content = content)
    }
}
