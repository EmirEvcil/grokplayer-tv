package com.grokplayer.tv.data

object DownloadPolicy {
    enum class Action { Enqueue, SkipDone, SkipActive, RetryFailed }

    data class Snapshot(
        val id: String,
        val title: String,
        val url: String,
        val status: DownloadStatus,
        val localPath: String?,
    )

    data class Decision(
        val action: Action,
        val existingId: String? = null,
    )

    data class BatchResult(
        val queued: Int = 0,
        val retried: Int = 0,
        val skippedDone: Int = 0,
        val skippedActive: Int = 0,
    ) {
        val accepted: Int get() = queued + retried
        fun notice(): String = when {
            accepted == 0 && skippedDone > 0 && skippedActive == 0 -> "Zaten indirildi"
            accepted == 0 && skippedActive > 0 && skippedDone == 0 -> "Zaten indirme kuyruğunda"
            accepted == 0 && skippedDone > 0 && skippedActive > 0 -> "Zaten indirildi veya kuyrukta"
            else -> buildString {
                if (queued > 0) append("$queued video kuyruğa alındı")
                if (retried > 0) {
                    if (isNotEmpty()) append(", ")
                    append("$retried yeniden denenecek")
                }
                if (skippedDone > 0) {
                    if (isNotEmpty()) append(", ")
                    append("$skippedDone zaten vardı")
                }
            }
        }
    }

    fun identity(title: String, url: String): String = normalizeUrl(url)

    fun normalizeUrl(url: String): String = url.trim().substringBefore('#').lowercase()

    fun decide(
        existing: List<Snapshot>,
        title: String,
        url: String,
        fileExists: (String) -> Boolean = { true },
    ): Decision {
        val key = identity(title, url)
        if (key.isBlank()) return Decision(Action.Enqueue)
        val match = existing.firstOrNull { normalizeUrl(it.url) == key }
        return when {
            match == null -> Decision(Action.Enqueue)
            match.status == DownloadStatus.Queued || match.status == DownloadStatus.Running ->
                Decision(Action.SkipActive, match.id)
            match.status == DownloadStatus.Done && match.localPath?.let(fileExists) == true ->
                Decision(Action.SkipDone, match.id)
            match.status == DownloadStatus.Failed || match.status == DownloadStatus.Done ->
                Decision(Action.RetryFailed, match.id)
            else -> Decision(Action.Enqueue)
        }
    }

    fun planBatch(
        existing: List<Snapshot>,
        requests: List<Pair<String, String>>,
        fileExists: (String) -> Boolean = { true },
    ): BatchResult {
        var queued = 0
        var retried = 0
        var skippedDone = 0
        var skippedActive = 0
        val seen = HashSet<String>()
        requests.forEach { (title, url) ->
            val key = identity(title, url)
            if (key.isNotBlank() && !seen.add(key)) {
                skippedDone += 1
                return@forEach
            }
            when (decide(existing, title, url, fileExists).action) {
                Action.Enqueue -> queued += 1
                Action.RetryFailed -> retried += 1
                Action.SkipDone -> skippedDone += 1
                Action.SkipActive -> skippedActive += 1
            }
        }
        return BatchResult(queued, retried, skippedDone, skippedActive)
    }
}
