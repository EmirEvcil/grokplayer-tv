package com.grokplayer.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KeyGateTest {
    @Before
    fun resetGate() {
        KeyGate.reset()
        KeyGate.nowMs = { System.currentTimeMillis() }
    }

    @Test
    fun leftoverOkIsEatenUntilKeyUp() {
        KeyGate.arm()
        assertTrue(KeyGate.armed())
        assertTrue(KeyGate.onOk(down = true, up = false))
        assertTrue(KeyGate.armed())
        assertTrue(KeyGate.onOk(down = false, up = true))
        assertFalse(KeyGate.armed())
        assertFalse(KeyGate.onOk(down = true, up = false))
        assertFalse(KeyGate.onOk(down = false, up = true))
    }

    @Test
    fun nestedFocusLockStaysUntilLastPop() {
        val lock = FocusLock()
        lock.push()
        lock.push()
        assertTrue(lock.locked)
        lock.pop()
        assertTrue(lock.locked)
        lock.pop()
        assertFalse(lock.locked)
        lock.pop()
        assertEquals(0, lock.depth)
        lock.push()
        lock.reset()
        assertFalse(lock.locked)
        assertEquals(0, lock.depth)
    }

    @Test
    fun holdOkDoesNotResetAfterLongAlreadyFired() {
        assertFalse(HoldOk.resetLongOnRepeat0(longFired = true))
        assertTrue(HoldOk.resetLongOnRepeat0(longFired = false))
        assertFalse(HoldOk.clickOnUp(longFired = true, suppressClick = true))
        assertTrue(HoldOk.clickOnUp(longFired = false, suppressClick = false))
    }

    @Test
    fun holdFlagClearsWhenTileLosesFocusSoNextOkWorks() {
        assertFalse(HoldOk.keepLongFired(focused = false, longFired = true))
        assertTrue(HoldOk.keepLongFired(focused = true, longFired = true))
        assertTrue(HoldOk.resetLongOnRepeat0(longFired = false))
        assertTrue(HoldOk.clickOnUp(longFired = false, suppressClick = false))
    }

    @Test
    fun modalActionFiresOnceOnKeyDownAndIgnoresKeyUpAndClickable() {
        var t = 0L
        val once = OkAction(windowMs = 80L, clock = { t })
        var runs = 0
        assertTrue(ModalOk.shouldFire(down = true, repeatCount = 0))
        assertTrue(once.fire { runs++ })
        assertFalse(once.fire { runs++ })
        assertFalse(ModalOk.shouldFire(down = true, repeatCount = 1))
        assertFalse(ModalOk.shouldFire(down = false, repeatCount = 0))
        assertEquals(1, runs)
        assertTrue(KeyGate.armed())
    }

    @Test
    fun detailsCanOpenAgainAfterClose() {
        var t = 0L
        val once = OkAction(windowMs = 80L, clock = { t })
        var opens = 0
        once.fire { opens++ }
        t = 100L
        KeyGate.reset()
        assertTrue(once.fire { opens++ })
        assertEquals(2, opens)
    }

    @Test
    fun leftoverGateExpiresSoNextShortOkWorks() {
        var t = 0L
        KeyGate.nowMs = { t }
        KeyGate.arm()
        t = KeyGate.TTL_MS + 1
        assertFalse(KeyGate.onOk(down = true, up = false, repeatCount = 0))
        assertFalse(KeyGate.armed())
        var opened = 0
        val once = OkAction(windowMs = 80L, clock = { t })
        assertTrue(once.fire { opened++ })
        assertEquals(1, opened)
    }

    @Test
    fun leftoverHoldRepeatsStayEatenUntilKeyUp() {
        var t = 0L
        KeyGate.nowMs = { t }
        KeyGate.arm()
        t = KeyGate.TTL_MS + 1
        assertTrue(KeyGate.onOk(down = true, up = false, repeatCount = 4))
        assertTrue(KeyGate.armed())
        assertTrue(KeyGate.onOk(down = false, up = true))
        assertFalse(KeyGate.armed())
    }

    @Test
    fun leftoverHoldDoesNotFireLongOnNewlyFocusedTile() {
        assertFalse(HoldOk.fireLongFromRepeat(holdStarted = false))
        assertTrue(HoldOk.fireLongFromRepeat(holdStarted = true))
    }

    @Test
    fun addCollectionBackReturnsToOptionsNotClosed() {
        val root = NestedMenu.pop(listOf("pickCollection"))
        assertTrue(root.isEmpty())
        assertFalse(NestedMenu.staysOpen(root))
        val nested = NestedMenu.pop(listOf("pickCollection", "name"))
        assertEquals(listOf("pickCollection"), nested)
        assertTrue(NestedMenu.staysOpen(nested))
    }

    @Test
    fun imeBackHidesKeyboardBeforeLeavingNamePage() {
        assertTrue(ImeBack.shouldHide(imeOpen = true, alreadyHid = false))
        assertFalse(ImeBack.shouldHide(imeOpen = true, alreadyHid = true))
        assertFalse(ImeBack.shouldHide(imeOpen = false, alreadyHid = false))
    }

    @Test
    fun menuFocusRestoresLastRowNotFirst() {
        assertEquals(3, MenuFocus.restore(3, 6, 0))
        assertEquals(0, MenuFocus.restore(null, 6, 0))
        assertEquals(5, MenuFocus.restore(99, 6, 0))
        assertEquals(0, MenuFocus.restore(2, 0, 0))
    }

    @Test
    fun detailsAndAddStayInsideSameMenuStack() {
        val fromDetails = NestedMenu.pop(listOf("root", "details"))
        assertEquals(listOf("root"), fromDetails)
        assertTrue(NestedMenu.staysOpen(fromDetails))
        val fromAdd = NestedMenu.pop(listOf("root", "pickCollection"))
        assertEquals(listOf("root"), fromAdd)
        val fromName = NestedMenu.pop(listOf("root", "pickCollection", "name"))
        assertEquals(listOf("root", "pickCollection"), fromName)
    }

    @Test
    fun deleteCollectionDoesNotEnterNeighborOnLeftoverKeyUp() {
        val keys = mutableListOf("alpha", "beta", "gamma")
        var anchor = FocusAnchor().remember("beta", keys)
        var deleted = 0
        var entered: String? = null
        val sil = OkAction()
        sil.fire {
            deleted++
            keys.remove("beta")
        }
        sil.fire { deleted++ }
        assertEquals(1, deleted)
        val neighbor = anchor.resolve(keys)
        assertEquals("gamma", neighbor)
        val leaked = !KeyGate.onOk(down = false, up = true)
        if (leaked) entered = neighbor
        assertNull(entered)
        assertFalse(KeyGate.armed())
    }

    @Test
    fun detailsCloseKeepsOptionsLockAndEatsLeftoverOk() {
        val lock = FocusLock()
        lock.push()
        lock.push()
        val close = OkAction()
        close.fire { lock.pop() }
        assertTrue(lock.locked)
        assertEquals(1, lock.depth)
        assertTrue(KeyGate.onOk(down = false, up = true))
        assertFalse(KeyGate.armed())
    }

    @Test
    fun addToCollectionRestorePicksSameVideoAfterPickerCloses() {
        val ids = listOf("v1", "v2", "v3")
        assertNull(OverlayRestore.targetId(overlayOpen = true, remembered = "v2", ids = ids))
        assertEquals("v2", OverlayRestore.targetId(overlayOpen = false, remembered = "v2", ids = ids))
        assertEquals("v1", OverlayRestore.targetId(overlayOpen = false, remembered = "gone", ids = ids))
    }

    @Test
    fun streamsRestoreKeepsLastIdAfterMenuBack() {
        val ids = listOf("s1", "s2", "s3")
        assertEquals("s3", OverlayRestore.targetId(false, "s3", ids))
        assertFalse(KeyGate.armed())
        assertFalse(KeyGate.onOk(down = true, up = false))
    }

    @Test
    fun absorbOpeningStaysArmedOnlyWhenGateIsClear() {
        assertTrue(AbsorbOk.startArmed(enabled = true, gateArmed = false))
        assertFalse(AbsorbOk.startArmed(enabled = true, gateArmed = true))
        assertFalse(AbsorbOk.startArmed(enabled = false, gateArmed = false))
    }

    @Test
    fun absorbPassesAFreshOkThroughSoPickerCanBeUsed() {
        assertTrue(AbsorbOk.passThroughNewPress(down = true, repeatCount = 0))
        assertFalse(AbsorbOk.passThroughNewPress(down = true, repeatCount = 1))
        assertFalse(AbsorbOk.passThroughNewPress(down = false, repeatCount = 0))
    }

    @Test
    fun holdOkMustNotArmGateOrFirstMenuOkIsEaten() {
        assertFalse(KeyGate.armed())
        assertTrue(AbsorbOk.startArmed(enabled = true, gateArmed = KeyGate.armed()))
    }

    @Test
    fun backMustNotArmGateOrNextOkIsEaten() {
        assertFalse(KeyGate.armed())
        assertFalse(KeyGate.onOk(down = true, up = false))
        assertFalse(KeyGate.onOk(down = false, up = true))
    }

    @Test
    fun unknownEventTypeDoesNotDisarmGate() {
        KeyGate.arm()
        assertFalse(KeyGate.onOk(down = false, up = false))
        assertTrue(KeyGate.armed())
        assertTrue(KeyGate.onOk(down = false, up = true))
        assertFalse(KeyGate.armed())
    }
}
