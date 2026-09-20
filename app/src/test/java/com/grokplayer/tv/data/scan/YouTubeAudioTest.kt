package com.grokplayer.tv.data.scan

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeAudioTest {
    @Test
    fun picksBestUrlPerLanguage() {
        val root = JSONObject(
            """
            {"streamingData":{"adaptiveFormats":[
              {"itag":139,"mimeType":"audio/mp4","url":"https://a/low-tr","audioTrack":{"id":"tr.3","displayName":"Turkish","audioIsDefault":false}},
              {"itag":251,"mimeType":"audio/webm; codecs=\"opus\"","url":"https://a/hi-tr","audioTrack":{"id":"tr.3","displayName":"Turkish","audioIsDefault":false}},
              {"itag":140,"mimeType":"audio/mp4","url":"https://a/en","audioTrack":{"id":"en.4","displayName":"English original","audioIsDefault":true}}
            ]}}
            """.trimIndent(),
        )
        val tracks = YouTubeAudio.parse(root)
        assertEquals(2, tracks.size)
        assertTrue(tracks.first().isDefault)
        assertEquals("en", tracks.first().language)
        val turkish = tracks.first { it.language == "tr" }
        assertEquals("https://a/hi-tr", turkish.url)
        assertEquals("Türkçe", turkish.menuLabel())
    }
}
