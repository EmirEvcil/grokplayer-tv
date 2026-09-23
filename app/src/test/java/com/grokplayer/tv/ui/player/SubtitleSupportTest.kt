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

    @Test
    fun youtubeMenuListsTranslationApartFromSource() {
        val merged = mergeSubtitleOptions(
            listOf(SubtitleOption.Off),
            listOf(
                YtCaptionTrack("en", "English", "https://x", auto = true),
                YtCaptionTrack("tr", "Türkçe", "https://x?tlang=tr", auto = true, translate = true),
            ),
        )
        assertEquals(listOf("off", "yt:en:asr", "yt:tr:t"), merged.map { it.key })
        assertEquals("Türkçe (Çeviri)", merged.last().label)
    }

    @Test
    fun youtubeMenuKeepsOnlyRealTracks() {
        val youtube = listOf(YtCaptionTrack("en", "English (auto)", "https://x", auto = true))
        val merged = mergeSubtitleOptions(listOf(SubtitleOption.Off), youtube)
        assertEquals(listOf("off", "yt:en:asr"), merged.map { it.key })
    }

    @Test
    fun youtubeMenuIgnoresHlsTracksFromAnotherVideo() {
        val exo = listOf(
            SubtitleOption.Off,
            SubtitleOption(key = "0:0:de", label = "Deutsch", language = "de"),
            SubtitleOption(key = "0:1:fr", label = "Français", language = "fr"),
        )
        val youtube = listOf(YtCaptionTrack("en", "English (auto)", "https://x", auto = true))
        val merged = mergeSubtitleOptions(exo, youtube)
        assertEquals(2, merged.size)
        assertEquals("yt:en:asr", merged.last().key)
        assertTrue(merged.none { it.language == "de" || it.language == "fr" })
    }

    @Test
    fun mergeAudioKeepsHlsGroupsWhenSeveralExist() {
        val exo = listOf(
            AudioOption(key = "a:0", label = "English", language = "en"),
            AudioOption(key = "a:1", label = "Türkçe", language = "tr"),
        )
        val yt = listOf(
            com.grokplayer.tv.data.scan.YtAudioTrack("tr.3", "tr", "Turkish", "https://a/tr", false, "audio/mp4", 40),
        )
        val merged = mergeAudioOptions(exo, yt)
        assertEquals(listOf("a:0", "a:1"), merged.map { it.key })
    }

    @Test
    fun mergeAudioFallsBackToYoutubeWhenExoHasNone() {
        val yt = listOf(
            com.grokplayer.tv.data.scan.YtAudioTrack("en.4", "en", "English original", "https://a/en", true, "audio/mp4", 40),
            com.grokplayer.tv.data.scan.YtAudioTrack("tr.3", "tr", "Turkish", "https://a/tr", false, "audio/mp4", 40),
        )
        val merged = mergeAudioOptions(emptyList(), yt)
        assertEquals(2, merged.size)
        assertTrue(merged.all { it.key.startsWith("yt-a:") })
    }
}
