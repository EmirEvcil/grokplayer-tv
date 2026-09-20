package com.grokplayer.tv.data.scan

import android.content.Context
import android.util.Log
import org.json.JSONObject

data class YtCaptionTrack(
    val language: String,
    val label: String,
    val baseUrl: String,
    val auto: Boolean,
    val visitor: String? = null,
    val lines: List<YtCaptionLine> = emptyList(),
) {
    fun displayLabel(): String {
        val named = label.trim()
        val lang = languageName(language)
        val base = named.ifBlank { lang }
        return if (auto && !base.contains("otomatik", ignoreCase = true) && !base.contains("auto", ignoreCase = true)) {
            "$base (Otomatik)"
        } else {
            base
        }
    }

    fun withFormat(fmt: String): String {
        val stripped = baseUrl.replace(Regex("""([?&])fmt=[^&]*"""), "").trimEnd('&', '?')
        val sep = if (stripped.contains('?')) "&" else "?"
        return "$stripped${sep}fmt=$fmt"
    }
}

data class YtCaptionWord(
    val text: String,
    val startMs: Long,
    val endMs: Long,
)

data class YtCaptionLine(
    val startMs: Long,
    val endMs: Long,
    val words: List<YtCaptionWord>,
)

object YouTubeCaptions {
    fun parseTracks(root: JSONObject): List<YtCaptionTrack> {
        val tracks = root.optJSONObject("captions")
            ?.optJSONObject("playerCaptionsTracklistRenderer")
            ?.optJSONArray("captionTracks")
            ?: return emptyList()
        val out = ArrayList<YtCaptionTrack>(tracks.length())
        for (i in 0 until tracks.length()) {
            val item = tracks.optJSONObject(i) ?: continue
            val url = item.optString("baseUrl")
            val lang = item.optString("languageCode")
            if (url.isBlank() || lang.isBlank()) continue
            val name = item.optJSONObject("name")?.optString("simpleText").orEmpty().ifBlank {
                item.optJSONObject("name")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text").orEmpty()
            }
            out += YtCaptionTrack(
                language = lang,
                label = name,
                baseUrl = url,
                auto = item.optString("kind") == "asr",
            )
        }
        return out.distinctBy { "${it.language}:${it.auto}" }
    }

    fun withVisitor(tracks: List<YtCaptionTrack>, visitor: String?): List<YtCaptionTrack> {
        if (visitor.isNullOrBlank()) return tracks
        return tracks.map { if (it.visitor.isNullOrBlank()) it.copy(visitor = visitor) else it }
    }

    fun merge(left: List<YtCaptionTrack>, right: List<YtCaptionTrack>): List<YtCaptionTrack> {
        if (left.isEmpty()) return right
        if (right.isEmpty()) return left
        val seen = LinkedHashMap<String, YtCaptionTrack>()
        (left + right).forEach { track ->
            val key = "${track.language}:${track.auto}"
            val existing = seen[key]
            if (existing == null || (existing.lines.isEmpty() && track.lines.isNotEmpty())) {
                seen[key] = track
            }
        }
        return seen.values.toList()
    }

    fun parseJson3(json: String): List<YtCaptionLine> {
        if (json.isBlank() || json.trimStart().startsWith("<")) return emptyList()
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val events = root.optJSONArray("events") ?: return emptyList()
        val lines = mutableListOf<YtCaptionLine>()
        for (i in 0 until events.length()) {
            val event = events.optJSONObject(i) ?: continue
            val segs = event.optJSONArray("segs") ?: continue
            val start = event.optLong("tStartMs")
            val duration = event.optLong("dDurationMs")
            val eventEnd = if (duration > 0L) start + duration else start + 1_200L
            var bucket = mutableListOf<YtCaptionWord>()
            fun flush() {
                if (bucket.isEmpty()) return
                val lineStart = bucket.first().startMs
                val lineEnd = maxOf(eventEnd, bucket.last().endMs)
                lines += YtCaptionLine(lineStart, lineEnd, bucket.toList())
                bucket = mutableListOf()
            }
            for (s in 0 until segs.length()) {
                val seg = segs.optJSONObject(s) ?: continue
                val raw = seg.optString("utf8")
                if (raw == "\n") {
                    flush()
                    continue
                }
                val text = raw.replace('\n', ' ').trim()
                if (text.isEmpty()) continue
                val wordStart = start + seg.optLong("tOffsetMs")
                bucket += YtCaptionWord(text, wordStart, maxOf(wordStart + 160L, eventEnd))
            }
            flush()
        }
        return lines.map { line ->
            val words = line.words.mapIndexed { index, word ->
                val next = line.words.getOrNull(index + 1)?.startMs ?: line.endMs
                word.copy(endMs = maxOf(word.startMs + 80L, next))
            }
            line.copy(
                endMs = maxOf(line.endMs, words.lastOrNull()?.endMs ?: line.endMs),
                words = words,
            )
        }
    }

