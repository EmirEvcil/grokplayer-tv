package com.grokplayer.tv.data

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

val LocalPlaybackQueue = staticCompositionLocalOf<PlaybackQueue?> { null }

class PlaybackQueue {
    var items by mutableStateOf<List<LibraryVideo>>(emptyList())
        private set
    var index by mutableIntStateOf(-1)
        private set

    fun current(): LibraryVideo? = items.getOrNull(index)

    fun addNext(video: LibraryVideo, currentId: String?): Boolean {
        if (!video.isVod()) return false
        val ids = queueAddNext(items.map { it.id }, currentId, video.id)
        val byId = (items + video).associateBy { it.id }
        items = ids.mapNotNull { byId[it] }
        index = items.indexOfFirst { it.id == currentId }.let { if (it >= 0) it else index.coerceAtMost(items.lastIndex) }
        return true
    }

    fun addLast(video: LibraryVideo): Boolean {
        if (!video.isVod()) return false
        if (items.any { it.id == video.id }) return false
        items = items + video
        if (index !in items.indices) index = items.lastIndex
        return true
    }

    fun remove(id: String) {
        val currentId = current()?.id
        items = items.filterNot { it.id == id }
        index = when {
            items.isEmpty() -> -1
            currentId == id -> -1
            else -> items.indexOfFirst { it.id == currentId }
        }
    }

    fun clear() {
        items = emptyList()
        index = -1
    }

    fun moveTo(id: String) {
        val at = items.indexOfFirst { it.id == id }
        if (at >= 0) index = at
    }

    fun move(from: Int, to: Int) {
        val currentId = items.getOrNull(index)?.id
        items = moveItem(items, from, to)
        if (currentId != null) {
            val at = items.indexOfFirst { it.id == currentId }
            if (at >= 0) index = at
        }
    }
}

fun nextPlayable(index: Int, lastIndex: Int, playable: (Int) -> Boolean): Int? {
    val next = index + 1
    if (next > lastIndex || !playable(next)) return null
    return next
}

fun previousPlayable(index: Int, playable: (Int) -> Boolean): Int? {
    val previous = index - 1
    if (previous < 0 || !playable(previous)) return null
    return previous
}

fun nextVodIndex(index: Int, videos: List<LibraryVideo>): Int? =
    nextPlayable(index, videos.lastIndex) { videos[it].isVod() }

fun previousVodIndex(index: Int, videos: List<LibraryVideo>): Int? =
    previousPlayable(index) { videos[it].isVod() }

fun <T> insertCopyAfter(items: List<T>, current: T, insert: T, same: (T, T) -> Boolean): List<T> {
    val at = items.indexOfFirst { same(it, current) }
    return insertCopyAfterIndex(items, at, insert)
}

fun <T> insertCopyAfterIndex(items: List<T>, currentIndex: Int, insert: T): List<T> {
    val index = if (currentIndex in items.indices) currentIndex + 1 else items.size
    return items.toMutableList().apply { add(index, insert) }
}

fun indexAfterInsert(index: Int, insertAt: Int): Int = if (insertAt <= index) index + 1 else index

fun markTemporaryInsert(flags: List<Boolean>, count: Int, insertAt: Int): List<Boolean> {
    val next = flags.take(count).toMutableList()
    while (next.size < count) next += false
    next.add(insertAt.coerceIn(0, next.size), true)
    return next
}

fun <T> dropPlayedInsert(items: List<T>, flags: List<Boolean>, at: Int): Pair<List<T>, List<Boolean>>? {
    if (at !in items.indices || flags.getOrNull(at) != true) return null
    return items.filterIndexed { index, _ -> index != at } to flags.filterIndexed { index, _ -> index != at }
}

fun indexAfterPlayedDrop(index: Int, size: Int): Int? =
    if (index in 0 until size) index else null

fun <T> withQueuedUpcoming(
    playlist: List<T>,
    index: Int,
    queued: List<T>,
    followingQueue: Boolean,
    id: (T) -> String,
): List<T> {
    if (followingQueue) return queued
    val ahead = playlist.drop(index.coerceAtLeast(0)).map(id).toSet()
    val extra = queued.filter { id(it) !in ahead }
    return if (extra.isEmpty()) playlist else playlist + extra
}

fun queuePlayNextIndex(ids: List<String>, currentId: String?, pickedId: String): Int =
    queueAddNext(ids, currentId, pickedId).indexOf(pickedId).coerceAtLeast(0)

fun <T> moveItem(items: List<T>, from: Int, to: Int): List<T> {
    if (from !in items.indices || to !in items.indices || from == to) return items
    val next = items.toMutableList()
    val item = next.removeAt(from)
    next.add(to, item)
    return next
}

internal fun queueAddNext(ids: List<String>, currentId: String?, id: String): List<String> {
    val without = ids.filterNot { it == id }
    val at = without.indexOf(currentId).let { if (it >= 0) it + 1 else without.size }
    return without.toMutableList().apply { add(at, id) }
}
