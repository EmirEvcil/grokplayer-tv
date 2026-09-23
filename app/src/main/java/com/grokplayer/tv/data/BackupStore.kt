package com.grokplayer.tv.data

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

class BackupStore(context: Context) {
    private val app = context.applicationContext

    fun list(): List<StoredBackup> = backupsDir().listFiles()
        ?.filter { it.isFile && it.extension.equals("gpb", true) }
        ?.mapNotNull { file ->
            runCatching {
                val manifest = BackupArchive.readManifest(file)
                StoredBackup(file, manifest, file.length())
            }.getOrNull()
        }
        .orEmpty()
        .sortedByDescending { it.manifest.createdAt }

    fun create(name: String, versionName: String, versionCode: Int): StoredBackup {
        val clean = name.trim().ifBlank { "Yedek" }
        val createdAt = System.currentTimeMillis()
        val sections = capture()
        val file = File(backupsDir(), "backup-$createdAt.gpb")
        val manifest = BackupArchive.Manifest(clean, createdAt, versionName, versionCode, emptyList())
        BackupArchive.write(file, manifest, sections)
        return StoredBackup(file, BackupArchive.readManifest(file), file.length())
    }

    fun delete(backup: StoredBackup) {
        backup.file.delete()
    }

    fun currentSections(): List<BackupArchive.Section> = capture()

    fun merge(selected: List<StoredBackup>, name: String, versionName: String, versionCode: Int): StoredBackup {
        val ordered = selected.sortedBy { it.manifest.createdAt }
        val sections = BackupArchive.merge(ordered.map { BackupArchive.readSections(it.file) })
        val createdAt = System.currentTimeMillis()
        val file = File(backupsDir(), "backup-$createdAt.gpb")
        BackupArchive.write(
            file,
            BackupArchive.Manifest(name.trim().ifBlank { "Birleşik yedek" }, createdAt, versionName, versionCode, emptyList()),
            sections,
        )
        return StoredBackup(file, BackupArchive.readManifest(file), file.length())
    }

    fun restore(backup: StoredBackup) {
        val root = DownloadPaths.dir(app).absolutePath
        BackupArchive.readSections(backup.file).forEach { section ->
            val body = remapPaths(section.body, root)
            if (section.id == "watch") {
                File(app.filesDir, "watch.json").writeText(body)
            } else if (section.id != "offline_library") {
                applyPrefs(section.id, body)
            }
        }
    }

    private fun capture(): List<BackupArchive.Section> {
        val sections = PREFS.map { (id, title) ->
            BackupArchive.Section(id, title, PreferenceCodec.encode(id, app.getSharedPreferences(id, Context.MODE_PRIVATE)))
        }
        val watch = File(app.filesDir, "watch.json")
        val watchBody = if (watch.isFile) watch.readText() else """{"items":{},"cursors":{}}"""
        return sections + listOf(
            BackupArchive.Section("watch", "İzleme geçmişi", watchBody),
            BackupArchive.Section("offline_library", "Çevrimdışı", offlineLibraryBody()),
        )
    }

    private fun offlineLibraryBody(): String {
        val index = File(app.filesDir, "library_index.json")
        val items = JSONArray()
        if (index.isFile) {
            val rows = runCatching { JSONArray(index.readText()) }.getOrNull()
            if (rows != null) {
                for (i in 0 until rows.length()) {
                    val item = rows.optJSONObject(i) ?: continue
                    if (item.optBoolean("isLive") || item.optBoolean("isStream")) continue
                    val path = item.optString("path")
                    val uri = item.optString("uri")
                    val scheme = uri.substringBefore(':', "").takeIf { ':' in uri }
                    if (!OfflineCollections.isLocalFile(path, scheme)) continue
                    items.put(
                        JSONObject()
                            .put("id", item.optString("id"))
                            .put("title", item.optString("title"))
                            .put("path", path)
                            .put("uri", uri)
                            .put("originUrl", item.optString("originUrl")),
                    )
                }
            }
        }
        return JSONObject().put("items", items).toString()
    }

    private fun backupsDir(): File = SharedRoots.backups(app)

