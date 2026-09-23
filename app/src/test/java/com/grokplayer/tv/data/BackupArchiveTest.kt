package com.grokplayer.tv.data

import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupArchiveTest {
    @Test
    fun zipRoundTripIsSmallerAndKeepsName() {
        val dir = File.createTempFile("bak", "dir").apply { delete(); mkdirs(); deleteOnExit() }
        val file = File(dir, "one.gpb")
        val repeated = "izleme-kaydi-".repeat(400)
        val sections = listOf(
            BackupArchive.Section("playback", "Oynatma", """{"resume":true,"note":"$repeated"}"""),
            BackupArchive.Section("watch", "İzleme geçmişi", """{"items":{"youtube:abc":{"positionMs":5,"updatedAt":2}}}"""),
        )
        BackupArchive.write(
            file,
            BackupArchive.Manifest("Gece", 10L, "0.2.70", 72, emptyList()),
            sections,
        )
        val raw = sections.sumOf { it.body.length }
        assertTrue(file.length() < raw)
        val manifest = BackupArchive.readManifest(file)
        assertEquals("Gece", manifest.name)
        assertEquals(10L, manifest.createdAt)
        assertEquals(2, manifest.sections.size)
        assertEquals(sections.map { it.body }, BackupArchive.readSections(file).map { it.body })
    }

    @Test
    fun mergeKeepsNewerWatchRecordAndUnionsKeys() {
        val older = listOf(
            BackupArchive.Section("watch", "İzleme geçmişi", """{"items":{"a":{"positionMs":1,"updatedAt":1},"b":{"positionMs":2,"updatedAt":5}}}"""),
        )
        val newer = listOf(
            BackupArchive.Section("watch", "İzleme geçmişi", """{"items":{"a":{"positionMs":9,"updatedAt":4},"c":{"positionMs":3,"updatedAt":3}}}"""),
        )
        val merged = BackupArchive.merge(listOf(older, newer)).single()
        val items = org.json.JSONObject(merged.body).getJSONObject("items")
        assertEquals(9, items.getJSONObject("a").getInt("positionMs"))
        assertEquals(2, items.getJSONObject("b").getInt("positionMs"))
        assertEquals(3, items.getJSONObject("c").getInt("positionMs"))
    }

    @Test
    fun mergeKeepsCollectionsAndPlaylistsFromTheOlderBackup() {
        val older = listOf(
            BackupArchive.Section(
                "video_collections",
                "Koleksiyonlar",
                """{"names":"{\"eski\":\"Eski filmler\",\"ortak\":\"Ortak\"}","users":"eski\nortak"}""",
            ),
            BackupArchive.Section(
                "folder_playlists",
                "Listeler",
                """{"items":"[{\"id\":\"p1\",\"title\":\"Eski liste\"},{\"id\":\"p2\",\"title\":\"Ortak liste\"}]","videos":"{\"p1\":[{\"id\":\"v1\",\"title\":\"Bir\"}]}"}""",
            ),
        )
        val newer = listOf(
            BackupArchive.Section(
                "video_collections",
                "Koleksiyonlar",
                """{"names":"{\"ortak\":\"Ortak yeni\",\"yeni\":\"Yeni\"}","users":"yeni"}""",
            ),
            BackupArchive.Section(
                "folder_playlists",
                "Listeler",
                """{"items":"[{\"id\":\"p2\",\"title\":\"Ortak liste\"},{\"id\":\"p3\",\"title\":\"Yeni liste\"}]"}""",
            ),
        )
        val merged = BackupArchive.merge(listOf(older, newer)).associateBy { it.id }
        val collections = JSONObject(merged.getValue("video_collections").body)
        val names = JSONObject(collections.getString("names"))
        assertEquals("Eski filmler", names.getString("eski"))
        assertEquals("Ortak yeni", names.getString("ortak"))
        assertEquals("Yeni", names.getString("yeni"))
        assertEquals(setOf("eski", "ortak", "yeni"), collections.getString("users").split("\n").toSet())
        val lists = JSONObject(merged.getValue("folder_playlists").body)
        val items = JSONArray(lists.getString("items"))
        val ids = (0 until items.length()).map { items.getJSONObject(it).getString("id") }
        assertEquals(listOf("p1", "p2", "p3"), ids)
        assertEquals(1, JSONObject(lists.getString("videos")).getJSONArray("p1").length())
    }

    @Test
    fun playbackSpeedRestoresAsFloat() {
        val encoded = PreferenceCodec.encode("playback", mapOf("speed" to 1.25f, "seek_step" to 10, "resume" to true))
        val decoded = PreferenceCodec.decode("playback", encoded)
        assertEquals(1.25f, decoded["speed"] as Float, 0.001f)
        assertEquals(10, decoded["seek_step"])
        assertEquals(true, decoded["resume"])
        val legacy = PreferenceCodec.decode("playback", """{"speed":1.25,"seek_step":10}""")
        assertTrue(legacy["speed"] is Float)
        assertEquals(1.25f, legacy["speed"] as Float, 0.001f)
        val whole = PreferenceCodec.decode("playback", """{"speed":1}""")
        assertEquals(1f, whole["speed"] as Float, 0.001f)
    }
}
