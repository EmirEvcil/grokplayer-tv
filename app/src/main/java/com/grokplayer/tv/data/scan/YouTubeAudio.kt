package com.grokplayer.tv.data.scan

import org.json.JSONObject

data class YtAudioTrack(
    val id: String,
    val language: String,
    val label: String,
    val url: String,
    val isDefault: Boolean,
    val mime: String,
    val rank: Int,
) {
    fun menuLabel(): String {
        val named = label.trim()
        val mapped = languageName(language)
        val base = when {
            named.isBlank() -> mapped
            language.equals("tr", true) -> "Türkçe"
            named.contains("original", ignoreCase = true) -> mapped
            else -> named
        }
        return if (isDefault) "$base (Orijinal)" else base
    }
}

object YouTubeAudio {
    fun parse(root: JSONObject): List<YtAudioTrack> {
        val formats = root.optJSONObject("streamingData")?.optJSONArray("adaptiveFormats")
            ?: return emptyList()
        val best = LinkedHashMap<String, YtAudioTrack>()
        for (i in 0 until formats.length()) {
            val format = formats.optJSONObject(i) ?: continue
            val mime = format.optString("mimeType")
            if (!mime.startsWith("audio")) continue
            val url = format.optString("url")
            if (url.isBlank()) continue
            val track = format.optJSONObject("audioTrack") ?: continue
            val id = track.optString("id")
            if (id.isBlank()) continue
            val language = id.substringBefore('.').ifBlank { "und" }
            val name = track.optString("displayName").ifBlank { language }
            val candidate = YtAudioTrack(
                id = id,
                language = language,
                label = name,
                url = url,
                isDefault = track.optBoolean("audioIsDefault"),
                mime = mime,
                rank = rank(format.optInt("itag")),
            )
            val existing = best[id]
            if (existing == null || candidate.rank > existing.rank) {
                best[id] = candidate
            }
        }
        return best.values.sortedWith(
            compareByDescending<YtAudioTrack> { it.isDefault }.thenBy { it.menuLabel().lowercase() },
        )
    }

    fun merge(left: List<YtAudioTrack>, right: List<YtAudioTrack>): List<YtAudioTrack> {
        if (left.isEmpty()) return right
        if (right.isEmpty()) return left
        val best = LinkedHashMap<String, YtAudioTrack>()
        (left + right).forEach { track ->
            val existing = best[track.id]
            if (existing == null || track.rank > existing.rank) best[track.id] = track
        }
        return best.values.sortedWith(
            compareByDescending<YtAudioTrack> { it.isDefault }.thenBy { it.menuLabel().lowercase() },
        )
    }

    fun mimeType(track: YtAudioTrack): String {
        val mime = track.mime.lowercase()
        return when {
            "opus" in mime || "webm" in mime -> androidx.media3.common.MimeTypes.AUDIO_OPUS
            else -> androidx.media3.common.MimeTypes.AUDIO_AAC
        }
    }

    private fun rank(itag: Int): Int = when (itag) {
        251 -> 50
        140 -> 40
        250 -> 30
        249 -> 20
        139 -> 10
        else -> 1
    }
}
