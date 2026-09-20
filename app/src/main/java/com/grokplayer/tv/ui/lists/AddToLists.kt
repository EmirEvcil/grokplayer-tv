package com.grokplayer.tv.ui.lists

import com.grokplayer.tv.data.CollectionStore
import com.grokplayer.tv.data.PlaylistStore

fun collectionTargets(
    playlists: PlaylistStore,
    collections: CollectionStore,
): List<Triple<String, String, String>> {
    return playlists.items.filter { it.custom }.flatMap { playlist ->
        val videos = playlists.videosOf(playlist.id)
        collections.apply(videos, playlist.id).map { bucket ->
            Triple(playlist.id, bucket.id, "${playlist.title} · ${bucket.name}")
        }
    }
}
