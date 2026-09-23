package com.grokplayer.tv.data

/**
 * Eats leftover Center/Enter after a modal action so the KeyUp cannot
 * activate whatever just gained focus underneath.
 *
 * Arm on the action's KeyDown (before the overlay is removed). The shell
 * then consumes this press's KeyUp so the newly focused row cannot click.
 */
object KeyGate {
    @Volatile
    private var eatOk = false
    @Volatile
    private var armedAt = 0L
    var nowMs: () -> Long = { System.currentTimeMillis() }
    const val TTL_MS = 250L

    fun arm() {
        eatOk = true
        armedAt = nowMs()
    }

    fun reset() {
        eatOk = false
        armedAt = 0L
    }

    fun armed(): Boolean = eatOk

    fun onOk(down: Boolean, up: Boolean, repeatCount: Int = 0): Boolean {
        if (!eatOk) return false
        if (up) {
            eatOk = false
            return true
        }
        if (down && repeatCount == 0 && nowMs() - armedAt > TTL_MS) {
            eatOk = false
            return false
        }
        return down
    }
}

/**
 * Dedupes clickable KeyDown and onPreviewKeyEvent on the SAME press.
 * After [windowMs] a later press (details -> close -> details) can fire again.
 */
class OkAction(
    private val windowMs: Long = 80L,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    @Volatile
    private var lastAt: Long? = null

    fun fire(block: () -> Unit): Boolean {
        val now = clock()
        val previous = lastAt
        if (previous != null && now - previous < windowMs) return false
        lastAt = now
        KeyGate.arm()
        block()
        return true
    }
}

object NestedMenu {
    fun <T> pop(stack: List<T>): List<T> =
        if (stack.size <= 1) emptyList() else stack.dropLast(1)

    fun staysOpen(stack: List<*>): Boolean = stack.isNotEmpty()
}

object ModalOk {
    fun shouldFire(down: Boolean, repeatCount: Int): Boolean = down && repeatCount == 0
}

object AbsorbOk {
    fun startArmed(enabled: Boolean, gateArmed: Boolean): Boolean = enabled && !gateArmed

    fun passThroughNewPress(down: Boolean, repeatCount: Int): Boolean = down && repeatCount == 0
}

class FocusLock {
    var depth: Int = 0
        private set
    val locked: Boolean get() = depth > 0
    fun push() {
        depth += 1
    }
    fun pop() {
        depth = (depth - 1).coerceAtLeast(0)
    }

    fun reset() {
        depth = 0
    }
}

object HoldOk {
    fun resetLongOnRepeat0(longFired: Boolean): Boolean = !longFired
    fun clickOnUp(longFired: Boolean, suppressClick: Boolean): Boolean = !longFired && !suppressClick
    fun keepLongFired(focused: Boolean, longFired: Boolean): Boolean = focused && longFired
    fun fireLongFromRepeat(holdStarted: Boolean): Boolean = holdStarted
}

object OverlayRestore {
    fun targetId(overlayOpen: Boolean, remembered: String?, ids: List<String>): String? {
        if (overlayOpen) return null
        if (remembered != null && remembered in ids) return remembered
        return ids.firstOrNull()
    }

    fun unlock(lock: FocusLock, overlayOpen: Boolean) {
        if (!overlayOpen && lock.locked) lock.reset()
    }
}

object MenuFocus {
    fun restore(saved: Int?, count: Int, fallback: Int = 0): Int {
        if (count <= 0) return 0
        return (saved ?: fallback).coerceIn(0, count - 1)
    }
}

object ImeBack {
    fun shouldHide(imeOpen: Boolean, alreadyHid: Boolean): Boolean = imeOpen && !alreadyHid
}
