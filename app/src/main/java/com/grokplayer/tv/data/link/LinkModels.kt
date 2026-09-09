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
    val sourceUrl: String? = null,
)

data class RemoteTrack(val index: Int, val label: String, val selected: Boolean)

data class RemoteItem(val index: Int, val title: String, val current: Boolean, val key: String = "")

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
    val have: List<RemoteHave> = emptyList(),
    val resume: ResumeOffer? = null,
) {
    fun hasVideo(title: String, key: String? = null): Boolean {
        val want = title.trim().lowercase()
        if (want.isBlank()) return false
        if (!key.isNullOrBlank() && have.any { it.key.equals(key, ignoreCase = true) }) return true
        if (have.any { it.title.equals(title, ignoreCase = true) }) return true
        return playlist.any { it.title.equals(title, ignoreCase = true) }
    }

    fun isCurrent(title: String): Boolean {
        val want = title.trim().lowercase()
        if (want.isBlank()) return false
        if (this.title?.trim()?.equals(title.trim(), ignoreCase = true) == true) return true
        return playlist.any { it.current && it.title.equals(title, ignoreCase = true) }
    }
}

data class RemoteHave(val key: String, val title: String, val positionMs: Long = 0L)

data class ResumeOffer(val title: String, val seconds: Double, val duration: Double)

data class LinkUi(
    val visible: Boolean = true,
    val deviceName: String = "Emir’in TV",
    val pin: String? = null,
    val pinUntil: Long = 0L,
    val pairingName: String? = null,
    val nearby: List<NearbyPc> = emptyList(),
    val paired: List<PairedPc> = emptyList(),
    val jobs: List<TransferJob> = emptyList(),
    val connectedId: String? = null,
    val connectingId: String? = null,
    val remote: RemoteState? = null,
    val notice: String? = null,
) {
    fun isLive(id: String) = nearby.any { it.id == id }
    fun isConnected(id: String) = connectedId == id
    fun isConnecting(id: String) = connectingId == id && connectedId != id
}

enum class SendMode { Copy, Stream }
enum class SendWhen { PlayNow, Queue }
