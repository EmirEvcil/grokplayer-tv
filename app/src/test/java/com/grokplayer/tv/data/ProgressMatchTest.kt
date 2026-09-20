package com.grokplayer.tv.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressMatchTest {
    @Test
    fun titles_match_inbox_suffix() {
        assertTrue(progressTitlesMatch("ThumbTest_1", "ThumbTest_1-0b48972c"))
        assertTrue(progressTitlesMatch("ThumbTest_1", "ThumbTest_1"))
        assertFalse(progressTitlesMatch("ThumbTest_1", "slowtest2"))
    }

    @Test
    fun similar_names_are_not_the_same_video() {
        assertFalse(progressTitlesMatch("914_1", "914_10"))
        assertFalse(progressTitlesMatch("914_10", "914_1"))
        assertFalse(progressTitlesMatch("show_1", "show_12"))
    }
}
