package com.grokplayer.tv.data

data class FocusAnchor(
    val key: String? = null,
    val index: Int = 0,
) {
    fun remember(key: String, keys: List<String>): FocusAnchor {
        val at = keys.indexOf(key).takeIf { it >= 0 } ?: index
        return FocusAnchor(key, at.coerceAtLeast(0))
    }

    fun resolve(keys: List<String>): String? {
        if (keys.isEmpty()) return null
        val byKey = key?.let { keys.indexOf(it) } ?: -1
        if (byKey >= 0) return keys[byKey]
        return keys.getOrNull(index.coerceIn(0, keys.lastIndex))
    }
}
