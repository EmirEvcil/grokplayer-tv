package com.grokplayer.tv.data.scan

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeCaptionTest {
    @Test
    fun parsesCaptionTracksAndFormatsUrl() {
        val root = JSONObject(
            """
            {"captions":{"playerCaptionsTracklistRenderer":{"captionTracks":[
              {"baseUrl":"https://www.youtube.com/api/timedtext?v=abc&lang=tr","languageCode":"tr","kind":"asr","name":{"simpleText":""}},
              {"baseUrl":"https://www.youtube.com/api/timedtext?v=abc&lang=en","languageCode":"en","name":{"simpleText":"English"}}
            ]}}}
            """.trimIndent(),
        )
        val tracks = YouTubeCaptions.parseTracks(root)
        assertEquals(2, tracks.size)
        assertEquals("Türkçe (Otomatik)", tracks[0].displayLabel())
        assertEquals("English", tracks[1].displayLabel())
        assertTrue(tracks[0].withFormat("json3").contains("fmt=json3"))
        assertTrue(tracks[0].withFormat("vtt").contains("fmt=vtt"))
    }

    @Test
    fun parsesJson3WordTiming() {
        val json = """
            {"wireMagic":"pb3","events":[
              {"tStartMs":1000,"dDurationMs":2000,"segs":[
                {"utf8":"merhaba"},
                {"utf8":" ","tOffsetMs":400},
                {"utf8":"dünya","tOffsetMs":500}
              ]},
              {"tStartMs":3200,"dDurationMs":800,"segs":[{"utf8":"\n"}]}
            ]}
        """.trimIndent()
        val lines = YouTubeCaptions.parseJson3(json)
        assertEquals(1, lines.size)
        assertEquals(2, lines[0].words.size)
        assertEquals("merhaba", lines[0].words[0].text)
        assertEquals("dünya", lines[0].words[1].text)
        assertEquals(1000L, lines[0].words[0].startMs)
        assertEquals(1500L, lines[0].words[1].startMs)
        val visible = YouTubeCaptions.visibleLines(lines, 1600L)
        assertEquals(1, visible.size)
        assertEquals("dünya", visible[0].words.last().text)
    }

    @Test
    fun ignoresHtmlTimedText() {
        assertTrue(YouTubeCaptions.parseJson3("<html>consent</html>").isEmpty())
    }

    @Test
    fun extractsTranscriptParams() {
        val html = """{"foo":{"getTranscriptEndpoint":{"params":"Cgtabc123%3D"}}}"""
        assertEquals("Cgtabc123%3D", YouTubeCaptions.extractTranscriptParams(html))
    }

    @Test
    fun parsesTranscriptSegments() {
        val json = """{"initialSegments":[{"transcriptSegmentRenderer":{"startMs":"1000","endMs":"2500","snippet":{"runs":[{"text":"merhaba dünya"}]}}}]}"""
        val parsed = YouTubeCaptions.parseTranscript(json)
        assertTrue(parsed.isNotEmpty())
        val lines = parsed.values.first()
        assertEquals(1, lines.size)
        assertEquals("merhaba", lines[0].words.first().text)
        assertEquals(1000L, lines[0].startMs)
    }

    @Test
    fun parsesHlsSubtitleTags() {
        val master = """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio",NAME="English",LANGUAGE="en",URI="audio.m3u8"
            #EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="subs",NAME="Turkish",LANGUAGE="tr",URI="tr.vtt"
        """.trimIndent()
        val media = YouTubeCaptions.parseExtXMedia(master)
        assertEquals(2, media.size)
        assertEquals("SUBTITLES", media[1].type)
        assertEquals("tr", media[1].language)
        assertEquals("audio", media[0].groupId)
    }

    @Test
    fun parsesSrv1Xml() {
        val xml = """
            <transcript>
              <text start="1.0" dur="2.0">merhaba dünya</text>
            </transcript>
        """.trimIndent()
        val lines = YouTubeCaptions.parseSrv1(xml)
        assertEquals(1, lines.size)
        assertEquals(listOf("merhaba", "dünya"), lines[0].words.map { it.text })
        assertEquals(1000L, lines[0].startMs)
    }

    @Test
    fun parsesVttCuesIntoWords() {
        val vtt = """
            WEBVTT

            00:00:01.000 --> 00:00:03.000
            merhaba dünya
        """.trimIndent()
        val lines = YouTubeCaptions.parseVtt(vtt)
        assertEquals(1, lines.size)
        assertEquals(listOf("merhaba", "dünya"), lines[0].words.map { it.text })
        assertEquals(1000L, lines[0].startMs)
    }
}
