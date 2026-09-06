package com.grokplayer.tv.data.link

data class NearbyPc(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val seenAt: Long,
)

data class PairedPc(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val token: String,
)

data class TransferJob(
    val id: String,
    val pcId: String,
    val title: String,
    val kind: String,
    val status: String,
    val done: Long,
    val total: Long,
)

data class RemoteTrack(val index: Int, val label: String, val selected: Boolean)

data class RemoteItem(val index: Int, val title: String, val current: Boolean)

data class RemoteState(
    val playing: Boolean = false,
    val paused: Boolean = false,
    val hasMedia: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val volume: Double = 100.0,
    val title: String? = null,
    val playlistIndex: Int = -1,
    val playlist: List<RemoteItem> = emptyList(),
    val audio: List<RemoteTrack> = emptyList(),
    val subs: List<RemoteTrack> = emptyList(),
    val resolution: String? = null,
    val dubbing: String? = null,
    val jobs: List<TransferJob> = emptyList(),
)

data class LinkUi(
    val visible: Boolean = true,
    val deviceName: String = "Emir’in TV",
    val pin: String? = null,
    val pinUntil: Long = 0L,
    val nearby: List<NearbyPc> = emptyList(),
    val paired: List<PairedPc> = emptyList(),
    val jobs: List<TransferJob> = emptyList(),
    val remote: RemoteState? = null,
    val notice: String? = null,
)

enum class SendMode { Copy, Stream }
enum class SendWhen { PlayNow, Queue }
