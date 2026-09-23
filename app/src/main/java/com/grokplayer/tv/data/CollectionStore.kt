package com.grokplayer.tv.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

class CollectionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("video_collections", Context.MODE_PRIVATE)
    private var names by mutableStateOf(mapOf<String, String>())
    private var homes by mutableStateOf(mapOf<String, Set<String>>())
    private var userIds by mutableStateOf(listOf<String>())
    private var known by mutableStateOf(setOf<String>())
    private var excluded by mutableStateOf(mapOf<String, Set<String>>())
    private var revision by mutableStateOf(0)

    init {
        load()
    }

    fun tick(): Int = revision

    fun apply(
        videos: List<LibraryVideo>,
        playlistId: String = "",
    ): List<CollectionGrouper.Bucket<LibraryVideo>> {
        revision
        val grouped = CollectionGrouper.group(
            items = videos,
            titleOf = { it.title },
            idOf = { it.id },
            renameOf = { names[it] },
            homesOf = { id ->
                val video = videos.firstOrNull { it.id == id }
                val keys = listOfNotNull(id, video?.let { PlaylistEntry.listKey(it) }).distinct()
                keys.flatMap { homes[it].orEmpty() }.toSet().filter { CollectionGrouper.inScope(it, playlistId) }
            },
            scope = playlistId,
            keepIds = known,
        )
        val buckets = grouped.map { bucket ->
            bucket.copy(
                items = bucket.items.filter { video ->
                    videoKeys(video).none { excluded[it]?.contains(bucket.id) == true }
                },
            )
        }
        val live = buckets.filter { it.items.isNotEmpty() }.toMutableList()
        userIds.filter { CollectionGrouper.inScope(it, playlistId) }.forEach { id ->
            if (live.none { it.id == id }) {
                live += CollectionGrouper.Bucket(
                    id = id,
                    stem = null,
                    name = names[id] ?: "Koleksiyon",
                    items = emptyList(),
                )
            }
        }
        if (playlistId.isBlank() || videos.isEmpty()) return live
        val liveIds = live.map { it.id }.toSet()
        val discovered = live.filter { !it.isGeneral && !CollectionGrouper.isUser(it.id) }.map { it.id }.toSet()
        val nextKnown = CollectionGrouper.mergeKnown(known, playlistId, liveIds, discovered)
        if (nextKnown != known) {
            known = nextKnown
            persist()
        }
        return live
    }

    fun rename(id: String, title: String) {
        val name = title.trim()
        if (name.isBlank()) return
        names = names + (id to name)
        persist()
        revision++
    }

    fun move(videoId: String, collectionId: String, playlistId: String = "") {
        val keep = if (playlistId.isBlank()) {
            emptySet()
        } else {
            homes[videoId].orEmpty().filter { !CollectionGrouper.inScope(it, playlistId) }
        }
        homes = homes + (videoId to (keep + collectionId).toSet())
        persist()
        revision++
    }

    fun assign(videoId: String, collectionId: String) {
        homes = homes + (videoId to (homes[videoId].orEmpty() + collectionId))
        val drop = excluded[videoId].orEmpty() - collectionId
        excluded = if (drop.isEmpty()) excluded - videoId else excluded + (videoId to drop)
        persist()
        revision++
    }

    fun assignVideo(video: LibraryVideo, collectionId: String) {
        videoKeys(video).forEach { assign(it, collectionId) }
    }

    fun removeVideo(video: LibraryVideo, collectionId: String, playlistId: String) {
        videoKeys(video).forEach { key ->
            if (CollectionGrouper.isGeneralId(collectionId)) {
                excluded = excluded + (key to (excluded[key].orEmpty() + collectionId))
            }
            val next = CollectionGrouper.homesAfterRemove(homes[key].orEmpty(), collectionId, playlistId)
            homes = if (next.isEmpty()) homes - key else homes + (key to next)
        }
        persist()
        revision++
    }

    private fun videoKeys(video: LibraryVideo): List<String> =
        listOf(video.id, PlaylistEntry.listKey(video)).distinct()

    fun create(title: String, playlistId: String = ""): String {
        val id = if (playlistId.isBlank()) {
            "user:${java.util.UUID.randomUUID()}"
        } else {
            CollectionGrouper.userId(playlistId)
        }
        val name = title.trim().ifBlank { "Koleksiyon" }
        userIds = userIds + id
        names = names + (id to name)
        persist()
        revision++
        return id
    }

    fun delete(id: String, playlistId: String, videoIds: List<String> = emptyList()) {
        if (CollectionGrouper.isUser(id)) {
            userIds = userIds.filterNot { it == id }
            names = names - id
            homes = homes.mapValues { (_, cols) -> cols - id }.filterValues { it.isNotEmpty() }
        } else {
            val general = CollectionGrouper.generalId(playlistId)
            homes = homes.mapValues { (_, cols) ->
                if (id in cols) (cols - id) + general else cols
            } + videoIds.associate { vid ->
                vid to ((homes[vid].orEmpty() - id) + general)
            }
        }
        known = known - id
        persist()
        revision++
    }

    fun resetFull(playlistId: String) {
        val next = CollectionReset.full(playlistId, snapshot())
        applyState(next)
    }

    fun resetKeepCustom(playlistId: String) {
        val next = CollectionReset.keepCustom(playlistId, snapshot())
        applyState(next)
    }

    private fun snapshot() = CollectionReset.State(names, homes, userIds, known, excluded)

    private fun applyState(state: CollectionReset.State) {
        names = state.names
        homes = state.homes
        userIds = state.userIds
        known = state.known
        excluded = state.excluded
        persist()
        revision++
    }

    private fun persist() {
        val nameObj = JSONObject()
        names.forEach { (k, v) -> nameObj.put(k, v) }
        val homeObj = JSONObject()
        homes.forEach { (k, cols) ->
            val arr = JSONArray()
            cols.forEach { arr.put(it) }
            homeObj.put(k, arr)
        }
        prefs.edit()
            .putString("names", nameObj.toString())
            .putString("homes", homeObj.toString())
            .putString("users", userIds.joinToString("\n"))
            .putString("known", known.joinToString("\n"))
            .putString("excluded", JSONObject().also { obj ->
                excluded.forEach { (k, cols) ->
                    val arr = JSONArray()
                    cols.forEach { arr.put(it) }
                    obj.put(k, arr)
                }
            }.toString())
            .apply()
    }

    private fun load() {
        names = runCatching {
            val obj = JSONObject(prefs.getString("names", "{}"))
            buildMap { obj.keys().forEach { put(it, obj.optString(it)) } }
        }.getOrDefault(emptyMap())
        homes = runCatching {
            val obj = JSONObject(prefs.getString("homes", "{}"))
            buildMap {
                obj.keys().forEach { key ->
                    val raw = obj.opt(key)
                    val cols = when (raw) {
                        is JSONArray -> buildSet {
                            for (i in 0 until raw.length()) {
                                raw.optString(i).takeIf { it.isNotBlank() }?.let { add(it) }
                            }
                        }
                        else -> obj.optString(key).takeIf { it.isNotBlank() }?.let { setOf(it) }.orEmpty()
                    }
                    if (cols.isNotEmpty()) put(key, cols)
                }
            }
        }.getOrDefault(emptyMap())
        userIds = prefs.getString("users", "")?.lines()?.filter { it.isNotBlank() }.orEmpty()
        known = prefs.getString("known", "")?.lines()?.filter { it.isNotBlank() }.orEmpty().toSet()
        excluded = runCatching {
            val obj = JSONObject(prefs.getString("excluded", "{}"))
            buildMap {
                obj.keys().forEach { key ->
                    val raw = obj.optJSONArray(key) ?: return@forEach
                    val cols = buildSet {
                        for (i in 0 until raw.length()) {
                            raw.optString(i).takeIf { it.isNotBlank() }?.let { add(it) }
                        }
                    }
                    if (cols.isNotEmpty()) put(key, cols)
                }
            }
        }.getOrDefault(emptyMap())
    }
}
