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
    fun listsOnlyTracksThisVideoHas() {
        val root = JSONObject(
            """
            {"captions":{"playerCaptionsTracklistRenderer":{
              "captionTracks":[
                {"baseUrl":"https://www.youtube.com/api/timedtext?v=abc&lang=en&kind=asr","languageCode":"en","kind":"asr","name":{"simpleText":"English (auto)"}}
              ],
              "translationLanguages":[
                {"languageCode":"de","languageName":{"simpleText":"German"}},
                {"languageCode":"tr","languageName":{"simpleText":"Turkish"}},
                {"languageCode":"ab","languageName":{"simpleText":"Abkhazian"}}
              ]
            }}}
            """.trimIndent(),
        )
        val tracks = YouTubeCaptions.parseTracks(root)
        assertEquals(1, tracks.size)
        assertEquals("en", tracks[0].language)
        assertTrue(tracks[0].auto)
        assertTrue(tracks.none { it.translate })
        assertTrue(tracks.none { "tlang=" in it.baseUrl })
        val loaded = tracks[0].copy(
            lines = listOf(YtCaptionLine(0, 1_000, listOf(YtCaptionWord("hi", 0, 1_000)))),
        )
        val extra = YouTubeCaptions.preferredTranslation(listOf(loaded), "tr")!!
        assertEquals("tr", extra.language)
        assertTrue(extra.translate)
        assertTrue(extra.baseUrl.contains("tlang=tr"))
        assertEquals(null, YouTubeCaptions.preferredTranslation(listOf(loaded), "en"))
        assertEquals(listOf("en"), YouTubeCaptions.tracksToLoad(tracks, "tr").map { it.language })
    }

    @Test
    fun readableLinesJoinRapidCuesAndHoldAPause() {
        fun line(start: Long, end: Long, text: String) =
            YtCaptionLine(start, end, listOf(YtCaptionWord(text, start, end)))
        val shown = YouTubeCaptions.readableLines(
            listOf(
                line(0, 600, "one"),
                line(600, 1_200, "two"),
                line(1_200, 1_800, "three"),
                line(5_000, 5_400, "later"),
            ),
        )
        assertEquals(2, shown.size)
        assertEquals(listOf("one", "two", "three"), shown[0].words.map { it.text })
        assertTrue(shown[0].endMs >= 1_800L)
        assertEquals("later", shown[1].words.single().text)
        assertTrue(shown[1].endMs - shown[1].startMs >= 2_000L)
        assertTrue(YouTubeCaptions.visibleLines(shown, 1_500L).any { line -> line.words.any { it.text == "three" } })
        assertTrue(YouTubeCaptions.visibleLines(shown, 6_500L).any { line -> line.words.any { it.text == "later" } })
        assertTrue(YouTubeCaptions.visibleLines(shown, 6_500L).none { line -> line.words.any { it.text == "one" } })
    }

    @Test
    fun parsesSrv3Paragraphs() {
        val xml = """
            <timedtext>
              <body>
                <p t="1000" d="2000">hello world</p>
                <p t="4000" d="800">again</p>
              </body>
            </timedtext>
        """.trimIndent()
        val lines = YouTubeCaptions.parseSrv3(xml)
        assertEquals(2, lines.size)
        assertEquals("hello", lines[0].words.first().text)
        assertEquals(1000L, lines[0].startMs)
        assertEquals("again", lines[1].words.first().text)
    }

    @Test
    fun parsesSrv3WhenDurationComesBeforeStart() {
        val xml = """<timedtext><body><p d="2000" t="500" w="1"><s>hello</s><s t="400"> world</s></p><p t="3000" d="800" a="1">
</p></body></timedtext>"""
        val lines = YouTubeCaptions.parseSrv3(xml)
        assertEquals(1, lines.size)
        assertEquals(500L, lines[0].startMs)
        assertEquals("hello", lines[0].words.first().text)
        assertEquals("world", lines[0].words.last().text)
    }

    @Test
    fun withFormatKeepsOtherQueryParams() {
        val track = YtCaptionTrack(
            "en",
            "English",
            "https://www.youtube.com/api/timedtext?fmt=json3&lang=en&v=abc",
            auto = true,
        )
        val url = track.withFormat("srv3")
        assertTrue(url.contains("fmt=srv3"))
        assertTrue(url.contains("lang=en"))
        assertTrue(url.contains("v=abc"))
        assertTrue(!url.contains("?&"))
        assertTrue(!url.contains("&&"))
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
