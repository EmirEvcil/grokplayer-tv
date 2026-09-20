package com.grokplayer.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistCollectionsTest {
    @Test
    fun hidesPlaylistWithNoCollections() {
        val videos = mapOf(
            "empty" to emptyList<String>(),
            "full" to listOf("Show S01E01", "Show S01E02"),
        )
        val shown = listOf("empty", "full").mapNotNull { id ->
            val buckets = CollectionGrouper.group(videos.getValue(id), titleOf = { it }, scope = id)
            if (buckets.isEmpty()) null else id to buckets.size
        }
        assertEquals(listOf("full" to 1), shown)
    }

    @Test
    fun eachPlaylistKeepsItsOwnGeneral() {
        val a = CollectionGrouper.group(listOf("clip-a"), titleOf = { it }, scope = "a")
        val b = CollectionGrouper.group(listOf("clip-b"), titleOf = { it }, scope = "b")
        assertEquals("general:a", a.single().id)
        assertEquals("general:b", b.single().id)
    }

    @Test
    fun moveTargetsStayInsidePlaylist() {
        val p1 = CollectionGrouper.group(
            listOf("Show S01E01", "Show S01E02"),
            titleOf = { it },
            scope = "p1",
        )
        val p2 = CollectionGrouper.group(
            listOf("Cars 1", "Cars 2"),
            titleOf = { it },
            scope = "p2",
        )
        val targets = PlaylistCollections.moveTargets("p1", p1 + p2)
        assertTrue(targets.all { CollectionGrouper.inScope(it.id, "p1") })
        assertTrue(targets.none { it.name == "Cars" })
    }
}
