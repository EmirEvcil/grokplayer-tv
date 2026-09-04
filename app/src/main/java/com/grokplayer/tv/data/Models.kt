package com.grokplayer.tv.data

import android.net.Uri

enum class StorageSource { Internal, Usb }

enum class VideoSort {
    RecentlyAdded,
    Oldest,
    Alphabetical,
    Newest,
}

data class LibraryVideo(
    val id: String,
    val title: String,
    val uri: Uri,
    val durationMs: Long,
    val format: String,
    val source: StorageSource,
    val dateAdded: Long,
    val lastModified: Long,
) {
    val progress: Float?
        get() = null
}

data class PlaySession(
    val queue: List<LibraryVideo>,
    val startIndex: Int,
)

fun Long.formatClock(): String {
    val total = (this / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) {
        "%d:%02d:%02d".format(h, m, s)
    } else {
        "%02d:%02d".format(m, s)
    }
}