    fun visibleLines(lines: List<YtCaptionLine>, positionMs: Long): List<YtCaptionLine> {
        if (lines.isEmpty()) return emptyList()
        val current = lines.lastOrNull { positionMs >= it.startMs && positionMs < it.endMs + 700L }
        val previous = if (current != null) {
            lines.lastOrNull { it.endMs <= current.startMs && positionMs < it.endMs + 2_400L }
        } else {
            lines.lastOrNull { positionMs >= it.startMs && positionMs < it.endMs + 1_600L }
        }
        return listOfNotNull(previous, current)
    }

    fun hydrate(
        context: Context,
        tracks: List<YtCaptionTrack>,
        playUrl: String?,
        referer: String?,
        userAgent: String?,
    ): List<YtCaptionTrack> {
        val fromHls = fromHls(context, playUrl, referer, userAgent)
        val merged = merge(fromHls, tracks)
        return merged.map { track ->
            if (track.lines.isNotEmpty()) {
                track
            } else {
                val fetched = fetchLines(context, track, referer, userAgent)
                track.copy(lines = fetched)
            }
        }
    }

    fun attachTranscript(
        context: Context,
        tracks: List<YtCaptionTrack>,
        html: String?,
        visitor: String?,
    ): List<YtCaptionTrack> {
        if (tracks.any { it.lines.isNotEmpty() }) return tracks
        val params = extractTranscriptParams(html) ?: return tracks
        val attempts = listOf(
            Triple("1", "2.20240918.01.00", transcriptBody(params, visitor, "WEB", "2.20240918.01.00")),
            Triple("7", "7.20240918.00.00", transcriptBody(params, visitor, "TVHTML5", "7.20240918.00.00")),
        )
        var body: String? = null
        for ((clientId, clientVer, payload) in attempts) {
            body = PageScanner.postJson(
                context,
                "https://www.youtube.com/youtubei/v1/get_transcript?prettyPrint=false",
                payload,
                YouTubeResolver.chromeUa,
                extra = mapOf(
                    "X-YouTube-Client-Name" to clientId,
                    "X-YouTube-Client-Version" to clientVer,
                ),
            )
            if (!body.isNullOrBlank()) break
        }
        val byLang = parseTranscript(body.orEmpty())
        Log.i(
            "GrokPlayer",
            "transcript params=${params.take(32)} body=${body?.length ?: 0} head=${body?.take(120)?.replace('\n', ' ')} langs=${byLang.keys}",
        )
        if (byLang.isEmpty()) return tracks
        if (tracks.isEmpty()) {
            return byLang.map { (lang, lines) ->
                YtCaptionTrack(language = lang, label = "", baseUrl = "", auto = true, visitor = visitor, lines = lines)
            }
        }
        return tracks.map { track ->
            val lines = byLang[track.language] ?: byLang.values.firstOrNull().orEmpty()
            if (track.lines.isNotEmpty()) track else track.copy(lines = lines)
        }
    }

    fun fromHls(
        context: Context,
        playUrl: String?,
        referer: String?,
        userAgent: String?,
    ): List<YtCaptionTrack> {
        if (playUrl.isNullOrBlank() || !playUrl.contains("m3u8", ignoreCase = true) &&
            !playUrl.contains("hls", ignoreCase = true)
        ) {
            return emptyList()
        }
        val ua = userAgent?.takeIf { it.isNotBlank() } ?: YouTubeResolver.chromeUa
        val ref = referer?.takeIf { it.isNotBlank() } ?: "https://www.youtube.com/"
        val master = PageScanner.fetchText(context, playUrl, ua, referer = ref) ?: return emptyList()
        val media = parseExtXMedia(master).filter { it.type.equals("SUBTITLES", ignoreCase = true) }
        Log.i("GrokPlayer", "hls master len=${master.length} subs=${media.size} audio=${parseExtXMedia(master).count { it.type.equals("AUDIO", true) }}")
        return media.mapNotNull { item ->
            val uri = absolutize(playUrl, item.uri)
            val body = fetchHlsVtt(context, uri, ua, ref)
            val lines = parseVtt(body.orEmpty())
            Log.i("GrokPlayer", "hls sub ${item.language} vtt=${body?.length ?: 0} lines=${lines.size}")
            if (lines.isEmpty()) null
            else YtCaptionTrack(
                language = item.language.ifBlank { "und" },
                label = item.name,
                baseUrl = uri,
                auto = item.name.contains("auto", ignoreCase = true),
                lines = lines,
            )
        }
    }

