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
    val path: String? = null,
    val isLive: Boolean = false,
    val isStream: Boolean = false,
    val originUrl: String? = null,
) {
    val progress: Float?
        get() = null

    val sourceLabel: String
        get() = when {
            isLive || format.equals("CANLI", true) -> "CANLI"
            isStream -> "Akış"
            uri.scheme == "http" || uri.scheme == "https" -> "Akış"
            source == StorageSource.Usb -> "USB"
            else -> "Dahili"
        }
}

internal fun sameOpened(a: LibraryVideo, b: LibraryVideo): Boolean {
    if (a.id == b.id) return true
    val pathA = a.path?.let { LibraryStore.normalizePath(it) }
    val pathB = b.path?.let { LibraryStore.normalizePath(it) }
    if (pathA != null && pathA == pathB) return true
    val originA = originKey(a)
    val originB = originKey(b)
    if (originA != null && originA == originB) return true
    return a.uri == b.uri && a.uri.scheme != null
}

private fun originKey(video: LibraryVideo): String? {
    val raw = video.originUrl?.ifBlank { null }
        ?: video.uri.takeIf { it.scheme == "http" || it.scheme == "https" }?.toString()
    return raw?.substringBefore('?')?.lowercase()
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

fun formatSpeedLabel(speed: Float): String {
    val snapped = PlaybackSettings.snapSpeed(speed)
    val raw = if (snapped % 1f == 0f) {
        snapped.toInt().toString()
    } else {
        "%.2f".format(java.util.Locale.US, snapped).trimEnd('0').replace('.', ',')
    }
    return "${raw}×"
}
