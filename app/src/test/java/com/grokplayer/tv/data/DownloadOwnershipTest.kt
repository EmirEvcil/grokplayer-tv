package com.grokplayer.tv.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadOwnershipTest {
    @Test
    fun displayNameStaysTitle() {
        assertEquals("test1", DownloadOwnership.fileName("test1"))
        assertEquals("test1", DownloadOwnership.dest(File("/dl"), "id-a", "test1", "mp4").nameWithoutExtension)
    }

    @Test
    fun sameTitleLivesInDifferentIdFolders() {
        val root = File("/dl")
        val a = DownloadOwnership.dest(root, "id-a", "test1", "mp4")
        val b = DownloadOwnership.dest(root, "id-b", "test1", "mp4")
        assertEquals("test1.mp4", a.name)
        assertEquals("test1.mp4", b.name)
        assertTrue(a.parentFile != b.parentFile)
        assertEquals("id-a", a.parentFile?.name)
        assertEquals("id-b", b.parentFile?.name)
    }

    @Test
    fun sidecarDoesNotTreatOtherIdFileAsThisVideosSub() {
        val dir = newRoot()
        assertTrue(DownloadOwnership.isOwnedSidecar("test1", fakeFile(dir, "test1.en.vtt")))
        assertTrue(DownloadOwnership.isOwnedSidecar("test1", fakeFile(dir, "test1.vtt")))
        assertTrue(DownloadOwnership.isOwnedSidecar("test1", fakeFile(dir, "test1-en.vtt")))
        assertTrue(DownloadOwnership.isOwnedSidecar("test1", fakeFile(dir, "test1.en-US.vtt")))
        assertTrue(DownloadOwnership.isOwnedSidecar("test1", fakeFile(dir, "test1.tr.m4a")))
        assertTrue(DownloadOwnership.isOwnedSidecar("test1", fakeFile(dir, "test1.en.aac")))
        assertTrue(DownloadOwnership.isOwnedSubtitle("test1", fakeFile(dir, "test1.en.vtt")))
        assertFalse(DownloadOwnership.isOwnedSubtitle("test1", fakeFile(dir, "test1.tr.m4a")))
        assertTrue(DownloadOwnership.isOwnedAudio("test1", fakeFile(dir, "test1.tr.m4a")))
        assertFalse(DownloadOwnership.isOwnedAudio("test1", fakeFile(dir, "test1.en.vtt")))
        assertEquals("en", DownloadOwnership.sidecarLanguage("test1", fakeFile(dir, "test1.en.vtt")))
        assertEquals("tr", DownloadOwnership.sidecarLanguage("test1", fakeFile(dir, "test1.tr.m4a")))
        assertFalse(DownloadOwnership.isOwnedSidecar("test1", fakeFile(dir, "test1_bbbbbbbb.en.vtt")))
        assertFalse(DownloadOwnership.isOwnedSidecar("test1", fakeFile(dir, "test1_aaaaaaaa.vtt")))
        assertFalse(DownloadOwnership.isOwnedSidecar("test1", fakeFile(dir, "test10.en.vtt")))
        assertFalse(DownloadOwnership.isOwnedSidecar("test1", fakeFile(dir, "test1.mp4")))
        assertFalse(DownloadOwnership.isOwnedSidecar("test1", fakeFile(dir, "test1_bbbbbbbb.en.m4a")))
    }

    @Test
    fun purgeIdFolderLeavesSiblingSameTitleUntouched() {
        val root = newRoot()
        val keepVideo = seedItem(root, "keep-id", "test1", video = "KEEP_VIDEO", subs = mapOf("en" to "KEEP_EN", "fr" to "KEEP_FR"))
        val dropVideo = seedItem(root, "drop-id", "test1", video = "DROP_VIDEO", subs = mapOf("en" to "DROP_EN"))
        DownloadOwnership.purge(
            root = root,
            item = DownloadOwnership.ItemRef("drop-id", "test1", dropVideo.absolutePath),
            keep = DownloadOwnership.keepPaths(
                root,
                listOf(DownloadOwnership.ItemRef("keep-id", "test1", keepVideo.absolutePath)),
            ),
        )
        assertTrue(keepVideo.exists())
        assertEquals("KEEP_VIDEO", keepVideo.readText())
        assertEquals("KEEP_EN", File(keepVideo.parentFile, "test1.en.vtt").readText())
        assertEquals("KEEP_FR", File(keepVideo.parentFile, "test1.fr.vtt").readText())
        assertTrue(File(keepVideo.parentFile, "meta.json").exists())
        assertFalse(dropVideo.exists())
        assertFalse(File(root, "drop-id").exists())
        assertFalse(File(dropVideo.parentFile, "test1.en.vtt").exists())
    }

    @Test
    fun purgeLegacyFlatSameTitleDoesNotEatOtherLegacyOrIdFolder() {
        val root = newRoot()
        val keepLegacy = File(root, "test1_aaaaaaaa.mp4").apply { writeText("KEEP_LEGACY") }
        File(root, "test1_aaaaaaaa.en.vtt").writeText("KEEP_LEGACY_EN")
        val keepFolder = seedItem(root, "folder-id", "test1", video = "KEEP_FOLDER", subs = mapOf("en" to "KEEP_FOLDER_EN"))
        val drop = File(root, "test1_bbbbbbbb.mp4").apply { writeText("DROP_LEGACY") }
        File(root, "test1_bbbbbbbb.en.vtt").writeText("DROP_LEGACY_EN")
        File(root, "test1.en.vtt").writeText("SHOULD_NOT_TOUCH_FLAT_TITLE_SUB")
        File(root, "dsada.ts").writeText("UNRELATED")

        val dropId = "bbbbbbbb-2222-4000-8000-000000000002"
        DownloadOwnership.purge(
            root = root,
            item = DownloadOwnership.ItemRef(dropId, "test1", drop.absolutePath),
            keep = DownloadOwnership.keepPaths(
                root,
                listOf(
                    DownloadOwnership.ItemRef("aaaaaaaa-1111-4000-8000-000000000001", "test1", keepLegacy.absolutePath),
                    DownloadOwnership.ItemRef("folder-id", "test1", keepFolder.absolutePath),
                ),
            ),
        )

        assertEquals("KEEP_LEGACY", keepLegacy.readText())
        assertEquals("KEEP_LEGACY_EN", File(root, "test1_aaaaaaaa.en.vtt").readText())
        assertEquals("KEEP_FOLDER", keepFolder.readText())
        assertEquals("KEEP_FOLDER_EN", File(keepFolder.parentFile, "test1.en.vtt").readText())
        assertEquals("SHOULD_NOT_TOUCH_FLAT_TITLE_SUB", File(root, "test1.en.vtt").readText())
        assertEquals("UNRELATED", File(root, "dsada.ts").readText())
        assertFalse(drop.exists())
        assertFalse(File(root, "test1_bbbbbbbb.en.vtt").exists())
    }

    @Test
    fun prefixTitleTestDoesNotMatchTest1() {
        val root = newRoot()
        val keep = seedItem(root, "id-test1", "test1", video = "KEEP_TEST1", subs = mapOf("en" to "KEEP_TEST1_EN"))
        val drop = seedItem(root, "id-test", "test", video = "DROP_TEST", subs = mapOf("en" to "DROP_TEST_EN"))
        DownloadOwnership.purge(
            root = root,
            item = DownloadOwnership.ItemRef("id-test", "test", drop.absolutePath),
            keep = DownloadOwnership.keepPaths(
                root,
                listOf(DownloadOwnership.ItemRef("id-test1", "test1", keep.absolutePath)),
            ),
        )
        assertEquals("KEEP_TEST1", keep.readText())
        assertEquals("KEEP_TEST1_EN", File(keep.parentFile, "test1.en.vtt").readText())
        assertFalse(drop.exists())
        assertFalse(File(root, "id-test").exists())
    }

    @Test
    fun keepBlocksDeletingASharedPath() {
        val root = newRoot()
        val shared = File(root, "shared.mp4").apply { writeText("SHARED") }
        DownloadOwnership.purge(
            root = root,
            item = DownloadOwnership.ItemRef("drop", "test1", shared.absolutePath),
            keep = setOf(shared.absolutePath),
        )
        assertTrue(shared.exists())
        assertEquals("SHARED", shared.readText())
    }

    @Test
    fun refusesToRecurseDeleteDownloadsRoot() {
        val root = newRoot()
        File(root, "other.mp4").writeText("SAFE")
        val fake = DownloadOwnership.ItemRef(root.name, "test1", null)
        DownloadOwnership.purge(root, fake, keep = emptySet())
        assertTrue(root.exists())
        assertEquals("SAFE", File(root, "other.mp4").readText())
    }

    @Test
    fun purgeSameTitleAlsoLeavesSiblingDubsAndDoesNotTouchRootMeta() {
        val root = newRoot()
        val keepVideo = seedItem(
            root,
            "keep-id",
            "test1",
            video = "KEEP_VIDEO",
            subs = mapOf("en" to "KEEP_EN", "fr" to "KEEP_FR"),
            dubs = mapOf("tr" to "KEEP_DUB_TR", "en" to "KEEP_DUB_EN"),
        )
        val dropVideo = seedItem(
            root,
            "drop-id",
            "test1",
            video = "DROP_VIDEO",
            subs = mapOf("en" to "DROP_EN"),
            dubs = mapOf("tr" to "DROP_DUB"),
        )
        File(root, "meta.json").writeText("ROOT_META")
        File(root, "test1.en.vtt").writeText("FLAT_SUB")
        File(root, "test1.tr.m4a").writeText("FLAT_DUB")

        DownloadOwnership.purge(
            root = root,
            item = DownloadOwnership.ItemRef("drop-id", "test1", dropVideo.absolutePath),
            keep = DownloadOwnership.keepPaths(
                root,
                listOf(DownloadOwnership.ItemRef("keep-id", "test1", keepVideo.absolutePath)),
            ),
        )

        assertEquals("KEEP_VIDEO", keepVideo.readText())
        assertEquals("KEEP_EN", File(keepVideo.parentFile, "test1.en.vtt").readText())
        assertEquals("KEEP_FR", File(keepVideo.parentFile, "test1.fr.vtt").readText())
        assertEquals("KEEP_DUB_TR", File(keepVideo.parentFile, "test1.tr.m4a").readText())
        assertEquals("KEEP_DUB_EN", File(keepVideo.parentFile, "test1.en.m4a").readText())
        assertEquals("ROOT_META", File(root, "meta.json").readText())
        assertEquals("FLAT_SUB", File(root, "test1.en.vtt").readText())
        assertEquals("FLAT_DUB", File(root, "test1.tr.m4a").readText())
        assertFalse(dropVideo.exists())
        assertFalse(File(root, "drop-id").exists())
    }

    @Test
    fun keepInsideSameFolderBlocksRecursiveWipe() {
        val root = newRoot()
        val dir = DownloadOwnership.itemDir(root, "shared-id")
        dir.mkdirs()
        val drop = File(dir, "test1.mp4").apply { writeText("DROP") }
        val keepFile = File(dir, "other.mp4").apply { writeText("KEEP_INSIDE") }
        File(dir, "test1.en.vtt").writeText("DROP_EN")
        DownloadOwnership.purge(
            root = root,
            item = DownloadOwnership.ItemRef("shared-id", "test1", drop.absolutePath),
            keep = setOf(keepFile.absolutePath),
        )
        assertTrue(dir.exists())
        assertEquals("KEEP_INSIDE", keepFile.readText())
        assertFalse(drop.exists())
        assertFalse(File(dir, "test1.en.vtt").exists())
    }

    @Test
    fun dangerousIdsNeverWalkOutOfDownloadsRoot() {
        val parent = newRoot()
        val root = File(parent, "downloads").apply { mkdirs() }
        File(parent, "outside.mp4").writeText("OUTSIDE")
        File(root, "other.mp4").writeText("INSIDE")
        listOf("..", ".", "", "foo/bar", "foo\\bar", parent.name).forEach { id ->
            DownloadOwnership.purge(
                root = root,
                item = DownloadOwnership.ItemRef(id, "test1", null),
                keep = emptySet(),
            )
        }
        assertEquals("OUTSIDE", File(parent, "outside.mp4").readText())
        assertEquals("INSIDE", File(root, "other.mp4").readText())
        assertTrue(root.exists())
        assertTrue(parent.exists())
    }

    @Test
    fun subtitleListingIgnoresSiblingAudioAndOtherTitle() {
        val root = newRoot()
        val video = seedItem(
            root,
            "keep-id",
            "test1",
            video = "KEEP_VIDEO",
            subs = mapOf("en" to "KEEP_EN", "fr" to "KEEP_FR"),
            dubs = mapOf("tr" to "KEEP_DUB"),
        )
        File(video.parentFile, "test1_bbbbbbbb.en.vtt").writeText("OTHER")
        val subs = DownloadOwnership.sidecarsBeside(video, subtitlesOnly = true)
        val all = DownloadOwnership.sidecarsBeside(video, subtitlesOnly = false)
        assertEquals(listOf("test1.en.vtt", "test1.fr.vtt"), subs.map { it.name })
        assertEquals(listOf("test1.en.vtt", "test1.fr.vtt", "test1.tr.m4a"), all.map { it.name })
    }

    @Test
    fun purgeLegacyDoesNotDeleteUnrelatedAudioNextToIt() {
        val root = newRoot()
        val drop = File(root, "test1_bbbbbbbb.mp4").apply { writeText("DROP") }
        File(root, "test1_bbbbbbbb.en.vtt").writeText("DROP_EN")
        File(root, "test1_bbbbbbbb.tr.m4a").writeText("DROP_DUB")
        val keepLegacy = File(root, "test1_aaaaaaaa.mp4").apply { writeText("KEEP") }
        File(root, "test1_aaaaaaaa.en.vtt").writeText("KEEP_EN")
        File(root, "test1_aaaaaaaa.tr.m4a").writeText("KEEP_DUB")
        File(root, "slowtest.mp4").writeText("SLOW")
        File(root, "32132132.en.vtt").writeText("USER_SUB")
        DownloadOwnership.purge(
            root = root,
            item = DownloadOwnership.ItemRef("bbbbbbbb-2222-4000-8000-000000000002", "test1", drop.absolutePath),
            keep = DownloadOwnership.keepPaths(
                root,
                listOf(
                    DownloadOwnership.ItemRef("aaaaaaaa-1111-4000-8000-000000000001", "test1", keepLegacy.absolutePath),
                ),
            ),
        )
        assertEquals("KEEP", keepLegacy.readText())
        assertEquals("KEEP_EN", File(root, "test1_aaaaaaaa.en.vtt").readText())
        assertEquals("KEEP_DUB", File(root, "test1_aaaaaaaa.tr.m4a").readText())
        assertEquals("SLOW", File(root, "slowtest.mp4").readText())
        assertEquals("USER_SUB", File(root, "32132132.en.vtt").readText())
        assertFalse(drop.exists())
        assertFalse(File(root, "test1_bbbbbbbb.en.vtt").exists())
        assertFalse(File(root, "test1_bbbbbbbb.tr.m4a").exists())
    }

    private fun newRoot(): File =
        File.createTempFile("dlown", "dir").apply {
            delete()
            mkdirs()
            deleteOnExit()
        }

    private fun seedItem(
        root: File,
        id: String,
        title: String,
        video: String,
        subs: Map<String, String>,
        dubs: Map<String, String> = emptyMap(),
    ): File {
        val dir = DownloadOwnership.itemDir(root, id)
        dir.mkdirs()
        val file = DownloadOwnership.dest(root, id, title, "mp4")
        file.writeText(video)
        File(dir, "meta.json").writeText("""{"id":"$id","title":"$title"}""")
        subs.forEach { (lang, body) ->
            File(dir, "${DownloadOwnership.fileName(title)}.$lang.vtt").writeText(body)
        }
        dubs.forEach { (lang, body) ->
            File(dir, "${DownloadOwnership.fileName(title)}.$lang.m4a").writeText(body)
        }
        return file
    }

    private fun fakeFile(dir: File, name: String): File {
        dir.mkdirs()
        return File(dir, name).apply { writeText("x") }
    }
}
