package com.grokplayer.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionResetTest {
    @Test
    fun addingSecondEpisodeCreatesCollection() {
        val first = CollectionGrouper.group(
            listOf("Show S01E01", "random clip"),
            titleOf = { it },
            scope = "p1",
        )
        assertTrue(first.single { it.isGeneral }.items.contains("Show S01E01"))
        val second = CollectionGrouper.group(
            listOf("Show S01E01", "Show S01E02", "random clip"),
            titleOf = { it },
            scope = "p1",
        )
        val show = second.first { !it.isGeneral }
        assertEquals("Show", show.name)
        assertEquals(listOf("Show S01E01", "Show S01E02"), show.items)
        assertEquals(listOf("random clip"), second.first { it.isGeneral }.items)
    }

    @Test
    fun fullResetDropsUserAndAutoHomes() {
        val state = CollectionReset.State(
            names = mapOf("user:p1:a" to "Mine", "auto:p1:show" to "Show"),
            homes = mapOf("v1" to setOf("user:p1:a"), "v2" to setOf("auto:p1:show")),
            userIds = listOf("user:p1:a", "user:p2:b"),
            known = setOf("auto:p1:show", "auto:p2:other"),
        )
        val next = CollectionReset.full("p1", state)
        assertEquals(mapOf<String, String>(), next.names)
        assertTrue(next.homes.values.flatten().none { CollectionGrouper.inScope(it, "p1") })
        assertEquals(listOf("user:p2:b"), next.userIds)
        assertEquals(setOf("auto:p2:other"), next.known)
    }

    @Test
    fun fullResetClearsExcludedGeneralSoVideoReturns() {
        val state = CollectionReset.State(
            names = emptyMap(),
            homes = emptyMap(),
            userIds = emptyList(),
            known = emptySet(),
            excluded = mapOf("v1" to setOf("general:p1"), "v2" to setOf("general:p2")),
        )
        val next = CollectionReset.full("p1", state)
        assertTrue(next.excluded["v1"].isNullOrEmpty())
        assertEquals(setOf("general:p2"), next.excluded["v2"])
    }

    @Test
    fun fullResetClearsUnscopedGeneralExclude() {
        val state = CollectionReset.State(
            names = emptyMap(),
            homes = emptyMap(),
            userIds = emptyList(),
            known = emptySet(),
            excluded = mapOf("v1" to setOf("general")),
        )
        val next = CollectionReset.full("p1", state)
        assertTrue(next.excluded["v1"].isNullOrEmpty())
    }

    @Test
    fun keepCustomClearsAutoHomesAndNames() {
        val state = CollectionReset.State(
            names = mapOf("user:p1:a" to "Mine", "auto:p1:show" to "Renamed"),
            homes = mapOf(
                "v1" to setOf("user:p1:a"),
                "v2" to setOf("auto:p1:show"),
                "v3" to setOf("general:p1"),
            ),
            userIds = listOf("user:p1:a"),
            known = setOf("auto:p1:show"),
        )
        val next = CollectionReset.keepCustom("p1", state)
        assertEquals(mapOf("user:p1:a" to "Mine"), next.names)
        assertEquals(mapOf("v1" to setOf("user:p1:a")), next.homes)
        assertEquals(listOf("user:p1:a"), next.userIds)
        assertTrue(next.known.none { it.startsWith("auto:p1:") })
    }

    @Test
    fun videoCanSitInTwoCollections() {
        val buckets = CollectionGrouper.group(
            items = listOf("Show S01E01", "Show S01E02", "Other S01E01", "Other S01E02"),
            titleOf = { it },
            idOf = { it },
            homesOf = { title ->
                if (title == "Show S01E01") listOf("auto:p1:show", "auto:p1:other") else emptyList()
            },
            scope = "p1",
        )
        val show = buckets.first { it.name == "Show" }
        val other = buckets.first { it.name == "Other" }
        assertTrue(show.items.contains("Show S01E01"))
        assertTrue(other.items.contains("Show S01E01"))
    }

    @Test
    fun removingOneHomeKeepsTheOtherInSamePlaylist() {
        val homes = setOf("user:p1:a", "user:p1:b")
        val next = CollectionGrouper.homesAfterRemove(homes, "user:p1:a", "p1")
        assertEquals(setOf("user:p1:b"), next)
    }

    @Test
    fun removingAutoHomeWithoutOthersPinsGeneral() {
        val next = CollectionGrouper.homesAfterRemove(setOf("auto:p1:show"), "auto:p1:show", "p1")
        assertEquals(setOf("general:p1"), next)
    }
}
