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
) {
    fun toVideo(): LibraryVideo = LibraryVideo(
        id = "stream:$id",
        title = title,
        uri = Uri.parse(url),
        durationMs = 0L,
        format = if (kind == StreamKind.Live) "CANLI" else "VOD",
        source = StorageSource.Internal,
        dateAdded = addedAt,
        lastModified = addedAt,
        path = null,
        isLive = kind == StreamKind.Live,
        isStream = true,
        originUrl = url,
    )
}

class StreamStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("streams", Context.MODE_PRIVATE)

    var items by mutableStateOf(load())
        private set

    fun add(title: String, url: String, kind: StreamKind = StreamKind.Vod) {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) return
        val item = StreamItem(
            id = UUID.randomUUID().toString(),
            title = title.trim().ifBlank { cleanUrl.substringAfterLast('/').ifBlank { "Akış" } },
            url = cleanUrl,
            kind = kind,
            favorite = false,
            addedAt = System.currentTimeMillis(),
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
                    .put("addedAt", item.addedAt),
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
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
