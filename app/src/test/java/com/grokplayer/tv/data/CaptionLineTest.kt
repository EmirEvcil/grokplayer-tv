package com.grokplayer.tv.data

import com.grokplayer.tv.data.scan.YouTubeCaptions
import com.grokplayer.tv.data.scan.YtCaptionLine
import com.grokplayer.tv.data.scan.YtCaptionWord
import org.junit.Assert.assertEquals
import org.junit.Test

class CaptionLineTest {
    @Test
    fun onlyTheCurrentLineIsVisibleWhenTheNextOneStarts() {
        val lines = listOf(
            YtCaptionLine(0, 1_000, listOf(YtCaptionWord("once", 0, 1_000))),
            YtCaptionLine(1_000, 2_000, listOf(YtCaptionWord("sonra", 1_000, 2_000))),
        )
        assertEquals("once", YouTubeCaptions.visibleLines(lines, 900).single().words.single().text)
        assertEquals("sonra", YouTubeCaptions.visibleLines(lines, 1_000).single().words.single().text)
        assertEquals(0, YouTubeCaptions.visibleLines(lines, 2_000).size)
    }

    @Test
    fun aLineThatRunsLongIsCutOffWhenTheNextLineStarts() {
        val lines = listOf(
            YtCaptionLine(0, 5_000, listOf(YtCaptionWord("eski", 0, 5_000))),
            YtCaptionLine(1_000, 2_000, listOf(YtCaptionWord("yeni", 1_000, 2_000))),
        )
        val readable = YouTubeCaptions.readableLines(lines)
        assertEquals(1_000L, readable[0].endMs)
        assertEquals(listOf("yeni"), YouTubeCaptions.visibleLines(readable, 1_000).map { it.words.single().text })
    }
}
