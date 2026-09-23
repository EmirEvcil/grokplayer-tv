package com.grokplayer.tv.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ModalMenuTest {
    @Test
    fun viewportShowsAtMostFourRows() {
        assertEquals(0, modalListMaxRows(0))
        assertEquals(3, modalListMaxRows(3))
        assertEquals(4, modalListMaxRows(4))
        assertEquals(4, modalListMaxRows(12))
        assertEquals(4, MODAL_VISIBLE_ACTIONS)
    }
}
