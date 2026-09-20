package com.grokplayer.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionGrouperTest {
    @Test
    fun toyStoryFilmsShareACollection() {
        val buckets = CollectionGrouper.group(
            listOf("Toy Story 1", "Toy Story 2", "random clip"),
            titleOf = { it },
        )
        assertEquals(2, buckets.size)
        val story = buckets.first { it.name == "Toy Story" }
        assertEquals(listOf("Toy Story 1", "Toy Story 2"), story.items)
        assertEquals(listOf("random clip"), buckets.first { it.isGeneral }.items)
    }

    @Test
    fun episodeCodesGroupAndSort() {
        val buckets = CollectionGrouper.group(
            listOf("supernatural_01x02", "supernatural_01x01", "other"),
            titleOf = { it },
        )
        val show = buckets.first { !it.isGeneral }
        assertEquals("Supernatural", show.name)
        assertEquals(listOf("supernatural_01x01", "supernatural_01x02"), show.items)
    }

    @Test
    fun singleRelatedVideoStaysGeneral() {
        val buckets = CollectionGrouper.group(listOf("Show S01E01", "unrelated"), titleOf = { it })
        assertEquals(1, buckets.size)
        assertTrue(buckets.single().isGeneral)
        assertEquals(2, buckets.single().items.size)
    }

    @Test
    fun s01e10SortsAfterS01e02() {
        val buckets = CollectionGrouper.group(
            listOf("Show S01E10", "Show S01E02", "Show S01E01"),
            titleOf = { it },
        )
        assertEquals(listOf("Show S01E01", "Show S01E02", "Show S01E10"), buckets.single().items)
    }

    @Test
    fun moveOverrideAndRenameStick() {
        val buckets = CollectionGrouper.group(
            items = listOf("alpha", "beta", "Show S01E01", "Show S01E02"),
            titleOf = { it },
            idOf = { it },
            renameOf = { if (it == CollectionGrouper.autoId("show")) "Dizi" else null },
            homeOf = { title -> if (title == "alpha") CollectionGrouper.autoId("show") else null },
        )
        val show = buckets.first { it.name == "Dizi" }
        assertTrue(show.items.contains("alpha"))
        assertTrue(show.items.contains("Show S01E01"))
    }

    @Test
    fun scopedIdsDoNotCollideAcrossPlaylists() {
        val left = CollectionGrouper.group(
            listOf("Show S01E01", "Show S01E02"),
            titleOf = { it },
            scope = "p1",
        )
        val right = CollectionGrouper.group(
            listOf("Show S01E01", "Show S01E02"),
            titleOf = { it },
            scope = "p2",
        )
        assertEquals("auto:p1:show", left.single().id)
        assertEquals("auto:p2:show", right.single().id)
    }

    @Test
    fun homeFromAnotherPlaylistIsIgnored() {
        val buckets = CollectionGrouper.group(
            items = listOf("clip a", "clip b"),
            titleOf = { it },
            idOf = { it },
            homeOf = { if (it == "clip a") "auto:p1:show" else null },
            scope = "p2",
        )
        assertTrue(buckets.single().isGeneral)
        assertEquals(listOf("clip a", "clip b"), buckets.single().items)
    }

    @Test
    fun generalIsPerPlaylist() {
        val buckets = CollectionGrouper.group(listOf("random"), titleOf = { it }, scope = "alpha")
        assertEquals("general:alpha", buckets.single().id)
        assertTrue(buckets.single().isGeneral)
    }

    @Test
    fun inScopeRejectsForeignCollections() {
        assertTrue(CollectionGrouper.inScope("auto:p1:show", "p1"))
        assertTrue(!CollectionGrouper.inScope("auto:p2:show", "p1"))
        assertTrue(CollectionGrouper.inScope("general:p1", "p1"))
        assertTrue(!CollectionGrouper.inScope("general:p2", "p1"))
        assertTrue(CollectionGrouper.inScope("user:p1:abc", "p1"))
        assertTrue(!CollectionGrouper.inScope("user:p2:abc", "p1"))
        assertTrue(!CollectionGrouper.inScope("user:deneme-uuid", "isolated"))
    }

    @Test
    fun knownCollectionSurvivesWithOneVideo() {
        val id = CollectionGrouper.autoId("show", "p1")
        val first = CollectionGrouper.group(
            listOf("Show S01E01", "Show S01E02"),
            titleOf = { it },
            scope = "p1",
        )
        assertEquals(2, first.single().items.size)
        val second = CollectionGrouper.group(
            listOf("Show S01E01"),
            titleOf = { it },
            scope = "p1",
            keepIds = setOf(id),
        )
        val show = second.first { !it.isGeneral }
        assertEquals(id, show.id)
        assertEquals(listOf("Show S01E01"), show.items)
    }

    @Test
    fun emptyCollectionIsDropped() {
        val id = CollectionGrouper.autoId("show", "p1")
        val buckets = CollectionGrouper.group(
            listOf("unrelated"),
            titleOf = { it },
            scope = "p1",
            keepIds = setOf(id),
        )
        assertTrue(buckets.none { it.id == id })
        assertEquals("general:p1", buckets.single().id)
    }

    @Test
    fun applyingAnotherPlaylistDoesNotForgetKnownCollection() {
        val known = setOf("auto:p1:nestedshow")
        val afterOther = CollectionGrouper.mergeKnown(
            known = known,
            playlistId = "isolated",
            liveIds = setOf("general:isolated"),
            discovered = setOf("general:isolated"),
        )
        assertTrue(afterOther.contains("auto:p1:nestedshow"))
        val afterSelf = CollectionGrouper.mergeKnown(
            known = afterOther,
            playlistId = "p1",
            liveIds = setOf("auto:p1:nestedshow"),
            discovered = setOf("auto:p1:nestedshow"),
        )
        assertTrue(afterSelf.contains("auto:p1:nestedshow"))
    }

    @Test
    fun emptyLiveIdsDoNotWipeKnown() {
        val known = setOf("auto:p1:nestedshow")
        val after = CollectionGrouper.mergeKnown(
            known = known,
            playlistId = "p1",
            liveIds = emptySet(),
            discovered = emptySet(),
        )
        assertEquals(known, after)
        val blank = CollectionGrouper.mergeKnown(known, "", emptySet(), emptySet())
        assertEquals(known, blank)
    }
}
