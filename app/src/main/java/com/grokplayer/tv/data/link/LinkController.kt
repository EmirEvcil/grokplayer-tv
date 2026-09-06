package com.grokplayer.tv.data.link

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import com.grokplayer.tv.data.LibraryVideo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.net.URL
import java.util.UUID
import kotlin.random.Random

class LinkController(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("grok_link", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tvId = prefs.getString("tvId", null) ?: UUID.randomUUID().toString().replace("-", "").also {
        prefs.edit().putString("tvId", it).apply()
    }
    private val tvName = Build.MODEL?.takeIf { it.isNotBlank() }?.let { "$it TV" } ?: "GrokPlayer TV"
    private var udp: DatagramSocket? = null
    private var multicast: WifiManager.MulticastLock? = null
    private var pollJob: Job? = null
    private var fileServer: FileOfferServer? = null
    private val _ui = MutableStateFlow(
        LinkUi(
            visible = prefs.getBoolean("visible", true),
            deviceName = tvName,
            paired = loadPaired(),
        ),
    )
    val ui: StateFlow<LinkUi> = _ui

    fun start() {
        if (udp != null) return
        val wm = app.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicast = wm.createMulticastLock("grok-link").apply { setReferenceCounted(false); acquire() }
        udp = DatagramSocket(null).apply {
            reuseAddress = true
            broadcast = true
            bind(InetSocketAddress(DISCOVER_PORT))
            soTimeout = 800
        }
        scope.launch { listenUdp() }
        scope.launch { tick() }
    }

    fun stop() {
        pollJob?.cancel()
        fileServer?.close()
        runCatching { udp?.close() }
        udp = null
        multicast?.release()
    }

    fun setVisible(on: Boolean) {
        prefs.edit().putBoolean("visible", on).apply()
        if (!on) cancelPair()
        _ui.update { it.copy(visible = on) }
    }

    fun beginPair() {
        if (!_ui.value.visible) return
        val pin = (100000 + Random.nextInt(900000)).toString()
        _ui.update { it.copy(pin = pin, pinUntil = System.currentTimeMillis() + 180_000, notice = null) }
        scope.launch {
            repeat(90) {
                if (_ui.value.pin != pin) return@launch
                sendUdp(JSONObject().put("t", "offer").put("tv", tvId).put("name", tvName))
                nearbyTargets().forEach { pc ->
                    runCatching {
                        postRaw(pc.host, pc.port, "/v1/expect", JSONObject()
                            .put("tv", tvId)
                            .put("name", tvName)
                            .put("pin", pin)
                            .toString())
                    }
                    runCatching {
                        val claim = postRaw(
                            pc.host,
                            pc.port,
                            "/v1/claim",
                            JSONObject().put("tv", tvId).put("pin", pin).toString(),
                        )
                        if (!claim.isNullOrBlank()) {
                            val json = JSONObject(claim)
                            if (json.optBoolean("ok") && json.optString("token").isNotBlank()) {
                                onPaired(json.put("t", "ok"))
                                return@launch
                            }
                        }
                    }
                }
                delay(2000)
            }
        }
    }

    private fun nearbyTargets(): List<NearbyPc> {
        val seen = _ui.value.nearby
        if (seen.isNotEmpty()) return seen
        return listOf(NearbyPc("lan", "GrokPlayer PC", emulatorHost(), 17422, System.currentTimeMillis()))
    }

    private fun emulatorHost(): String {
        val fingerprint = Build.FINGERPRINT.lowercase()
        return if (fingerprint.contains("generic") || fingerprint.contains("emulator") || fingerprint.contains("ranchu")) {
            "10.0.2.2"
        } else {
            "192.168.1.1"
        }
    }

    fun cancelPair() {
        _ui.update { it.copy(pin = null, pinUntil = 0) }
    }

    fun disconnect(id: String) {
        if (_ui.value.remote != null) {
            watch(null)
        }
        _ui.update { it.copy(remote = null, notice = "Bağlantı kesildi") }
    }

    fun forget(id: String) {
        disconnect(id)
        val next = _ui.value.paired.filterNot { it.id == id }
        savePaired(next)
        _ui.update { it.copy(paired = next, notice = "Cihaz kaldırıldı") }
    }

    fun watch(pc: PairedPc?) {
        pollJob?.cancel()
        if (pc == null) return
        pollJob = scope.launch {
            while (isActive) {
                refreshState(pc)
                delay(280)
            }
        }
    }

    fun command(pc: PairedPc, op: String, extra: JSONObject = JSONObject()) {
        scope.launch {
            extra.put("op", op)
            post(pc, "/v1/cmd", extra.toString())
            refreshState(pc)
        }
    }

    fun send(
        pc: PairedPc,
        video: LibraryVideo,
        mode: SendMode,
        whenPlay: SendWhen,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ) {
        val job = TransferJob(
            id = UUID.randomUUID().toString().take(8),
            pcId = pc.id,
            title = video.title,
            kind = if (mode == SendMode.Copy) "copy" else "stream",
            status = "starting",
            done = 0,
            total = 0,
        )
        _ui.update { it.copy(jobs = listOf(job) + it.jobs) }
        scope.launch {
            try {
                if (mode == SendMode.Stream) {
                    stream(pc, video, whenPlay, job)
                } else {
                    copy(pc, video, whenPlay, job, onProgress)
                }
            } catch (e: Exception) {
                updateJob(job.id) { it.copy(status = "failed") }
                _ui.update { it.copy(notice = e.message) }
            }
        }
    }

    private suspend fun copy(
        pc: PairedPc,
        video: LibraryVideo,
        whenPlay: SendWhen,
        job: TransferJob,
        onProgress: (Long, Long) -> Unit,
    ) {
        val file = videoFile(video) ?: error("Dosya bulunamadı")
        updateJob(job.id) { it.copy(status = "sending", total = file.length()) }
        putFile(pc, file, video.title, whenPlay == SendWhen.PlayNow) { done, total ->
            updateJob(job.id) { it.copy(done = done, total = total) }
            onProgress(done, total)
        }
        sidecarFiles(video).forEach { side ->
            putFile(pc, side, side.name, play = false, sidecar = true) { _, _ -> }
        }
        updateJob(job.id) { it.copy(status = "done", done = file.length(), total = file.length()) }
    }

    private suspend fun stream(pc: PairedPc, video: LibraryVideo, whenPlay: SendWhen, job: TransferJob) {
        val file = videoFile(video) ?: error("Dosya bulunamadı")
        val server = fileServer ?: FileOfferServer().also { fileServer = it; it.start() }
        val url = server.offer(file)
        val sub = sidecarFiles(video).firstOrNull()?.let { server.offer(it) }
        val body = JSONObject()
            .put("op", "open")
            .put("url", url)
            .put("title", video.title)
            .put("play", whenPlay == SendWhen.PlayNow)
            .put("subUrl", sub)
        post(pc, "/v1/cmd", body.toString())
        updateJob(job.id) { it.copy(status = "streaming", total = file.length(), done = file.length()) }
    }

    private fun videoFile(video: LibraryVideo): File? {
        val path = video.path
        if (!path.isNullOrBlank()) {
            val file = File(path)
            if (file.isFile) return file
        }
        val uri = video.uri.path
        if (!uri.isNullOrBlank()) {
            val file = File(uri)
            if (file.isFile) return file
        }
        return null
    }

    private fun sidecarFiles(video: LibraryVideo): List<File> {
        val videoFile = videoFile(video) ?: return emptyList()
        val parent = videoFile.parentFile ?: return emptyList()
        val stem = videoFile.nameWithoutExtension
        val exts = listOf("srt", "vtt", "ass", "ssa")
        val suffixes = listOf("", ".tr", ".tur", ".en", ".eng")
        return suffixes.flatMap { suffix ->
            exts.mapNotNull { ext -> File(parent, "$stem$suffix.$ext").takeIf { it.isFile } }
        }.distinctBy { it.absolutePath.lowercase() }
    }

    private suspend fun listenUdp() {
        val buf = ByteArray(2048)
        while (udp != null) {
            try {
                val packet = DatagramPacket(buf, buf.size)
                udp?.receive(packet)
                val text = String(packet.data, 0, packet.length)
                val json = JSONObject(text)
                when (json.optString("t")) {
                    "hello" -> onHello(json)
                    "ok" -> onPaired(json)
                }
            } catch (_: SocketTimeoutException) {
            } catch (_: Exception) {
            }
        }
    }

    private fun onHello(json: JSONObject) {
        val id = json.optString("pc")
        val host = json.optString("host")
        val port = json.optInt("port")
        if (id.isBlank() || host.isBlank() || port <= 0) return
        val pc = NearbyPc(id, json.optString("name").ifBlank { "PC" }, host, port, System.currentTimeMillis())
        _ui.update { ui ->
            val rest = ui.nearby.filterNot { it.id == id }
            ui.copy(nearby = (listOf(pc) + rest).take(8))
        }
        val paired = _ui.value.paired.firstOrNull { it.id == id }
        if (paired != null && (paired.host != host || paired.port != port)) {
            val next = _ui.value.paired.map {
                if (it.id == id) it.copy(host = host, port = port, name = pc.name) else it
            }
            savePaired(next)
            _ui.update { it.copy(paired = next) }
        }
    }

    private fun onPaired(json: JSONObject) {
        val claimed = json.optString("tv")
        if (claimed.isNotBlank() && claimed != tvId) return
        val token = json.optString("token")
        val host = json.optString("host")
        val port = json.optInt("port")
        val id = json.optString("pc")
        if (token.isBlank() || host.isBlank() || id.isBlank()) return
        val peer = PairedPc(id, json.optString("name").ifBlank { "PC" }, host, port, token)
        val next = listOf(peer) + _ui.value.paired.filterNot { it.id == id }
        savePaired(next)
        _ui.update { it.copy(paired = next, pin = null, pinUntil = 0, notice = "Eşleşti: ${peer.name}") }
    }

    private suspend fun tick() {
        while (udp != null) {
            val now = System.currentTimeMillis()
            _ui.update { ui ->
                ui.copy(
                    nearby = ui.nearby.filter { now - it.seenAt < 8_000 },
                    pin = ui.pin?.takeIf { ui.pinUntil > now },
                )
            }
            delay(1000)
        }
    }

    private suspend fun refreshState(pc: PairedPc) {
        val raw = get(pc, "/v1/state") ?: return
        val json = JSONObject(raw)
        _ui.update { it.copy(remote = parseState(json, pc.id)) }
    }

    private fun parseState(json: JSONObject, pcId: String): RemoteState {
        fun tracks(key: String) = json.optJSONArray(key).orEmpty().mapNotNull { item ->
            RemoteTrack(item.optInt("index"), item.optString("label"), item.optBoolean("selected"))
        }
        fun items() = json.optJSONArray("playlist").orEmpty().mapNotNull { item ->
            RemoteItem(item.optInt("index"), item.optString("title"), item.optBoolean("current"))
        }
        val jobs = json.optJSONArray("jobs").orEmpty().mapNotNull { item ->
            TransferJob(
                id = item.optString("id"),
                pcId = pcId,
                title = item.optString("title"),
                kind = item.optString("kind"),
                status = item.optString("status"),
                done = item.optLong("done"),
                total = item.optLong("total"),
            )
        }
        return RemoteState(
            playing = json.optBoolean("playing"),
            paused = json.optBoolean("paused"),
            hasMedia = json.optBoolean("hasMedia"),
            positionMs = json.optLong("positionMs"),
            durationMs = json.optLong("durationMs"),
            volume = json.optDouble("volume", 100.0),
            title = json.optString("title").ifBlank { null },
            playlistIndex = json.optInt("playlistIndex", -1),
            playlist = items(),
            audio = tracks("audio"),
            subs = tracks("subs"),
            resolution = json.optString("resolution").ifBlank { null },
            dubbing = json.optString("dubbing").ifBlank { null },
            jobs = jobs,
        )
    }

    private fun updateJob(id: String, map: (TransferJob) -> TransferJob) {
        _ui.update { ui -> ui.copy(jobs = ui.jobs.map { if (it.id == id) map(it) else it }) }
    }

    private fun sendUdp(json: JSONObject) {
        try {
            val bytes = json.toString().toByteArray()
            udp?.send(DatagramPacket(bytes, bytes.size, InetAddress.getByName("255.255.255.255"), DISCOVER_PORT))
        } catch (_: Exception) {
        }
    }

    private fun loadPaired(): List<PairedPc> {
        val raw = prefs.getString("paired", "[]") ?: "[]"
        return JSONArray(raw).orEmpty().mapNotNull { o ->
            PairedPc(
                o.optString("id"),
                o.optString("name"),
                o.optString("host"),
                o.optInt("port"),
                o.optString("token"),
            ).takeIf { it.id.isNotBlank() && it.token.isNotBlank() }
        }
    }

    private fun savePaired(list: List<PairedPc>) {
        val arr = JSONArray()
        list.forEach { pc ->
            arr.put(
                JSONObject()
                    .put("id", pc.id)
                    .put("name", pc.name)
                    .put("host", pc.host)
                    .put("port", pc.port)
                    .put("token", pc.token),
            )
        }
        prefs.edit().putString("paired", arr.toString()).apply()
    }

    private suspend fun get(pc: PairedPc, path: String): String? = withContext(Dispatchers.IO) {
        val conn = open(pc, path, "GET")
        conn.connectTimeout = 2000
        conn.readTimeout = 2500
        try {
            if (conn.responseCode !in 200..299) return@withContext null
            conn.inputStream.bufferedReader().readText()
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun post(pc: PairedPc, path: String, body: String): String? = withContext(Dispatchers.IO) {
        val bytes = body.toByteArray()
        val conn = open(pc, path, "POST")
        conn.doOutput = true
        conn.setFixedLengthStreamingMode(bytes.size)
        conn.setRequestProperty("Content-Type", "application/json")
        conn.outputStream.use { it.write(bytes) }
        try {
            conn.inputStream.bufferedReader().readText()
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun putFile(
        pc: PairedPc,
        file: File,
        title: String,
        play: Boolean,
        sidecar: Boolean = false,
        onProgress: (Long, Long) -> Unit,
    ) {
        val conn = open(pc, "/v1/inbox/${encode(file.name)}", "PUT")
        conn.doOutput = true
        conn.setFixedLengthStreamingMode(file.length())
        conn.setRequestProperty("Content-Type", "application/octet-stream")
        conn.setRequestProperty("X-Title", title)
        conn.setRequestProperty("X-Play", if (play) "now" else "queue")
        if (sidecar) conn.setRequestProperty("X-Sidecar", "1")
        file.inputStream().use { input ->
            conn.outputStream.use { output ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    output.write(buf, 0, n)
                    done += n
                    onProgress(done, file.length())
                }
            }
        }
        conn.inputStream.close()
        conn.disconnect()
    }

    private fun open(pc: PairedPc, path: String, method: String): HttpURLConnection {
        val conn = URL("http://${pc.host}:${pc.port}$path").openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 8000
        conn.readTimeout = 120_000
        conn.setRequestProperty("X-Grok-Token", pc.token)
        return conn
    }

    private fun encode(name: String) = java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20")

    private fun postRaw(host: String, port: Int, path: String, body: String): String? {
        val bytes = body.toByteArray()
        val conn = URL("http://$host:$port$path").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 1500
        conn.readTimeout = 2500
        conn.doOutput = true
        conn.setFixedLengthStreamingMode(bytes.size)
        conn.setRequestProperty("Content-Type", "application/json")
        return try {
            conn.outputStream.use { it.write(bytes) }
            if (conn.responseCode == 204) null
            else conn.inputStream.bufferedReader().readText()
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val DISCOVER_PORT = 17421
    }
}

private fun JSONArray.orEmpty(): List<JSONObject> = buildList {
    for (i in 0 until this@orEmpty.length()) add(optJSONObject(i) ?: continue)
}

internal class FileOfferServer {
    private var server: ServerSocket? = null
    private val files = LinkedHashMap<String, File>()
    private var host: String = "127.0.0.1"

    fun start() {
        server = ServerSocket(0)
        host = localHost()
        Thread {
            while (true) {
                val socket = try { server?.accept() } catch (_: Exception) { break } ?: break
                Thread { serve(socket) }.start()
            }
        }.apply { isDaemon = true; start() }
    }

    fun offer(file: File): String {
        val key = file.name
        files[key] = file
        return "http://$host:${server?.localPort}/$key"
    }

    fun close() {
        runCatching { server?.close() }
    }

    private fun serve(socket: java.net.Socket) {
        socket.use { sock ->
            val input = sock.getInputStream().bufferedReader()
            val line = input.readLine() ?: return
            while (input.readLine()?.isNotEmpty() == true) { }
            val path = line.split(" ").getOrNull(1)?.removePrefix("/") ?: return
            val file = files[java.net.URLDecoder.decode(path, "UTF-8")] ?: return
            val bytes = file.length()
            val out = sock.getOutputStream()
            val head = "HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Length: $bytes\r\nConnection: close\r\n\r\n"
            out.write(head.toByteArray())
            file.inputStream().use { it.copyTo(out) }
            out.flush()
        }
    }

    private fun localHost(): String {
        return try {
            java.net.NetworkInterface.getNetworkInterfaces().toList()
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { !it.isLoopbackAddress && it.hostAddress?.contains(':') != true }
                ?.hostAddress ?: "127.0.0.1"
        } catch (_: Exception) {
            "127.0.0.1"
        }
    }
}
