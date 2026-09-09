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
