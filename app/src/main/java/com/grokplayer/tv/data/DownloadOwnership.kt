package com.grokplayer.tv.data

import java.io.File

/**
 * Owns every file a download may create. Purge never walks the shared
 * downloads root by title — only this item's id folder, its exact legacy
 * dest names, its localPath, and sidecars that match this stem exactly.
 */
internal object DownloadOwnership {
    private val LANG = Regex("^[a-z]{2,3}(?:-[a-z]{2,8})?$", RegexOption.IGNORE_CASE)
    private val SUB_EXT = setOf("srt", "vtt", "webvtt", "ass", "ssa")
    private val AUDIO_EXT = setOf("m4a", "aac", "mp3", "ac3", "eac3", "opus", "ogg", "wav", "flac")

    fun fileName(title: String): String =
        title.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').ifBlank { "vod" }.take(48)

    fun isSafeId(id: String): Boolean =
        id.isNotBlank() && id != "." && id != ".." && '/' !in id && '\\' !in id

    fun itemDir(root: File, id: String): File = File(root, id)

    fun dest(root: File, id: String, title: String, ext: String): File =
        File(itemDir(root, id), "${fileName(title)}.$ext")

    fun legacyDest(root: File, id: String, title: String, ext: String): File {
        val tag = id.replace("-", "").take(8)
        return File(root, "${fileName(title)}_$tag.$ext")
    }

    fun isOwnedSubtitle(videoStem: String, file: File): Boolean =
        matchesSidecar(videoStem, file, SUB_EXT)

    fun isOwnedAudio(videoStem: String, file: File): Boolean =
        matchesSidecar(videoStem, file, AUDIO_EXT)

    fun isOwnedSidecar(videoStem: String, file: File): Boolean =
        isOwnedSubtitle(videoStem, file) || isOwnedAudio(videoStem, file)

    fun sidecarLanguage(videoStem: String, file: File): String? {
        val name = file.nameWithoutExtension
        if (name.equals(videoStem, ignoreCase = true)) return null
        val rest = when {
            name.startsWith("$videoStem.", ignoreCase = true) -> name.substring(videoStem.length + 1)
            name.startsWith("$videoStem-", ignoreCase = true) -> name.substring(videoStem.length + 1)
            else -> return null
        }
        return rest.substringBefore('.').substringBefore('_').lowercase().ifBlank { null }
    }

    fun sidecarsBeside(video: File, subtitlesOnly: Boolean = false): List<File> {
        val parent = video.parentFile ?: return emptyList()
        val stem = video.nameWithoutExtension
        return parent.listFiles()
            ?.filter { file ->
                if (subtitlesOnly) isOwnedSubtitle(stem, file) else isOwnedSidecar(stem, file)
            }
            .orEmpty()
            .sortedBy { it.name.lowercase() }
    }

    private fun matchesSidecar(videoStem: String, file: File, exts: Set<String>): Boolean {
        if (!file.isFile) return false
        if (file.extension.lowercase() !in exts) return false
        val name = file.nameWithoutExtension
        if (name.equals(videoStem, ignoreCase = true)) return true
        val lang = sidecarLanguage(videoStem, file) ?: return false
        return LANG.matches(lang)
    }

    data class ItemRef(val id: String, val title: String, val localPath: String?)

    fun ownedFiles(
        root: File,
        item: ItemRef,
        extras: List<File> = emptyList(),
    ): List<File> {
        val found = LinkedHashSet<File>()
        if (isSafeId(item.id)) {
            found += dest(root, item.id, item.title, "ts")
            found += dest(root, item.id, item.title, "mp4")
            found += File(itemDir(root, item.id), "meta.json")
        }
        found += legacyDest(root, item.id, item.title, "ts")
        found += legacyDest(root, item.id, item.title, "mp4")
        item.localPath?.let { found += File(it) }
        found.addAll(extras)
        val videos = found.filter { it.extension.equals("mp4", true) || it.extension.equals("ts", true) }
        videos.forEach { video ->
            val parent = video.parentFile ?: return@forEach
            val stem = video.nameWithoutExtension
            parent.listFiles()?.forEach { child ->
                if (isOwnedSidecar(stem, child)) found += child
            }
        }
        return found.toList()
    }

    fun keepPaths(root: File, others: List<ItemRef>): Set<String> =
        others.flatMap { ownedFiles(root, it) }.map { pathKey(it) }.toSet()

    fun purge(
        root: File,
        item: ItemRef,
        extras: List<File> = emptyList(),
        keep: Set<String>,
    ) {
        val keepKeys = keep.map { pathKey(File(it)) }.toSet()
        ownedFiles(root, item, extras).forEach { file ->
            if (pathKey(file) !in keepKeys) file.delete()
        }
        if (!isSafeId(item.id)) return
        val dir = itemDir(root, item.id)
        if (isSafeItemDir(root, dir, item.id) && !keepTouches(dir, keepKeys)) {
            dir.deleteRecursively()
        }
    }

    private fun keepTouches(dir: File, keepKeys: Set<String>): Boolean {
        val dirKey = pathKey(dir)
        val prefix = dirKey + File.separator
        return keepKeys.any { it == dirKey || it.startsWith(prefix) }
    }

    private fun pathKey(file: File): String =
        runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)

    private fun isSafeItemDir(root: File, dir: File, id: String): Boolean {
        if (!isSafeId(id)) return false
        if (!dir.exists() || !dir.isDirectory) return false
        if (dir.name != id) return false
        val rootCanon = runCatching { root.canonicalFile }.getOrDefault(root.absoluteFile)
        val dirCanon = runCatching { dir.canonicalFile }.getOrDefault(dir.absoluteFile)
        if (dirCanon == rootCanon) return false
        val parent = dirCanon.parentFile ?: return false
        return parent == rootCanon
    }
}
