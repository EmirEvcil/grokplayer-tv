package com.grokplayer.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryboardSpecTest {
    @Test
    fun parsesVodSpecAndCellAtMinute() {
        val spec =
            "https://i.ytimg.com/sb/abcdefghijk/storyboard3_L\$L/\$N.jpg|80#45#50#10#10#5000#M\$M#rs\$abc|160#90#100#5#5#2000#M\$M#rs\$abc"
        val json =
            """{"storyboards":{"playerStoryboardSpecRenderer":{"spec":"$spec"}}}"""
        assertTrue(StoryboardSpec.fromPlayerJson(json)!!.contains("storyboard3_L"))
        val levels = StoryboardSpec.parse(spec)
        assertEquals(2, levels.size)
        val fast = StoryboardSpec.fastLevel(levels)!!
        assertEquals(160, fast.width)
        val cell = fast.cellAt(60_000L, 120_000L)!!
        assertTrue(cell.url.contains("storyboard3_L1"))
        assertEquals(60_000L, cell.timeMs)
    }
}
