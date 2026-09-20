package com.grokplayer.tv.data

data class PlaylistCollectionSummary(
    val playlistId: String,
    val title: String,
    val collectionCount: Int,
    val videoCount: Int,
)

object PlaylistCollections {
    fun summarize(
        playlists: List<FolderPlaylist>,
        videosOf: (String) -> List<LibraryVideo>,
        collectionsOf: (List<LibraryVideo>, String) -> List<CollectionGrouper.Bucket<LibraryVideo>>,
    ): List<PlaylistCollectionSummary> {
        return playlists.mapNotNull { playlist ->
            val videos = videosOf(playlist.id)
            val buckets = collectionsOf(videos, playlist.id)
            if (buckets.isEmpty()) {
                null
            } else {
                PlaylistCollectionSummary(
                    playlistId = playlist.id,
                    title = playlist.title,
                    collectionCount = buckets.size,
                    videoCount = videos.size,
                )
            }
        }
    }

    fun <T> moveTargets(
        currentPlaylistId: String,
        buckets: List<CollectionGrouper.Bucket<T>>,
    ): List<CollectionGrouper.Bucket<T>> {
        return buckets.filter { CollectionGrouper.inScope(it.id, currentPlaylistId) }
    }
}
