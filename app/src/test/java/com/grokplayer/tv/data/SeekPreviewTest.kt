package com.grokplayer.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SeekPreviewTest {
    @Test
    fun timesCoverShortAndLongVod() {
        assertEquals(listOf(0L, 10_000L), SeekPreviewPlan.times(10_000L, 10_000L))
        val hour = SeekPreviewPlan.times(3_600_000L, 10_000L)
        assertTrue(hour.first() == 0L)
        assertTrue(hour.contains(1_800_000L))
        assertTrue(hour.size in 300..400)
    }

    @Test
    fun firstWaveIsTheVisibleStripAroundCursor() {
        val times = SeekPreviewPlan.times(120_000L, 10_000L)
        val wave = SeekPreviewPlan.firstWave(times, 50_000L, 7)
        assertEquals(50_000L, wave.first())
        assertTrue(wave.contains(40_000L))
        assertTrue(wave.contains(60_000L))
        assertEquals(7, wave.size)
        wave.drop(1).forEach { time ->
            assertTrue(abs(time - 50_000L) <= 40_000L)
        }
    }

    @Test
    fun nearestRespectsDelta() {
        val times = listOf(0L, 10_000L, 20_000L)
        assertEquals(10_000L, SeekPreviewPlan.nearest(times, 10_400L, 1_500L))
        assertEquals(null, SeekPreviewPlan.nearest(times, 10_400L, 200L))
    }

    @Test
    fun bucketGroupsNearbyScrubTimes() {
        assertEquals(SeekPreviewPlan.bucket(10_000L), SeekPreviewPlan.bucket(10_150L))
        assertTrue(SeekPreviewPlan.bucket(10_000L) != SeekPreviewPlan.bucket(10_400L))
    }

    @Test
    fun seekCursorKeepsBackwardScrub() {
        assertEquals(4_000L, seekCursor(true, 4_000L, 20_000L))
        assertEquals(20_000L, seekCursor(false, 4_000L, 20_000L))
    }

    @Test
    fun prioritizeIsStableAndFast() {
        val times = SeekPreviewPlan.times(3_600_000L, 10_000L)
        val start = System.nanoTime()
        repeat(500) { SeekPreviewPlan.prioritize(times, 1_800_000L) }
        val avgMs = (System.nanoTime() - start) / 500_000_000.0
        assertTrue("prioritize avg ${avgMs}ms", avgMs < 2.0)
        val order = SeekPreviewPlan.prioritize(times, 1_800_000L)
        assertEquals(1_800_000L, order.first())
        assertEquals(times.size, order.toSet().size)
    }

    @Test
    fun extractOnlyLocalOrProgressiveFiles() {
        assertTrue(SeekPreviewPlan.canExtract("file", "/sdcard/a.mp4", null))
        assertTrue(SeekPreviewPlan.canExtract("https", "/vod/BigBuckBunny.mp4", null))
        assertTrue(!SeekPreviewPlan.canExtract("https", "/live.m3u8", null))
        assertTrue(!SeekPreviewPlan.canExtract("https", "/manifest.mpd", null))
        assertTrue(!SeekPreviewPlan.canExtract("file", "/movies/play.m3u8", "/movies/play.m3u8"))
    }

    @Test
    fun firstWaveThenRestCoversAtMostMaxPreload() {
        val times = SeekPreviewPlan.times(3_600_000L, 10_000L)
        val missing = SeekPreviewPlan.prioritize(times, 0L).take(SeekPreviewPlan.MAX_PRELOAD)
        assertEquals(SeekPreviewPlan.MAX_PRELOAD, missing.size)
        val first = missing.take(SeekPreviewPlan.FIRST_WAVE)
        assertEquals(0L, first.first())
        assertTrue(first.contains(10_000L))
    }
}
