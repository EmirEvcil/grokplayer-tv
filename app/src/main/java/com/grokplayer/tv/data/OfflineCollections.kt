package com.grokplayer.tv.data

object OfflineCollections {
    const val SCOPE = "offline"
    const val SOURCE_ID = "offline"

    fun isLocalFile(path: String?, scheme: String?): Boolean {
        if (scheme == "file" || scheme == "content") return true
        val p = path ?: return false
        return !p.startsWith("http://", ignoreCase = true) &&
            !p.startsWith("https://", ignoreCase = true)
    }

    fun isOfflineVideo(video: LibraryVideo): Boolean =
        !video.isLive && !video.isStream && isLocalFile(video.path, video.uri.scheme)

    fun fileKey(path: String?, uri: String?, id: String): String {
        val file = path?.trim()?.replace('\\', '/')?.lowercase().orEmpty()
        if (file.isNotBlank()) return "p:$file"
        val remote = uri?.trim()?.lowercase().orEmpty()
        if (remote.isNotBlank()) return "u:$remote"
        return "i:$id"
    }

    fun fileKey(video: LibraryVideo): String = fileKey(video.path, video.uri.toString(), video.id)

    fun <T> mergeByKey(preferred: List<T>, extra: List<T>, keyOf: (T) -> String): List<T> {
        val seen = HashSet<String>()
        val out = ArrayList<T>()
        (preferred + extra).forEach { item ->
            val key = keyOf(item)
            if (key.isNotBlank() && seen.add(key)) out += item
        }
        return out
    }
}
