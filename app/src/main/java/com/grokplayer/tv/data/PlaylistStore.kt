package com.grokplayer.tv.data

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

const val CUSTOM_PLAYLIST_PC = "local"

data class FolderPlaylist(
    val id: String,
    val title: String,
    val pcId: String,
    val path: String,
    val addedAt: Long,
    val custom: Boolean = false,
)

data class PlaylistEntry(
    val id: String,
    val title: String,
    val uri: String,
    val originUrl: String? = null,
    val durationMs: Long = 0L,
    val format: String = "VOD",
    val posterUrl: String? = null,
    val referer: String? = null,
    val userAgent: String? = null,
    val path: String? = null,
    val addedAt: Long = 0L,
) {
    fun toVideo(): LibraryVideo = LibraryVideo(
        id = id,
        title = title,
        uri = Uri.parse(uri),
        durationMs = durationMs,
        format = format,
        source = StorageSource.Internal,
        dateAdded = addedAt,
        lastModified = addedAt,
        path = path,
        isStream = uri.startsWith("http", ignoreCase = true),
        originUrl = originUrl,
        posterUrl = posterUrl,
        referer = referer,
        userAgent = userAgent,
    )

    companion object {
        fun from(video: LibraryVideo): PlaylistEntry = PlaylistEntry(
            id = listKey(video),
            title = video.title,
            uri = video.uri.toString(),
            originUrl = video.originUrl,
            durationMs = video.durationMs,
            format = video.format,
            posterUrl = video.posterUrl,
            referer = video.referer,
            userAgent = video.userAgent,
            path = video.path,
            addedAt = System.currentTimeMillis(),
        )

        fun listKey(video: LibraryVideo): String {
            val url = video.originUrl?.ifBlank { null } ?: video.uri.toString()
            return if (url.startsWith("http", ignoreCase = true)) {
                DownloadPolicy.normalizeUrl(url)
            } else {
                video.path?.replace('\\', '/')?.lowercase() ?: video.id
            }
        }
    }
}

class PlaylistStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("folder_playlists", Context.MODE_PRIVATE)
    var items by mutableStateOf(load())
        private set
    private var hidden by mutableStateOf(loadHidden())
    private var entries by mutableStateOf(loadEntries())
    private var revision by mutableStateOf(0)

    fun tick(): Int = revision

    fun videosOf(id: String): List<LibraryVideo> = entries[id].orEmpty().map { it.toVideo() }

    fun createCustom(title: String): FolderPlaylist {
        val item = FolderPlaylist(
            id = UUID.randomUUID().toString(),
            title = title.trim().ifBlank { "Oynatma listesi" },
            pcId = CUSTOM_PLAYLIST_PC,
            path = "",
            addedAt = System.currentTimeMillis(),
            custom = true,
        )
        items = listOf(item) + items
        persist()
        revision++
        return item
    }

    fun addVideo(playlistId: String, video: LibraryVideo): Boolean {
        if (!video.isVod()) return false
        val playlist = items.firstOrNull { it.id == playlistId } ?: return false
        if (!playlist.custom) return false
        val entry = PlaylistEntry.from(video)
        val current = entries[playlistId].orEmpty()
        if (current.any { it.id == entry.id }) return false
        entries = entries + (playlistId to (current + entry))
        persist()
        revision++
        return true
    }

    fun removeVideo(playlistId: String, videoId: String) {
        val current = entries[playlistId] ?: return
        entries = entries + (playlistId to current.filterNot { it.id == videoId })
        persist()
        revision++
    }

    fun syncGranted(pcId: String, granted: List<String>) {
        if (granted.isEmpty()) return
        val existing = items.filter { it.pcId == pcId }.map { it.path.lowercase() }.toSet()
        val extra = granted.filter { path ->
            path.isNotBlank() &&
                path.lowercase() !in existing &&
                hideKey(pcId, path) !in hidden
        }
        if (extra.isEmpty()) return
        val now = System.currentTimeMillis()
        items = extra.map { path ->
            FolderPlaylist(
                id = UUID.randomUUID().toString(),
                title = folderTitle(path),
                pcId = pcId,
                path = path,
                addedAt = now,
                custom = false,
            )
        } + items
        persist()
    }

    fun availableFolders(pcId: String, granted: List<String>): List<Pair<String, String>> {
        val current = items.filter { it.pcId == pcId }.map { it.path.lowercase() }.toSet()
        return granted
            .filter { it.isNotBlank() && it.lowercase() !in current }
            .distinctBy { it.lowercase() }
            .map { it to folderTitle(it) }
    }

    fun add(pcId: String, path: String, title: String): FolderPlaylist? {
        val clean = path.trim()
        if (clean.isBlank()) return null
        hidden = hidden - hideKey(pcId, clean)
        items.firstOrNull { it.pcId == pcId && it.path.equals(clean, ignoreCase = true) }?.let {
            persist()
            return it
        }
        val item = FolderPlaylist(
            id = UUID.randomUUID().toString(),
            title = title.trim().ifBlank { clean.substringAfterLast('\\').substringAfterLast('/') },
            pcId = pcId,
            path = clean,
            addedAt = System.currentTimeMillis(),
            custom = false,
        )
        items = listOf(item) + items
        persist()
        return item
    }

    fun remove(id: String) {
        val item = items.firstOrNull { it.id == id }
        if (item != null && !item.custom) hidden = hidden + hideKey(item.pcId, item.path)
        items = items.filterNot { it.id == id }
        entries = entries - id
        persist()
        revision++
    }

    fun rename(id: String, title: String) {
        val name = title.trim()
        if (name.isBlank()) return
        items = items.map { if (it.id == id) it.copy(title = name) else it }
        persist()
    }

    private fun persist() {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("title", item.title)
                    .put("pcId", item.pcId)
                    .put("path", item.path)
                    .put("addedAt", item.addedAt)
                    .put("custom", item.custom),
            )
        }
        val hiddenArray = JSONArray()
        hidden.forEach { hiddenArray.put(it) }
        val videosObj = JSONObject()
        entries.forEach { (id, list) ->
            val rows = JSONArray()
            list.forEach { entry ->
                rows.put(
                    JSONObject()
                        .put("id", entry.id)
                        .put("title", entry.title)
                        .put("uri", entry.uri)
                        .put("originUrl", entry.originUrl.orEmpty())
                        .put("durationMs", entry.durationMs)
                        .put("format", entry.format)
                        .put("posterUrl", entry.posterUrl.orEmpty())
                        .put("referer", entry.referer.orEmpty())
                        .put("userAgent", entry.userAgent.orEmpty())
                        .put("path", entry.path.orEmpty())
                        .put("addedAt", entry.addedAt),
                )
            }
            videosObj.put(id, rows)
        }
        prefs.edit()
            .putString("items", array.toString())
            .putString("hidden", hiddenArray.toString())
            .putString("videos", videosObj.toString())
            .apply()
    }

    private fun hideKey(pcId: String, path: String): String = "$pcId|${path.trim().lowercase()}"

    private fun folderTitle(path: String): String =
        path.trimEnd('\\', '/').substringAfterLast('\\').substringAfterLast('/').ifBlank { path }

    private fun loadHidden(): Set<String> {
        val raw = prefs.getString("hidden", null) ?: return emptySet()
        return runCatching {
            val array = JSONArray(raw)
            buildSet {
                for (i in 0 until array.length()) {
                    val key = array.optString(i)
                    if (key.isNotBlank()) add(key)
                }
            }
        }.getOrDefault(emptySet())
    }

    private fun load(): List<FolderPlaylist> {
        val raw = prefs.getString("items", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        FolderPlaylist(
                            id = obj.getString("id"),
                            title = obj.getString("title"),
                            pcId = obj.getString("pcId"),
                            path = obj.getString("path"),
                            addedAt = obj.optLong("addedAt"),
                            custom = obj.optBoolean("custom") || obj.optString("pcId") == CUSTOM_PLAYLIST_PC,
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun loadEntries(): Map<String, List<PlaylistEntry>> {
        val raw = prefs.getString("videos", null) ?: return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            buildMap {
                obj.keys().forEach { id ->
                    val rows = obj.optJSONArray(id) ?: return@forEach
                    put(
                        id,
                        buildList {
                            for (i in 0 until rows.length()) {
                                val row = rows.optJSONObject(i) ?: continue
                                add(
                                    PlaylistEntry(
                                        id = row.optString("id"),
                                        title = row.optString("title"),
                                        uri = row.optString("uri"),
                                        originUrl = row.optString("originUrl").ifBlank { null },
                                        durationMs = row.optLong("durationMs"),
                                        format = row.optString("format").ifBlank { "VOD" },
                                        posterUrl = row.optString("posterUrl").ifBlank { null },
                                        referer = row.optString("referer").ifBlank { null },
                                        userAgent = row.optString("userAgent").ifBlank { null },
                                        path = row.optString("path").ifBlank { null },
                                        addedAt = row.optLong("addedAt"),
                                    ),
                                )
                            }
                        },
                    )
                }
            }
        }.getOrDefault(emptyMap())
    }
}
