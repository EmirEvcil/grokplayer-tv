package com.grokplayer.tv.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import org.json.JSONObject

enum class WatchStatus { Unwatched, Watching, Watched }

enum class WatchFeedback { Liked, Disliked }

data class WatchRecord(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val manual: WatchStatus? = null,
    val feedback: WatchFeedback? = null,
    val updatedAt: Long = 0L,
)

data class WatchStats(val watched: Int, val total: Int)

object WatchLogic {
    const val MIN_WATCH_MS = 1_000L
    const val WATCHED_RATIO = 0.90f
    const val REMAINING_MS = 15_000L

    private val dropQuery = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_content", "utm_term",
        "si", "feature", "pp", "fbclid", "gclid", "t", "start", "time_continue",
    )

    fun originIdentity(raw: String): String {
        val yt = com.grokplayer.tv.data.scan.YouTubeResolver.videoId(raw)
        if (yt != null) return "youtube:$yt"
        val trimmed = raw.trim().substringBefore('#').lowercase()
        val base = trimmed.substringBefore('?')
        val query = trimmed.substringAfter('?', missingDelimiterValue = "")
        if (query.isBlank()) return base
        val kept = query.split('&').filter { part ->
            val name = part.substringBefore('=').lowercase()
            name.isNotBlank() && name !in dropQuery
        }
        return if (kept.isEmpty()) base else "$base?${kept.joinToString("&")}"
    }

    fun key(isVod: Boolean, originUrl: String?, path: String?, id: String): String? {
        if (!isVod) return null
        val remote = originUrl?.ifBlank { null }
        if (remote != null) return originIdentity(remote)
        path?.let { raw ->
            val file = java.io.File(raw)
            if (file.isFile) return mediaFileKey(file.name, file.length())
        }
        return id
    }

    fun key(video: LibraryVideo): String? {
        val remote = video.originUrl?.ifBlank { null }
            ?: runCatching {
                video.uri.takeIf { it.scheme == "http" || it.scheme == "https" }?.toString()
            }.getOrNull()
        return key(video.isVod(), remote, video.path, video.id)
    }

    fun isFinished(positionMs: Long, durationMs: Long): Boolean {
        if (durationMs <= 0L || positionMs < MIN_WATCH_MS) return false
        if (positionMs >= (durationMs * WATCHED_RATIO).toLong()) return true
        return durationMs >= 30_000L && durationMs - positionMs <= REMAINING_MS
    }

    fun status(record: WatchRecord?, live: Boolean): WatchStatus? {
        if (live) return null
        when (record?.manual) {
            WatchStatus.Watched -> return WatchStatus.Watched
            WatchStatus.Unwatched -> return WatchStatus.Unwatched
            else -> Unit
        }
        val pos = record?.positionMs ?: 0L
        val dur = record?.durationMs ?: 0L
        if (pos < MIN_WATCH_MS) return WatchStatus.Unwatched
        if (isFinished(pos, dur)) return WatchStatus.Watched
        return WatchStatus.Watching
    }

    fun fraction(record: WatchRecord?, live: Boolean): Float? {
        return when (status(record, live)) {
            null, WatchStatus.Unwatched -> null
            WatchStatus.Watched -> 1f
            WatchStatus.Watching -> {
                val dur = record?.durationMs ?: return null
                if (dur <= 0L) return null
                (record.positionMs.toFloat() / dur).coerceIn(0.04f, 0.96f)
            }
        }
    }

    fun applyProgress(record: WatchRecord, positionMs: Long, durationMs: Long, now: Long): WatchRecord {
        val pos = positionMs.coerceAtLeast(0L)
        val incoming = durationMs.takeIf { it > 0L }
        val dur = when {
            incoming != null && record.durationMs > 0L -> minOf(incoming, record.durationMs).coerceAtLeast(pos)
            incoming != null -> incoming.coerceAtLeast(pos)
            else -> record.durationMs.coerceAtLeast(pos)
        }
        val manual = when {
            pos < MIN_WATCH_MS -> record.manual
            record.manual == WatchStatus.Unwatched -> null
            record.manual == WatchStatus.Watched && !isFinished(pos, dur) -> null
            else -> record.manual
        }
        return record.copy(positionMs = pos, durationMs = dur, manual = manual, updatedAt = now)
    }

    fun markWatched(record: WatchRecord, durationMs: Long, now: Long): WatchRecord {
        val dur = durationMs.takeIf { it > 0L } ?: record.durationMs
        return record.copy(
            positionMs = if (dur > 0L) dur else record.positionMs,
            durationMs = dur,
            manual = WatchStatus.Watched,
            updatedAt = now,
        )
    }

    fun markUnwatched(record: WatchRecord, now: Long): WatchRecord {
        return record.copy(positionMs = 0L, manual = WatchStatus.Unwatched, updatedAt = now)
    }

    fun toggleFeedback(record: WatchRecord, target: WatchFeedback, now: Long): WatchRecord {
        val next = if (record.feedback == target) null else target
        return record.copy(feedback = next, updatedAt = now)
    }

    fun playlistId(id: String) = "playlist:$id"
    fun collectionId(id: String) = "collection:$id"

    fun <T> resume(items: List<T>, cursorKey: String?, keyOf: (T) -> String?): T? {
        if (cursorKey.isNullOrBlank()) return null
        return items.firstOrNull { keyOf(it) == cursorKey }
    }

    fun resume(items: List<LibraryVideo>, cursorKey: String?): LibraryVideo? =
        resume(items, cursorKey, ::key)

    fun <T> stats(
        items: List<T>,
        isVod: (T) -> Boolean,
        recordOf: (T) -> WatchRecord?,
    ): WatchStats {
        val vods = items.filter(isVod)
        val watched = vods.count { status(recordOf(it), live = false) == WatchStatus.Watched }
        return WatchStats(watched = watched, total = vods.size)
    }

    fun stats(items: List<LibraryVideo>, recordOf: (LibraryVideo) -> WatchRecord?): WatchStats =
        stats(items, { it.isVod() }, recordOf)

    fun collectionMeta(isGeneral: Boolean, count: Int, stats: WatchStats): String {
        val base = if (isGeneral) "Genel · $count video" else "Koleksiyon · $count video"
        if (stats.total <= 0) return base
        return "$base · ${stats.watched} izlendi"
    }

    fun listWatchedLine(stats: WatchStats): String? {
        if (stats.total <= 0) return null
        return "${stats.watched}/${stats.total} izlendi"
    }
}

