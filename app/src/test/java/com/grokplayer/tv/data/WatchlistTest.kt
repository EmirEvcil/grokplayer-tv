package com.grokplayer.tv.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchlistTest {
    @Test
    fun addKeepsTheFirstCopyAndPreservesOrder() {
        val first = item("youtube:abc", "Bir")
        val again = item("youtube:abc", "Bir kopya")
        val second = item("file:1", "İki")
        val added = WatchlistLogic.add(WatchlistLogic.add(emptyList(), first), second)
        val againList = WatchlistLogic.add(added, again)
        assertEquals(listOf("Bir", "İki"), againList.map { it.title })
        assertTrue(againList === added || againList.size == 2)
        assertEquals("Bir", againList.first().title)
    }

    @Test
    fun removeDropsOnlyTheChosenVideo() {
        val items = listOf(item("a", "A"), item("b", "B"))
        assertEquals(listOf("B"), WatchlistLogic.remove(items, "a").map { it.title })
    }

    @Test
    fun encodeRoundTripKeepsTitlesAndOrder() {
        val items = listOf(item("a", "A"), item("b", "B"))
        val decoded = WatchlistLogic.decode(WatchlistLogic.encode(items))
        assertEquals(listOf("a", "b"), decoded.map { it.id })
        assertEquals(listOf("A", "B"), decoded.map { it.title })
        assertEquals(90_000L, decoded.first().durationMs)
    }

    @Test
    fun oldBackupRestoreClearsAWatchlistThePreviewSaysWillBeRemoved() {
        assertEquals(setOf("watchlist"), restoredPrefsToClear(emptySet()))
        assertTrue("watchlist" !in restoredPrefsToClear(setOf("watchlist", "playback")))
        val current = listOf(section(item("a", "OfflineCollisionQZ")))
        val older = listOf(BackupArchive.Section("playback", "Oynatma", """{"resume":true}"""))
        val review = BackupPreview.review(current, older)
        assertTrue(review.removed >= 1)
        assertTrue(
            review.items.any { item ->
                (item.title == "OfflineCollisionQZ" || item.more.any { it.title == "OfflineCollisionQZ" }) &&
                    (item.detail.contains("İzleme listesi") || item.more.any { it.detail.contains("İzleme listesi") })
            },
        )
    }

    @Test
    fun backupNamesWatchlistVideosAndMergesById() {
        val older = listOf(section(item("a", "Eski"), item("b", "Ortak eski")))
        val newer = listOf(section(item("b", "Ortak yeni"), item("c", "Yeni")))
        val merged = BackupArchive.merge(listOf(older, newer)).single()
        val catalog = BackupPreview.catalog(listOf(merged)).first { it.id == "watchlist" }
        assertEquals(listOf("Eski", "Ortak yeni", "Yeni"), catalog.rows.map { it.title })
        assertEquals(3, BackupPreview.visibleCount(merged, listOf(merged)))
        val summary = BackupPreview.summary(listOf(merged))
        assertEquals(3, summary.watchlist)
        assertTrue(BackupPreview.summaryText(summary).contains("3 izlenecek"))
        val review = BackupPreview.review(older, newer)
        assertTrue(review.items.any { it.title == "Yeni" || it.more.any { line -> line.title == "Yeni" } })
        assertFalse(review.items.any { it.title == "items" })
    }

    private fun item(id: String, title: String) = WatchlistItem(
        id = id,
        videoId = id,
        title = title,
        uri = "file:///tmp/$id.mp4",
        path = "/tmp/$id.mp4",
        durationMs = 90_000L,
        format = "MP4",
        source = "Internal",
        isStream = false,
        originUrl = null,
        posterUrl = null,
        referer = null,
        userAgent = null,
        addedAt = 1L,
    )

    private fun section(vararg items: WatchlistItem): BackupArchive.Section {
        val array = JSONArray(WatchlistLogic.encode(items.toList()))
        return BackupArchive.Section(
            "watchlist",
            "İzleme listesi",
            JSONObject().put("items", array.toString()).toString(),
        )
    }
}
