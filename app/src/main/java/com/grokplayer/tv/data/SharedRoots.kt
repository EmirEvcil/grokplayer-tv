package com.grokplayer.tv.data

import android.content.Context
import android.os.Environment
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

object SharedRoots {
    fun downloads(context: Context): File {
        val shared = sharedDownloads(context)
        val legacy = legacyDownloads(context)
        if (!canWrite(shared)) return legacy.apply { mkdirs() }
        val pending = legacy.isDirectory &&
            legacy.list()?.isNotEmpty() == true &&
            !File(shared, ".migrated").exists()
        if (pending) return legacy
        return shared.apply { mkdirs() }
    }

    fun backups(context: Context): File {
        val parent = if (canWrite(sharedRoot(context))) sharedRoot(context) else appRoot(context)
        return File(parent, "backups").apply { mkdirs() }
    }

    fun usingSharedStorage(context: Context): Boolean = canWrite(sharedRoot(context))

    fun sharedRoot(context: Context): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "GrokPlayer")

    fun migrateLegacyDownloads(context: Context): Boolean {
        val legacy = legacyDownloads(context)
        val shared = sharedDownloads(context)
        if (!legacy.isDirectory || legacy.list().isNullOrEmpty()) return false
        if (!canWrite(shared)) return false
        if (legacy.canonicalPath == shared.canonicalPath) return false
        legacy.listFiles()?.forEach { child ->
            val dest = File(shared, child.name)
            if (dest.exists()) return@forEach
            val moved = child.renameTo(dest) || runCatching {
                child.copyRecursively(dest, overwrite = false)
                true
            }.getOrDefault(false)
            if (moved && dest.exists() && child.exists()) child.deleteRecursively()
        }
        rewriteDownloadPaths(context, legacy.absolutePath, shared.absolutePath)
        File(shared, ".migrated").writeText("1")
        if (legacy.list().isNullOrEmpty()) legacy.delete()
        return true
    }

    private fun sharedDownloads(context: Context) = File(sharedRoot(context), "downloads")

    private fun legacyDownloads(context: Context) =
        File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "downloads")

    private fun appRoot(context: Context) =
        File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "GrokPlayer")

    private fun canWrite(dir: File): Boolean = try {
        dir.mkdirs()
        val probe = File(dir, ".write")
        probe.writeText("1")
        val ok = probe.exists()
        probe.delete()
        ok
    } catch (_: Exception) {
        false
    }

    private fun rewriteDownloadPaths(context: Context, from: String, to: String) {
        val prefs = context.getSharedPreferences("downloads", Context.MODE_PRIVATE)
        val raw = prefs.getString("items", null) ?: return
        if (!raw.contains(from)) return
        prefs.edit().putString("items", raw.replace(from, to)).apply()
    }
}

object BackupArchive {
    data class Section(val id: String, val title: String, val body: String)

    data class Summary(val id: String, val title: String, val entries: Int)

    data class Manifest(
        val name: String,
        val createdAt: Long,
        val appVersion: String,
        val versionCode: Int,
        val sections: List<Summary>,
    )

    fun write(file: File, manifest: Manifest, sections: List<Section>) {
        file.parentFile?.mkdirs()
        java.util.zip.ZipOutputStream(file.outputStream().buffered()).use { zip ->
            zip.setLevel(java.util.zip.Deflater.BEST_COMPRESSION)
            put(zip, "manifest.json", manifestJson(manifest, sections).toString())
            sections.forEach { section ->
                put(zip, "sections/${section.id}.json", section.body)
            }
        }
    }

    fun readManifest(file: File): Manifest {
        java.util.zip.ZipFile(file).use { zip ->
            val entry = zip.getEntry("manifest.json") ?: error("manifest yok")
            val root = JSONObject(zip.getInputStream(entry).reader().readText())
            val list = root.optJSONArray("sections") ?: JSONArray()
            return Manifest(
                name = root.optString("name"),
                createdAt = root.optLong("createdAt"),
                appVersion = root.optString("appVersion"),
                versionCode = root.optInt("versionCode"),
                sections = buildList {
                    for (i in 0 until list.length()) {
                        val item = list.getJSONObject(i)
                        add(Summary(item.optString("id"), item.optString("title"), item.optInt("entries")))
                    }
                },
            )
        }
    }

    fun readSections(file: File): List<Section> {
        val manifest = readManifest(file)
        java.util.zip.ZipFile(file).use { zip ->
            return manifest.sections.map { summary ->
                val entry = zip.getEntry("sections/${summary.id}.json")
                val body = if (entry == null) "{}" else zip.getInputStream(entry).reader().readText()
                Section(summary.id, summary.title, body)
            }
        }
    }

    fun merge(parts: List<List<Section>>): List<Section> {
        val ids = parts.flatMap { group -> group.map { it.id } }.distinct()
        return ids.map { id ->
            val chosen = parts.mapNotNull { group -> group.firstOrNull { it.id == id } }
            val title = chosen.last().title
            val body = mergeBodies(chosen.map { it.body })
            Section(id, title, body)
        }
    }

