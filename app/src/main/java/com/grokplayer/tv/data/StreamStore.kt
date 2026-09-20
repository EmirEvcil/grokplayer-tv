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

    init {
        if (!prefs.getBoolean("catalog_seeded", false)) {
            if (items.isEmpty()) {
                items = samples()
                persist()
            }
            prefs.edit().putBoolean("catalog_seeded", true).apply()
        }
    }

    fun add(
        title: String,
        url: String,
        kind: StreamKind = StreamKind.Vod,
        posterUrl: String? = null,
        referer: String? = null,
        userAgent: String? = null,
        durationMs: Long = 0L,
        pageUrl: String? = null,
    ): String? {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) return null
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
        return item.id
    }

    fun applyHit(id: String, hit: com.grokplayer.tv.data.scan.ScanHit) {
        items = items.map { item ->
            if (item.id != id) item else item.copy(
                title = hit.title.ifBlank { item.title },
                url = hit.playUrl,
                kind = hit.kind,
                posterUrl = hit.thumbnailUrl ?: item.posterUrl,
                referer = hit.referer ?: item.referer,
                userAgent = hit.userAgent ?: item.userAgent,
                durationMs = hit.durationMs.takeIf { it > 0L } ?: item.durationMs,
                pageUrl = hit.pageUrl.ifBlank { item.pageUrl },
            )
        }
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
        val raw = prefs.getString("items", null)
        if (raw.isNullOrBlank()) return emptyList()
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

    private fun samples(): List<StreamItem> {
        val now = System.currentTimeMillis()
        fun vod(id: String, title: String, url: String) = StreamItem(
            id = id,
            title = title,
            url = url,
            kind = StreamKind.Vod,
            favorite = false,
            addedAt = now,
        )
        return listOf(
            vod(
                "sample:bbb",
                "Big Buck Bunny",
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            ),
            vod(
                "sample:elephants",
                "Elephant's Dream",
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
            ),
            vod(
                "sample:sintel",
                "Sintel",
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
            ),
            vod(
                "sample:tears",
                "Tears of Steel",
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
            ),
            StreamItem(
                id = "sample:bbc-testcard",
                title = "BBC Testcard",
                url = "https://rdmedia.bbc.co.uk/testcard/simulcast/manifests/avc-ctv-stereo-en.m3u8",
                kind = StreamKind.Live,
                favorite = false,
                addedAt = now,
            ),
        )
    }
}
