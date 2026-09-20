package com.grokplayer.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineCollectionsTest {
    @Test
    fun localAndDownloadedTitlesGroupWithoutDroppingSameNameFiles() {
        val titles = listOf("Toy Story 2", "Toy Story 1", "random clip")
        val buckets = CollectionGrouper.group(titles, titleOf = { it }, scope = OfflineCollections.SCOPE)
        val story = buckets.first { it.name == "Toy Story" }
        assertEquals(2, story.items.size)
        assertTrue(story.id.startsWith("auto:offline:"))
        assertEquals("general:offline", buckets.first { it.isGeneral }.id)
    }

    @Test
    fun offlineScopeDoesNotCollideWithOnlinePlaylist() {
        val offline = CollectionGrouper.group(
            listOf("Show S01E01", "Show S01E02"),
            titleOf = { it },
            scope = OfflineCollections.SCOPE,
        )
        val online = CollectionGrouper.group(
            listOf("Show S01E01", "Show S01E02"),
            titleOf = { it },
            scope = "pc-playlist",
        )
        assertEquals("auto:offline:show", offline.single().id)
        assertEquals("auto:pc-playlist:show", online.single().id)
        assertTrue(CollectionGrouper.inScope(offline.single().id, OfflineCollections.SCOPE))
        assertTrue(!CollectionGrouper.inScope(offline.single().id, "pc-playlist"))
    }

    @Test
    fun httpPathsAreNotLocal() {
        assertTrue(!OfflineCollections.isLocalFile("https://x/a.mp4", "https"))
        assertTrue(OfflineCollections.isLocalFile("/sdcard/Movies/a.mp4", "file"))
        assertTrue(OfflineCollections.isLocalFile("/data/data/app/a.mp4", null))
    }

    @Test
    fun sameNameDifferentFilesAreBothKept() {
        val merged = OfflineCollections.mergeByKey(
            preferred = listOf("dl:/movies/a/clip.mp4"),
            extra = listOf("tv:/movies/b/clip.mp4", "dl:/movies/a/clip.mp4"),
            keyOf = { OfflineCollections.fileKey(it.substringAfter(':'), null, it) },
        )
        assertEquals(listOf("dl:/movies/a/clip.mp4", "tv:/movies/b/clip.mp4"), merged)
    }

    @Test
    fun sameNameVideosBothAppearInGeneral() {
        val items = listOf("clip" to "file:/a/clip.mp4", "clip" to "file:/b/clip.mp4")
        val buckets = CollectionGrouper.group(
            items,
            titleOf = { it.first },
            idOf = { it.second },
            scope = OfflineCollections.SCOPE,
        )
        assertEquals(1, buckets.size)
        assertTrue(buckets.single().isGeneral)
        assertEquals(2, buckets.single().items.size)
    }
}
