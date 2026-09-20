package com.grokplayer.tv.data.scan

import android.content.Context
import android.util.Log
import com.grokplayer.tv.data.StreamKind
import org.json.JSONObject

object YouTubeResolver {
    private val idPattern = Regex("^[A-Za-z0-9_-]{11}$")
    private val shortPattern = Regex("""(?:https?://)?(?:www\.)?youtu\.be/([A-Za-z0-9_-]{11})""")
    private val queryPattern = Regex("""[?&]v=([A-Za-z0-9_-]{11})""")
    private val pathPattern = Regex("""youtube(?:-nocookie)?\.com/(?:live|embed|shorts|v)/([A-Za-z0-9_-]{11})""")
    internal const val chromeUa =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15"

    fun watchUrl(raw: String, originUrl: String? = null): String? {
        val id = videoId(raw) ?: originUrl?.let { videoId(it) } ?: return null
        return originUrl?.takeIf { videoId(it) != null }
            ?: raw.takeIf { videoId(it) != null }
            ?: "https://www.youtube.com/watch?v=$id"
    }

    fun posterUrl(id: String): String = "https://i.ytimg.com/vi/$id/hqdefault.jpg"

    fun videoId(raw: String): String? {
        val text = raw.trim()
        if (idPattern.matches(text)) return text
        shortPattern.find(text)?.groupValues?.get(1)?.let { return it }
        val youtube = text.contains("youtube.com", ignoreCase = true) ||
            text.contains("youtube-nocookie.com", ignoreCase = true)
        if (!youtube) return null
        queryPattern.find(text)?.groupValues?.get(1)?.let { return it }
        pathPattern.find(text)?.groupValues?.get(1)?.let { return it }
        return null
    }

    fun resolve(context: Context, id: String, pageUrl: String): ScanHit? {
        val watch = "https://www.youtube.com/watch?v=$id&hl=en&bpctr=9999999999&has_verified=1"
        val page = PageScanner.fetchText(context, watch, chromeUa, referer = "https://www.youtube.com/")
        val visitor = extractVisitor(page)
        var best: ScanHit? = null
        var captions = emptyList<YtCaptionTrack>()
        var audios = emptyList<YtAudioTrack>()
        fun takeCaptions(json: String?) {
            if (json.isNullOrBlank()) return
            runCatching { JSONObject(json) }.getOrNull()?.let { root ->
                captions = YouTubeCaptions.merge(captions, YouTubeCaptions.parseTracks(root))
                audios = YouTubeAudio.merge(audios, YouTubeAudio.parse(root))
            }
        }
        fun consider(hit: ScanHit?) {
            if (hit == null) return
            captions = YouTubeCaptions.merge(captions, hit.captions)
            if (best == null || score(hit) > score(best!!)) best = hit
        }
        val pageJson = extractAssignedJson(page, "ytInitialPlayerResponse")
        takeCaptions(pageJson)
        consider(parsePlayer(pageJson, id, pageUrl))
        if (score(best) < 3 || audios.size <= 1) {
            for (client in clients(id, visitor)) {
                val extra = buildMap {
                    put("X-YouTube-Client-Name", client.id.toString())
                    put("X-YouTube-Client-Version", client.version)
                    if (!visitor.isNullOrBlank()) put("X-Goog-Visitor-Id", visitor)
                }
                val json = PageScanner.postJson(
                    context,
                    "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
                    client.body,
                    client.ua,
                    extra,
                )
                takeCaptions(json)
                consider(parsePlayer(json, id, pageUrl, client.ua))
                if (score(best) >= 3) break
            }
        }
        val merged = YouTubeCaptions.withVisitor(
            YouTubeCaptions.merge(captions, best?.captions.orEmpty()),
            visitor,
        )
        val hydrated = YouTubeCaptions.attachTranscript(
            context,
            YouTubeCaptions.hydrate(
                context,
                merged,
                best?.playUrl,
                best?.referer ?: "https://www.youtube.com/",
                best?.userAgent,
            ),
            page,
            visitor,
        )
        val picked = best?.copy(captions = hydrated, audios = audios)
        Log.i(
            "GrokPlayer",
            "yt $id picked=${picked?.playUrl?.take(96)} caps=${hydrated.size} lined=${hydrated.count { it.lines.isNotEmpty() }} dubs=${audios.size}",
        )
        return picked
    }

    private fun score(hit: ScanHit?): Int {
        if (hit == null) return 0
        val url = hit.playUrl.lowercase()
        return when {
            "hls_variant" in url || ".m3u8" in url -> 3
            ".mpd" in url || "/dash/" in url -> 2
            else -> 1
        }
    }