class WatchStore(private val file: File, now: () -> Long = { System.currentTimeMillis() }) {
    private val clock = now
    var records by mutableStateOf<Map<String, WatchRecord>>(emptyMap())
        private set
    var cursors by mutableStateOf<Map<String, String>>(emptyMap())
        private set

    init {
        load()
    }

    fun record(video: LibraryVideo): WatchRecord? = WatchLogic.key(video)?.let { records[it] }

    fun status(video: LibraryVideo): WatchStatus? = WatchLogic.status(record(video), video.isLive || !video.isVod())

    fun progressFraction(video: LibraryVideo): Float? = WatchLogic.fraction(record(video), video.isLive || !video.isVod())

    fun positionMs(video: LibraryVideo): Long = record(video)?.positionMs ?: 0L

    fun durationMs(video: LibraryVideo): Long {
        val recorded = record(video)?.durationMs ?: 0L
        val pos = record(video)?.positionMs ?: 0L
        val labeled = video.durationMs
        val best = when {
            recorded > 0L && labeled > 0L -> minOf(recorded, labeled)
            recorded > 0L -> recorded
            else -> labeled
        }
        return best.coerceAtLeast(pos)
    }

    fun feedback(video: LibraryVideo): WatchFeedback? = record(video)?.feedback

    fun onPlayed(video: LibraryVideo, positionMs: Long, durationMs: Long = video.durationMs) {
        val key = WatchLogic.key(video) ?: return
        put(key, WatchLogic.applyProgress(records[key] ?: WatchRecord(), positionMs, durationMs, clock()))
    }

    fun markWatched(video: LibraryVideo) {
        val key = WatchLogic.key(video) ?: return
        put(key, WatchLogic.markWatched(records[key] ?: WatchRecord(), video.durationMs, clock()))
    }

    fun markUnwatched(video: LibraryVideo) {
        val key = WatchLogic.key(video) ?: return
        put(key, WatchLogic.markUnwatched(records[key] ?: WatchRecord(), clock()))
    }

