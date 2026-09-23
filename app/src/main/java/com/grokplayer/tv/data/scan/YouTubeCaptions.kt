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
    val translate: Boolean = false,
) {
    fun optionKey(): String =
        "yt:$language:${if (translate) "t" else if (auto) "asr" else "m"}"

    fun displayLabel(): String {
        val named = label.trim()
        val known = languageName(language)
        val base = if (known.isNotBlank() && !known.equals(language, true) && known != "Altyazı") {
            known
        } else {
            named.ifBlank { known }
        }
        return when {
            translate && !base.contains("çeviri", ignoreCase = true) -> "$base (Çeviri)"
            auto && !translate &&
                !base.contains("otomatik", ignoreCase = true) &&
                !base.contains("auto", ignoreCase = true) -> "$base (Otomatik)"
            else -> base
        }
    }

    fun withFormat(fmt: String): String = withQuery("fmt", fmt)

    fun withTlang(lang: String): String = withQuery("tlang", lang)

    private fun withQuery(key: String, value: String): String {
        val stripped = baseUrl
            .replace(Regex("""([?&])$key=[^&]*"""), "$1")
            .replace("&&", "&")
            .replace("?&", "?")
            .trimEnd('&', '?')
        val sep = if (stripped.contains('?')) "&" else "?"
        return "$stripped$sep$key=$value"
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
        val renderer = root.optJSONObject("captions")
            ?.optJSONObject("playerCaptionsTracklistRenderer")
            ?: return emptyList()
        val tracks = renderer.optJSONArray("captionTracks") ?: return emptyList()
        val out = ArrayList<YtCaptionTrack>()
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
        return out.distinctBy { trackKey(it) }
    }

    fun tracksToLoad(tracks: List<YtCaptionTrack>, wantLang: String): List<YtCaptionTrack> {
        val real = tracks.filter { !it.translate }
        if (real.isEmpty()) return emptyList()
        val want = wantLang.trim().lowercase()
        val preferred = if (want.isBlank()) {
            emptyList()
        } else {
            real.filter { it.language.lowercase().startsWith(want.take(2)) }
        }
        return (preferred + real).distinctBy { trackKey(it) }.take(3)
    }

    fun preferredTranslation(tracks: List<YtCaptionTrack>, wantLang: String): YtCaptionTrack? {
        val want = wantLang.trim()
        if (want.isBlank()) return null
        val stem = want.lowercase().take(2)
        if (tracks.any { !it.translate && it.language.lowercase().startsWith(stem) }) return null
        val source = tracks.firstOrNull { !it.translate && it.lines.isNotEmpty() } ?: return null
        return source.copy(
            language = want,
            label = languageName(want),
            baseUrl = source.withTlang(want),
            translate = true,
            lines = emptyList(),
        )
    }

    fun usableTracks(
        context: Context,
        tracks: List<YtCaptionTrack>,
        referer: String?,
        userAgent: String?,
        wantLang: String,
    ): List<YtCaptionTrack> {
        val real = tracks.filter { !it.translate }
        if (real.isEmpty()) return emptyList()
        val loadedLines = HashMap<String, List<YtCaptionLine>>()
        var blocked = false
        for (track in tracksToLoad(real, wantLang)) {
            if (track.lines.isNotEmpty()) {
                loadedLines[trackKey(track)] = track.lines
                continue
            }
            val fetched = fetchLinesResult(context, track, referer, userAgent)
            if (fetched.lines.isNotEmpty()) loadedLines[trackKey(track)] = fetched.lines
            if (fetched.blocked) {
                blocked = true
                break
            }
        }
        val withLines = real.map { track ->
            loadedLines[trackKey(track)]?.let { track.copy(lines = it) } ?: track
        }
        if (blocked) return withLines
        val extra = preferredTranslation(withLines, wantLang) ?: return withLines
        val translated = fetchLinesResult(context, extra, referer, userAgent)
        if (translated.blocked || translated.lines.isEmpty()) return withLines
        return withLines + extra.copy(lines = translated.lines)
    }

    private fun trackKey(track: YtCaptionTrack): String =
        "${track.language.lowercase()}:${track.auto}:${track.translate}"

    fun withVisitor(tracks: List<YtCaptionTrack>, visitor: String?): List<YtCaptionTrack> {
        if (visitor.isNullOrBlank()) return tracks
        return tracks.map { if (it.visitor.isNullOrBlank()) it.copy(visitor = visitor) else it }
    }

    fun merge(left: List<YtCaptionTrack>, right: List<YtCaptionTrack>): List<YtCaptionTrack> {
        if (left.isEmpty()) return right
        if (right.isEmpty()) return left
        val seen = LinkedHashMap<String, YtCaptionTrack>()
        (left + right).forEach { track ->
            val key = "${track.language.lowercase()}:${track.auto}:${track.translate}"
            val existing = seen[key]
            when {
                existing == null -> seen[key] = track
                existing.lines.isEmpty() && track.lines.isNotEmpty() -> seen[key] = track
                existing.lines.isEmpty() && track.baseUrl.length > existing.baseUrl.length -> seen[key] = track
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

    fun readableLines(lines: List<YtCaptionLine>): List<YtCaptionLine> {
        if (lines.isEmpty()) return lines
        val minHold = 2_200L
        val maxSpan = 4_800L
        val maxChars = 84
        val parts = mutableListOf<YtCaptionLine>()
        var buf = lines.first()
        for (next in lines.drop(1)) {
            val gap = next.startMs - buf.endMs
            val chars = captionChars(buf) + 1 + captionChars(next)
            val span = next.endMs.coerceAtLeast(next.startMs) - buf.startMs
            if (gap <= 400L && chars <= maxChars && span <= maxSpan) {
                buf = YtCaptionLine(buf.startMs, maxOf(buf.endMs, next.endMs), buf.words + next.words)
            } else {
                parts += buf
                buf = next
            }
        }
        parts += buf
        return parts.mapIndexed { index, line ->
            val nextStart = parts.getOrNull(index + 1)?.startMs
            val want = maxOf(line.endMs, line.startMs + minHold)
            val end = if (nextStart == null) want else minOf(want, nextStart)
            line.copy(endMs = maxOf(line.endMs, end))
        }
    }

    private fun captionChars(line: YtCaptionLine): Int =
        line.words.sumOf { it.text.length + 1 }

    fun visibleLines(lines: List<YtCaptionLine>, positionMs: Long): List<YtCaptionLine> {
        if (lines.isEmpty()) return emptyList()
        val active = lines.filter { positionMs >= it.startMs && positionMs < it.endMs + 900L }
        if (active.isNotEmpty()) return if (active.size <= 2) active else active.takeLast(2)
        return listOfNotNull(
            lines.lastOrNull { positionMs >= it.startMs && positionMs < it.startMs + 2_600L },
        )
    }

    fun hydrate(
        context: Context,
        tracks: List<YtCaptionTrack>,
        playUrl: String?,
        referer: String?,
        userAgent: String?,
    ): List<YtCaptionTrack> {
        if (tracks.isNotEmpty()) return tracks
        return fromHls(context, playUrl, referer, userAgent)
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

    fun writeVtt(context: Context, track: YtCaptionTrack, videoId: String = ""): java.io.File? {
        if (track.lines.isEmpty()) return null
        val tag = videoId.ifBlank { "v" }.replace(Regex("[^A-Za-z0-9_-]"), "").take(24)
        val file = java.io.File(context.cacheDir, "yt-$tag-${track.language}-${if (track.auto) "asr" else "m"}.vtt")
        return runCatching {
            file.writeText(toVtt(track.lines), Charsets.UTF_8)
            file
        }.getOrNull()
    }

    data class CaptionFetch(val lines: List<YtCaptionLine>, val blocked: Boolean)

    fun fetchLines(
        context: Context,
        track: YtCaptionTrack,
        referer: String?,
        userAgent: String?,
    ): List<YtCaptionLine> = fetchLinesResult(context, track, referer, userAgent).lines

    fun fetchLinesResult(
        context: Context,
        track: YtCaptionTrack,
        referer: String?,
        userAgent: String?,
    ): CaptionFetch {
        if (track.lines.isNotEmpty()) return CaptionFetch(track.lines, blocked = false)
        val ref = referer?.takeIf { it.isNotBlank() } ?: "https://www.youtube.com/"
        val extra = buildMap {
            put("Origin", "https://www.youtube.com")
            put("Accept", "*/*")
            track.visitor?.takeIf { it.isNotBlank() }?.let { put("X-Goog-Visitor-Id", it) }
        }
        val id = Regex("[?&]v=([A-Za-z0-9_-]{11})").find(track.baseUrl)?.groupValues?.get(1)
            ?: Regex("[?&]v=([A-Za-z0-9_-]{11})").find(ref)?.groupValues?.get(1)
        val lang = track.language
        val kind = if (track.auto) "&kind=asr" else ""
        val tlang = Regex("[?&]tlang=([^&]+)").find(track.baseUrl)?.groupValues?.get(1)
        val tlangQ = if (tlang.isNullOrBlank()) "" else "&tlang=$tlang"
        val primary = userAgent?.takeIf { it.isNotBlank() } ?: YouTubeResolver.chromeUa
        val urls = buildList {
            add(track.withFormat("srv3") to "srv3")
            add(track.withFormat("json3") to "json3")
            add(track.withFormat("vtt") to "vtt")
            if (id != null && track.baseUrl.contains("timedtext", ignoreCase = true).not()) {
                add("https://www.youtube.com/api/timedtext?v=$id&lang=$lang$kind$tlangQ&fmt=srv3" to "plain-srv3")
            }
        }
        var blocked = false
        fun pull(ua: String, url: String, label: String): List<YtCaptionLine>? {
            val body = PageScanner.fetchText(context, url, ua, referer = ref, extra = extra)
            if (isGoogleRestriction(body)) {
                Log.i("GrokPlayer", "yt caption ${track.language} $label restricted")
                blocked = true
                return null
            }
            if (body.isNullOrBlank() || looksLikeHtml(body)) {
                Log.i("GrokPlayer", "yt caption ${track.language} $label=${body?.length ?: 0} skip")
                return null
            }
            val lines = parseAny(body)
            Log.i("GrokPlayer", "yt caption ${track.language} $label=${body.length} lines=${lines.size}")
            return lines.takeIf { it.isNotEmpty() }
        }
        for ((url, label) in urls) {
            pull(primary, url, label)?.let { return CaptionFetch(it, blocked = false) }
            if (blocked) return CaptionFetch(emptyList(), blocked = true)
        }
        if (primary != YouTubeResolver.chromeUa) {
            pull(YouTubeResolver.chromeUa, urls.first().first, "srv3-chrome")?.let {
                return CaptionFetch(it, blocked = false)
            }
        }
        return CaptionFetch(emptyList(), blocked = blocked)
    }

    internal fun parseAny(raw: String): List<YtCaptionLine> {
        if (raw.isBlank() || looksLikeHtml(raw)) return emptyList()
        if (raw.contains("<timedtext", ignoreCase = true) || raw.contains("<p t=") || raw.contains("<p ")) {
            parseSrv3(raw).takeIf { it.isNotEmpty() }?.let { return it }
        }
        parseJson3(raw).takeIf { it.isNotEmpty() }?.let { return it }
        parseVtt(raw).takeIf { it.isNotEmpty() }?.let { return it }
        parseSrv3(raw).takeIf { it.isNotEmpty() }?.let { return it }
        return parseSrv1(raw)
    }

    internal fun parseSrv3(xml: String): List<YtCaptionLine> {
        if (!xml.contains("<p", ignoreCase = true)) return emptyList()
        val regex = Regex("""<p\b([^>]*)>([\s\S]*?)</p>""", RegexOption.IGNORE_CASE)
        val lines = mutableListOf<YtCaptionLine>()
        regex.findAll(xml).forEach { match ->
            val start = xmlAttr(match.groupValues[1], "t")?.toLongOrNull() ?: return@forEach
            val dur = xmlAttr(match.groupValues[1], "d")?.toLongOrNull() ?: return@forEach
            if (dur <= 0L) return@forEach
            val text = decodeXml(match.groupValues[2].replace(Regex("<[^>]+>"), " ")).replace('\n', ' ').trim()
            if (text.isBlank()) return@forEach
            val words = text.split(Regex("\\s+")).mapIndexed { index, word ->
                val wStart = start + index * 80L
                YtCaptionWord(word, wStart, minOf(start + dur, wStart + 400L))
            }
            if (words.isNotEmpty()) lines += YtCaptionLine(start, start + dur, words)
        }
        return lines
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
                groupId = mediaAttr(body, "GROUP-ID").orEmpty(),
                isDefault = mediaAttr(body, "DEFAULT")?.equals("YES", true) == true,
                autoSelect = mediaAttr(body, "AUTOSELECT")?.equals("YES", true) == true,
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
        val named = label?.trim().orEmpty()
        if (named.isNotEmpty()) {
            tracks.firstOrNull { it.displayLabel().equals(named, ignoreCase = true) }?.let { return it }
        }
        val lang = language?.trim()?.lowercase().orEmpty()
        if (lang.isNotEmpty() && lang != "und") {
            tracks.firstOrNull { it.language.lowercase() == lang && !it.translate }?.let { return it }
            tracks.firstOrNull { it.language.lowercase() == lang }?.let { return it }
            tracks.firstOrNull { it.language.lowercase().startsWith(lang.take(2)) }?.let { return it }
        }
        return null
    }
}

internal data class HlsMediaTag(
    val type: String,
    val language: String,
    val name: String,
    val uri: String,
    val groupId: String = "",
    val isDefault: Boolean = false,
    val autoSelect: Boolean = false,
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

private fun looksLikeHtml(raw: String): Boolean {
    val head = raw.trimStart().take(80).lowercase()
    return head.startsWith("<!doctype html") || head.startsWith("<html") || head.contains("<title>before you continue")
}

private fun isGoogleRestriction(raw: String?): Boolean {
    if (raw.isNullOrBlank()) return false
    val text = raw.lowercase()
    return "automated queries" in text || "sorry..." in text && "<html" in text
}

private fun xmlAttr(attrs: String, name: String): String? {
    Regex("""\b$name=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(attrs)?.groupValues?.get(1)?.let { return it }
    return Regex("""\b$name=([^\s>]+)""", RegexOption.IGNORE_CASE).find(attrs)?.groupValues?.get(1)
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
