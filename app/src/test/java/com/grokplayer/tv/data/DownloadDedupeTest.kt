package com.grokplayer.tv.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadDedupeTest {
    @Test
    fun duplicateIdsKeepFirstAndDropRest() {
        val first = item("cccccccc-cccc-4000-8000-00000000dub3", "dubtest", DownloadStatus.Failed)
        val keep = item("aaaaaaaa-aaaa-4000-8000-00000000dub1", "dubtest", DownloadStatus.Done)
        val dup = item("cccccccc-cccc-4000-8000-00000000dub3", "dubtest", DownloadStatus.Failed)
        val out = dedupeDownloads(listOf(first, keep, dup, dup))
        assertEquals(listOf(first.id, keep.id), out.map { it.id })
    }

    @Test
    fun blankIdsAreDropped() {
        val ok = item("id-1", "a", DownloadStatus.Done)
        val out = dedupeDownloads(listOf(item("", "x", DownloadStatus.Failed), ok))
        assertEquals(listOf("id-1"), out.map { it.id })
    }

    private fun item(id: String, title: String, status: DownloadStatus) = DownloadItem(
        id = id,
        title = title,
        url = "http://example/$id",
        status = status,
        progress = if (status == DownloadStatus.Done) 1f else 0f,
        localPath = null,
        error = null,
        addedAt = 1L,
    )
}
