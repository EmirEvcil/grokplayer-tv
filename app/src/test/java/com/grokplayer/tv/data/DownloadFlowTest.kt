package com.grokplayer.tv.data

import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadFlowTest {
    @Test
    fun singleVideoThenDuplicateIsSkipped() {
        val url = "http://pc/v1/file?path=folder1/clip.mp4"
        val done = snap("1", "clip", DownloadStatus.Done, "/tmp/s01e01.mp4", url)
        val first = DownloadPolicy.decide(emptyList(), "clip", url)
        assertEquals(DownloadPolicy.Action.Enqueue, first.action)
        val again = DownloadPolicy.decide(listOf(done), "clip", url) { true }
        assertEquals(DownloadPolicy.Action.SkipDone, again.action)
    }

    @Test
    fun sameTitleInAnotherFolderIsANewDownload() {
        val done = snap(
            "1",
            "clip",
            DownloadStatus.Done,
            "/tmp/folder1/clip.mp4",
            "http://pc/v1/file?path=folder1/clip.mp4",
        )
        val other = DownloadPolicy.decide(
            listOf(done),
            "clip",
            "http://pc/v1/file?path=folder2/clip.mp4",
        ) { true }
        assertEquals(DownloadPolicy.Action.Enqueue, other.action)
    }

    @Test
    fun failedDownloadIsRetryableAndDoesNotDuplicate() {
        val failed = snap("1", "clip", DownloadStatus.Failed, null, "http://x/clip.mp4")
        val decision = DownloadPolicy.decide(listOf(failed), "clip", "http://x/clip.mp4")
        assertEquals(DownloadPolicy.Action.RetryFailed, decision.action)
        assertEquals("1", decision.existingId)
    }

    @Test
    fun missingLocalFileIsRetryableInsteadOfDuplicate() {
        val gone = snap("1", "clip", DownloadStatus.Done, "/missing/clip.mp4", "http://x/clip.mp4")
        val decision = DownloadPolicy.decide(listOf(gone), "clip", "http://x/clip.mp4") { false }
        assertEquals(DownloadPolicy.Action.RetryFailed, decision.action)
        assertEquals("1", decision.existingId)
    }

    @Test
    fun playlistBatchSkipsDoneAndRetriesFailed() {
        val existing = listOf(
            snap("a", "Show S01E01", DownloadStatus.Done, "/tmp/1.mp4", "http://pc/1.mp4"),
            snap("b", "Show S01E02", DownloadStatus.Failed, null, "http://pc/2.mp4"),
            snap("c", "Show S01E03", DownloadStatus.Queued, null, "http://pc/3.mp4"),
        )
        val result = DownloadPolicy.planBatch(
            existing,
            listOf(
                "Show S01E01" to "http://pc/1.mp4",
                "Show S01E02" to "http://pc/2.mp4",
                "Show S01E03" to "http://pc/3.mp4",
                "Show S01E04" to "http://pc/4.mp4",
                "Show S01E04" to "http://pc/4.mp4",
            ),
        ) { true }
        assertEquals(1, result.queued)
        assertEquals(1, result.retried)
        assertEquals(2, result.skippedDone)
        assertEquals(1, result.skippedActive)
        assertTrue(result.notice().contains("kuyruğa"))
    }

    @Test
    fun collectionBatchSameAsPlaylistBatch() {
        val result = DownloadPolicy.planBatch(
            emptyList(),
            listOf(
                "Toy Story 1" to "http://pc/ts1.mp4",
                "Toy Story 2" to "http://pc/ts2.mp4",
            ),
        )
        assertEquals(2, result.queued)
        assertEquals(0, result.skippedDone)
    }

    @Test
    fun downloadedFileIsPlayableOffline() {
        val payload = ByteArray(32_000) { 4 }
        withServer { port, add ->
            add("/ep.mp4") { payload }
            StreamHttp.attach(OkHttpClient())
            val dest = File.createTempFile("off", ".ts")
            dest.deleteOnExit()
            val file = VodDownloader.download(
                url = "http://127.0.0.1:$port/ep.mp4",
                dest = dest,
                maxHeight = 720,
                cancelled = { false },
                onProgress = {},
            )
            assertTrue(file.exists())
            assertEquals(payload.size.toLong(), file.length())
            assertTrue(OfflineCollections.isLocalFile(file.absolutePath, "file"))
            val skip = DownloadPolicy.decide(
                listOf(snap("z", "ep", DownloadStatus.Done, file.absolutePath, "http://127.0.0.1:$port/ep.mp4")),
                "ep",
                "http://127.0.0.1:$port/ep.mp4",
            ) { File(it).exists() }
            assertEquals(DownloadPolicy.Action.SkipDone, skip.action)
        }
    }

    private fun snap(
        id: String,
        title: String,
        status: DownloadStatus,
        path: String?,
        url: String = "http://x/$id",
    ) = DownloadPolicy.Snapshot(id, title, url, status, path)

    private fun withServer(block: (Int, (String, () -> ByteArray) -> Unit) -> Unit) {
        val routes = ConcurrentHashMap<String, () -> ByteArray>()
        ServerSocket(0, 50, InetAddress.getByName("127.0.0.1")).use { server ->
            val port = server.localPort
            val thread = Thread {
                while (!server.isClosed) {
                    val socket = runCatching { server.accept() }.getOrNull() ?: break
                    Thread {
                        socket.use { s ->
                            val req = s.getInputStream().bufferedReader()
                            val line = req.readLine() ?: return@Thread
                            while (req.readLine().orEmpty().isNotBlank()) Unit
                            val path = line.split(" ").getOrNull(1) ?: "/"
                            val body = routes[path]?.invoke() ?: ByteArray(0)
                            val out = s.getOutputStream()
                            out.write(
                                "HTTP/1.1 200 OK\r\nContent-Type: video/mp4\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
                                    .toByteArray(),
                            )
                            out.write(body)
                            out.flush()
                        }
                    }.start()
                }
            }
            thread.isDaemon = true
            thread.start()
            try {
                block(port) { path, body -> routes[path] = body }
            } finally {
                runCatching { server.close() }
            }
        }
    }
}
