package com.grokplayer.tv.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchStoreTest {
    @Test
    fun liveHasNoWatchKeyOrStatus() {
        assertNull(WatchLogic.key(isVod = false, originUrl = "https://ex/live", path = null, id = "l1"))
        assertNull(WatchLogic.status(WatchRecord(positionMs = 50_000L, durationMs = 100_000L), live = true))
        assertNull(WatchLogic.fraction(WatchRecord(positionMs = 50_000L, durationMs = 100_000L), live = true))
    }

    @Test
    fun sameOriginSharesIdentityAcrossListsAndLocalCopy() {
        val playlist = WatchLogic.key(true, "https://ex/show/ep1", null, "playlist-a")
        val collection = WatchLogic.key(true, "https://ex/show/ep1", null, "collection-b")
        val downloaded = WatchLogic.key(true, "https://ex/show/ep1", "C:\\tmp\\missing.mp4", "download:1")
        assertEquals(playlist, collection)
        assertEquals(playlist, downloaded)
        assertEquals("https://ex/show/ep1", playlist)
    }

    @Test
    fun youtubeWatchIdsStayDistinct() {
        val a = WatchLogic.key(true, "https://www.youtube.com/watch?v=aaaaaaaaaaa", null, "a")
        val b = WatchLogic.key(true, "https://www.youtube.com/watch?v=bbbbbbbbbbb", null, "b")
        val same = WatchLogic.key(true, "https://youtu.be/aaaaaaaaaaa?t=30", null, "c")
        assertEquals("youtube:aaaaaaaaaaa", a)
        assertEquals(a, same)
        assertEquals("youtube:bbbbbbbbbbb", b)
    }

    @Test
    fun localFileWithoutOriginUsesFileKey() {
        val file = File.createTempFile("clip", ".mp4")
        file.writeBytes(ByteArray(12))
        try {
            val key = WatchLogic.key(true, null, file.absolutePath, "id-1")
            assertEquals(mediaFileKey(file.name, file.length()), key)
        } finally {
            file.delete()
        }
    }

    @Test
    fun subSecondProgressStaysUnwatched() {
        val rec = WatchLogic.applyProgress(WatchRecord(), 500L, 100_000L, 1L)
        assertEquals(WatchStatus.Unwatched, WatchLogic.status(rec, false))
        assertNull(WatchLogic.fraction(rec, false))
        assertFalse(WatchLogic.isFinished(500L, 100_000L))
        assertFalse(WatchLogic.isFinished(50_000L, 0L))
    }

    @Test
    fun progressBecomesWatchingThenWatched() {
        var rec = WatchRecord()
        rec = WatchLogic.applyProgress(rec, 20_000L, 100_000L, 1L)
        assertEquals(WatchStatus.Watching, WatchLogic.status(rec, false))
        val fraction = WatchLogic.fraction(rec, false)!!
        assertTrue(fraction in 0.04f..0.96f)
        rec = WatchLogic.applyProgress(rec, 90_000L, 100_000L, 2L)
        assertEquals(WatchStatus.Watched, WatchLogic.status(rec, false))
        assertEquals(1f, WatchLogic.fraction(rec, false))
    }

    @Test
    fun remainingWindowMarksWatched() {
        val rec = WatchLogic.applyProgress(WatchRecord(), 86_000L, 100_000L, 1L)
        assertEquals(WatchStatus.Watched, WatchLogic.status(rec, false))
    }

    @Test
    fun seekCursorDoesNotSnapBackwardToPlayerPosition() {
        assertEquals(4_000L, seekCursor(previewVisible = true, previewPos = 4_000L, position = 20_000L))
        assertEquals(20_000L, seekCursor(previewVisible = false, previewPos = 4_000L, position = 20_000L))
    }

    @Test
    fun reachingTheEndCountsAsWatched() {
        var rec = WatchLogic.applyProgress(WatchRecord(), 20_000L, 100_000L, 1L)
        rec = WatchLogic.applyProgress(rec, 100_000L, 100_000L, 2L)
        assertEquals(100_000L, rec.durationMs)
        assertEquals(WatchStatus.Watched, WatchLogic.status(rec, false))
    }

    @Test
    fun playheadIsNotTreatedAsTheVideoLength() {
        var rec = WatchLogic.applyProgress(WatchRecord(), 10_000L, 20_000L, 1L)
        rec = WatchLogic.applyProgress(rec, 10_000L, 10_000L, 2L)
        assertEquals(20_000L, rec.durationMs)
        assertEquals(10_000L, rec.positionMs)
        assertEquals(WatchStatus.Watching, WatchLogic.status(rec, false))
        val partial = WatchLogic.applyProgress(WatchRecord(), 30_000L, 30_000L, 1L)
        assertEquals(0L, partial.durationMs)
        assertEquals(WatchStatus.Watching, WatchLogic.status(partial, false))
    }

    @Test
    fun shortMp4NeedsNinetyPercent() {
        val mid = WatchLogic.applyProgress(WatchRecord(), 5_000L, 10_000L, 1L)
        assertEquals(WatchStatus.Watching, WatchLogic.status(mid, false))
        assertFalse(WatchLogic.isFinished(5_000L, 10_000L))
        val done = WatchLogic.applyProgress(WatchRecord(), 9_000L, 10_000L, 2L)
        assertEquals(WatchStatus.Watched, WatchLogic.status(done, false))
    }

    @Test
    fun manualUnwatchedClearsUntilPlayback() {
        var rec = WatchLogic.applyProgress(WatchRecord(), 40_000L, 100_000L, 1L)
        rec = WatchLogic.markUnwatched(rec, 2L)
        assertEquals(WatchStatus.Unwatched, WatchLogic.status(rec, false))
        assertNull(WatchLogic.fraction(rec, false))
        rec = WatchLogic.applyProgress(rec, 5_000L, 100_000L, 3L)
        assertEquals(WatchStatus.Watching, WatchLogic.status(rec, false))
        assertNull(rec.manual)
    }

    @Test
    fun manualWatchedOverridesThenPlaybackResumes() {
        var rec = WatchLogic.markWatched(WatchRecord(), 100_000L, 1L)
        assertEquals(WatchStatus.Watched, WatchLogic.status(rec, false))
        rec = WatchLogic.applyProgress(rec, 10_000L, 100_000L, 2L)
        assertEquals(WatchStatus.Watching, WatchLogic.status(rec, false))
    }

    @Test
    fun feedbackTogglesAndIsIndependent() {
        var rec = WatchRecord()
        rec = WatchLogic.toggleFeedback(rec, WatchFeedback.Liked, 1L)
        assertEquals(WatchFeedback.Liked, rec.feedback)
        rec = WatchLogic.toggleFeedback(rec, WatchFeedback.Liked, 2L)
        assertNull(rec.feedback)
        rec = WatchLogic.toggleFeedback(rec, WatchFeedback.Disliked, 3L)
        assertEquals(WatchFeedback.Disliked, rec.feedback)
        rec = WatchLogic.applyProgress(rec, 40_000L, 100_000L, 4L)
        assertEquals(WatchFeedback.Disliked, rec.feedback)
        assertEquals(WatchStatus.Watching, WatchLogic.status(rec, false))
    }

    @Test
    fun collectionStatsCountVodWatchedOnly() {
        val watched = WatchLogic.markWatched(WatchRecord(), 100_000L, 1L)
        data class Item(val vod: Boolean, val record: WatchRecord?)
        val items = listOf(
            Item(true, watched),
            Item(true, WatchRecord()),
            Item(false, null),
        )
        val stats = WatchLogic.stats(items, { it.vod }) { it.record }
        assertEquals(1, stats.watched)
        assertEquals(2, stats.total)
        assertEquals("Koleksiyon · 3 video · 1 izlendi", WatchLogic.collectionMeta(false, 3, stats))
        assertEquals("Genel · 3 video · 1 izlendi", WatchLogic.collectionMeta(true, 3, stats))
        assertEquals("1/2 izlendi", WatchLogic.listWatchedLine(stats))
        assertNull(WatchLogic.listWatchedLine(WatchStats(0, 0)))
    }

    @Test
    fun resumeFindsCursorInList() {
        assertEquals("b", WatchLogic.resume(listOf("a", "b"), "b") { it })
        assertNull(WatchLogic.resume(listOf("a"), "b") { it })
        assertNull(WatchLogic.resume(listOf("a", "b"), null) { it })
        assertEquals("playlist:pl", WatchLogic.playlistId("pl"))
        assertEquals("collection:c1", WatchLogic.collectionId("c1"))
    }

    @Test
    fun storePersistsSharedProgressCursorAndFeedback() {
        val dir = File("build/tmp/watch-test-${System.nanoTime()}").apply { mkdirs() }
        val file = File(dir, "watch.json")
        try {
            val store = WatchStore(file) { 10L }
            val key = "https://ex/show"
            store.put(key, WatchLogic.applyProgress(WatchRecord(), 25_000L, 100_000L, 10L))
            store.putCursor(WatchLogic.playlistId("pl"), key)
            store.putCursor(WatchLogic.collectionId("col"), key)
            store.put(key, WatchLogic.toggleFeedback(store.records.getValue(key), WatchFeedback.Liked, 10L))
            assertEquals(WatchStatus.Watching, WatchLogic.status(store.records[key], false))
            assertEquals(WatchFeedback.Liked, store.records[key]?.feedback)
            assertEquals(key, store.cursor(WatchLogic.playlistId("pl")))
            assertEquals(key, store.cursor(WatchLogic.collectionId("col")))

            val reloaded = WatchStore(file)
            assertEquals(WatchStatus.Watching, WatchLogic.status(reloaded.records[key], false))
            assertEquals(25_000L, reloaded.records[key]?.positionMs)
            assertEquals(WatchFeedback.Liked, reloaded.records[key]?.feedback)
            assertEquals(key, reloaded.cursor(WatchLogic.playlistId("pl")))
            assertEquals(key, reloaded.cursor(WatchLogic.collectionId("col")))
            store.put(key, WatchLogic.markUnwatched(store.records.getValue(key), 11L))
            assertEquals(WatchStatus.Unwatched, WatchLogic.status(store.records[key], false))
            assertTrue(file.readText().contains("unwatched"))
            store.put(key, WatchLogic.markWatched(store.records.getValue(key), 100_000L, 12L))
            assertEquals(WatchStatus.Watched, WatchLogic.status(store.records[key], false))
            val afterMark = WatchStore(file)
            assertEquals(WatchStatus.Watched, WatchLogic.status(afterMark.records[key], false))
            assertEquals(WatchFeedback.Liked, afterMark.records[key]?.feedback)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun importLegacyProgressOnceAndSkipsTitleKeys() {
        val dir = File("build/tmp/watch-legacy-${System.nanoTime()}").apply { mkdirs() }
        val file = File(dir, "watch.json")
        try {
            val store = WatchStore(file)
            store.importKeyed(mapOf("https://ex/show" to 40_000L, "title|ignored" to 80_000L))
            assertEquals(40_000L, store.records.getValue("https://ex/show").positionMs)
            assertFalse(store.records.containsKey("title|ignored"))
            store.importKeyed(mapOf("https://ex/show" to 90_000L))
            assertEquals(40_000L, store.records.getValue("https://ex/show").positionMs)
        } finally {
            dir.deleteRecursively()
        }
    }
}