    fun writeVtt(context: Context, track: YtCaptionTrack): java.io.File? {
        if (track.lines.isEmpty()) return null
        val file = java.io.File(context.cacheDir, "yt-${track.language}-${if (track.auto) "asr" else "m"}.vtt")
        return runCatching {
            file.writeText(toVtt(track.lines), Charsets.UTF_8)
            file
        }.getOrNull()
    }

    fun fetchLines(
        context: Context,
        track: YtCaptionTrack,
        referer: String?,
        userAgent: String?,
    ): List<YtCaptionLine> {
        if (track.lines.isNotEmpty()) return track.lines
        val ua = userAgent?.takeIf { it.isNotBlank() } ?: YouTubeResolver.chromeUa
        val ref = referer?.takeIf { it.isNotBlank() } ?: "https://www.youtube.com/"
        val extra = buildMap {
            put("Origin", "https://www.youtube.com")
            track.visitor?.takeIf { it.isNotBlank() }?.let { put("X-Goog-Visitor-Id", it) }
        }
        val id = Regex("[?&]v=([A-Za-z0-9_-]{11})").find(track.baseUrl)?.groupValues?.get(1)
        val kind = if (track.auto) "&kind=asr" else ""
        val urls = buildList {
            add(track.baseUrl to "raw")
            add(track.withFormat("srv1") to "srv1")
            add(track.withFormat("json3") + "&c=ANDROID" to "json3-and")
            add(track.withFormat("vtt") + "&c=ANDROID" to "vtt-and")
            add(track.withFormat("json3") to "json3")
            add(track.withFormat("vtt") to "vtt")
            if (id != null) {
                add("https://www.youtube.com/api/timedtext?v=$id&lang=${track.language}$kind&fmt=srv1" to "plain-xml")
                add("https://www.youtube.com/api/timedtext?v=$id&lang=${track.language}$kind&fmt=json3&c=ANDROID" to "plain-and")
            }
        }
        for ((url, label) in urls) {
            val body = PageScanner.fetchText(context, url, ua, referer = ref, extra = extra)
            val lines = parseAny(body.orEmpty())
            Log.i(
                "GrokPlayer",
                "yt caption ${track.language} $label=${body?.length ?: 0} lines=${lines.size}",
            )
            if (lines.isNotEmpty()) return lines
        }
        return emptyList()
    }

    internal fun parseAny(raw: String): List<YtCaptionLine> {
        if (raw.isBlank()) return emptyList()
        parseJson3(raw).takeIf { it.isNotEmpty() }?.let { return it }
        parseVtt(raw).takeIf { it.isNotEmpty() }?.let { return it }
        return parseSrv1(raw)
    }

    internal fun parseSrv1(xml: String): List<YtCaptionLine> {
        if (!xml.contains("<text", ignoreCase = true)) return emptyList()
        val regex = Regex(
            """<text[^>]*start=["']([^"']+)["'][^>]*dur=["']([^"']+)["'][^>]*>([\s\S]*?)</text>""",
            RegexOption.IGNORE_CASE,
        )
        val lines = mutableListOf<YtCaptionLine>()
        regex.findAll(xml).forEach { match ->
            val start = ((match.groupValues[1].toDoubleOrNull() ?: return@forEach) * 1000.0).toLong()
            val dur = ((match.groupValues[2].toDoubleOrNull() ?: 2.0) * 1000.0).toLong()
            val text = decodeXml(match.groupValues[3]).replace('\n', ' ').trim()
            if (text.isBlank()) return@forEach
            val words = text.split(Regex("\\s+")).mapIndexed { index, word ->
                val wStart = start + index * 80L
                YtCaptionWord(word, wStart, minOf(start + dur, wStart + 400L))
            }
            if (words.isNotEmpty()) lines += YtCaptionLine(start, start + dur, words)
        }
        return lines
    }

