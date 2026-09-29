package com.grokplayer.tv.data

import com.grokplayer.tv.data.scan.YouTubeCaptions
import com.grokplayer.tv.data.scan.YtCaptionLine
import com.grokplayer.tv.data.scan.YtCaptionWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun rollingKeepsThePhraseThatHasStarted() {
        val words = (1..12).map { n ->
            YtCaptionWord("w$n", n * 100L, n * 100L + 100L)
        }
        val shown = YouTubeCaptions.wordsOnScreen(words, 1_150L)
        val text = shown.joinToString(" ") { it.text }
        assertTrue(shown.none { it.startMs > 1_150L })
        assertEquals("w1", shown.first().text)
        assertEquals("w11", shown.last().text)
        assertFalse(text.contains("w12"))
        assertFalse(text.contains("\n"))
    }

    @Test
    fun overlappingCuesNeverShareTheScreen() {
        val lines = YouTubeCaptions.readableLines(
            listOf(
                YtCaptionLine(
                    0,
                    5_000,
                    listOf(
                        YtCaptionWord("eski", 0, 1_000),
                        YtCaptionWord("cumle", 1_000, 5_000),
                    ),
                ),
                YtCaptionLine(
                    1_200,
                    6_000,
                    listOf(
                        YtCaptionWord("yeni", 1_200, 2_000),
                        YtCaptionWord("satir", 2_000, 6_000),
                    ),
                ),
            ),
        )
        val at = 1_500L
        assertEquals(1, YouTubeCaptions.visibleLines(lines, at).size)
        val text = YouTubeCaptions.onScreenCaption(lines, at)
        assertEquals("yeni", text)
        assertFalse(text.contains("eski"))
        assertFalse(text.contains("\n"))
    }

    @Test
    fun aLongLineKeepsItsBeginning() {
        val words = listOf("eski", "cumle", "burada", "kaliyor", "simdi", "yeni", "kelime")
            .mapIndexed { index, word -> YtCaptionWord(word, index * 100L, index * 100L + 90L) }
        val at = 650L
        val shown = YouTubeCaptions.wordsOnScreen(words, at)
        val text = shown.joinToString(" ") { it.text }
        assertEquals("kelime", shown.last().text)
        assertEquals("eski cumle burada kaliyor simdi yeni kelime", text)
        assertTrue(shown.none { it.startMs > at })
        assertFalse(text.contains("\n"))
    }

    @Test
    fun overlappingVttCuesShowOnlyTheNewestFragment() {
        val raw = """
            WEBVTT

            00:00:00.120 --> 00:00:06.919
            Çocukluğumuzun efsane oyunu

            00:00:04.880 --> 00:00:10.719
            kazanıp polislerden kaçıp
        """.trimIndent()
        val lines = YouTubeCaptions.readableLines(YouTubeCaptions.parseVtt(raw))
        val at = 5_000L
        assertEquals(1, YouTubeCaptions.visibleLines(lines, at).size)
        val text = YouTubeCaptions.onScreenCaption(lines, at)
        assertTrue(text.contains("kazanıp"))
        assertFalse(text.contains("Çocukluğumuzun"))
        assertFalse(text.contains("\n"))
    }

    @Test
    fun theOverlayShowsOneSourceAndNeverBoth() {
        val lines = listOf(
            YtCaptionLine(0, 4_000, listOf(YtCaptionWord("youtube satiri", 0, 4_000))),
        )
        val both = YouTubeCaptions.shownCaption(lines, 1_000L, "eski exo\nyeni exo")
        assertEquals("youtube satiri", both)
        assertFalse(both.contains("exo"))
        assertFalse(both.contains("\n"))
        val exoOnly = YouTubeCaptions.shownCaption(emptyList(), 1_000L, "eski exo\nyeni exo")
        assertEquals("eski exo\nyeni exo", exoOnly)
        assertEquals("", YouTubeCaptions.shownCaption(emptyList(), 1_000L, "   "))
    }

    @Test
    fun aTwoLineCueKeepsBothLines() {
        val text = YouTubeCaptions.singleCaptionText("Yok canım hazır böyle şey.\nVazgeçer miyim?")
        assertEquals("Yok canım hazır böyle şey.\nVazgeçer miyim?", text)
        assertEquals("bir\niki\nuc", YouTubeCaptions.singleCaptionText("bir\niki\nuc"))
        val phrase = "şey yani bütün çocuklar ikna oldu öyle mi?"
        assertEquals(phrase, YouTubeCaptions.singleCaptionText(phrase))
        val longLine = "Aman Şerim Hanımcığım ya. Dert ettiğiniz şey çocuk olsun. Bende boy boy var."
        assertEquals(longLine, YouTubeCaptions.singleCaptionText(longLine))
        assertFalse(YouTubeCaptions.singleCaptionText(longLine).contains("..."))
    }

    @Test
    fun theFullQuestionStaysVisibleAtSeventeenSeconds() {
        val phrase = "şey yani bütün çocuklar ikna oldu öyle mi?"
        val raw = """
            WEBVTT

            00:00:12.000 --> 00:00:16.400
            önceki cümle burada kalmasın

            00:00:15.200 --> 00:00:19.000
            $phrase
        """.trimIndent()
        val lines = YouTubeCaptions.readableLines(YouTubeCaptions.parseVtt(raw))
        val text = YouTubeCaptions.onScreenCaption(lines, 17_000L)
        assertEquals(phrase, text)
        assertFalse(text.contains("önceki"))
        assertFalse(text.contains("\n"))
    }
}
