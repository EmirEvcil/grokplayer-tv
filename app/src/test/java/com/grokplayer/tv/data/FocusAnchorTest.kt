package com.grokplayer.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FocusAnchorTest {
    @Test
    fun restoresTheSameKey() {
        val keys = listOf("a", "b", "c")
        val anchor = FocusAnchor().remember("b", keys)
        assertEquals("b", anchor.resolve(keys))
        assertEquals(1, anchor.index)
    }

    @Test
    fun afterRemovingFocusedItemKeepsNeighborIndex() {
        val before = listOf("a", "b", "c", "d")
        val after = listOf("a", "c", "d")
        val anchor = FocusAnchor().remember("b", before)
        assertEquals("c", anchor.resolve(after))
    }

    @Test
    fun afterRemovingLastItemFallsBackToNewLast() {
        val before = listOf("a", "b", "c")
        val after = listOf("a", "b")
        val anchor = FocusAnchor().remember("c", before)
        assertEquals("b", anchor.resolve(after))
    }

    @Test
    fun afterRemovingFirstItemStaysAtIndexZero() {
        val before = listOf("a", "b", "c")
        val after = listOf("b", "c")
        val anchor = FocusAnchor().remember("a", before)
        assertEquals("b", anchor.resolve(after))
    }

    @Test
    fun emptyListReturnsNull() {
        assertNull(FocusAnchor("x", 2).resolve(emptyList()))
    }

    @Test
    fun unknownKeyUsesStoredIndex() {
        val keys = listOf("one", "two", "three")
        assertEquals("three", FocusAnchor("gone", 2).resolve(keys))
    }

    @Test
    fun indexIsClamped() {
        val keys = listOf("only")
        assertEquals("only", FocusAnchor("gone", 40).resolve(keys))
    }
}
