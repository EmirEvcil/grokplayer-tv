package com.grokplayer.tv.data

object CollectionReset {
    data class State(
        val names: Map<String, String>,
        val homes: Map<String, Set<String>>,
        val userIds: List<String>,
        val known: Set<String>,
        val excluded: Map<String, Set<String>> = emptyMap(),
    )

    fun full(scope: String, state: State): State {
        if (scope.isBlank()) return state
        return State(
            names = state.names.filterKeys { !CollectionGrouper.inScope(it, scope) },
            homes = pruneHomes(state.homes) { !CollectionGrouper.inScope(it, scope) },
            userIds = state.userIds.filter { !CollectionGrouper.inScope(it, scope) },
            known = state.known.filter { !CollectionGrouper.inScope(it, scope) }.toSet(),
            excluded = pruneHomes(state.excluded) { col ->
                !CollectionGrouper.inScope(col, scope) && col != CollectionGrouper.GENERAL_ID
            },
        )
    }

    fun keepCustom(scope: String, state: State): State {
        if (scope.isBlank()) return state
        return State(
            names = state.names.filterKeys { !CollectionGrouper.inScope(it, scope) || CollectionGrouper.isUser(it) },
            homes = pruneHomes(state.homes) { !CollectionGrouper.inScope(it, scope) || CollectionGrouper.isUser(it) },
            userIds = state.userIds,
            known = state.known.filter { !it.startsWith("auto:$scope:") }.toSet(),
            excluded = pruneHomes(state.excluded) { col ->
                (!CollectionGrouper.inScope(col, scope) || CollectionGrouper.isUser(col)) &&
                    col != CollectionGrouper.GENERAL_ID
            },
        )
    }

    private fun pruneHomes(
        homes: Map<String, Set<String>>,
        keep: (String) -> Boolean,
    ): Map<String, Set<String>> =
        homes.mapValues { (_, cols) -> cols.filter(keep).toSet() }.filterValues { it.isNotEmpty() }
}