    private fun parsePlayer(json: String?, id: String, pageUrl: String, ua: String? = null): ScanHit? {
        if (json.isNullOrBlank()) return null
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val status = root.optJSONObject("playabilityStatus")?.optString("status").orEmpty()
        val details = root.optJSONObject("videoDetails")
        val title = details?.optString("title").orEmpty().ifBlank { id }
        val liveNow = root.optJSONObject("microformat")
            ?.optJSONObject("playerMicroformatRenderer")
            ?.optJSONObject("liveBroadcastDetails")
            ?.optBoolean("isLiveNow") == true
        val live = details?.optBoolean("isLive") == true ||
            details?.optString("isLive").equals("true", true) ||
            liveNow ||
            pageUrl.contains("/live/", ignoreCase = true)
        val seconds = details?.optString("lengthSeconds")?.toLongOrNull() ?: 0L
        val thumb = details?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            ?.let { arr ->
                (arr.length() - 1 downTo 0).firstNotNullOfOrNull { i ->
                    arr.optJSONObject(i)?.optString("url")?.takeIf { it.isNotBlank() }
                }
            }
            ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg"
        val streaming = root.optJSONObject("streamingData")
        val playUrl = streaming?.optString("hlsManifestUrl")?.takeIf { it.isNotBlank() }
            ?: streaming?.optString("dashManifestUrl")?.takeIf { it.isNotBlank() }
            ?: streaming?.let { bestProgressive(it) }
        val captions = YouTubeCaptions.parseTracks(root)
        Log.i(
            "GrokPlayer",
            "yt $id status=${status.ifBlank { "?" }} title=${title.take(48)} hls=${!streaming?.optString("hlsManifestUrl").isNullOrBlank()} caps=${captions.size}",
        )
        if (playUrl.isNullOrBlank()) return null
        return ScanHit(
            title = title,
            playUrl = playUrl,
            pageUrl = pageUrl,
            kind = if (live) StreamKind.Live else StreamKind.Vod,
            durationMs = seconds * 1000L,
            thumbnailUrl = thumb,
            referer = "https://www.youtube.com/",
            userAgent = ua ?: chromeUa,
            detail = if (live) "YouTube canlı" else formatClock(seconds),
            captions = captions,
            audios = YouTubeAudio.parse(root),
        )
    }

    private fun bestProgressive(streaming: JSONObject): String? {
        val formats = streaming.optJSONArray("formats") ?: return null
        var best: String? = null
        var score = -1
        for (i in 0 until formats.length()) {
            val item = formats.optJSONObject(i) ?: continue
            val url = item.optString("url")
            if (url.isBlank()) continue
            val width = item.optInt("width")
            if (width > score) {
                score = width
                best = url
            }
        }
        return best
    }

    private fun extractVisitor(html: String?): String? {
        if (html.isNullOrBlank()) return null
        for (key in listOf("\"VISITOR_DATA\":\"", "\"visitorData\":\"")) {
            val at = html.indexOf(key)
            if (at < 0) continue
            val start = at + key.length
            val end = html.indexOf('"', start)
            if (end > start) return html.substring(start, end)
        }
        return null
    }

    private fun extractAssignedJson(html: String?, name: String): String? {
        if (html.isNullOrBlank()) return null
        for (marker in listOf("$name = ", "$name=", "var $name = ", "var $name=")) {
            val at = html.indexOf(marker)
            if (at < 0) continue
            val brace = html.indexOf('{', at + marker.length)
            if (brace >= 0) return sliceObject(html, brace)
        }
        return null
    }

    private fun sliceObject(text: String, brace: Int): String? {
        var depth = 0
        var inString = false
        var escape = false
        for (i in brace until text.length) {
            val ch = text[i]
            if (inString) {
                when {
                    escape -> escape = false
                    ch == '\\' -> escape = true
                    ch == '"' -> inString = false
                }
            } else {
                when (ch) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return text.substring(brace, i + 1)
                    }
                }
            }
        }
        return null
    }

    private fun formatClock(seconds: Long): String {
        if (seconds <= 0) return "YouTube"
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    private data class Client(val name: String, val id: Int, val version: String, val ua: String, val body: String)

    private fun clients(id: String, visitor: String?): List<Client> {
        val visit = if (visitor.isNullOrBlank()) "" else JSONObject.quote(visitor).let { ",\"visitorData\":$it" }
        val safeId = JSONObject.quote(id)
        return listOf(
            Client(
                "VISIONOS",
                101,
                "1.02",
                chromeUa,
                """{"context":{"client":{"clientName":"VISIONOS","clientVersion":"1.02","deviceMake":"Apple","deviceModel":"RealityDevice17,1","osName":"visionOS","osVersion":"26.5.23O471","hl":"en","gl":"US"$visit}},"videoId":$safeId,"contentCheckOk":true,"racyCheckOk":true}""",
            ),
            Client(
                "IOS",
                5,
                "21.26.4",
                "com.google.ios.youtube/21.26.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
                """{"context":{"client":{"clientName":"IOS","clientVersion":"21.26.4","deviceMake":"Apple","deviceModel":"iPhone16,2","osName":"iPhone","osVersion":"18.3.2.22D82","hl":"en","gl":"US"$visit}},"videoId":$safeId,"contentCheckOk":true,"racyCheckOk":true}""",
            ),
            Client(
                "ANDROID",
                3,
                "20.10.38",
                "com.google.android.youtube/20.10.38 (Linux; U; Android 14) gzip",
                """{"context":{"client":{"clientName":"ANDROID","clientVersion":"20.10.38","androidSdkVersion":34,"osName":"Android","osVersion":"14","hl":"en","gl":"US"$visit}},"videoId":$safeId,"contentCheckOk":true,"racyCheckOk":true}""",
            ),
        )
    }
}
