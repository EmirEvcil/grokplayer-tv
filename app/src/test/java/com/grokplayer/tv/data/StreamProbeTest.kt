package com.grokplayer.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamProbeTest {
    @Test
    fun playUrlKeepsKnownHlsAndDash() {
        val hls = "https://example.com/live/master.m3u8?token=1"
        val dash = "https://example.com/vod/manifest.mpd"
        assertEquals(hls, StreamProbe.playUrl(hls))
        assertEquals(dash, StreamProbe.playUrl(dash))
    }

    @Test
    fun mimeFromExtension() {
        assertEquals("application/x-mpegURL", StreamProbe.mimeForUrl("https://x/a.m3u8"))
        assertEquals("application/dash+xml", StreamProbe.mimeForUrl("https://x/a.mpd"))
        assertEquals(
            "application/dash+xml",
            StreamProbe.mimeForUrl("https://storage.googleapis.com/shaka-demo-assets/angel-one/dash.mpd"),
        )
        assertEquals(
            "application/x-mpegURL",
            StreamProbe.mimeForUrl("https://manifest.googlevideo.com/api/manifest/hls_variant/id/abc"),
        )
        assertNull(StreamProbe.mimeForUrl("https://x/live/channel"))
    }

    @Test
    fun dashIsNotClassifiedAsHls() {
        val dash = "https://storage.googleapis.com/shaka-demo-assets/angel-one/dash.mpd"
        assertEquals(true, StreamProbe.isDash(dash))
        assertEquals(false, StreamProbe.isHls(dash))
    }
}
