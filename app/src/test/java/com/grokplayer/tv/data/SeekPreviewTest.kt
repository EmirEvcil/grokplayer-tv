package com.grokplayer.tv.data

import java.io.File
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
        println("PERF prioritize avgMs=$avgMs n=${times.size} repeats=500")
        assertTrue("prioritize avg ${avgMs}ms", avgMs < 2.0)
        val order = SeekPreviewPlan.prioritize(times, 1_800_000L)
        assertEquals(1_800_000L, order.first())
        assertEquals(times.size, order.toSet().size)
    }

    @Test
    fun incrementalFirstFrameBeatsBatchWave() {
        val times = SeekPreviewPlan.times(3_600_000L, 10_000L)
        val work = SeekPreviewPlan.workList(times, 60_000L, emptySet()).take(SeekPreviewPlan.FIRST_WAVE)
        val extractNs = 8_000_000L
        val batchFirstVisibleNs = extractNs * work.size
        val incrementalFirstVisibleNs = extractNs
        println(
            "PERF first-visible batchNs=${batchFirstVisibleNs} incrementalNs=${incrementalFirstVisibleNs} " +
                "wave=${work.size} speedup=${batchFirstVisibleNs / incrementalFirstVisibleNs.toDouble()}",
        )
        assertEquals(60_000L, work.first())
        assertTrue(incrementalFirstVisibleNs * 8 <= batchFirstVisibleNs)
        val start = System.nanoTime()
        repeat(200) { SeekPreviewPlan.workList(times, 1_800_000L, emptySet()) }
        val avgWorkMs = (System.nanoTime() - start) / 200_000_000.0
        println("PERF workList avgMs=$avgWorkMs around=30m")
        assertTrue("workList avg ${avgWorkMs}ms", avgWorkMs < 2.0)
    }

    @Test
    fun downloadedHlsPreviewsWhilePlayingWithoutStoryboard() {
        assertEquals(
            listOf(PreviewStep.LocalHls),
            previewSteps(playlist = true, storyboard = false, onDevice = false, playing = true),
        )
        assertEquals(
            listOf(PreviewStep.LocalHls, PreviewStep.Storyboard),
            previewSteps(playlist = true, storyboard = true, onDevice = false, playing = true),
        )
    }

    @Test
    fun previewCardUsesOnlyItsOwnTime() {
        val frames = mapOf(0L to 1, 10_000L to 2)
        assertEquals(2, exactPreviewFrame(frames, 10_000L))
        assertEquals(null, exactPreviewFrame(frames, 20_000L))
        assertEquals(null, exactPreviewFrame(frames, 9_000L))
    }

    @Test
    fun timesInsideOneSegmentShareThatFile() {
        val dir = File.createTempFile("hlsone", "dir").apply { delete(); mkdirs(); deleteOnExit() }
        File(dir, "v.m3u8").writeText(
            """
            #EXTM3U
            #EXTINF:10.0,
            v-0000.seg
            #EXTINF:10.0,
            v-0001.seg
            """.trimIndent(),
        )
        File(dir, "v-0000.seg").writeBytes(byteArrayOf(1))
        File(dir, "v-0001.seg").writeBytes(byteArrayOf(1))
        val playlist = File(dir, "v.m3u8")
        val early = hlsPreviewSlice(playlist, 1_000L)!!
        val later = hlsPreviewSlice(playlist, 8_000L)!!
        val next = hlsPreviewSlice(playlist, 12_000L)!!
        assertEquals(early.file, later.file)
        assertEquals("v-0001.seg", next.file.name)
        assertEquals(2_000L, next.offsetMs)
        assertEquals(File(dir, "previews/t-1000.jpg").absolutePath, previewJpegForTime(playlist, 1_000L).absolutePath)
        assertTrue(previewJpegForTime(playlist, 1_000L).absolutePath != previewJpegForTime(playlist, 12_000L).absolutePath)
        assertTrue(!posterUsesNetwork(playlist.absolutePath))
        assertTrue(posterUsesNetwork(null))
    }

    @Test
    fun hlsPreviewReadsTheVideoSegmentForThatTime() {
        val dir = File.createTempFile("hlsprev", "dir").apply { delete(); mkdirs(); deleteOnExit() }
        File(dir, "v.m3u8").writeText(
            """
            #EXTM3U
            #EXTINF:5.0,
            v-0000.seg
            #EXTINF:5.0,
            v-0001.seg
            """.trimIndent(),
        )
        File(dir, "v-0000.seg").writeBytes(byteArrayOf(1))
        File(dir, "v-0001.seg").writeBytes(byteArrayOf(1))
        File(dir, "show.m3u8").writeText(
            """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=1,AUDIO="aud"
            v.m3u8
            """.trimIndent(),
        )
        val slice = hlsPreviewSlice(File(dir, "show.m3u8"), 6_000L)!!
        assertEquals("v-0001.seg", slice.file.name)
        assertEquals(1_000L, slice.offsetMs)
        val start = hlsPreviewSlice(File(dir, "show.m3u8"), 0L)!!
        assertEquals("v-0000.seg", start.file.name)
        assertEquals(0L, start.offsetMs)
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
    fun workListPrioritizesJumpTargetAndSkipsReady() {
        val times = SeekPreviewPlan.times(3_600_000L, 10_000L)
        val atOneMin = SeekPreviewPlan.workList(times, 60_000L, emptySet())
        assertEquals(60_000L, atOneMin.first())
        assertTrue(atOneMin[1] in setOf(50_000L, 70_000L))
        val atThirty = SeekPreviewPlan.workList(times, 1_800_000L, setOf(1_800_000L))
        assertTrue(atThirty.first() in setOf(1_790_000L, 1_810_000L))
        assertTrue(1_800_000L !in atThirty)
    }

    @Test
    fun aroundBucketChangesWhenJumpingMinutes() {
        assertEquals(SeekPreviewPlan.aroundBucket(60_000L), SeekPreviewPlan.aroundBucket(62_000L))
        assertTrue(SeekPreviewPlan.aroundBucket(60_000L) != SeekPreviewPlan.aroundBucket(1_800_000L))
    }

    @Test
    fun incrementalOrderEmitsCursorFirst() {
        val times = SeekPreviewPlan.times(3_600_000L, 10_000L)
        val start = System.nanoTime()
        val order = ArrayList<Long>(32)
        var firstNs = 0L
        SeekPreviewPlan.workList(times, 1_800_000L, emptySet()).take(32).forEachIndexed { index, time ->
            order += time
            if (index == 0) firstNs = System.nanoTime() - start
        }
        val totalNs = System.nanoTime() - start
        assertEquals(1_800_000L, order.first())
        assertTrue("first emit should be immediate, firstNs=$firstNs totalNs=$totalNs", firstNs <= totalNs)
        println("PERF prioritize+workList firstNs=${firstNs} totalNs=${totalNs} n=${order.size}")
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