    fun toggleFeedback(video: LibraryVideo, target: WatchFeedback) {
        val key = WatchLogic.key(video) ?: return
        put(key, WatchLogic.toggleFeedback(records[key] ?: WatchRecord(), target, clock()))
    }

    fun setCursor(listId: String, video: LibraryVideo) {
        val key = WatchLogic.key(video) ?: return
        putCursor(listId, key)
    }

    internal fun put(key: String, record: WatchRecord) {
        if (key.isBlank()) return
        records = records + (key to record)
        persist()
    }

    internal fun putCursor(listId: String, key: String) {
        if (listId.isBlank() || key.isBlank()) return
        cursors = cursors + (listId to key)
        persist()
    }

    fun importKeyed(keyed: Map<String, Long>) = importLegacy(emptyMap(), keyed, emptyList())

    fun cursor(listId: String): String? = cursors[listId]

    fun resumeIn(items: List<LibraryVideo>, listId: String): LibraryVideo? =
        WatchLogic.resume(items, cursor(listId))

    fun stats(items: List<LibraryVideo>): WatchStats = WatchLogic.stats(items, ::record)

    fun importLegacy(progressById: Map<String, Long>, keyed: Map<String, Long>, videos: List<LibraryVideo>) {
        if (records.isNotEmpty()) return
        var next = records
        videos.forEach { video ->
            val key = WatchLogic.key(video) ?: return@forEach
            val pos = maxOf(progressById[video.id] ?: 0L, video.fileKey()?.let { keyed[it] } ?: 0L)
            if (pos < WatchLogic.MIN_WATCH_MS) return@forEach
            next = next + (key to WatchRecord(positionMs = pos, durationMs = video.durationMs, updatedAt = clock()))
        }
        keyed.forEach { (mapKey, pos) ->
            if (pos < WatchLogic.MIN_WATCH_MS) return@forEach
            if (mapKey.startsWith("title|")) return@forEach
            if (next.containsKey(mapKey)) return@forEach
            next = next + (mapKey to WatchRecord(positionMs = pos, updatedAt = clock()))
        }
        if (next !== records) {
            records = next
            persist()
        }
    }

    private fun load() {
        if (!file.exists()) return
        runCatching {
            val root = JSONObject(file.readText())
            val items = root.optJSONObject("items") ?: return
            val next = mutableMapOf<String, WatchRecord>()
            items.keys().forEach { key ->
                val obj = items.optJSONObject(key) ?: return@forEach
                next[key] = WatchRecord(
                    positionMs = obj.optLong("positionMs"),
                    durationMs = obj.optLong("durationMs"),
                    manual = when (obj.optString("manual")) {
                        "watched" -> WatchStatus.Watched
                        "unwatched" -> WatchStatus.Unwatched
                        else -> null
                    },
                    feedback = when (obj.optString("feedback")) {
                        "liked" -> WatchFeedback.Liked
                        "disliked" -> WatchFeedback.Disliked
                        else -> null
                    },
                    updatedAt = obj.optLong("updatedAt"),
                )
            }
            records = next
            val cur = root.optJSONObject("cursors") ?: return
            val nextCur = mutableMapOf<String, String>()
            cur.keys().forEach { id ->
                val value = cur.optString(id)
                if (value.isNotBlank()) nextCur[id] = value
            }
            cursors = nextCur
        }
    }

    private fun persist() {
        val items = JSONObject()
        records.forEach { (key, record) ->
            items.put(
                key,
                JSONObject()
                    .put("positionMs", record.positionMs)
                    .put("durationMs", record.durationMs)
                    .put("manual", when (record.manual) {
                        WatchStatus.Watched -> "watched"
                        WatchStatus.Unwatched -> "unwatched"
                        else -> JSONObject.NULL
                    })
                    .put("feedback", when (record.feedback) {
                        WatchFeedback.Liked -> "liked"
                        WatchFeedback.Disliked -> "disliked"
                        else -> JSONObject.NULL
                    })
                    .put("updatedAt", record.updatedAt),
            )
        }
        val cur = JSONObject()
        cursors.forEach { (id, key) -> cur.put(id, key) }
        file.writeText(JSONObject().put("items", items).put("cursors", cur).toString())
    }
}
