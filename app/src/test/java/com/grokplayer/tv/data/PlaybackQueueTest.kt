package com.grokplayer.tv.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackQueueTest {
    @Test
    fun playNextInsertsAfterTheCurrentAndMovesAnExistingEntry() {
        assertEquals(listOf("a", "b", "c"), queueAddNext(listOf("a", "c"), "a", "b"))
        assertEquals(listOf("a", "c", "b"), queueAddNext(listOf("a", "b", "c"), "a", "c"))
    }

    @Test
    fun nextAndPreviousSkipALiveNeighborAndStopAtTheEnds() {
        val playable = listOf(true, true, false)
        assertEquals(1, nextPlayable(0, playable.lastIndex) { playable[it] })
        assertEquals(null, nextPlayable(1, playable.lastIndex) { playable[it] })
        assertEquals(0, previousPlayable(1, playable.lastIndex) { playable[it] })
        assertEquals(null, previousPlayable(0, playable.lastIndex) { playable[it] })
    }

    @Test
    fun playlistPlayNextInsertsACopyAndKeepsTheOldOrder() {
        val order = insertCopyAfter(listOf("a", "b", "c", "d"), "b", "d") { left, right -> left == right }
        assertEquals(listOf("a", "b", "d", "c", "d"), order)
    }

    @Test
    fun queuePlayNextMovesTheVideoToJustAfterTheCurrentOne() {
        assertEquals(listOf("a", "b", "d", "c"), queueAddNext(listOf("a", "b", "c", "d"), "b", "d"))
    }

    @Test
    fun moveItemStepsOnePlaceAtATime() {
        assertEquals(listOf("a", "c", "b", "d"), moveItem(listOf("a", "b", "c", "d"), 2, 1))
        assertEquals(listOf("a", "c", "b", "d"), moveItem(listOf("a", "b", "c", "d"), 1, 2))
    }

    @Test
    fun playNextAppendsWhenNothingIsCurrent() {
        assertEquals(listOf("a", "b"), queueAddNext(listOf("a"), null, "b"))
    }

    @Test
    fun insertAfterThePlayingIndexKeepsAnEarlierCopy() {
        val order = insertCopyAfterIndex(listOf("a", "b", "d", "c", "d"), 2, "c")
        assertEquals(listOf("a", "b", "d", "c", "c", "d"), order)
        assertEquals(4, indexAfterInsert(3, 3))
        assertEquals(1, indexAfterInsert(1, 3))
    }

    @Test
    fun queuePlayNextReportsTheNewIndex() {
        assertEquals(2, queuePlayNextIndex(listOf("a", "b", "c", "d"), "b", "d"))
    }
}
