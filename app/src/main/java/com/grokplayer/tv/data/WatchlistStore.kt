package com.grokplayer.tv.data

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

data class WatchlistItem(
    val id: String,
    val videoId: String,
    val title: String,
    val uri: String,
    val path: String?,
    val durationMs: Long,
    val format: String,
    val source: String,
    val isStream: Boolean,
    val originUrl: String?,
    val posterUrl: String?,
    val referer: String?,
    val userAgent: String?,
    val addedAt: Long,
) {
    fun toVideo(): LibraryVideo = LibraryVideo(
        id = videoId.ifBlank { id },
        title = title.ifBlank { "Video" },
        uri = runCatching { Uri.parse(uri) }.getOrElse { Uri.EMPTY },
        durationMs = durationMs,
        format = format.ifBlank { "VOD" },
        source = runCatching { StorageSource.valueOf(source) }.getOrDefault(StorageSource.Internal),
        dateAdded = addedAt,
        lastModified = addedAt,
        path = path?.ifBlank { null },
        isLive = false,
        isStream = isStream,
        originUrl = originUrl?.ifBlank { null },
        posterUrl = posterUrl?.ifBlank { null },
        referer = referer?.ifBlank { null },
        userAgent = userAgent?.ifBlank { null },
    )
}

data class WatchlistEntry(val key: String, val video: LibraryVideo)

object WatchlistLogic {
    fun key(video: LibraryVideo): String? {
        if (!video.isVod()) return null
        return WatchLogic.key(video)?.takeIf { it.isNotBlank() }
    }

    fun fromVideo(video: LibraryVideo, now: Long): WatchlistItem? {
        val id = key(video) ?: return null
        return WatchlistItem(
            id = id,
            videoId = video.id,
            title = video.title,
            uri = video.uri.toString(),
            path = video.path,
            durationMs = video.durationMs,
            format = video.format,
            source = video.source.name,
            isStream = video.isStream,
            originUrl = video.originUrl,
            posterUrl = video.posterUrl,
            referer = video.referer,
            userAgent = video.userAgent,
            addedAt = now,
        )
    }

    fun add(items: List<WatchlistItem>, item: WatchlistItem): List<WatchlistItem> {
        if (items.any { it.id == item.id }) return items
        return items + item
    }

    fun remove(items: List<WatchlistItem>, id: String): List<WatchlistItem> =
        items.filterNot { it.id == id }

    fun encode(items: List<WatchlistItem>): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("videoId", item.videoId)
                    .put("title", item.title)
                    .put("uri", item.uri)
                    .put("path", item.path ?: "")
                    .put("durationMs", item.durationMs)
                    .put("format", item.format)
                    .put("source", item.source)
                    .put("isStream", item.isStream)
                    .put("originUrl", item.originUrl ?: "")
                    .put("posterUrl", item.posterUrl ?: "")
                    .put("referer", item.referer ?: "")
                    .put("userAgent", item.userAgent ?: "")
                    .put("addedAt", item.addedAt),
            )
        }
        return array.toString()
    }

    fun decode(raw: String?): List<WatchlistItem> {
        val array = runCatching { JSONArray(raw ?: return emptyList()) }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id")
                if (id.isBlank()) continue
                add(
                    WatchlistItem(
                        id = id,
                        videoId = item.optString("videoId").ifBlank { id },
                        title = item.optString("title"),
                        uri = item.optString("uri"),
                        path = item.optString("path").ifBlank { null },
                        durationMs = item.optLong("durationMs"),
                        format = item.optString("format"),
                        source = item.optString("source").ifBlank { StorageSource.Internal.name },
                        isStream = item.optBoolean("isStream"),
                        originUrl = item.optString("originUrl").ifBlank { null },
                        posterUrl = item.optString("posterUrl").ifBlank { null },
                        referer = item.optString("referer").ifBlank { null },
                        userAgent = item.optString("userAgent").ifBlank { null },
                        addedAt = item.optLong("addedAt"),
                    ),
                )
            }
        }
    }

    fun entries(items: List<WatchlistItem>, known: List<LibraryVideo>): List<WatchlistEntry> {
        val byKey = linkedMapOf<String, LibraryVideo>()
        known.sortedByDescending { !it.path.isNullOrBlank() }.forEach { video ->
            val id = key(video) ?: return@forEach
            byKey.putIfAbsent(id, video)
        }
        return items.map { item -> WatchlistEntry(item.id, byKey[item.id] ?: item.toVideo()) }
    }
}

class WatchlistStore(context: Context, now: () -> Long = { System.currentTimeMillis() }) {
    private val prefs = context.applicationContext.getSharedPreferences("watchlist", Context.MODE_PRIVATE)
    private val clock = now

    var items by mutableStateOf(WatchlistLogic.decode(prefs.getString("items", null)))
        private set

    fun contains(video: LibraryVideo): Boolean {
        val id = WatchlistLogic.key(video) ?: return false
        return items.any { it.id == id }
    }

    fun add(video: LibraryVideo): Boolean {
        val item = WatchlistLogic.fromVideo(video, clock()) ?: return false
        val next = WatchlistLogic.add(items, item)
        if (next === items || next.size == items.size) return false
        items = next
        persist()
        return true
    }

    fun remove(video: LibraryVideo) {
        val id = WatchlistLogic.key(video) ?: return
        val next = WatchlistLogic.remove(items, id)
        if (next.size == items.size) return
        items = next
        persist()
    }

    fun entries(known: List<LibraryVideo>): List<WatchlistEntry> = WatchlistLogic.entries(items, known)

    private fun persist() {
        prefs.edit().putString("items", WatchlistLogic.encode(items)).commit()
    }
}