    internal fun extractTranscriptParams(html: String?): String? {
        if (html.isNullOrBlank()) return null
        val match = Regex(""""getTranscriptEndpoint"\s*:\s*\{[^}]{0,400}"params"\s*:\s*"([^"]+)"""").find(html)
        return match?.groupValues?.get(1)?.replace("\\u0026", "&")
    }

    internal fun parseTranscript(json: String): Map<String, List<YtCaptionLine>> {
        if (json.isBlank() || json.trimStart().startsWith("<")) return emptyMap()
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyMap()
        val segments = findTranscriptSegments(root) ?: return emptyMap()
        val lines = mutableListOf<YtCaptionLine>()
        for (i in 0 until segments.length()) {
            val item = segments.optJSONObject(i) ?: continue
            val seg = item.optJSONObject("transcriptSegmentRenderer") ?: continue
            val start = seg.optString("startMs").toLongOrNull() ?: continue
            val end = seg.optString("endMs").toLongOrNull() ?: (start + 2_000L)
            val text = runsText(seg.optJSONObject("snippet")).ifBlank {
                runsText(seg.optJSONObject("snippet")?.optJSONObject("simpleText")?.let { null })
            }.ifBlank {
                seg.optJSONObject("snippet")?.optString("simpleText").orEmpty()
            }
            val clean = text.replace('\n', ' ').trim()
            if (clean.isBlank()) continue
            val words = clean.split(Regex("\\s+")).mapIndexed { index, word ->
                val wStart = start + index * 80L
                YtCaptionWord(word, wStart, minOf(end, wStart + 400L))
            }
            if (words.isNotEmpty()) lines += YtCaptionLine(start, end, words)
        }
        return if (lines.isEmpty()) emptyMap() else mapOf("und" to lines)
    }

    private fun findTranscriptSegments(root: JSONObject): org.json.JSONArray? {
        val actions = root.optJSONArray("actions") ?: return walkForSegments(root)
        for (i in 0 until actions.length()) {
            val action = actions.optJSONObject(i) ?: continue
            val content = action.optJSONObject("updateEngagementPanelAction")?.optJSONObject("content")
                ?: action.optJSONObject("appendContinuationItemsAction")
            val found = walkForSegments(content ?: action)
            if (found != null) return found
        }
        return walkForSegments(root)
    }

    private fun walkForSegments(node: JSONObject?): org.json.JSONArray? {
        if (node == null) return null
        node.optJSONArray("initialSegments")?.let { return it }
        val keys = node.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val child = node.optJSONObject(key)
            if (child != null) {
                walkForSegments(child)?.let { return it }
                continue
            }
            val arr = node.optJSONArray(key) ?: continue
            for (i in 0 until arr.length()) {
                walkForSegments(arr.optJSONObject(i))?.let { return it }
            }
        }
        return null
    }

    private fun runsText(obj: JSONObject?): String {
        if (obj == null) return ""
        obj.optString("simpleText").takeIf { it.isNotBlank() }?.let { return it }
        val runs = obj.optJSONArray("runs") ?: return ""
        val out = StringBuilder()
        for (i in 0 until runs.length()) {
            out.append(runs.optJSONObject(i)?.optString("text").orEmpty())
        }
        return out.toString()
    }

    private fun transcriptBody(params: String, visitor: String?, client: String, version: String): String {
        val visit = if (visitor.isNullOrBlank()) "" else ""","visitorData":${JSONObject.quote(visitor)}"""
        return """{"context":{"client":{"clientName":${JSONObject.quote(client)},"clientVersion":${JSONObject.quote(version)},"hl":"tr","gl":"TR"$visit}},"params":${JSONObject.quote(params)}}"""
    }

    internal fun parseExtXMedia(master: String): List<HlsMediaTag> {
        return master.lineSequence().mapNotNull { line ->
            if (!line.startsWith("#EXT-X-MEDIA:")) return@mapNotNull null
            val body = line.removePrefix("#EXT-X-MEDIA:")
            val type = mediaAttr(body, "TYPE") ?: return@mapNotNull null
            val uri = mediaAttr(body, "URI") ?: return@mapNotNull null
            HlsMediaTag(
                type = type,
                language = mediaAttr(body, "LANGUAGE").orEmpty(),
                name = mediaAttr(body, "NAME").orEmpty(),
                uri = uri,
            )
        }.toList()
    }

    internal fun toVtt(lines: List<YtCaptionLine>): String {
        val out = StringBuilder("WEBVTT\n\n")
        lines.forEach { line ->
            out.append(vttClock(line.startMs)).append(" --> ").append(vttClock(line.endMs)).append('\n')
            out.append(line.words.joinToString(" ") { it.text }).append("\n\n")
        }
        return out.toString()
    }

