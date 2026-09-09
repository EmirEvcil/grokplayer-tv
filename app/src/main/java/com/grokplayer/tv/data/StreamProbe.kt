package com.grokplayer.tv.data

import java.net.HttpURLConnection
import java.net.URL

object StreamProbe {
    fun detectKind(url: String): StreamKind {
        val body = fetchPrefix(url) ?: return guessFromUrl(url)
        return classify(url, body)
    }

    private fun classify(url: String, body: String): StreamKind {
        val text = body
        if (text.contains("#EXTM3U")) {
            if (text.contains("#EXT-X-ENDLIST") || text.contains("PLAYLIST-TYPE:VOD")) {
                return StreamKind.Vod
            }
            if (text.contains("#EXT-X-STREAM-INF")) {
                val variant = text.lineSequence()
                    .map { it.trim() }
                    .firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
                if (variant != null) {
                    val child = resolve(url, variant)
                    val nested = fetchPrefix(child)
                    if (nested != null) return classify(child, nested)
                }
            }
            if (text.contains("#EXTINF") && !text.contains("#EXT-X-ENDLIST")) {
                return StreamKind.Live
            }
            return StreamKind.Live
        }
        if (text.contains("<MPD") || text.contains("<mpd")) {
            return if (
                text.contains("type=\"dynamic\"") ||
                text.contains("type='dynamic'")
            ) {
                StreamKind.Live
            } else {
                StreamKind.Vod
            }
        }
        return guessFromUrl(url)
    }

    private fun guessFromUrl(url: String): StreamKind {
        val lower = url.lowercase()
        return if (lower.contains("/live") || lower.contains("livestream")) {
            StreamKind.Live
        } else {
            StreamKind.Vod
        }
    }

    fun playUrl(url: String): String {
        if (mimeForUrl(url) != null) return url
        return resolveFinalUrl(url)
    }

    fun resolveFinalUrl(url: String): String {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return url
        return try {
            var current = URL(url)
            repeat(5) {
                val conn = (current.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 8_000
                    readTimeout = 8_000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", USER_AGENT)
                }
                val code = conn.responseCode
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (code in 300..399 && !location.isNullOrBlank()) {
                    current = URL(current, location)
                } else {
                    return current.toString()
                }
            }
            current.toString()
        } catch (_: Exception) {
            url
        }
    }

    fun mimeForUrl(url: String): String? {
        val lower = url.lowercase()
        return when {
            ".m3u8" in lower -> "application/x-mpegURL"
            ".mpd" in lower -> "application/dash+xml"
            else -> null
        }
    }

    private fun fetchPrefix(url: String): String? {
        return try {
            val target = resolveFinalUrl(url)
            val conn = (URL(target).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", USER_AGENT)
            }
            conn.inputStream.bufferedReader().use { it.readText().take(24_000) }
        } catch (_: Exception) {
            null
        }
    }

    private fun resolve(base: String, ref: String): String {
        if (ref.startsWith("http://") || ref.startsWith("https://")) return ref
        return try {
            URL(URL(base), ref).toString()
        } catch (_: Exception) {
            ref
        }
    }

    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; SHIELD Android TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
}
