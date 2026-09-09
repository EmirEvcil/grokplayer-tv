package com.grokplayer.tv.data

import android.content.Context
import java.io.File
import org.json.JSONArray

data class BrowseDir(val name: String, val path: String)
data class BrowseVideo(
    val name: String,
    val path: String,
    val size: Long,
    val title: String,
) {
    val key: String get() = "${name.lowercase()}|$size"
}

data class BrowseListing(
    val path: String,
    val parent: String?,
    val dirs: List<BrowseDir>,
    val videos: List<BrowseVideo>,
    val granted: List<String>,
)

class SharedFolders(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("shared_folders", Context.MODE_PRIVATE)

    fun list(): List<String> {
        val raw = prefs.getString("paths", "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val path = array.optString(i)
                    if (path.isNotBlank() && File(path).isDirectory) add(path)
                }
            }
        }.getOrDefault(emptyList())
    }

    fun add(dir: File) {
        val path = runCatching { dir.canonicalPath }.getOrDefault(dir.absolutePath)
        if (!File(path).isDirectory) return
        val next = (list() + path).distinct()
        save(next)
    }

    fun remove(path: String) {
        save(list().filterNot { it.equals(path, ignoreCase = true) })
    }

    fun listing(path: String?): BrowseListing {
        val grants = list()
        if (path.isNullOrBlank()) {
            val dirs = grants.map { BrowseDir(File(it).name.ifBlank { it }, it) }
            return BrowseListing("", null, dirs, emptyList(), grants)
        }
        val dir = resolve(path) ?: return BrowseListing(path, parentOf(path, grants), emptyList(), emptyList(), grants)
        val children = dir.listFiles().orEmpty()
        val dirs = children
            .filter { it.isDirectory && !it.name.startsWith('.') }
            .sortedBy { it.name.lowercase() }
            .map { BrowseDir(it.name, it.absolutePath) }
        val videos = MediaOrder.sortByTitle(
            children.filter { it.isFile && LibraryStore.isVideoName(it.name) },
        ) { it.nameWithoutExtension }
            .map { BrowseVideo(it.name, it.absolutePath, it.length(), it.nameWithoutExtension) }
        return BrowseListing(dir.absolutePath, parentOf(dir.absolutePath, grants), dirs, videos, grants)
    }

    fun resolve(path: String?): File? {
        if (path.isNullOrBlank()) return null
        val file = runCatching { File(path).canonicalFile }.getOrNull() ?: return null
        val ok = list().any { grant ->
            val root = runCatching { File(grant).canonicalFile }.getOrNull() ?: return@any false
            file == root || file.path.startsWith(root.path + File.separator)
        }
        return if (ok) file else null
    }

    private fun parentOf(path: String, grants: List<String>): String? {
        if (grants.any { it.equals(path, ignoreCase = true) }) return ""
        return File(path).parent
    }

    private fun save(paths: List<String>) {
        val array = JSONArray()
        paths.forEach { array.put(it) }
        prefs.edit().putString("paths", array.toString()).apply()
    }
}
