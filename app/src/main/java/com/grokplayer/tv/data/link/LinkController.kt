package com.grokplayer.tv.data.link

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.grokplayer.tv.data.BrowseListing
import com.grokplayer.tv.data.BrowseVideo
import com.grokplayer.tv.data.DownloadOwnership
import com.grokplayer.tv.data.LibraryStore
import com.grokplayer.tv.data.LibraryVideo
import com.grokplayer.tv.data.SharedFolders
import com.grokplayer.tv.data.StorageSource
import com.grokplayer.tv.data.StreamHttp
import com.grokplayer.tv.data.mediaFileKey
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
import java.net.ConnectException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.PortUnreachableException
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URL
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
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
    private var watchGen = 0
    private var fileServer: FileOfferServer? = null
    val sharedFolders = SharedFolders(app)
    private val cancelledJobs = ConcurrentHashMap.newKeySet<String>()
    private val unauthorized = AtomicBoolean(false)
    private val lastProbeUnreachable = AtomicBoolean(false)
    private var stateMisses = 0
    private var unreachableStreak = 0
    private var connectedFalseStreak = 0
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
        ensureFileServer()
    }

    fun ensureFileServer() {
        if (fileServer != null) return
        fileServer = FileOfferServer(
            tokens = { _ui.value.paired.map { it.token }.toSet() },
            folders = sharedFolders,
            have = { JSONArray() },
            onProgress = { },
        ).also { it.start() }
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

    fun beginPair(target: NearbyPc? = null) {
        if (!_ui.value.visible) return
        val pin = (100000 + Random.nextInt(900000)).toString()
        _ui.update {
            it.copy(
                pin = pin,
                pinUntil = System.currentTimeMillis() + 180_000,
                pairingName = target?.name,
                notice = null,
            )
        }
        scope.launch {
            repeat(90) {
                if (_ui.value.pin != pin) return@launch
                announceOffer(pin)
                val live = _ui.value.nearby
                val hosts = buildList {
                    val chosen = target?.let { want -> live.firstOrNull { it.id == want.id } ?: want }
                    if (chosen != null) add(chosen)
                    addAll(live.filter { it.id != target?.id })
                }
                hosts.forEach { pc ->
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
                                onPaired(json.put("t", "ok"), viaHost = pc.host)
                                return@launch
                            }
                        }
                    }
                }
                delay(2000)
            }
        }
    }

    private fun announceOffer(pin: String) {
        val note = JSONObject().put("t", "offer").put("tv", tvId).put("name", tvName).put("pin", pin)
        sendUdp(note)
        sendWho()
    }

    private fun sendWho() {
        val note = JSONObject().put("t", "who").put("tv", tvId).put("name", tvName)
        sendUdp(note)
        if (isEmulator()) {
            sendUdpTo("10.0.2.2", note)
        }
    }

    private fun isEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT.lowercase()
        return fingerprint.contains("generic") || fingerprint.contains("emulator") || fingerprint.contains("ranchu")
    }

    fun cancelPair() {
        _ui.update { it.copy(pin = null, pinUntil = 0, pairingName = null) }
    }

    fun connect(pc: PairedPc) {
        if (_ui.value.connectedId == pc.id && _ui.value.remote != null) return
        _ui.update {
            it.copy(
                connectingId = pc.id,
                connectedId = if (it.connectedId == pc.id) it.connectedId else null,
                notice = null,
            )
        }
        watch(pc)
    }

    fun disconnect(id: String) {
        val active = _ui.value.connectedId == id || _ui.value.connectingId == id
        if (!active) return
        val pc = _ui.value.paired.firstOrNull { it.id == id }
        cancelJobsFor(id)
        dropSession("Bağlantı kesildi")
        if (pc != null) {
            scope.launch {
                post(pc, "/v1/cmd", JSONObject().put("op", "disconnect").toString())
            }
        }
    }

    fun forget(id: String) {
        val pc = _ui.value.paired.firstOrNull { it.id == id } ?: return
        val wasActive = _ui.value.connectedId == id || _ui.value.connectingId == id
        cancelJobsFor(id)
        val next = _ui.value.paired.filterNot { it.id == id }
        savePaired(next)
        if (wasActive) dropSession("Eşleşme unutuldu")
        _ui.update { it.copy(paired = next, notice = "Eşleşme unutuldu") }
        scope.launch {
            if (wasActive) {
                post(pc, "/v1/cmd", JSONObject().put("op", "disconnect").toString())
            }
            post(pc, "/v1/cmd", JSONObject().put("op", "unpair").toString())
        }
    }

    private fun dropSession(notice: String? = null) {
        stateMisses = 0
        unreachableStreak = 0
        connectedFalseStreak = 0
        watch(null)
        _ui.update { ui ->
            ui.copy(
                connectedId = null,
                connectingId = null,
                remote = null,
                notice = notice ?: ui.notice,
            )
        }
    }

    fun watch(pc: PairedPc?) {
        pollJob?.cancel()
        val gen = ++watchGen
        if (pc == null) return
        pollJob = scope.launch {
            while (isActive && gen == watchGen) {
                refreshState(pc, gen)
                delay(800)
            }
        }
    }

    fun command(pc: PairedPc, op: String, extra: JSONObject = JSONObject()) {
        scope.launch {
            extra.put("op", op)
            post(pc, "/v1/cmd", extra.toString())
            refreshState(pc, watchGen)
        }
    }

    fun send(
        pc: PairedPc,
        video: LibraryVideo,
        mode: SendMode,
        whenPlay: SendWhen,
        startOver: Boolean = true,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
    ): String {
        val remoteUrl = remoteUrl(video)
        val key = videoKey(video)
        val already = _ui.value.remote?.hasVideo(video.title, key) == true
        val stream = !already && (mode == SendMode.Stream || remoteUrl != null)
        val job = TransferJob(
            id = UUID.randomUUID().toString().take(8),
            pcId = pc.id,
            title = video.title,
            kind = if (stream) "stream" else "copy",
            status = "starting",
            done = 0,
            total = 0,
            sourceUrl = remoteUrl,
        )
        cancelledJobs.remove(job.id)
        _ui.update { it.copy(jobs = listOf(job) + it.jobs) }
        if (_ui.value.connectedId != pc.id) connect(pc)
        scope.launch {
            try {
                when {
                    already -> openKnown(pc, video, key, whenPlay, startOver, job, onProgress)
                    stream && remoteUrl != null -> streamRemote(pc, remoteUrl, video.title, whenPlay, job, onProgress, startOver)
                    stream -> streamFile(pc, video, whenPlay, job, onProgress, startOver)
                    else -> copy(pc, video, whenPlay, job, onProgress, startOver)
                }
            } catch (e: Exception) {
                if (!cancelledJobs.contains(job.id)) {
                    updateJob(job.id) { it.copy(status = "failed") }
                    _ui.update { it.copy(notice = e.message) }
                }
            }
        }
        return job.id
    }

    suspend fun browse(pc: PairedPc, path: String): BrowseListing? {
        val raw = get(pc, "/v1/browse?path=" + java.net.URLEncoder.encode(path, Charsets.UTF_8)) ?: return null
        return runCatching { parseListing(raw) }.getOrNull()
    }

    fun fileUrl(pc: PairedPc, path: String): String =
        "http://${pc.host}:${pc.port}/v1/file?path=" +
            java.net.URLEncoder.encode(path, Charsets.UTF_8) +
            "&token=" + java.net.URLEncoder.encode(pc.token, Charsets.UTF_8)

    fun videosFrom(pc: PairedPc, videos: List<BrowseVideo>): List<LibraryVideo> {
        return videos.map { item ->
            val url = fileUrl(pc, item.path)
            LibraryVideo(
                id = "remote:${pc.id}:${item.path}",
                title = item.title,
                uri = android.net.Uri.parse(url),
                durationMs = 0L,
                format = "VOD",
                source = StorageSource.Internal,
                dateAdded = System.currentTimeMillis(),
                lastModified = System.currentTimeMillis(),
                path = null,
                isLive = false,
                isStream = true,
                originUrl = url,
            )
        }
    }

    fun pushProgress(library: LibraryStore) {
        val pc = _ui.value.paired.firstOrNull { it.id == _ui.value.connectedId } ?: return
        val items = JSONArray()
        library.keyedProgressSnapshot().forEach { (key, pos) ->
            if (pos >= 1_000L) {
                items.put(JSONObject().put("key", key).put("positionMs", pos))
            }
        }
        if (items.length() == 0) return
        scope.launch {
            post(pc, "/v1/progress", JSONObject().put("items", items).toString())
        }
    }

    private fun parseListing(raw: String): BrowseListing {
        val json = JSONObject(raw)
        val dirs = (json.optJSONArray("dirs")?.orEmpty() ?: emptyList()).map {
            com.grokplayer.tv.data.BrowseDir(it.optString("name"), it.optString("path"))
        }
        val videos = (json.optJSONArray("videos")?.orEmpty() ?: emptyList()).map {
            BrowseVideo(it.optString("name"), it.optString("path"), it.optLong("size"), it.optString("title"))
        }
        val granted = json.optJSONArray("granted")?.let { array ->
            buildList { for (i in 0 until array.length()) add(array.optString(i)) }
        }.orEmpty()
        return BrowseListing(
            path = json.optString("path"),
            parent = json.optString("parent").ifBlank { null },
            dirs = dirs,
            videos = com.grokplayer.tv.data.MediaOrder.sortByTitle(videos) { it.title },
            granted = granted,
        )
    }

    fun cancelJob(id: String) {
        cancelledJobs.add(id)
        val job = _ui.value.jobs.firstOrNull { it.id == id }
        updateJob(id) { it.copy(status = "stopped") }
        fileServer?.revoke(job?.title)
        val pc = job?.let { item -> _ui.value.paired.firstOrNull { it.id == item.pcId } }
        if (pc != null) {
            scope.launch {
                val body = JSONObject().put("op", "stopStream")
                job.sourceUrl?.let { body.put("url", it) }
                post(pc, "/v1/cmd", body.toString())
            }
        }
    }

    fun cancelJobsFor(pcId: String) {
        _ui.value.jobs.filter { it.pcId == pcId && it.status in listOf("starting", "sending", "streaming") }
            .forEach { cancelJob(it.id) }
    }

    private suspend fun openKnown(
        pc: PairedPc,
        video: LibraryVideo,
        key: String,
        whenPlay: SendWhen,
        startOver: Boolean,
        job: TransferJob,
        onProgress: (Long, Long) -> Unit,
    ) {
        val body = JSONObject()
            .put("op", "openKnown")
            .put("key", key)
            .put("title", video.title)
            .put("play", whenPlay == SendWhen.PlayNow)
            .put("startOver", startOver)
        val ok = post(pc, "/v1/cmd", body.toString())
        if (ok == null) error("PC listedeki dosyayı açamadı")
        updateJob(job.id) { it.copy(status = "queued", kind = "existing", done = 1, total = 1) }
        onProgress(1, 1)
    }

    private fun videoKey(video: LibraryVideo): String {
        val file = videoFile(video)
        if (file != null) return "${file.name.lowercase()}|${file.length()}"
        val remote = remoteUrl(video)
        if (!remote.isNullOrBlank()) return remote.lowercase()
        return "title|${video.title.trim().lowercase()}"
    }

    private suspend fun copy(
        pc: PairedPc,
        video: LibraryVideo,
        whenPlay: SendWhen,
        job: TransferJob,
        onProgress: (Long, Long) -> Unit,
        startOver: Boolean,
    ) {
        val file = videoFile(video) ?: error("Dosya bulunamadı")
        updateJob(job.id) { it.copy(status = "sending", total = file.length()) }
        onProgress(0, file.length())
        putFile(
            pc,
            file,
            video.title,
            whenPlay == SendWhen.PlayNow,
            jobId = job.id,
            uploadName = file.name,
            key = videoKey(video),
            startOver = startOver,
        ) { done, total ->
            updateJob(job.id) { it.copy(done = done, total = total) }
            onProgress(done, total)
        }
        if (cancelledJobs.contains(job.id)) return
        sidecarFiles(video).forEach { side ->
            putFile(pc, side, side.name, play = false, sidecar = true, jobId = job.id) { _, _ -> }
        }
        updateJob(job.id) { it.copy(status = "done", done = file.length(), total = file.length()) }
    }

    private suspend fun streamRemote(
        pc: PairedPc,
        url: String,
        title: String,
        whenPlay: SendWhen,
        job: TransferJob,
        onProgress: (Long, Long) -> Unit,
        startOver: Boolean,
    ) {
        if (cancelledJobs.contains(job.id)) return
        val body = JSONObject()
            .put("op", "open")
            .put("url", url)
            .put("title", title)
            .put("play", whenPlay == SendWhen.PlayNow)
            .put("startOver", startOver)
        val ok = post(pc, "/v1/cmd", body.toString())
        if (ok == null) error("PC yayını açamadı")
        updateJob(job.id) { it.copy(status = "streaming", sourceUrl = url, done = 1, total = 1) }
        onProgress(1, 1)
    }

    private suspend fun streamFile(
        pc: PairedPc,
        video: LibraryVideo,
        whenPlay: SendWhen,
        job: TransferJob,
        onProgress: (Long, Long) -> Unit,
        startOver: Boolean,
    ) {
        val file = videoFile(video) ?: error("Dosya bulunamadı")
        val server = fileServer ?: FileOfferServer().also { fileServer = it; it.start() }
        val url = server.offer(file, pc.host)
        val sub = sidecarFiles(video).firstOrNull()?.let { server.offer(it, pc.host) }
        if (cancelledJobs.contains(job.id)) return
        val body = JSONObject()
            .put("op", "open")
            .put("url", url)
            .put("title", video.title)
            .put("play", whenPlay == SendWhen.PlayNow)
            .put("startOver", startOver)
            .put("subUrl", sub)
        val ok = post(pc, "/v1/cmd", body.toString())
        if (ok == null) error("PC yayını açamadı")
        updateJob(job.id) { it.copy(status = "streaming", total = 1, done = 1, sourceUrl = url) }
        onProgress(1, 1)
    }

    private fun remoteUrl(video: LibraryVideo): String? {
        val origin = video.originUrl?.trim().orEmpty()
        if (origin.startsWith("http://") || origin.startsWith("https://")) return origin
        val raw = video.uri.toString()
        if (raw.startsWith("http://") || raw.startsWith("https://")) return raw
        return null
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
        return DownloadOwnership.sidecarsBeside(videoFile, subtitlesOnly = true)
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
                    "hello" -> onHello(json, packet.address?.hostAddress)
                    "ok" -> onPaired(json)
                    "bye" -> onBye(json)
                }
            } catch (_: SocketTimeoutException) {
            } catch (_: Exception) {
            }
        }
    }

    private fun onHello(json: JSONObject, fromHost: String?) {
        val id = json.optString("pc")
        val advertised = json.optString("host")
        val host = reachableFrom(fromHost, advertised)
        val port = json.optInt("port")
        if (id.isBlank() || host.isBlank() || port <= 0) return
        val pc = NearbyPc(id, json.optString("name").ifBlank { "PC" }, host, port, System.currentTimeMillis())
        _ui.update { ui ->
            val rest = ui.nearby.filterNot { it.id == id }
            ui.copy(nearby = (listOf(pc) + rest).take(8))
        }
        val paired = _ui.value.paired.firstOrNull { it.id == id }
        if (paired != null && (paired.host != host || paired.port != port)) {
            scope.launch {
                if (getRaw(paired.host, paired.port, "/v1/hello") != null) return@launch
                rememberHost(id, host, port)
                val next = _ui.value.paired.map {
                    if (it.id == id) it.copy(name = pc.name) else it
                }
                savePaired(next)
                _ui.update { it.copy(paired = next) }
            }
        }
    }

    private fun onPaired(json: JSONObject, viaHost: String? = null) {
        val claimed = json.optString("tv")
        if (claimed.isNotBlank() && claimed != tvId) return
        val token = json.optString("token")
        val advertised = json.optString("host")
        val host = viaHost?.takeIf { it.isNotBlank() } ?: advertised
        val port = json.optInt("port")
        val id = json.optString("pc")
        if (token.isBlank() || host.isBlank() || id.isBlank()) return
        val peer = PairedPc(id, json.optString("name").ifBlank { "PC" }, host, port, token)
        val next = listOf(peer) + _ui.value.paired.filterNot { it.id == id }
        savePaired(next)
        _ui.update {
            it.copy(
                paired = next,
                pin = null,
                pinUntil = 0,
                pairingName = null,
                notice = "Eşleşti: ${peer.name}",
                connectedId = peer.id,
                connectingId = null,
            )
        }
        markReachable(peer)
        watch(peer)
    }

    private fun markReachable(pc: PairedPc) {
        val now = System.currentTimeMillis()
        _ui.update { ui ->
            val rest = ui.nearby.filterNot { it.id == pc.id }
            ui.copy(nearby = listOf(NearbyPc(pc.id, pc.name, pc.host, pc.port, now)) + rest)
        }
    }

    private fun rememberHost(id: String, host: String, port: Int? = null) {
        val next = _ui.value.paired.map { pc ->
            if (pc.id != id) pc
            else pc.copy(host = host, port = port ?: pc.port)
        }
        if (next == _ui.value.paired) return
        savePaired(next)
        _ui.update { it.copy(paired = next) }
    }

    private fun hostsFor(pc: PairedPc): List<String> =
        listOf(pc.host).filter { it.isNotBlank() }

    private fun reachableFrom(fromHost: String?, advertised: String): String {
        val from = fromHost?.substringBefore('%').orEmpty()
        if (from.isNotBlank() && from != "0.0.0.0" && !from.startsWith("127.")) return from
        return advertised
    }

    private suspend fun tick() {
        while (udp != null) {
            if (_ui.value.visible) sendWho()
            val now = System.currentTimeMillis()
            _ui.update { ui ->
                ui.copy(
                    nearby = ui.nearby.filter { now - it.seenAt < 8_000 },
                    pin = ui.pin?.takeIf { ui.pinUntil > now },
                )
            }
            probePaired()
            delay(2000)
        }
    }

    private suspend fun probePaired() {
        for (pc in _ui.value.paired) {
            if (hello(pc)) {
                val live = _ui.value.paired.firstOrNull { it.id == pc.id } ?: pc
                markReachable(live)
            }
        }
    }

    private suspend fun hello(pc: PairedPc): Boolean = withContext(Dispatchers.IO) {
        for (host in hostsFor(pc)) {
            val raw = getRaw(host, pc.port, "/v1/hello") ?: continue
            val id = runCatching { JSONObject(raw).optString("id") }.getOrDefault("")
            if (id.isBlank() || id == pc.id) {
                if (host != pc.host) rememberHost(pc.id, host, pc.port)
                return@withContext true
            }
        }
        false
    }

    private fun onBye(json: JSONObject) {
        val id = json.optString("pc")
        if (id.isBlank()) return
        if (_ui.value.connectedId != id && _ui.value.connectingId != id) return
        dropSession("PC kapandı")
    }

    private suspend fun refreshState(pc: PairedPc, gen: Int) {
        try {
            refreshStateInner(pc, gen)
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w("GrokPlayer", "state poll failed", error)
        }
    }

    private suspend fun refreshStateInner(pc: PairedPc, gen: Int) {
        if (gen != watchGen) return
        unauthorized.set(false)
        val raw = get(pc, "/v1/state")
        if (gen != watchGen) return
        if (unauthorized.get()) {
            stateMisses++
            unreachableStreak = 0
            if (stateMisses >= 3 && gen == watchGen) {
                dropSession("PC eşleşmeyi kaldırdı")
                forget(pc.id)
            }
            return
        }
        if (raw == null) {
            val live = hello(pc)
            if (live) {
                lastProbeUnreachable.set(false)
                unreachableStreak = 0
            } else if (lastProbeUnreachable.get()) {
                unreachableStreak++
            }
            stateMisses++
            if (LinkWatch.shouldDropAfterMiss(stateMisses, live) && gen == watchGen) {
                val connectingOnly = _ui.value.connectingId == pc.id && _ui.value.connectedId != pc.id
                if (connectingOnly) {
                    watch(null)
                    _ui.update { it.copy(connectingId = null, notice = "Bağlanılamadı") }
                } else {
                    dropSession("PC kapandı")
                }
            }
            return
        }
        stateMisses = 0
        unreachableStreak = 0
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return
        if (json.has("connected") && !json.optBoolean("connected")) {
            connectedFalseStreak++
            if (LinkWatch.shouldDropDisconnected(connectedFalseStreak)) {
                dropSession("Bağlantı kesildi")
            }
            return
        }
        connectedFalseStreak = 0
        val next = runCatching { parseState(json, pc.id) }.getOrNull() ?: return
        val prev = _ui.value.remote
        val staleEmpty = next.playlist.isEmpty() && next.have.isEmpty() && !next.hasMedia &&
            prev != null && (prev.playlist.isNotEmpty() || prev.have.isNotEmpty())
        val live = _ui.value.paired.firstOrNull { it.id == pc.id } ?: pc
        markReachable(live)
        _ui.update { ui ->
            val remote = if (staleEmpty) ui.remote else next
            ui.copy(connectedId = pc.id, connectingId = null, remote = remote)
        }
    }

    private fun parseState(json: JSONObject, pcId: String): RemoteState {
        fun tracks(key: String) = json.optJSONArray(key)?.orEmpty().orEmpty().mapNotNull { item ->
            RemoteTrack(item.optInt("index"), item.optString("label"), item.optBoolean("selected"))
        }
        fun items() = json.optJSONArray("playlist")?.orEmpty().orEmpty().mapNotNull { item ->
            RemoteItem(item.optInt("index"), item.optString("title"), item.optBoolean("current"), item.optString("key"))
        }
        val have = json.optJSONArray("have")?.orEmpty().orEmpty().mapNotNull { item ->
            RemoteHave(item.optString("key"), item.optString("title"), item.optLong("positionMs"))
        }
        val jobs = json.optJSONArray("jobs")?.orEmpty().orEmpty().mapNotNull { item ->
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
        val resume = json.optJSONObject("resume")?.let { item ->
            ResumeOffer(
                item.optString("title"),
                item.optDouble("seconds"),
                item.optDouble("duration"),
            ).takeIf { it.seconds >= 5 }
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
            have = have,
            resume = resume,
        )
    }

    private fun updateJob(id: String, map: (TransferJob) -> TransferJob) {
        _ui.update { ui -> ui.copy(jobs = ui.jobs.map { if (it.id == id) map(it) else it }) }
    }

    private fun sendUdp(json: JSONObject) {
        sendUdpTo("255.255.255.255", json)
    }

    private fun sendUdpTo(host: String, json: JSONObject) {
        try {
            val bytes = json.toString().toByteArray()
            udp?.send(DatagramPacket(bytes, bytes.size, InetAddress.getByName(host), DISCOVER_PORT))
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
        lastProbeUnreachable.set(false)
        var unreachable = false
        for (host in hostsFor(pc)) {
            val text = getAt(host, pc.port, path, pc.token)
            if (text != null) {
                if (host != pc.host) rememberHost(pc.id, host, pc.port)
                lastProbeUnreachable.set(false)
                return@withContext text
            }
            if (lastProbeUnreachable.get()) unreachable = true
        }
        lastProbeUnreachable.set(unreachable)
        null
    }

    private fun getAt(host: String, port: Int, path: String, token: String): String? {
        lastProbeUnreachable.set(false)
        return try {
            val req = Request.Builder()
                .url("http://$host:$port$path")
                .header("Accept", "application/json")
                .apply { if (token.isNotBlank()) header("X-Grok-Token", token) }
                .get()
                .build()
            StreamHttp.client(app).newBuilder()
                .connectTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
                .build()
                .newCall(req)
                .execute()
                .use { resp ->
                val body = resp.body?.string()
                when {
                    resp.code == 401 -> {
                        unauthorized.set(true)
                        Log.w("GrokPlayer", "GET $path HTTP 401")
                        null
                    }
                    resp.isSuccessful -> body
                    else -> {
                        Log.w("GrokPlayer", "GET $path HTTP ${resp.code} ${body?.take(80)}")
                        null
                    }
                }
            }
        } catch (e: Exception) {
            lastProbeUnreachable.set(LinkWatch.isUnreachable(e))
            Log.w("GrokPlayer", "GET $path ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    private fun getRaw(host: String, port: Int, path: String): String? {
        return try {
            val req = Request.Builder().url("http://$host:$port$path").get().build()
            StreamHttp.client(app).newBuilder()
                .connectTimeout(1500, java.util.concurrent.TimeUnit.MILLISECONDS)
                .readTimeout(1500, java.util.concurrent.TimeUnit.MILLISECONDS)
                .build()
                .newCall(req)
                .execute()
                .use { resp -> if (resp.isSuccessful) resp.body?.string() else null }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun post(pc: PairedPc, path: String, body: String): String? = withContext(Dispatchers.IO) {
        val media = "application/json; charset=utf-8".toMediaType()
        val reqBody = body.toRequestBody(media)
        for (host in hostsFor(pc)) {
            val text = try {
                val req = Request.Builder()
                    .url("http://$host:${pc.port}$path")
                    .header("X-Grok-Token", pc.token)
                    .post(reqBody)
                    .build()
                StreamHttp.client(app).newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() else null
                }
            } catch (_: Exception) {
                null
            }
            if (text != null) {
                if (host != pc.host) rememberHost(pc.id, host, pc.port)
                return@withContext text
            }
        }
        null
    }

    private fun putFile(
        pc: PairedPc,
        file: File,
        title: String,
        play: Boolean,
        sidecar: Boolean = false,
        jobId: String? = null,
        uploadName: String? = null,
        key: String? = null,
        startOver: Boolean = true,
        onProgress: (Long, Long) -> Unit,
    ) {
        if (jobId != null && cancelledJobs.contains(jobId)) return
        val name = uploadName ?: file.name
        val conn = open(pc, "/v1/inbox/${encode(name)}", "PUT")
        conn.doOutput = true
        conn.setFixedLengthStreamingMode(file.length())
        conn.setRequestProperty("Content-Type", "application/octet-stream")
        conn.setRequestProperty("X-Title", title)
        conn.setRequestProperty("X-Play", if (play) "now" else "queue")
        conn.setRequestProperty("X-Resume", if (startOver) "start" else "continue")
        if (!key.isNullOrBlank()) conn.setRequestProperty("X-Key", key)
        if (sidecar) conn.setRequestProperty("X-Sidecar", "1")
        try {
            file.inputStream().use { input ->
                conn.outputStream.use { output ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        if (jobId != null && cancelledJobs.contains(jobId)) return
                        val n = input.read(buf)
                        if (n <= 0) break
                        output.write(buf, 0, n)
                        done += n
                        onProgress(done, file.length())
                    }
                }
            }
            conn.inputStream.close()
        } finally {
            conn.disconnect()
        }
    }

    private fun open(pc: PairedPc, path: String, method: String): HttpURLConnection {
        val live = _ui.value.paired.firstOrNull { it.id == pc.id } ?: pc
        return openAt(live.host, live.port, path, method, live.token)
    }

    private fun openAt(host: String, port: Int, path: String, method: String, token: String?): HttpURLConnection {
        val conn = URL("http://$host:$port$path").openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 8000
        conn.readTimeout = 120_000
        if (!token.isNullOrBlank()) conn.setRequestProperty("X-Grok-Token", token)
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

internal data class DeviceBuckets(
    val connected: List<PairedPc>,
    val paired: List<PairedPc>,
    val nearby: List<NearbyPc>,
)

internal data class DeviceActions(
    val pair: Boolean = false,
    val connect: Boolean = false,
    val manage: Boolean = false,
    val disconnect: Boolean = false,
    val forget: Boolean = false,
)

internal object LinkWatch {
    const val UnreachableToDrop = 3
    const val MissesToDrop = 8

    fun buckets(
        nearby: List<NearbyPc>,
        paired: List<PairedPc>,
        connectedId: String?,
    ): DeviceBuckets {
        val connected = paired.filter { it.id == connectedId }
        val rest = paired.filter { it.id != connectedId }
        val unpaired = nearby.filter { seen -> paired.none { it.id == seen.id } }
        return DeviceBuckets(connected, rest, unpaired)
    }

    fun actions(nearby: Boolean, connected: Boolean): DeviceActions = when {
        nearby -> DeviceActions(pair = true)
        connected -> DeviceActions(manage = true, disconnect = true, forget = true)
        else -> DeviceActions(connect = true, forget = true)
    }

    const val DisconnectedToDrop = 3

    fun shouldEndSession(misses: Int, unreachableStreak: Int): Boolean =
        unreachableStreak >= UnreachableToDrop || misses >= MissesToDrop

    fun shouldDropAfterMiss(misses: Int, helloOk: Boolean): Boolean =
        if (helloOk) misses >= 4 else misses >= 2

    fun shouldDropDisconnected(falseStreak: Int): Boolean =
        falseStreak >= DisconnectedToDrop

    fun isUnreachable(error: Throwable): Boolean {
        var cur: Throwable? = error
        while (cur != null) {
            when (cur) {
                is ConnectException,
                is NoRouteToHostException,
                is UnknownHostException,
                is PortUnreachableException,
                is SocketTimeoutException,
                -> return true
            }
            val msg = cur.message.orEmpty().lowercase()
            if (msg.contains("refused") ||
                msg.contains("econnrefused") ||
                msg.contains("enetunreach") ||
                msg.contains("ehostunreach") ||
                msg.contains("network is unreachable")
            ) {
                return true
            }
            cur = cur.cause
        }
        return false
    }
}

private fun JSONArray.orEmpty(): List<JSONObject> = buildList {
    for (i in 0 until this@orEmpty.length()) add(optJSONObject(i) ?: continue)
}

internal class FileOfferServer(
    private val tokens: () -> Set<String> = { emptySet() },
    private val folders: SharedFolders? = null,
    private val have: () -> JSONArray = { JSONArray() },
    private val onProgress: (JSONArray) -> Unit = {},
) {
    private var server: ServerSocket? = null
    private val files = LinkedHashMap<String, File>()

    fun start() {
        server = try {
            ServerSocket(OFFER_PORT)
        } catch (_: Exception) {
            ServerSocket(0)
        }
        Thread {
            while (true) {
                val socket = try { server?.accept() } catch (_: Exception) { break } ?: break
                Thread { serve(socket) }.start()
            }
        }.apply { isDaemon = true; start() }
    }

    fun offer(file: File, peerHost: String? = null): String {
        val key = file.name
        files[key] = file
        val host = pickHost(peerHost)
        return "http://$host:${server?.localPort}/${encodeKey(key)}"
    }

    fun revoke(title: String?) {
        if (title.isNullOrBlank()) return
        files.keys.filter { it.contains(title, ignoreCase = true) || title.contains(it, ignoreCase = true) }
            .forEach { files.remove(it) }
    }

    fun close() {
        runCatching { server?.close() }
        files.clear()
    }

    private fun encodeKey(name: String) = java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20")

    private fun pickHost(peerHost: String?): String {
        if (peerHost == "10.0.2.2" || peerHost == "127.0.0.1" || peerHost == "localhost") {
            return "127.0.0.1"
        }
        val ips = localIps()
        if (!peerHost.isNullOrBlank()) {
            val match = ips.firstOrNull { sameLan(it, peerHost) }
            if (match != null) return match
        }
        return ips.firstOrNull() ?: "127.0.0.1"
    }

    private fun sameLan(a: String, b: String): Boolean {
        val left = a.split('.')
        val right = b.split('.')
        if (left.size != 4 || right.size != 4) return false
        return left[0] == right[0] && left[1] == right[1] && left[2] == right[2]
    }

    private fun serve(socket: java.net.Socket) {
        socket.use { sock ->
            val input = sock.getInputStream().bufferedReader()
            val line = input.readLine() ?: return
            val parts = line.split(" ")
            val method = parts.getOrNull(0) ?: return
            val request = parts.getOrNull(1) ?: return
            val rawPath = request.substringBefore('?').removePrefix("/")
            val query = request.substringAfter('?', "")
            var range: String? = null
            var token = ""
            var contentLength = 0
            while (true) {
                val header = input.readLine() ?: break
                if (header.isEmpty()) break
                if (header.startsWith("Range:", ignoreCase = true)) {
                    range = header.substringAfter(':').trim()
                }
                if (header.startsWith("X-Grok-Token:", ignoreCase = true)) {
                    token = header.substringAfter(':').trim()
                }
                if (header.startsWith("Content-Length:", ignoreCase = true)) {
                    contentLength = header.substringAfter(':').trim().toIntOrNull() ?: 0
                }
            }
            if (rawPath.startsWith("v1/")) {
                serveApi(sock, method, rawPath, query, token, range, contentLength, input)
                return
            }
            val file = files[java.net.URLDecoder.decode(rawPath, "UTF-8")] ?: return
            val total = file.length()
            val (start, end) = parseRange(range, total)
            val length = end - start + 1
            val out = sock.getOutputStream()
            val status = if (start == 0L && end == total - 1L) "200 OK" else "206 Partial Content"
            val head = buildString {
                append("HTTP/1.1 $status\r\n")
                append("Content-Type: application/octet-stream\r\n")
                append("Accept-Ranges: bytes\r\n")
                append("Content-Length: $length\r\n")
                if (status.startsWith("206")) append("Content-Range: bytes $start-$end/$total\r\n")
                append("Connection: close\r\n\r\n")
            }
            out.write(head.toByteArray())
            if (method != "HEAD") {
                file.inputStream().use { inputFile ->
                    inputFile.skip(start)
                    var left = length
                    val buf = ByteArray(64 * 1024)
                    while (left > 0) {
                        val n = inputFile.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        left -= n
                    }
                }
            }
            out.flush()
        }
    }

    private fun serveApi(
        sock: java.net.Socket,
        method: String,
        rawPath: String,
        query: String,
        token: String,
        range: String?,
        contentLength: Int,
        input: java.io.BufferedReader,
    ) {
        val out = sock.getOutputStream()
        val queryToken = queryValue(query, "token")
        val ok = token.takeIf { it.isNotBlank() } ?: queryToken
        if (ok.isBlank() || ok !in tokens()) {
            out.write("HTTP/1.1 401 Unauthorized\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
            return
        }
        when {
            rawPath == "v1/browse" -> {
                val path = queryValue(query, "path")
                val listing = folders?.listing(path) ?: return
                val body = JSONObject()
                    .put("path", listing.path)
                    .put("parent", listing.parent ?: "")
                    .put("granted", JSONArray(listing.granted))
                    .put("dirs", JSONArray(listing.dirs.map { JSONObject().put("name", it.name).put("path", it.path) }))
                    .put("videos", JSONArray(listing.videos.map {
                        JSONObject().put("name", it.name).put("path", it.path).put("size", it.size).put("title", it.title)
                    }))
                    .toString()
                writeJson(out, body)
            }
            rawPath == "v1/file" -> {
                val path = queryValue(query, "path")
                val file = folders?.resolve(path)?.takeIf { it.isFile } ?: return
                streamFile(out, file, range, method)
            }
            rawPath == "v1/have" -> writeJson(out, JSONObject().put("items", have()).toString())
            rawPath == "v1/progress" && method == "POST" -> {
                val buf = CharArray(contentLength.coerceAtLeast(0))
                if (contentLength > 0) input.read(buf, 0, buf.size)
                val items = JSONObject(String(buf)).optJSONArray("items") ?: JSONArray()
                onProgress(items)
                writeJson(out, """{"ok":true}""")
            }
            else -> out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
        }
    }

    private fun writeJson(out: java.io.OutputStream, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        out.write(
            "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray(),
        )
        out.write(bytes)
        out.flush()
    }

    private fun streamFile(out: java.io.OutputStream, file: File, range: String?, method: String) {
        val total = file.length()
        val (start, end) = parseRange(range, total)
        val length = end - start + 1
        val status = if (start == 0L && end == total - 1L) "200 OK" else "206 Partial Content"
        val head = buildString {
            append("HTTP/1.1 $status\r\n")
            append("Content-Type: application/octet-stream\r\n")
            append("Accept-Ranges: bytes\r\n")
            append("Content-Length: $length\r\n")
            if (status.startsWith("206")) append("Content-Range: bytes $start-$end/$total\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(head.toByteArray())
        if (method != "HEAD") {
            file.inputStream().use { inputFile ->
                inputFile.skip(start)
                var left = length
                val buf = ByteArray(64 * 1024)
                while (left > 0) {
                    val n = inputFile.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                    if (n <= 0) break
                    out.write(buf, 0, n)
                    left -= n
                }
            }
        }
        out.flush()
    }

    private fun queryValue(query: String, name: String): String {
        query.split('&').forEach { part ->
            val key = part.substringBefore('=')
            if (key == name) {
                return java.net.URLDecoder.decode(part.substringAfter('=', ""), "UTF-8")
            }
        }
        return ""
    }

    private fun parseRange(header: String?, total: Long): Pair<Long, Long> {
        if (header.isNullOrBlank() || total <= 0) return 0L to (total - 1).coerceAtLeast(0)
        val spec = header.removePrefix("bytes=").trim()
        val startRaw = spec.substringBefore('-', "")
        val endRaw = spec.substringAfter('-', "")
        val start = startRaw.toLongOrNull() ?: 0L
        val end = endRaw.toLongOrNull() ?: (total - 1)
        return start.coerceIn(0, total - 1) to end.coerceIn(start, total - 1)
    }

    companion object {
        const val OFFER_PORT = 17423
    }

    private fun localIps(): List<String> {
        return try {
            java.net.NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .mapNotNull { it.hostAddress }
                .filter { !it.contains(':') && it != "127.0.0.1" }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
