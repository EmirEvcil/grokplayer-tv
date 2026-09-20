package com.grokplayer.tv.data

object CollectionGrouper {
    const val GENERAL_ID = "general"
    const val GENERAL_NAME = "Genel"

    data class Bucket<T>(
        val id: String,
        val stem: String?,
        val name: String,
        val items: List<T>,
    ) {
        val isGeneral: Boolean get() = id == GENERAL_ID || id.startsWith("general:")
    }

    fun <T> group(
        items: List<T>,
        titleOf: (T) -> String,
        idOf: (T) -> String = { titleOf(it) },
        renameOf: (String) -> String? = { null },
        homeOf: (String) -> String? = { null },
        homesOf: (String) -> List<String> = { id -> listOfNotNull(homeOf(id)) },
        scope: String = "",
        keepIds: Set<String> = emptySet(),
    ): List<Bucket<T>> {
        val generalId = generalId(scope)
        val assigned = linkedMapOf<String, MutableList<T>>()
        val loose = mutableListOf<T>()
        items.forEach { item ->
            val forced = homesOf(idOf(item)).filter { inScope(it, scope) }
            if (forced.isNotEmpty()) {
                forced.forEach { assigned.getOrPut(it) { mutableListOf() } += item }
            } else {
                val stem = MediaOrder.collectionStem(titleOf(item))
                if (stem.isNullOrBlank()) loose += item else assigned.getOrPut(autoId(stem, scope)) { mutableListOf() } += item
            }
        }
        val leftover = mutableListOf<T>()
        loose.forEach { item ->
            val normalized = MediaOrder.normalizeTitle(titleOf(item))
            val hit = assigned.keys.firstOrNull { key -> stemOf(key) == normalized }
            if (hit != null) assigned.getValue(hit) += item else leftover += item
        }
        val buckets = mutableListOf<Bucket<T>>()
        assigned.forEach { (id, list) ->
            val stem = stemOf(id)
            val autoName = stem?.let { MediaOrder.prettyTitle(it) } ?: id.substringAfterLast(':')
            val keep = list.isNotEmpty() && (list.size > 1 || id in keepIds || isUser(id))
            if (id == generalId || id == GENERAL_ID || keep) {
                buckets += Bucket(
                    id = id,
                    stem = stem,
                    name = renameOf(id) ?: if (id == generalId || id == GENERAL_ID) GENERAL_NAME else autoName,
                    items = MediaOrder.sortByTitle(list, titleOf),
                )
            } else {
                leftover += list
            }
        }
        if (leftover.isNotEmpty()) {
            val existing = buckets.indexOfFirst { it.id == generalId || it.id == GENERAL_ID }
            val merged = MediaOrder.sortByTitle(
                (if (existing >= 0) buckets[existing].items else emptyList()) + leftover,
                titleOf,
            )
            val general = Bucket(generalId, null, renameOf(generalId) ?: renameOf(GENERAL_ID) ?: GENERAL_NAME, merged)
            if (existing >= 0) buckets[existing] = general else buckets += general
        }
        return buckets.sortedWith(compareBy({ if (it.isGeneral) 1 else 0 }, { it.name.lowercase() }))
    }

    fun autoId(stem: String, scope: String = ""): String {
        val clean = stem.trim().lowercase()
        return if (scope.isBlank()) "auto:$clean" else "auto:$scope:$clean"
    }

    fun generalId(scope: String = ""): String =
        if (scope.isBlank()) GENERAL_ID else "general:$scope"

    fun userId(scope: String): String = "user:$scope:${java.util.UUID.randomUUID()}"

    fun inScope(id: String, scope: String): Boolean {
        if (scope.isBlank()) return true
        if (id == "general:$scope") return true
        if (id.startsWith("auto:$scope:")) return true
        if (id.startsWith("user:$scope:")) return true
        return false
    }

    fun stemOf(id: String): String? {
        if (!id.startsWith("auto:")) return null
        val rest = id.removePrefix("auto:")
        val colon = rest.indexOf(':')
        return if (colon >= 0) rest.substring(colon + 1) else rest
    }

    fun isUser(id: String): Boolean = id.startsWith("user:")

    fun mergeKnown(
        known: Set<String>,
        playlistId: String,
        liveIds: Set<String>,
        discovered: Set<String>,
    ): Set<String> {
        if (playlistId.isBlank()) return known
        if (liveIds.isEmpty() && discovered.isEmpty()) return known
        val others = known.filter { !inScope(it, playlistId) }.toSet()
        val scoped = (known.filter { inScope(it, playlistId) }.toSet() + discovered)
            .filter { it in liveIds }
            .toSet()
        return others + scoped
    }
}
