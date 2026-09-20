package com.grokplayer.tv.data

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class StreamKind { Vod, Live }

data class StreamItem(
    val id: String,
    val title: String,
    val url: String,
    val kind: StreamKind,
    val favorite: Boolean,
    val addedAt: Long,
    val posterUrl: String? = null,
    val referer: String? = null,
    val userAgent: String? = null,
    val durationMs: Long = 0L,
    val pageUrl: String? = null,
) {
    fun toVideo(): LibraryVideo = LibraryVideo(
        id = "stream:$id",
        title = title,
        uri = Uri.parse(url),
        durationMs = durationMs,
        format = if (kind == StreamKind.Live) "CANLI" else "VOD",
        source = StorageSource.Internal,
        dateAdded = addedAt,
        lastModified = addedAt,
        path = null,
        isLive = kind == StreamKind.Live,
        isStream = true,
        originUrl = pageUrl ?: url,
        posterUrl = posterUrl,
        referer = referer,
        userAgent = userAgent,
    )
}

class StreamStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("streams", Context.MODE_PRIVATE)

    var items by mutableStateOf(load())
        private set

    fun add(
        title: String,
        url: String,
        kind: StreamKind = StreamKind.Vod,
        posterUrl: String? = null,
        referer: String? = null,
        userAgent: String? = null,
        durationMs: Long = 0L,
        pageUrl: String? = null,
    ) {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) return
        val item = StreamItem(
            id = UUID.randomUUID().toString(),
            title = title.trim().ifBlank { cleanUrl.substringAfterLast('/').ifBlank { "Akış" } },
            url = cleanUrl,
            kind = kind,
            favorite = false,
            addedAt = System.currentTimeMillis(),
            posterUrl = posterUrl,
            referer = referer,
            userAgent = userAgent,
            durationMs = durationMs,
            pageUrl = pageUrl?.trim()?.ifBlank { null },
        )
        items = listOf(item) + items
        persist()
    }

    fun updateKind(id: String, kind: StreamKind) {
        items = items.map { if (it.id == id) it.copy(kind = kind) else it }
        persist()
    }

    fun toggleFavorite(id: String) {
        items = items.map { if (it.id == id) it.copy(favorite = !it.favorite) else it }
        persist()
    }

    fun remove(id: String) {
        items = items.filterNot { it.id == id }
        persist()
    }

    private fun persist() {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("title", item.title)
                    .put("url", item.url)
                    .put("kind", item.kind.name)
                    .put("favorite", item.favorite)
                    .put("addedAt", item.addedAt)
                    .put("posterUrl", item.posterUrl)
                    .put("referer", item.referer)
                    .put("userAgent", item.userAgent)
                    .put("durationMs", item.durationMs)
                    .put("pageUrl", item.pageUrl),
            )
        }
        prefs.edit().putString("items", array.toString()).apply()
    }

    private fun load(): List<StreamItem> {
        val raw = prefs.getString("items", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        StreamItem(
                            id = obj.getString("id"),
                            title = obj.getString("title"),
                            url = obj.getString("url"),
                            kind = runCatching { StreamKind.valueOf(obj.getString("kind")) }
                                .getOrDefault(StreamKind.Vod),
                            favorite = obj.optBoolean("favorite"),
                            addedAt = obj.optLong("addedAt"),
                            posterUrl = obj.optString("posterUrl").ifBlank { null },
                            referer = obj.optString("referer").ifBlank { null },
                            userAgent = obj.optString("userAgent").ifBlank { null },
                            durationMs = obj.optLong("durationMs"),
                            pageUrl = obj.optString("pageUrl").ifBlank { null },
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
