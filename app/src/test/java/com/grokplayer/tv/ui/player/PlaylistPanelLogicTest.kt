package com.grokplayer.tv.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistPanelLogicTest {
    @Test
    fun focusSkipsTheSeparator() {
        val labels = listOf("Sonra oynat", "Sıraya ekle", "—", "İzleme listesine ekle", "Beğendim")
        assertEquals(3, actionNeighbor(labels, 1, 1))
        assertEquals(1, actionNeighbor(labels, 3, -1))
        assertNull(actionNeighbor(labels, 0, -1))
        assertNull(actionNeighbor(labels, 2, 1))
    }
}
