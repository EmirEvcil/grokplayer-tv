package com.grokplayer.tv.ui.search

import com.grokplayer.tv.data.WatchStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchFilterTest {
    @Test
    fun allSourcesListTheDeviceCopyAndTheOnlineCopy() {
        val hits = searchHits("nfs", listOf(item("local", "NFS", "Cihazda · Dahili")), listOf(item("net", "NFS", "Çevrimiçi")), status, SearchSource.All, SearchWatch.All)
        assertEquals(listOf("v:local", "s:net"), hits.map { it.key })
        assertEquals("Cihazda · Dahili", hits[0].lines[0])
        assertEquals("Çevrimiçi", hits[1].lines[0])
        assertEquals("İzlenmedi", hits[0].lines[1])
    }

    @Test
    fun sourceFilterKeepsOnlyThatPlace() {
        val videos = listOf(item("local", "NFS", "Cihazda · Dahili"))
        val streams = listOf(item("net", "NFS", "Çevrimiçi"))
        assertEquals(listOf("v:local"), searchHits("nfs", videos, streams, status, SearchSource.Device, SearchWatch.All).map { it.key })
        assertEquals(listOf("s:net"), searchHits("nfs", videos, streams, status, SearchSource.Online, SearchWatch.All).map { it.key })
    }

    @Test
    fun watchFilterKeepsOnlyTheMatchingStatus() {
        val videos = listOf(item("seen", "Race"), item("mid", "Race"), item("fresh", "Race"))
        val watch = mapOf("seen" to WatchStatus.Watched, "mid" to WatchStatus.Watching, "fresh" to WatchStatus.Unwatched)
        fun of(filter: SearchWatch) = searchHits("race", videos, emptyList(), { watch[it] }, SearchSource.All, filter).map { it.key }
        assertEquals(listOf("v:seen", "v:mid", "v:fresh"), of(SearchWatch.All))
        assertEquals(listOf("v:seen"), of(SearchWatch.Watched))
        assertEquals(listOf("v:mid"), of(SearchWatch.Watching))
        assertEquals(listOf("v:fresh"), of(SearchWatch.Unwatched))
    }

    @Test
    fun liveStreamIsMarkedLiveAndSettingsNeedBothFiltersOpen() {
        val live = searchHits("news", emptyList(), listOf(item("bbc", "News", "Canlı")), status, SearchSource.All, SearchWatch.All)
        assertEquals("Canlı", live.single().lines[0])
        assertTrue(searchHits("yedek", emptyList(), emptyList(), status, SearchSource.All, SearchWatch.All).any { it.title == "Yedekler" })
        assertTrue(searchHits("yedek", emptyList(), emptyList(), status, SearchSource.Device, SearchWatch.All).isEmpty())
        assertTrue(searchHits("yedek", emptyList(), emptyList(), status, SearchSource.All, SearchWatch.Unwatched).isEmpty())
        assertTrue(searchHits("   ", listOf(item("a", "A")), emptyList(), status, SearchSource.All, SearchWatch.All).isEmpty())
    }

    private val status: (String) -> WatchStatus? = { null }

    private fun item(id: String, title: String, place: String = "Cihazda · Dahili") =
        Searchable(id, title, "$title mp4", place)
}
