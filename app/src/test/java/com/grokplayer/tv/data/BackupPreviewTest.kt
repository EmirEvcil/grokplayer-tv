package com.grokplayer.tv.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupPreviewTest {
    @Test
    fun collectionCountIsTheNamedCollections() {
        val lines = BackupPreview.contents(listOf(collections("a" to "Filmler", "b" to "Diziler")))
        assertEquals(listOf("Diziler", "Filmler"), lines.map { it.title })
        assertTrue(lines.all { it.detail.startsWith("Koleksiyon") })
        assertEquals(2, BackupPreview.visibleCount(collections("a" to "Filmler", "b" to "Diziler")))
    }

    @Test
    fun playlistLineUsesThePlaylistTitleAndVideoCount() {
        val lines = BackupPreview.contents(listOf(playlist("p1", "Tatil", "v1" to "Sahne")))
        assertEquals("Tatil", lines[0].title)
        assertEquals("Liste · 1 video", lines[0].detail)
        assertEquals("Sahne", lines[1].title)
        assertEquals("Liste · Tatil", lines[1].detail)
    }

    @Test
    fun sameVideoCountWithDifferentVideosIsAChange() {
        val current = listOf(playlist("p1", "Tatil", "a" to "Video A"))
        val backup = listOf(playlist("p1", "Tatil", "b" to "Video B"))
        val lines = BackupPreview.diff(current, backup)
        assertTrue(lines.any { it.title == "Video A" && it.action == "Silinecek" && it.detail.contains("Liste") })
        assertTrue(lines.any { it.title == "Video B" && it.action == "Eklenecek" && it.detail.contains("Liste") })
        assertTrue(lines.none { it.detail.contains("1 video → 1 video") })
    }

    @Test
    fun playlistRenameIsShownOnItsOwn() {
        val current = listOf(playlist("p1", "Tatil", "a" to "Video A"))
        val backup = listOf(playlist("p1", "Gezi", "a" to "Video A"))
        val lines = BackupPreview.diff(current, backup)
        assertTrue(lines.any { it.action == "Değişecek" && it.detail.contains("ad: Tatil → Gezi") })
        assertTrue(lines.none { it.title == "Video A" && it.action != null })
    }

    @Test
    fun collectionRenameIsShown() {
        val lines = BackupPreview.diff(
            listOf(collections("a" to "Filmler")),
            listOf(collections("a" to "Sinemalar")),
        )
        assertTrue(
            lines.any {
                it.title == "Sinemalar" &&
                    it.action == "Değişecek" &&
                    it.detail.contains("Filmler") &&
                    it.detail.contains("Sinemalar")
            },
        )
    }

    @Test
    fun autoCollectionFromEpisodeTitlesIsListed() {
        val cols = emptyCollections()
        val lists = playlist("p1", "Dizilerim", "e1" to "Show S01E01", "e2" to "Show S01E02")
        val lines = BackupPreview.contents(listOf(cols, lists))
        assertTrue(lines.any { it.title == "Show" && it.detail.contains("Koleksiyon") && it.detail.contains("2 video") })
        assertTrue(lines.any { it.title == "Show S01E01" && it.detail.contains("Koleksiyon") })
        assertTrue(lines.any { it.title == "Show S01E02" && it.detail.contains("Dizilerim") })
        assertTrue(lines.none { it.title == "Genel" })
        assertEquals(1, BackupPreview.visibleCount(cols, listOf(cols, lists)))
    }

    @Test
    fun renamedAutoCollectionUsesTheSavedName() {
        val names = JSONObject().put("auto:p1:show", "Benim dizi")
        val cols = BackupArchive.Section(
            "video_collections",
            "Koleksiyonlar",
            JSONObject()
                .put("names", names.toString())
                .put("homes", "{}")
                .put("users", "")
                .put("known", "")
                .put("excluded", "{}")
                .toString(),
        )
        val lines = BackupPreview.contents(
            listOf(cols, playlist("p1", "Dizilerim", "e1" to "Show S01E01", "e2" to "Show S01E02")),
        )
        assertTrue(lines.any { it.title == "Benim dizi" && it.detail.contains("Koleksiyon") })
        assertTrue(lines.none { it.title == "Show" })
    }

    @Test
    fun likeStateIsShownInTheRestorePreview() {
        val before = watch("clip.mp4|10", """{"positionMs":5000,"feedback":"disliked"}""")
        val after = watch("clip.mp4|10", """{"positionMs":5000,"feedback":"liked"}""")
        val lines = BackupPreview.diff(listOf(before), listOf(after))
        assertTrue(lines.any { it.title == "clip.mp4" && it.detail.contains("beğenilmedi") && it.detail.contains("beğenildi") && it.action == "Değişecek" })
        val review = BackupPreview.review(listOf(before), listOf(after))
        assertTrue(review.items.any { it.title == "clip.mp4" && it.detail.contains("beğenildi") && it.more.isEmpty() })
    }

    @Test
    fun offlineAutoCollectionFromTheLibrarySnapshotIsListed() {
        val items = org.json.JSONArray()
            .put(JSONObject().put("id", "v1").put("title", "Show S01E01").put("path", "/sdcard/Show S01E01.mp4").put("uri", "file:///sdcard/Show S01E01.mp4"))
            .put(JSONObject().put("id", "v2").put("title", "Show S01E02").put("path", "/sdcard/Show S01E02.mp4").put("uri", "file:///sdcard/Show S01E02.mp4"))
        val library = BackupArchive.Section("offline_library", "Çevrimdışı", JSONObject().put("items", items).toString())
        val lines = BackupPreview.contents(listOf(emptyCollections(), library))
        assertTrue(lines.any { it.title == "Show" && it.detail.contains("Çevrimdışı") })
    }

    @Test
    fun offlineAutoCollectionFromDownloadsIsListed() {
        val cols = emptyCollections()
        val downloads = downloads("Show S01E01", "Show S01E02")
        val lines = BackupPreview.contents(listOf(cols, downloads))
        assertTrue(lines.any { it.title == "Show" && it.detail.contains("Çevrimdışı") && it.detail.contains("2 video") })
        val summary = BackupPreview.summary(listOf(cols, downloads))
        assertEquals(1, summary.collections)
        assertEquals(2, summary.downloads)
        assertTrue(BackupPreview.summaryText(summary).contains("1 koleksiyon"))
        assertTrue(BackupPreview.summaryText(summary).contains("2 indirme"))
    }

    @Test
    fun addedCollectionsCollapseIntoOneReviewRow() {
        val current = listOf(emptyCollections())
        val backup = listOf(collections("a" to "Filmler", "b" to "Diziler"))
        val review = BackupPreview.review(current, backup)
        assertEquals(2, review.added)
        val row = review.items.single { it.kind == "Eklenecek" }
        assertEquals("2 koleksiyon", row.title)
        assertTrue(row.more.any { it.title == "Filmler" })
        assertTrue(row.more.any { it.title == "Diziler" })
    }

    @Test
    fun removedVideosStayVisibleInsideTheCollectionDetail() {
        val current = listOf(twoPlaylists(full = true))
        val backup = listOf(twoPlaylists(full = false))
        val review = BackupPreview.review(current, backup)
        val row = review.items.first { it.kind == "Silinecek" && it.more.any { line -> line.children.isNotEmpty() } }
        val tatil = row.more.first { it.title == "Tatil" }
        assertEquals(listOf("Video A", "Video B"), tatil.children.map { it.title })
    }

    @Test
    fun singleEpisodeStaysInAnAutoCollectionWhenItIsKnown() {
        val cols = BackupArchive.Section(
            "video_collections",
            "Koleksiyonlar",
            JSONObject()
                .put("names", "{}")
                .put("homes", "{}")
                .put("users", "")
                .put("known", "auto:p1:show")
                .put("excluded", "{}")
                .toString(),
        )
        val lines = BackupPreview.contents(listOf(cols, playlist("p1", "Dizilerim", "e1" to "Show S01E01")))
        assertTrue(lines.any { it.title == "Show" && it.detail.contains("1 video") })
    }

    @Test
    fun watchRowUsesTheFileName() {
        val body = """{"items":{"thumbtest_3.mp4|26805408":{"positionMs":59000}}}"""
        val lines = BackupPreview.contents(listOf(BackupArchive.Section("watch", "İzleme", body)))
        assertEquals("thumbtest_3.mp4", lines.single().title)
        assertTrue(lines.single().detail.contains("İzleme"))
    }

    @Test
    fun restorePreviewNamesTheCollectionAndTheSpeed() {
        val current = listOf(
            BackupArchive.Section("playback", "Oynatma", """{"speed":1,"resume":true}"""),
            collections("a" to "Filmler"),
        )
        val backup = listOf(
            BackupArchive.Section("playback", "Oynatma", """{"speed":1.25,"resume":true}"""),
            collections("a" to "Filmler", "b" to "Yeniler"),
        )
        val lines = BackupPreview.diff(current, backup)
        assertTrue(lines.any { it.title == "Oynatma hızı" && it.detail.contains("1,25") && it.action == "Değişecek" })
        assertTrue(lines.any { it.title == "Yeniler" && it.detail.startsWith("Koleksiyon") && it.action == "Eklenecek" })
        assertTrue(lines.none { it.title == "homes" || it.detail.contains("homes") || it.detail.contains("items") })
    }

    private fun twoPlaylists(full: Boolean): BackupArchive.Section {
        val items = org.json.JSONArray()
            .put(JSONObject().put("id", "p1").put("title", "Tatil"))
            .put(JSONObject().put("id", "p2").put("title", "Gezi"))
        val videos = JSONObject()
        videos.put("p1", if (full) org.json.JSONArray().put(JSONObject().put("id", "a").put("title", "Video A")).put(JSONObject().put("id", "b").put("title", "Video B")) else org.json.JSONArray())
        videos.put("p2", if (full) org.json.JSONArray().put(JSONObject().put("id", "c").put("title", "Video C")).put(JSONObject().put("id", "d").put("title", "Video D")) else org.json.JSONArray())
        val body = JSONObject().put("items", items.toString()).put("videos", videos.toString()).toString()
        return BackupArchive.Section("folder_playlists", "Listeler", body)
    }

    private fun playlist(id: String, title: String, vararg videos: Pair<String, String>): BackupArchive.Section {
        val rows = org.json.JSONArray()
        videos.forEach { (videoId, name) ->
            rows.put(JSONObject().put("id", videoId).put("title", name))
        }
        val body = JSONObject()
            .put("items", org.json.JSONArray().put(JSONObject().put("id", id).put("title", title)).toString())
            .put("videos", JSONObject().put(id, rows).toString())
            .toString()
        return BackupArchive.Section("folder_playlists", "Listeler", body)
    }

    private fun watch(key: String, record: String): BackupArchive.Section =
        BackupArchive.Section("watch", "İzleme", """{"items":{"$key":$record}}""")

    private fun downloads(vararg titles: String): BackupArchive.Section {
        val items = org.json.JSONArray()
        titles.forEachIndexed { index, title ->
            items.put(
                JSONObject()
                    .put("id", "d$index")
                    .put("title", title)
                    .put("status", "Done")
                    .put("localPath", "/sdcard/$title.mp4"),
            )
        }
        return BackupArchive.Section("downloads", "İndirmeler", JSONObject().put("items", items.toString()).toString())
    }

    private fun emptyCollections(): BackupArchive.Section {
        val body = JSONObject()
            .put("names", "{}")
            .put("homes", "{}")
            .put("users", "")
            .put("known", "")
            .put("excluded", "{}")
            .toString()
        return BackupArchive.Section("video_collections", "Koleksiyonlar", body)
    }

    private fun collections(vararg names: Pair<String, String>): BackupArchive.Section {
        val objectNames = JSONObject()
        names.forEach { (id, name) -> objectNames.put(id, name) }
        val body = JSONObject()
            .put("names", objectNames.toString())
            .put("homes", "{}")
            .put("users", names.joinToString("\n") { it.first })
            .put("known", "")
            .put("excluded", "{}")
            .toString()
        return BackupArchive.Section("video_collections", "Koleksiyonlar", body)
    }
}
