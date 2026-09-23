package com.grokplayer.tv.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryIndexTest {
    @Test
    fun skipsBrokenTsAndDownloadFragments() {
        val dir = File.createTempFile("idx", "dir").apply { delete(); mkdirs(); deleteOnExit() }
        File(dir, "meta.json").writeText("{}")
        val ts = File(dir, "clip.ts")
        ts.writeBytes(byteArrayOf(0, 0, 0, 16, 109, 111, 111, 102) + ByteArray(40))
        assertTrue(!LibraryStore.shouldIndexVideo(ts))
        val seg = File(dir, "v-0000.seg")
        seg.writeBytes(ByteArray(48) { 1 })
        assertTrue(!LibraryStore.shouldIndexVideo(seg))
        File(dir, "clip.m3u8").writeText("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\nv.m3u8\n")
        File(dir, "v.m3u8").writeText("#EXTM3U\n#EXTINF:1,\nv-0000.seg\n")
        File(dir, "a-en.m3u8").writeText("#EXTM3U\n#EXTINF:1,\na-0000.seg\n")
        assertTrue(!LibraryStore.shouldIndexVideo(File(dir, "v.m3u8")))
        assertTrue(!LibraryStore.shouldIndexVideo(File(dir, "a-en.m3u8")))
        assertTrue(!LibraryStore.shouldIndexVideo(File(dir, "clip.m3u8")))
        val ok = File.createTempFile("okv", ".mp4")
        ok.writeBytes(byteArrayOf(0, 0, 0, 24, 102, 116, 121, 112, 105, 115, 111, 109) + ByteArray(40))
        ok.deleteOnExit()
        assertTrue(LibraryStore.shouldIndexVideo(ok))
    }

    @Test
    fun youtubeConcatTsInDownloadFolderIsNotPlayable() {
        val dir = File.createTempFile("ytd", "dir").apply { delete(); mkdirs(); deleteOnExit() }
        File(dir, "meta.json").writeText("""{"url":"https://manifest.googlevideo.com/api/manifest/hls_variant/file/index.m3u8"}""")
        val ts = File(dir, "clip.ts")
        ts.writeBytes(byteArrayOf(0x47, 0x40, 0x00, 0x30) + ByteArray(40))
        assertEquals("ts", sniffContainer(ts))
        assertTrue(isYoutubeConcatDump(ts))
        assertTrue(!isPlayableDownload(ts))
        assertTrue(!LibraryStore.shouldIndexVideo(ts))
    }
}
