package com.grokplayer.tv.data

import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class VodDownloaderTest {
    @Test
    fun sameTitleKeepsDisplayNameAndUsesIdForPath() {
        assertEquals("test1", downloadFileName("test1"))
        val a = downloadStem("test1", "aaaaaaaa-1111-4000-8000-000000000001")
        val b = downloadStem("test1", "bbbbbbbb-2222-4000-8000-000000000002")
        assertTrue(a != b)
    }

    @Test
    fun copyToUsesOneGetAndReportsBytes() {
        val payload = ByteArray(180_000) { 7 }
        val gets = AtomicInteger(0)
        withServer { port, add ->
            add("/clip.mp4") { gets.incrementAndGet(); payload }
            StreamHttp.attach(OkHttpClient())
            val reports = mutableListOf<Pair<Long, Long>>()
            val out = ByteArrayOutputStream()
            val copied = StreamHttp.copyTo("http://127.0.0.1:$port/clip.mp4", out) { done, total ->
                reports += done to total
            }
            assertEquals(1, gets.get())
            assertEquals(payload.size.toLong(), copied)
            assertEquals(payload.size.toLong(), reports.last().first)
            assertEquals(payload.size.toLong(), reports.last().second)
            assertTrue("expected mid-copy reports, got ${reports.size}", reports.size > 1)
            assertTrue(reports.any { it.first in 1 until payload.size.toLong() })
        }
    }

    @Test
    fun copyToStopsWhenCancelled() {
        val payload = ByteArray(400_000) { 3 }
        val cancelled = AtomicBoolean(false)
        withServer { port, add ->
            add("/big.mp4") { payload }
            StreamHttp.attach(OkHttpClient())
            try {
                StreamHttp.copyTo(
                    url = "http://127.0.0.1:$port/big.mp4",
                    out = ByteArrayOutputStream(),
                    cancelled = { cancelled.get() },
                ) { copied, _ ->
                    if (copied > 40_000) cancelled.set(true)
                }
                fail("cancel should abort copyTo")
            } catch (error: IllegalStateException) {
                assertEquals("İptal edildi", error.message)
            }
        }
    }

    @Test
    fun progressiveCancelDoesNotLeaveASuccessfulFile() {
        val payload = ByteArray(300_000) { 9 }
        val cancelled = AtomicBoolean(false)
        withServer { port, add ->
            add("/vod.mp4") { payload }
            StreamHttp.attach(OkHttpClient())
            val dest = File.createTempFile("vod", ".ts")
            dest.deleteOnExit()
            val mp4 = File(dest.parentFile, dest.nameWithoutExtension + ".mp4")
            mp4.deleteOnExit()
            val ticks = mutableListOf<Float>()
            try {
                VodDownloader.download(
                    url = "http://127.0.0.1:$port/vod.mp4",
                    dest = dest,
                    maxHeight = 720,
                    cancelled = { cancelled.get() },
                    onProgress = { value ->
                        ticks += value
                        if (value >= 0.2f) cancelled.set(true)
                    },
                )
                fail("cancel should abort the download")
            } catch (error: IllegalStateException) {
                assertEquals("İptal edildi", error.message)
            }
            assertTrue("progress should move before cancel, got $ticks", ticks.any { it > 0f && it < 1f })
            assertTrue("cancelled mp4 must be deleted, leftover=${mp4.length()}", !mp4.exists())
            assertTrue("cancelled ts stub must be deleted", !dest.exists() || dest.length() == 0L)
        }
    }

    @Test
    fun hlsCancelDeletesPartialFile() {
        val seg1 = ByteArray(120_000) { 1 }
        val seg2 = ByteArray(120_000) { 2 }
        val cancelled = AtomicBoolean(false)
        withServer { port, add ->
            add("/master.m3u8") {
                """
                #EXTM3U
                #EXT-X-TARGETDURATION:2
                #EXT-X-ENDLIST
                #EXTINF:2,
                /a.ts
                #EXTINF:2,
                /b.ts
                """.trimIndent().toByteArray()
            }
            add("/a.ts") { seg1 }
            add("/b.ts") { seg2 }
            StreamHttp.attach(OkHttpClient())
            val dest = File.createTempFile("hls", ".ts")
            dest.deleteOnExit()
            try {
                VodDownloader.download(
                    url = "http://127.0.0.1:$port/master.m3u8",
                    dest = dest,
                    maxHeight = 720,
                    cancelled = { cancelled.get() },
                    onProgress = { value -> if (value >= 0.2f) cancelled.set(true) },
                )
                fail("cancel should abort the download")
            } catch (error: IllegalStateException) {
                assertEquals("İptal edildi", error.message)
            }
            assertTrue("cancelled hls dest must be deleted, leftover=${dest.length()}", !dest.exists())
        }
    }

    @Test
    fun youtubeFmp4PlaylistIncludesInitMap() {
        val body = """
            #EXTM3U
            #EXT-X-VERSION:7
            #EXT-X-TARGETDURATION:5
            #EXT-X-MAP:URI="init.mp4"
            #EXT-X-ENDLIST
            #EXTINF:5.0,
            seg0.m4s
            #EXTINF:5.0,
            seg1.m4s
        """.trimIndent()
        val parts = hlsMediaParts(body)
        assertEquals("init.mp4", parts.mapUri)
        assertEquals(listOf("seg0.m4s", "seg1.m4s"), parts.segments)
        assertTrue(parts.fragmentedMp4)
    }

    @Test
    fun prefersAvcOverHigherVp9() {
        val picked = chooseHlsVariant(
            listOf(
                HlsVideoVariant(720, 2_500_000, "https://x/vp9", "aud", codecs = "vp09.00.51.08,mp4a.40.2"),
                HlsVideoVariant(720, 1_200_000, "https://x/avc", "aud", codecs = "avc1.4d401f,mp4a.40.2"),
            ),
            720,
        )!!
        assertEquals("https://x/avc", picked.uri)
    }

    @Test
    fun chooseVariantDoesNotFallToLowestWhenCapMisses() {
        val variants = listOf(
            HlsVideoVariant(144, 100_000, "https://x/144", "aud"),
            HlsVideoVariant(1080, 5_000_000, "https://x/1080", "aud"),
        )
        val picked = chooseHlsVariant(variants, 720)!!
        assertEquals(1080, picked.height)
        val with360 = chooseHlsVariant(
            variants + HlsVideoVariant(360, 800_000, "https://x/360", "aud"),
            720,
        )!!
        assertEquals(360, with360.height)
        val exact = chooseHlsVariant(variants, 144)!!
        assertEquals(144, exact.height)
        val best = chooseHlsVariant(variants, 0)!!
        assertEquals(1080, best.height)
    }

    @Test
    fun googlevideoSegmentsCountAsFragmentedMp4() {
        val parts = hlsMediaParts(
            """
            #EXTM3U
            #EXT-X-MAP:URI="https://rr1.googlevideo.com/init"
            #EXTINF:5.0,
            https://rr1.googlevideo.com/videoplayback/sq/0
            """.trimIndent(),
        )
        assertTrue(parts.fragmentedMp4)
        assertEquals("https://rr1.googlevideo.com/init", parts.mapUri)
    }

    @Test
    fun mpegTsPlaylistIsNotFragmentedMp4() {
        val body = """
            #EXTM3U
            #EXT-X-TARGETDURATION:2
            #EXT-X-ENDLIST
            #EXTINF:2,
            a.ts
            #EXTINF:2,
            b.ts
        """.trimIndent()
        val parts = hlsMediaParts(body)
        assertEquals(null, parts.mapUri)
        assertEquals(listOf("a.ts", "b.ts"), parts.segments)
        assertTrue(!parts.fragmentedMp4)
    }

    @Test
    fun fmp4HlsWritesInitThenSegmentsAsMp4() {
        val init = byteArrayOf(0, 0, 0, 24, 102, 116, 121, 112, 105, 115, 111, 109) + ByteArray(12)
        val seg = byteArrayOf(0, 0, 0, 16, 109, 111, 111, 102) + ByteArray(8)
        withServer { port, add ->
            add("/master.m3u8") {
                """
                #EXTM3U
                #EXT-X-STREAM-INF:BANDWIDTH=1000,RESOLUTION=640x360
                /media.m3u8
                """.trimIndent().toByteArray()
            }
            add("/media.m3u8") {
                """
                #EXTM3U
                #EXT-X-TARGETDURATION:5
                #EXT-X-MAP:URI="init.mp4"
                #EXT-X-ENDLIST
                #EXTINF:5,
                /a.m4s
                """.trimIndent().toByteArray()
            }
            add("/init.mp4") { init }
            add("/a.m4s") { seg }
            StreamHttp.attach(OkHttpClient())
            val dir = File.createTempFile("ytd", "dir").apply { delete(); mkdirs(); deleteOnExit() }
            val dest = File(dir, "clip.ts")
            val file = VodDownloader.download(
                url = "http://127.0.0.1:$port/master.m3u8",
                dest = dest,
                maxHeight = 720,
                cancelled = { false },
                onProgress = {},
            )
            assertEquals("m3u8", file.extension.lowercase())
            assertTrue(isPlayableDownload(file))
            val text = file.readText()
            assertTrue(text.contains("v.m3u8"))
            val video = File(dir, "v.m3u8")
            assertTrue(video.isFile)
            assertTrue(video.readText().contains("v-init.seg"))
            assertEquals((init.size).toLong(), File(dir, "v-init.seg").length())
            assertEquals((seg.size).toLong(), File(dir, "v-0000.seg").length())
            assertEquals(5_000L, localHlsDurationMs(file))
        }
    }

    @Test
    fun fmp4HlsWithAudioGroupWritesMp4AndM4a() {
        val init = byteArrayOf(0, 0, 0, 24, 102, 116, 121, 112, 105, 115, 111, 109) + ByteArray(20)
        val vseg = byteArrayOf(0, 0, 0, 16, 109, 111, 111, 102) + ByteArray(20)
        val ainit = byteArrayOf(0, 0, 0, 24, 102, 116, 121, 112, 77, 52, 65, 32) + ByteArray(20)
        val aseg = byteArrayOf(0, 0, 0, 16, 109, 111, 111, 102) + ByteArray(16)
        withServer { port, add ->
            add("/master.m3u8") {
                """
                #EXTM3U
                #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud",NAME="Turkish",DEFAULT=NO,AUTOSELECT=YES,LANGUAGE="tr",URI="/audio-tr.m3u8"
                #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud",NAME="original",DEFAULT=YES,AUTOSELECT=YES,LANGUAGE="en",URI="/audio-en.m3u8"
                #EXT-X-STREAM-INF:BANDWIDTH=1000,RESOLUTION=640x360,AUDIO="aud"
                /video.m3u8
                """.trimIndent().toByteArray()
            }
            add("/video.m3u8") {
                """
                #EXTM3U
                #EXT-X-TARGETDURATION:5
                #EXT-X-MAP:URI="init.mp4"
                #EXT-X-ENDLIST
                #EXTINF:5,
                /v.m4s
                """.trimIndent().toByteArray()
            }
            add("/audio-tr.m3u8") {
                """
                #EXTM3U
                #EXT-X-TARGETDURATION:5
                #EXT-X-MAP:URI="ainit.mp4"
                #EXT-X-ENDLIST
                #EXTINF:5,
                /a.m4s
                """.trimIndent().toByteArray()
            }
            add("/audio-en.m3u8") {
                """
                #EXTM3U
                #EXT-X-TARGETDURATION:5
                #EXT-X-MAP:URI="ainit.mp4"
                #EXT-X-ENDLIST
                #EXTINF:5,
                /a.m4s
                """.trimIndent().toByteArray()
            }
            add("/init.mp4") { init }
            add("/v.m4s") { vseg }
            add("/ainit.mp4") { ainit }
            add("/a.m4s") { aseg }
            StreamHttp.attach(OkHttpClient())
            val dir = File.createTempFile("yta", "dir").apply { delete(); mkdirs(); deleteOnExit() }
            val dest = File(dir, "clip.ts")
            val file = VodDownloader.download(
                url = "http://127.0.0.1:$port/master.m3u8",
                dest = dest,
                maxHeight = 720,
                cancelled = { false },
                onProgress = {},
                preferredAudioLang = "tr",
            )
            assertEquals("m3u8", file.extension.lowercase())
            assertTrue(isPlayableDownload(file))
            val master = file.readText()
            assertTrue(master.contains("a-en.m3u8"))
            assertTrue(master.contains("a-tr.m3u8"))
            val defaultLine = master.lineSequence().first { it.contains("LANGUAGE=\"tr\"") }
            assertTrue(defaultLine.contains("DEFAULT=YES"))
            val originalLine = master.lineSequence().first { it.contains("LANGUAGE=\"en\"") }
            assertTrue(originalLine.contains("DEFAULT=NO"))
            assertTrue(File(dir, "a-en.m3u8").isFile)
            assertTrue(File(dir, "a-tr.m3u8").isFile)
            assertEquals(5_000L, localHlsDurationMs(file))
        }
    }

    @Test
    fun moofWithoutFtypIsNotPlayable() {
        val frag = File.createTempFile("frag", ".ts")
        frag.writeBytes(byteArrayOf(0, 0, 0, 16, 109, 111, 111, 102) + ByteArray(40))
        frag.deleteOnExit()
        assertEquals("mp4", sniffContainer(frag))
        assertTrue(!isPlayableDownload(frag))
        assertEquals(null, localPlaybackFile(frag.absolutePath, null))
    }

    @Test
    fun ftypNamedTsIsPlayableLocalFile() {
        val mp4 = File.createTempFile("okv", ".ts")
        mp4.writeBytes(byteArrayOf(0, 0, 0, 24, 102, 116, 121, 112, 105, 115, 111, 109) + ByteArray(40))
        mp4.deleteOnExit()
        assertEquals("mp4", sniffContainer(mp4))
        assertTrue(isPlayableDownload(mp4))
        assertEquals(mp4.absolutePath, localPlaybackFile(mp4.absolutePath, null)?.absolutePath)
    }

    @Test
    fun sniffDetectsFtypAndTsSync() {
        val mp4 = File.createTempFile("box", ".ts")
        mp4.writeBytes(byteArrayOf(0, 0, 0, 24, 102, 116, 121, 112, 105, 115, 111, 109))
        mp4.deleteOnExit()
        assertEquals("mp4", sniffContainer(mp4))
        val ts = File.createTempFile("mpeg", ".bin")
        ts.writeBytes(byteArrayOf(0x47, 0x40, 0x00, 0x10) + ByteArray(40))
        ts.deleteOnExit()
        assertEquals("ts", sniffContainer(ts))
        assertTrue(isPlayableDownload(ts))
    }

    @Test
    fun pickDefaultAudioIgnoresFirstAutoselectDub() {
        val tracks = com.grokplayer.tv.data.scan.YouTubeCaptions.parseExtXMedia(
            """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud",NAME="Turkish",DEFAULT=NO,AUTOSELECT=YES,LANGUAGE="tr",URI="tr.m3u8"
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="aud",NAME="original",DEFAULT=YES,AUTOSELECT=YES,LANGUAGE="en",URI="en.m3u8"
            """.trimIndent(),
        )
        val picked = pickDefaultAudio(tracks, "aud", null)
        assertEquals("en", picked?.language)
        assertEquals("en.m3u8", picked?.uri)
        val turkish = pickDefaultAudio(tracks, "aud", "tr")
        assertEquals("tr", turkish?.language)
        assertEquals("tr.m3u8", turkish?.uri)
    }

    @Test
    fun rewriteMediaPlaylistKeepsInitAndForcesVod() {
        val body = """
            #EXTM3U
            #EXT-X-TARGETDURATION:5
            #EXT-X-MAP:URI="init.mp4"
            #EXTINF:5.0,
            seg0.m4s
        """.trimIndent()
        val rewritten = rewriteMediaPlaylist(body, listOf("v-init.seg", "v-0000.seg"))
        assertTrue(rewritten.contains("#EXT-X-MAP:URI=\"v-init.seg\""))
        assertTrue(rewritten.contains("v-0000.seg"))
        assertTrue(!rewritten.contains("seg0.m4s"))
        assertTrue(rewritten.contains("#EXT-X-PLAYLIST-TYPE:VOD"))
        assertTrue(rewritten.contains("#EXT-X-ENDLIST"))
        assertEquals(5_000L, hlsDurationMs(body))
    }

    @Test
    fun hlsReportsProgressDuringSegmentsNotOnlyAtTheEnd() {
        val seg1 = ByteArray(80_000) { 1 }
        val seg2 = ByteArray(80_000) { 2 }
        withServer { port, add ->
            add("/master.m3u8") {
                """
                #EXTM3U
                #EXT-X-TARGETDURATION:2
                #EXT-X-ENDLIST
                #EXTINF:2,
                /a.ts
                #EXTINF:2,
                /b.ts
                """.trimIndent().toByteArray()
            }
            add("/a.ts") { seg1 }
            add("/b.ts") { seg2 }
            StreamHttp.attach(OkHttpClient())
            val dest = File.createTempFile("hls", ".ts")
            dest.deleteOnExit()
            val ticks = mutableListOf<Float>()
            val file = VodDownloader.download(
                url = "http://127.0.0.1:$port/master.m3u8",
                dest = dest,
                maxHeight = 720,
                cancelled = { false },
                onProgress = { ticks += it },
            )
            assertEquals(dest, file)
            assertTrue(file.length() >= (seg1.size + seg2.size).toLong())
            assertTrue("stuck at 0 then jump, ticks=$ticks", ticks.any { it in 0.05f..0.7f })
            assertTrue("never left 0 until the end, ticks=$ticks", ticks.count { it < 1f } >= 2)
        }
    }

    private fun withServer(block: (port: Int, add: (path: String, body: () -> ByteArray) -> Unit) -> Unit) {
        val routes = ConcurrentHashMap<String, () -> ByteArray>()
        val server = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))
        val thread = Thread {
            try {
                while (!server.isClosed) {
                    val socket = server.accept()
                    Thread {
                        socket.use { client ->
                            val input = client.getInputStream()
                            val header = StringBuilder()
                            while (true) {
                                val ch = input.read()
                                if (ch < 0) return@Thread
                                header.append(ch.toChar())
                                if (header.endsWith("\r\n\r\n")) break
                            }
                            val line = header.lineSequence().firstOrNull().orEmpty()
                            val path = line.split(" ").getOrNull(1)?.substringBefore("?").orEmpty()
                            val body = routes[path]?.invoke()
                            val out = client.getOutputStream()
                            if (body == null) {
                                out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                            } else {
                                out.write(
                                    "HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray(),
                                )
                                out.write(body)
                            }
                            out.flush()
                        }
                    }.start()
                }
            } catch (_: Exception) {
            }
        }
        thread.isDaemon = true
        thread.start()
        try {
            block(server.localPort) { path, body -> routes[path] = body }
        } finally {
            server.close()
        }
    }
}