    private fun applyPrefs(name: String, body: String) {
        val edit = app.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
        PreferenceCodec.decode(name, body).forEach { (key, value) ->
            when (value) {
                is Boolean -> edit.putBoolean(key, value)
                is Int -> edit.putInt(key, value)
                is Long -> edit.putLong(key, value)
                is Float -> edit.putFloat(key, value)
                is Set<*> -> edit.putStringSet(key, value.map { it.toString() }.toSet())
                is String -> edit.putString(key, value)
            }
        }
        edit.commit()
    }

    private fun remapPaths(body: String, downloadsRoot: String): String {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return body
        return remapJson(root, downloadsRoot).toString()
    }

    private fun remapJson(value: Any?, downloadsRoot: String): Any? {
        return when (value) {
            is JSONObject -> JSONObject().also { out ->
                value.keys().forEach { key -> out.put(key, remapJson(value.get(key), downloadsRoot)) }
            }
            is JSONArray -> JSONArray().also { out ->
                for (i in 0 until value.length()) out.put(remapJson(value.get(i), downloadsRoot))
            }
            is String -> remapPath(value, downloadsRoot)
            else -> value
        }
    }

    private fun remapPath(path: String, downloadsRoot: String): String {
        val marker = "/downloads/"
        val index = path.indexOf(marker)
        if (index < 0) return path
        return File(downloadsRoot, path.substring(index + marker.length)).absolutePath
    }

    data class StoredBackup(val file: File, val manifest: BackupArchive.Manifest, val bytes: Long)

    private companion object {
        val PREFS = listOf(
            "playback" to "Oynatma",
            "video_collections" to "Koleksiyonlar",
            "folder_playlists" to "Listeler",
            "streams" to "Yayınlar",
            "shared_folders" to "Klasörler",
            "downloads" to "İndirme kayıtları",
            "library" to "Kitaplık",
            "grok_link" to "Eşleşmiş cihaz",
        )
    }
}

internal object PreferenceCodec {
    private const val TYPES = "_types"

    fun encode(section: String, prefs: SharedPreferences): String = encode(section, prefs.all)

    fun encode(section: String, values: Map<String, Any?>): String {
        val root = JSONObject()
        val types = JSONObject()
        values.forEach { (key, value) ->
            when (value) {
                null -> Unit
                is Boolean -> root.put(key, value)
                is Int -> root.put(key, value)
                is Long -> {
                    root.put(key, value)
                    types.put(key, "long")
                }
                is Float -> {
                    root.put(key, value.toDouble())
                    types.put(key, "float")
                }
                is Double -> {
                    root.put(key, value)
                    types.put(key, "float")
                }
                is Set<*> -> {
                    root.put(key, JSONArray(value.map { it.toString() }))
                    types.put(key, "set")
                }
                else -> root.put(key, value.toString())
            }
        }
        if (section == "playback" && !types.has("speed") && root.has("speed")) types.put("speed", "float")
        if (types.length() > 0) root.put(TYPES, types)
        return root.toString()
    }

    fun decode(section: String, body: String): Map<String, Any?> {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyMap()
        val types = root.optJSONObject(TYPES)
        val out = linkedMapOf<String, Any?>()
        root.keys().forEach { key ->
            if (key == TYPES) return@forEach
            val value = root.get(key)
            val type = types?.optString(key).orEmpty().ifBlank {
                if (section == "playback" && key == "speed") "float" else ""
            }
            out[key] = typed(type, value)
        }
        return out
    }

    private fun typed(type: String, value: Any?): Any? {
        if (value == null || value == JSONObject.NULL) return null
        return when (type) {
            "float" -> (value as? Number)?.toFloat() ?: value.toString().toFloatOrNull()
            "long" -> (value as? Number)?.toLong()
            "set" -> stringSet(value)
            else -> when (value) {
                is Boolean -> value
                is Int -> value
                is Long -> value
                is Double -> if (value % 1.0 == 0.0) {
                    val asLong = value.toLong()
                    if (asLong in Int.MIN_VALUE..Int.MAX_VALUE) asLong.toInt() else asLong
                } else {
                    value.toFloat()
                }
                is JSONArray -> stringSet(value)
                else -> value.toString()
            }
        }
    }

    private fun stringSet(value: Any?): Set<String> {
        val array = value as? JSONArray ?: return emptySet()
        return buildSet {
            for (i in 0 until array.length()) add(array.optString(i))
        }
    }
}