    fun mergeBodies(bodies: List<String>): String {
        val objects = bodies.mapNotNull { runCatching { JSONObject(it) }.getOrNull() }
        if (objects.isEmpty()) return bodies.lastOrNull() ?: "{}"
        return mergeJson(objects).toString()
    }

    private fun mergeJson(parts: List<JSONObject>): JSONObject {
        val out = JSONObject()
        parts.forEach { part ->
            part.keys().forEach { key ->
                val next = part.get(key)
                val prev = if (out.has(key)) out.get(key) else null
                out.put(key, mergeValue(key, prev, next))
            }
        }
        return out
    }

    private fun mergeValue(key: String, prev: Any?, next: Any?): Any? {
        if (prev == null || prev == JSONObject.NULL) return next
        if (next == null || next == JSONObject.NULL) return prev
        if (key == "_types" && prev is JSONObject && next is JSONObject) {
            val copy = JSONObject(prev.toString())
            next.keys().forEach { typeKey -> copy.put(typeKey, next.get(typeKey)) }
            return copy
        }
        val left = embedded(prev)
        val right = embedded(next)
        val merged: Any? = when {
            key == "users" || key == "known" -> unionLines(textOf(left), textOf(right), "\n")
            key == "recents" -> unionLines(textOf(left), textOf(right), "|")
            key == "progress" || key == "kprogress" -> unionPairs(textOf(left), textOf(right))
            left is JSONObject && right is JSONObject &&
                (left.has("updatedAt") || right.has("updatedAt")) ->
                if (right.optLong("updatedAt") >= left.optLong("updatedAt")) right else left
            left is JSONObject && right is JSONObject -> mergeJson(listOf(left, right))
            left is JSONArray && right is JSONArray -> mergeArrays(left, right)
            else -> right
        }
        val embed = (prev is String && left !== prev) || (next is String && right !== next)
        return if (embed && merged !is String) merged.toString() else merged
    }

    private fun embedded(value: Any?): Any? {
        if (value !is String) return value
        val text = value.trim()
        if (text.startsWith("{")) return runCatching { JSONObject(text) }.getOrNull() ?: value
        if (text.startsWith("[")) return runCatching { JSONArray(text) }.getOrNull() ?: value
        return value
    }

    private fun textOf(value: Any?): String = when (value) {
        null, JSONObject.NULL -> ""
        else -> value.toString()
    }

    private fun unionLines(older: String, newer: String, separator: String): String {
        val seen = LinkedHashSet<String>()
        older.split(separator).forEach { if (it.isNotBlank()) seen.add(it) }
        newer.split(separator).forEach { if (it.isNotBlank()) seen.add(it) }
        return seen.joinToString(separator)
    }

    private fun unionPairs(older: String, newer: String): String {
        val map = linkedMapOf<String, String>()
        fun take(raw: String) {
            raw.split('|').forEach { part ->
                val key = part.substringBefore('=')
                if (key.isNotBlank() && part.contains('=')) map[key] = part.substringAfter('=')
            }
        }
        take(older)
        take(newer)
        return map.entries.joinToString("|") { "${it.key}=${it.value}" }
    }

    private fun mergeArrays(older: JSONArray, newer: JSONArray): JSONArray {
        val left = arrayItems(older)
        val right = arrayItems(newer)
        val objects = (left + right).filterIsInstance<JSONObject>()
        if (objects.isNotEmpty() && objects.size == left.size + right.size && objects.all { it.has("id") }) {
            val byId = linkedMapOf<String, JSONObject>()
            left.filterIsInstance<JSONObject>().forEach { byId[it.getString("id")] = it }
            right.filterIsInstance<JSONObject>().forEach { item ->
                val id = item.getString("id")
                val previous = byId[id]
                byId[id] = if (previous == null) item else mergeJson(listOf(previous, item)) as JSONObject
            }
            return JSONArray().also { array -> byId.values.forEach { array.put(it) } }
        }
        if ((left + right).all { it is String }) {
            val seen = LinkedHashSet<String>()
            left.forEach { seen.add(it as String) }
            right.forEach { seen.add(it as String) }
            return JSONArray().also { array -> seen.forEach { array.put(it) } }
        }
        val seen = LinkedHashSet<String>()
        val out = JSONArray()
        (left + right).forEach { item ->
            val token = item.toString()
            if (seen.add(token)) out.put(item)
        }
        return out
    }

    private fun arrayItems(array: JSONArray): List<Any> = buildList {
        for (i in 0 until array.length()) {
            val value = array.get(i)
            if (value != JSONObject.NULL) add(value)
        }
    }

    private fun manifestJson(manifest: Manifest, sections: List<Section>): JSONObject {
        val list = JSONArray()
        sections.forEach { section ->
            list.put(
                JSONObject()
                    .put("id", section.id)
                    .put("title", section.title)
                    .put("entries", BackupPreview.visibleCount(section, sections)),
            )
        }
        return JSONObject()
            .put("name", manifest.name)
            .put("createdAt", manifest.createdAt)
            .put("appVersion", manifest.appVersion)
            .put("versionCode", manifest.versionCode)
            .put("sections", list)
    }

    private fun put(zip: java.util.zip.ZipOutputStream, name: String, text: String) {
        zip.putNextEntry(java.util.zip.ZipEntry(name))
        zip.write(text.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }
}
