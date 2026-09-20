package com.grokplayer.tv.ui.player

import com.grokplayer.tv.data.scan.YtCaptionTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleSupportTest {
    @Test
    fun youtubeOverlayOptionIsNotOff() {
        val option = SubtitleOption(
            key = "yt:en:m",
            label = "English",
            language = "en",
        )
        assertTrue(!option.isOff)
        assertEquals("off", SubtitleOption.Off.key)
        assertTrue(SubtitleOption.Off.isOff)
    }

    @Test
    fun pickingYoutubeKeySurvivesExoKeyChange() {
        val exo = listOf(
            SubtitleOption.Off,
            SubtitleOption(
                key = "0:0:en:English",
                label = "English",
                language = "en",
            ),
        )
        val resolved = resolveSubtitleOption(
            options = exo,
            selectedKey = "yt:en:m",
            language = "en",
            picked = true,
            captionsOn = false,
            captionLang = "tr",
        )
        assertEquals("0:0:en:English", resolved.key)
        assertTrue(!resolved.isOff)
    }

    @Test
    fun mergeKeepsOffAndAddsYoutubeTrack() {
        val merged = mergeSubtitleOptions(
            listOf(SubtitleOption.Off),
            listOf(YtCaptionTrack("tr", "Türkçe", "https://x", auto = false)),
        )
        assertTrue(merged.first().isOff)
        assertEquals("yt:tr:m", merged.last().key)
        assertTrue(!merged.last().isOff)
    }
}
