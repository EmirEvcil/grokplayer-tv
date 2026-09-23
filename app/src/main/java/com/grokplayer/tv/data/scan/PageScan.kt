package com.grokplayer.tv.data.scan

import android.content.Context
import com.grokplayer.tv.data.StreamHttp
import com.grokplayer.tv.data.StreamKind
import com.grokplayer.tv.data.StreamProbe
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class ScanHit(
    val title: String,
    val playUrl: String,
    val pageUrl: String,
    val kind: StreamKind,
    val durationMs: Long = 0L,
    val thumbnailUrl: String? = null,
    val referer: String? = null,
    val userAgent: String? = null,
    val detail: String = "",
    val captions: List<YtCaptionTrack> = emptyList(),
    val audios: List<YtAudioTrack> = emptyList(),
    val storyboardSpec: String? = null,
) {
    fun toVideo(): com.grokplayer.tv.data.LibraryVideo = com.grokplayer.tv.data.LibraryVideo(
        id = "scan:${playUrl.hashCode()}",
        title = title,
        uri = android.net.Uri.parse(playUrl),
        durationMs = durationMs,
        format = if (kind == StreamKind.Live) "CANLI" else "VOD",
        source = com.grokplayer.tv.data.StorageSource.Internal,
        dateAdded = System.currentTimeMillis(),
        lastModified = System.currentTimeMillis(),
        isLive = kind == StreamKind.Live,
        isStream = true,
        originUrl = pageUrl,
        posterUrl = thumbnailUrl,
        referer = referer,
        userAgent = userAgent,
    )
}

object PageScanner {
    fun scan(context: Context, raw: String): List<ScanHit> {
        val url = raw.trim()
        if (url.isBlank()) return emptyList()
        YouTubeResolver.videoId(url)?.let { id ->
            val hit = YouTubeResolver.resolve(context, id, url)
            android.util.Log.i("GrokPlayer", "scan youtube id=$id hit=${hit?.title} url=${hit?.playUrl?.take(80)}")
            if (hit != null) return listOf(hit)
        }
        if (looksDirect(url)) {
            val kind = runCatching { StreamProbe.detectKind(url) }.getOrDefault(StreamKind.Vod)
            return listOf(
                ScanHit(
                    title = url.substringAfterLast('/').substringBefore('?').ifBlank { url },
                    playUrl = url,
                    pageUrl = url,
                    kind = kind,
                    detail = if (kind == StreamKind.Live) "Doğrudan canlı yayın" else "Doğrudan VOD",
                ),
            )
        }
        return scanHtml(context, url)
    }

    private fun looksDirect(url: String): Boolean {
        val lower = url.lowercase()
        return listOf(".m3u8", ".mpd", ".mp4", ".mkv", ".webm", ".m4v", ".mov").any { it in lower }
    }

    private fun scanHtml(context: Context, url: String): List<ScanHit> {
        val html = fetchText(context, url, StreamProbe.USER_AGENT, referer = url) ?: return emptyList()
        val title = meta(html, "og:title") ?: meta(html, "twitter:title") ?: htmlTitle(html) ?: url
        val image = meta(html, "og:image") ?: meta(html, "twitter:image")
        val seconds = meta(html, "og:video:duration")?.toLongOrNull()
            ?: meta(html, "video:duration")?.toLongOrNull()
            ?: durationFromItemprop(html)
        val found = linkedMedia(html, url)
        if (found.isEmpty()) return emptyList()
        return found.distinctBy { it.lowercase() }.take(8).mapIndexed { index, media ->
            val kind = runCatching { StreamProbe.detectKind(media) }.getOrDefault(StreamKind.Vod)
            ScanHit(
                title = if (found.size == 1) title else "$title (${index + 1})",
                playUrl = media,
                pageUrl = url,
                kind = kind,
                durationMs = (seconds ?: 0L) * 1000L,
                thumbnailUrl = image,
                referer = url,
                detail = media.substringAfterLast('/').take(48),
            )
        }
    }

