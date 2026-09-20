package com.grokplayer.tv.data.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeResolverTest {
    @Test
    fun parsesWatchAndShortLinks() {
        assertEquals("m7lJeL1DJjA", YouTubeResolver.videoId("https://www.youtube.com/watch?v=m7lJeL1DJjA"))
        assertEquals("m7lJeL1DJjA", YouTubeResolver.videoId("https://youtu.be/m7lJeL1DJjA"))
        assertEquals("m7lJeL1DJjA", YouTubeResolver.videoId("https://m.youtube.com/watch?v=m7lJeL1DJjA&t=12"))
        assertEquals("m7lJeL1DJjA", YouTubeResolver.videoId("https://www.youtube.com/embed/m7lJeL1DJjA"))
        assertEquals("m7lJeL1DJjA", YouTubeResolver.videoId("m7lJeL1DJjA"))
    }

    @Test
    fun ignoresNonYoutube() {
        assertNull(YouTubeResolver.videoId("https://example.com/watch?v=abcdefghijk"))
        assertNull(YouTubeResolver.videoId("https://vimeo.com/12345"))
        assertNull(YouTubeResolver.videoId(""))
    }

    @Test
    fun watchUrlFromSavedHlsStillFindsThePage() {
        val page = "https://www.youtube.com/watch?v=m7lJeL1DJjA"
        val hls = "https://manifest.googlevideo.com/api/manifest/hls_variant/index.m3u8?expire=1"
        assertEquals(page, YouTubeResolver.watchUrl(hls, page))
        assertEquals(page, YouTubeResolver.watchUrl(page, null))
        assertNull(YouTubeResolver.watchUrl("file:///sdcard/clip.mp4", null))
        assertNull(YouTubeResolver.watchUrl("http://pc/v1/file?path=a.mp4", null))
    }
}
