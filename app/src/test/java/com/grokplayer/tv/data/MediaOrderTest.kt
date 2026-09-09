package com.grokplayer.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaOrderTest {
    @Test
    fun episodeCodesSortBySeasonThenEpisode() {
        val names = listOf("Show S01E10", "Show S01E02", "Show S02E01", "Show S01E03")
        val ordered = MediaOrder.sortByTitle(names) { it }
        assertEquals(listOf("Show S01E02", "Show S01E03", "Show S01E10", "Show S02E01"), ordered)
    }

    @Test
    fun xNotationSortsLikeEpisodes() {
        val names = listOf("Anime 02x06", "Anime 01x12", "Anime 1x02")
        val ordered = MediaOrder.sortByTitle(names) { it }
        assertEquals(listOf("Anime 1x02", "Anime 01x12", "Anime 02x06"), ordered)
    }

    @Test
    fun numberedFilmsSortByTrailingNumber() {
        val names = listOf("Toy Story 2", "Toy Story 10", "Toy Story 1")
        val ordered = MediaOrder.sortByTitle(names) { it }
        assertEquals(listOf("Toy Story 1", "Toy Story 2", "Toy Story 10"), ordered)
    }

    @Test
    fun differentTitlesStayGroupedSeparately() {
        val names = listOf("Cars 2", "Toy Story 2", "Cars 1", "Toy Story 1")
        val ordered = MediaOrder.sortByTitle(names) { it }
        assertEquals(listOf("Cars 1", "Cars 2", "Toy Story 1", "Toy Story 2"), ordered)
    }

    @Test
    fun naturalSortBeatsLexicographic() {
        val names = listOf("clip10", "clip2", "clip1")
        val ordered = MediaOrder.sortByTitle(names) { it }
        assertEquals(listOf("clip1", "clip2", "clip10"), ordered)
    }

    @Test
    fun seriesKeyParsesS01E03() {
        val key = MediaOrder.key("Show.S01E03.1080p.mkv")
        assertEquals(1, key.season)
        assertEquals(3, key.episode)
        assertTrue(key.series.contains("show"))
    }
}