    internal fun parseVtt(raw: String): List<YtCaptionLine> {
        if (!raw.contains("-->")) return emptyList()
        val lines = mutableListOf<YtCaptionLine>()
        val blocks = raw.replace("\r\n", "\n").split("\n\n")
        for (block in blocks) {
            val parts = block.trim().lines()
            val stamp = parts.firstOrNull { "-->" in it } ?: continue
            val times = stamp.split("-->")
            if (times.size < 2) continue
            val start = parseVttClock(times[0].trim())
            val end = parseVttClock(times[1].trim().substringBefore(' '))
            val text = parts.dropWhile { "-->" !in it }.drop(1).joinToString(" ").trim()
            if (start == null || end == null || text.isBlank()) continue
            val words = text.split(Regex("\\s+")).mapIndexed { index, word ->
                val wStart = start + index * 80L
                YtCaptionWord(word, wStart, minOf(end, wStart + 400L))
            }
            if (words.isNotEmpty()) lines += YtCaptionLine(start, end, words)
        }
        return lines
    }

    private fun parseVttClock(raw: String): Long? {
        val clean = raw.trim()
        val bits = clean.split(':', '.')
        return try {
            when (bits.size) {
                3 -> bits[0].toLong() * 60_000L + bits[1].toLong() * 1_000L + bits[2].padEnd(3, '0').take(3).toLong()
                4 -> bits[0].toLong() * 3_600_000L + bits[1].toLong() * 60_000L + bits[2].toLong() * 1_000L +
                    bits[3].padEnd(3, '0').take(3).toLong()
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun match(tracks: List<YtCaptionTrack>, language: String?, label: String?): YtCaptionTrack? {
        val lang = language?.trim()?.lowercase().orEmpty()
        if (lang.isNotEmpty() && lang != "und") {
            tracks.firstOrNull { it.language.lowercase() == lang }?.let { return it }
            tracks.firstOrNull { it.language.lowercase().startsWith(lang.take(2)) }?.let { return it }
        }
        val named = label?.trim().orEmpty()
        if (named.isNotEmpty()) {
            tracks.firstOrNull { it.displayLabel().equals(named, ignoreCase = true) }?.let { return it }
        }
        return null
    }
}

internal data class HlsMediaTag(
    val type: String,
    val language: String,
    val name: String,
    val uri: String,
)

private fun mediaAttr(body: String, key: String): String? {
    Regex("""$key="([^"]*)"""", RegexOption.IGNORE_CASE).find(body)?.groupValues?.get(1)?.let { return it }
    return Regex("""$key=([^,]+)""", RegexOption.IGNORE_CASE).find(body)?.groupValues?.get(1)
}

private fun fetchHlsVtt(context: Context, uri: String, ua: String, referer: String): String? {
    val body = PageScanner.fetchText(context, uri, ua, referer = referer) ?: return null
    if (body.contains("WEBVTT") || !body.contains("#EXTM3U")) return body
    val parts = body.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .take(80)
        .toList()
    if (parts.isEmpty()) return body
    val chunks = parts.mapNotNull { ref ->
        PageScanner.fetchText(context, absolutize(uri, ref), ua, referer = referer)
    }
    return chunks.joinToString("\n\n").ifBlank { body }
}

private fun absolutize(base: String, ref: String): String {
    if (ref.startsWith("http://") || ref.startsWith("https://")) return ref
    if (ref.startsWith("//")) return "https:$ref"
    return try {
        java.net.URL(java.net.URL(base), ref).toString()
    } catch (_: Exception) {
        ref
    }
}

private fun decodeXml(text: String): String =
    text.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
        .replace(Regex("""<[^>]+>"""), "")

private fun vttClock(ms: Long): String {
    val total = ms.coerceAtLeast(0L)
    val h = total / 3_600_000
    val m = (total % 3_600_000) / 60_000
    val s = (total % 60_000) / 1000
    val frac = total % 1000
    return "%02d:%02d:%02d.%03d".format(h, m, s, frac)
}

internal fun languageName(code: String): String = when (code.trim().lowercase()) {
    "tr", "tur" -> "Türkçe"
    "en", "eng" -> "English"
    "fr", "fra", "fre" -> "Français"
    "es", "spa" -> "Español"
    "de", "deu", "ger" -> "Deutsch"
    "it", "ita" -> "Italiano"
    "ja", "jpn" -> "日本語"
    "pt", "por" -> "Português"
    "ru", "rus" -> "Русский"
    "ar", "ara" -> "العربية"
    "ko", "kor" -> "한국어"
    "zh", "chi", "zho" -> "中文"
    else -> code.ifBlank { "Altyazı" }
}