    private fun linkedMedia(html: String, page: String): List<String> {
        val urls = LinkedHashSet<String>()
        val pattern = Regex(
            """https?:\\?/\\?/[^"'\\s<>]+?\\.(?:m3u8|m3u|mpd|mp4|webm|mkv|mov)(?:/(?:master|playlist)\\.txt)?(?:\\?[^"'\\s<>]*)?""",
            RegexOption.IGNORE_CASE,
        )
        pattern.findAll(html).forEach { match ->
            val cleaned = match.value.replace("\\/", "/").replace("\\u0026", "&")
            if (cleaned.startsWith("http")) urls.add(cleaned)
        }
        val src = Regex("""(?:file|src|source|sourceURL|hlsUrl|dashUrl)\s*[:=]\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        src.findAll(html).forEach { match ->
            val raw = match.groupValues[1].replace("\\/", "/")
            if (looksDirect(raw)) urls.add(absolutize(page, raw))
        }
        return urls.toList()
    }

    private fun meta(html: String, name: String): String? {
        val prop = Regex(
            """<meta[^>]+(?:property|name)=["']${Regex.escape(name)}["'][^>]+content=["']([^"']+)["']""",
            RegexOption.IGNORE_CASE,
        ).find(html)?.groupValues?.get(1)
        if (prop != null) return unescape(prop)
        val rev = Regex(
            """<meta[^>]+content=["']([^"']+)["'][^>]+(?:property|name)=["']${Regex.escape(name)}["']""",
            RegexOption.IGNORE_CASE,
        ).find(html)?.groupValues?.get(1)
        return rev?.let { unescape(it) }
    }

    private fun htmlTitle(html: String): String? =
        Regex("""<title[^>]*>([^<]+)</title>""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)?.trim()

    private fun durationFromItemprop(html: String): Long? {
        val raw = Regex("""itemprop=["']duration["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1) ?: return null
        return parseIsoDuration(raw)
    }

    private fun parseIsoDuration(raw: String): Long? {
        val match = Regex("""PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?""", RegexOption.IGNORE_CASE).matchEntire(raw.trim())
            ?: return raw.toLongOrNull()
        val h = match.groupValues[1].toLongOrNull() ?: 0L
        val m = match.groupValues[2].toLongOrNull() ?: 0L
        val s = match.groupValues[3].toLongOrNull() ?: 0L
        return h * 3600 + m * 60 + s
    }

    private fun absolutize(page: String, href: String): String {
        if (href.startsWith("http://") || href.startsWith("https://")) return href
        if (href.startsWith("//")) return "https:$href"
        return try {
            java.net.URL(java.net.URL(page), href).toString()
        } catch (_: Exception) {
            href
        }
    }

    private fun unescape(text: String): String =
        text.replace("&amp;", "&").replace("&#39;", "'").replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">")

    internal fun fetchText(
        context: Context,
        url: String,
        userAgent: String,
        referer: String? = null,
        extra: Map<String, String> = emptyMap(),
    ): String? {
        return runCatching {
            val client = StreamHttp.client(context).newBuilder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            val builder = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Accept-Language", "tr-TR,tr;q=0.9,en-US,en;q=0.8")
            if (!referer.isNullOrBlank()) builder.header("Referer", referer)
            extra.forEach { (k, v) -> builder.header(k, v) }
            client.newCall(builder.build()).execute().use { resp ->
                val body = resp.body?.string()
                if (url.contains("timedtext") || !resp.isSuccessful) {
                    android.util.Log.i(
                        "GrokPlayer",
                        "fetch ${resp.code} ${resp.header("Content-Type")} len=${body?.length ?: 0} ${url.take(72)}",
                    )
                }
                if (!resp.isSuccessful) null else body
            }
        }.getOrNull()
    }

    internal fun postJson(
        context: Context,
        url: String,
        body: String,
        userAgent: String,
        extra: Map<String, String> = emptyMap(),
    ): String? {
        return runCatching {
            val client = StreamHttp.client(context).newBuilder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            val builder = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Origin", "https://www.youtube.com")
                .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            extra.forEach { (k, v) -> builder.header(k, v) }
            client.newCall(builder.build()).execute().use { resp ->
                val text = resp.body?.string()
                if (!resp.isSuccessful) {
                    android.util.Log.w(
                        "GrokPlayer",
                        "post ${resp.code} ${url.take(72)} len=${text?.length ?: 0} head=${text?.take(180)}",
                    )
                    null
                } else {
                    text
                }
            }
        }.getOrNull()
    }
}
