package com.grokplayer.tv.data

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

object BackupPreview {
    data class Line(
        val title: String,
        val detail: String,
        val action: String? = null,
        val children: List<Line> = emptyList(),
    )

    fun contents(sections: List<BackupArchive.Section>): List<Line> {
        val titles = titles(sections)
        return listed(sections, titles, false).flatMap { record ->
            val head = Line(record.name, join(record.group, record.note, record.detail, record.extra))
            val children = record.facts.map { fact ->
                Line(fact.label, join(record.group, record.note, record.name))
            }
            listOf(head) + children
        }
    }

    fun diff(current: List<BackupArchive.Section>, incoming: List<BackupArchive.Section>): List<Line> {
        val titles = linkedMapOf<String, String>()
        titles(current).forEach { (key, value) -> titles.putIfAbsent(key, value) }
        titles(incoming).forEach { (key, value) -> titles.putIfAbsent(key, value) }
        val before = pack(current, titles, true)
        val after = pack(incoming, titles, true)
        val ids = (before.keys + after.keys).filter { it != "playback" }.distinct()
        val lines = ArrayList<Line>()
        lines += playbackChanges(
            current.firstOrNull { it.id == "playback" }?.body,
            incoming.firstOrNull { it.id == "playback" }?.body,
        )
        ids.forEach { id ->
            val left = before[id].orEmpty()
            val right = after[id].orEmpty()
            val leftById = left.associateBy { it.id }
            val rightById = right.associateBy { it.id }
            (leftById.keys + rightById.keys).distinct().forEach { key ->
                diffRecord(leftById[key], rightById[key], lines)
            }
            orderLine(id, left, right, lines)
        }
        return lines
    }

    fun visibleCount(section: BackupArchive.Section, siblings: List<BackupArchive.Section> = listOf(section)): Int =
        records(section, titles(siblings), siblings, false).size

    data class Summary(
        val playlists: Int,
        val collections: Int,
        val downloads: Int,
        val streams: Int,
        val watches: Int,
        val likes: Int,
        val watchlist: Int = 0,
    )

    data class Catalog(
        val id: String,
        val title: String,
        val count: Int,
        val rows: List<Entry>,
    )

    data class Entry(
        val title: String,
        val detail: String,
        val children: List<Line> = emptyList(),
    )

    data class ReviewItem(
        val title: String,
        val detail: String,
        val kind: String,
        val more: List<Line> = emptyList(),
    )

    data class Review(
        val added: Int,
        val changed: Int,
        val removed: Int,
        val items: List<ReviewItem>,
    )

    fun summary(sections: List<BackupArchive.Section>): Summary {
        val titles = titles(sections)
        val records = listed(sections, titles, false)
        return Summary(
            playlists = records.count { it.group == "Liste" && it.id.startsWith("list:") },
            collections = records.count { it.group == "Koleksiyon" },
            downloads = records.count { it.id.startsWith("download:") },
            streams = records.count { it.id.startsWith("stream:") },
            watches = records.count { it.id.startsWith("watch:") },
            likes = records.count { it.id.startsWith("watch:") && (it.detail.contains("beğenildi") || it.detail.contains("beğenilmedi")) },
            watchlist = records.count { it.id.startsWith("watchlist:") },
        )
    }

    fun summaryText(summary: Summary): String {
        val parts = ArrayList<String>()
        if (summary.playlists > 0) parts += countLabel(summary.playlists, "liste", "liste")
        if (summary.collections > 0) parts += countLabel(summary.collections, "koleksiyon", "koleksiyon")
        if (summary.downloads > 0) parts += countLabel(summary.downloads, "indirme", "indirme")
        if (summary.watchlist > 0) parts += countLabel(summary.watchlist, "izlenecek", "izlenecek")
        return parts.joinToString(" · ")
    }

    fun catalog(sections: List<BackupArchive.Section>): List<Catalog> {
        val titles = titles(sections)
        val records = listed(sections, titles, false)
        val playback = sections.firstOrNull { it.id == "playback" }?.body?.let { jsonObject(it) }
        val out = ArrayList<Catalog>()
        if (playback != null && playback.length() > 0) {
            val rows = playbackValues(playback).map { Entry(it.name, it.detail) }
            out += Catalog("settings", "Ayarlar", rows.size, rows)
        }
        out += catalogOf("playlists", "Listeler", records.filter { it.id.startsWith("list:") })
        out += catalogOf("collections", "Koleksiyonlar", records.filter { it.group == "Koleksiyon" })
        out += catalogOf("downloads", "İndirmeler", records.filter { it.id.startsWith("download:") })
        val watchlistRows = records.filter { it.id.startsWith("watchlist:") }
        if (sections.any { it.id == "watchlist" } || watchlistRows.isNotEmpty()) {
            out += catalogOf("watchlist", "İzleme listesi", watchlistRows)
        }
        out += catalogOf("streams", "Yayınlar", records.filter { it.id.startsWith("stream:") })
        val watchRows = records.filter { it.id.startsWith("watch:") || it.id.startsWith("cursor:") || it.id.startsWith("opened:") || it.id.startsWith("recent:") }
        out += catalogOf("watch", "İzleme ve beğeniler", watchRows)
        val folders = records.filter { it.id.startsWith("folder:") || it.id.startsWith("libfolder:") }
        if (folders.isNotEmpty()) out += catalogOf("folders", "Klasörler", folders)
        val devices = records.filter { it.id.startsWith("device:") }
        if (devices.isNotEmpty()) out += catalogOf("devices", "Cihazlar", devices)
        return out
    }

    fun review(current: List<BackupArchive.Section>, incoming: List<BackupArchive.Section>): Review {
        val titles = linkedMapOf<String, String>()
        titles(current).forEach { (key, value) -> titles.putIfAbsent(key, value) }
        titles(incoming).forEach { (key, value) -> titles.putIfAbsent(key, value) }
        val before = pack(current, titles, true)
        val after = pack(incoming, titles, true)
        val built = ArrayList<Built>()
        built += playbackBuilt(
            current.firstOrNull { it.id == "playback" }?.body,
            incoming.firstOrNull { it.id == "playback" }?.body,
        )
        (before.keys + after.keys).filter { it != "playback" }.distinct().forEach { id ->
            val left = before[id].orEmpty().associateBy { it.id }
            val right = after[id].orEmpty().associateBy { it.id }
            (left.keys + right.keys).distinct().forEach { key ->
                built += builtRecord(left[key], right[key])
            }
        }
        val items = collapse(built)
        return Review(
            added = built.filter { it.kind == "Eklenecek" }.sumOf { it.weight },
            changed = built.filter { it.kind == "Değişecek" }.sumOf { it.weight },
            removed = built.filter { it.kind == "Silinecek" }.sumOf { it.weight },
            items = items,
        )
    }

    private fun catalogOf(id: String, title: String, records: List<Record>): Catalog {
        val rows = records.map { record ->
            Entry(
                title = record.name,
                detail = join(record.note, record.detail, record.extra),
                children = record.facts.map { Line(it.label, record.name) },
            )
        }
        return Catalog(id, title, rows.size, rows)
    }

    private data class Built(
        val group: String,
        val kind: String,
        val title: String,
        val detail: String,
        val more: List<Line> = emptyList(),
        val collapsible: Boolean = false,
        val weight: Int = 1,
    )

    private fun playbackBuilt(current: String?, incoming: String?): List<Built> {
        return playbackChanges(current, incoming).map { line ->
            Built("Oynatma", "Değişecek", line.title, line.detail.removePrefix("Oynatma · değişecek · "), collapsible = false)
        }
    }

    private fun builtRecord(old: Record?, new: Record?): List<Built> {
        if (old == null && new != null) {
            return listOf(addedRecord(new, "Eklenecek", "eklenecek"))
        }
        if (old != null && new == null) {
            return listOf(addedRecord(old, "Silinecek", "silinecek"))
        }
        if (old == null || new == null) return emptyList()
        val out = ArrayList<Built>()
        if (old.name != new.name) {
            out += Built(new.group, "Değişecek", new.name, join(place(new), "ad: ${old.name} → ${new.name}"))
        }
        if (old.extra != new.extra && (old.extra.isNotBlank() || new.extra.isNotBlank())) {
            out += Built(new.group, "Değişecek", new.name, join(place(new), "${old.extra.ifBlank { "yok" }} → ${new.extra.ifBlank { "yok" }}"))
        }
        if (old.facts.isNotEmpty() || new.facts.isNotEmpty()) {
            out += factBuilt(old, new)
        } else if (old.detail != new.detail) {
            out += Built(new.group, "Değişecek", new.name, join(place(new), "${old.detail} → ${new.detail}"))
        }
        return out
    }

    private fun addedRecord(record: Record, kind: String, word: String): Built {
        val names = record.facts.joinToString(", ") { it.label }
        return Built(
            group = record.group,
            kind = kind,
            title = record.name,
            detail = join(place(record), word, record.detail, names),
            more = if (record.facts.size >= 2) record.facts.map { Line(it.label, "") } else emptyList(),
            collapsible = record.group != "Oynatma",
        )
    }

    private fun factBuilt(old: Record, new: Record): List<Built> {
        val left = old.facts.associateBy { it.id }
        val right = new.facts.associateBy { it.id }
        val added = ArrayList<Line>()
        val removed = ArrayList<Line>()
        val changed = ArrayList<Line>()
        (left.keys + right.keys).distinct().forEach { id ->
            val before = left[id]
            val after = right[id]
            when {
                before == null && after != null -> added += Line(after.label, join(place(new), new.name))
                before != null && after == null -> removed += Line(before.label, join(place(old), old.name))
                before != null && after != null && before.label != after.label ->
                    changed += Line(after.label, join(place(new), "${before.label} → ${after.label}"))
            }
        }
        val out = ArrayList<Built>()
        if (added.isNotEmpty()) out += factRow(new, "Eklenecek", "eklenecek", added)
        if (removed.isNotEmpty()) out += factRow(old, "Silinecek", "silinecek", removed)
        if (changed.isNotEmpty()) out += factRow(new, "Değişecek", "değişecek", changed)
        return out
    }

    private fun factRow(record: Record, kind: String, word: String, lines: List<Line>): Built {
        val what = if (lines.size == 1) "${lines[0].title} $word" else "${lines.size} video $word"
        val detail = join(place(record), what)
        return Built(
            group = record.group,
            kind = kind,
            title = record.name,
            detail = detail,
            more = if (lines.size >= 2) lines else emptyList(),
            collapsible = true,
            weight = lines.size,
        )
    }

    private fun collapse(built: List<Built>): List<ReviewItem> {
        val out = ArrayList<ReviewItem>()
        var index = 0
        while (index < built.size) {
            val item = built[index]
            val siblings = built.filter { it.group == item.group && it.kind == item.kind && it.collapsible }
            val first = built.indexOfFirst { it.group == item.group && it.kind == item.kind && it.collapsible }
            if (item.collapsible && siblings.size >= 2 && index == first) {
                out += ReviewItem(
                    title = "${siblings.size} ${groupNoun(item.group)}",
                    detail = kindWord(item.kind),
                    kind = item.kind,
                    more = siblings.map { Line(it.title, it.detail, children = it.more) },
                )
            } else if (!item.collapsible || siblings.size < 2) {
                out += ReviewItem(item.title, item.detail, item.kind, item.more)
            }
            index++
        }
        return out
    }

    private fun groupNoun(group: String): String = when (group) {
        "Koleksiyon" -> "koleksiyon"
        "Liste" -> "liste"
        "İndirme" -> "indirme"
        "Yayın" -> "yayın"
        "İzleme" -> "izleme kaydı"
        "İzleme listesi" -> "izlenecek"
        "Klasör" -> "klasör"
        "Cihaz" -> "cihaz"
        "Kitaplık", "Son açılan" -> "kayıt"
        else -> "kayıt"
    }

    private fun place(record: Record): String {
        val note = record.note.trim()
        return if (note.isNotEmpty() && !note.equals(record.name, ignoreCase = true)) {
            "${record.group} · $note"
        } else {
            record.group
        }
    }

    private fun kindWord(kind: String): String = when (kind) {
        "Eklenecek" -> "eklenecek"
        "Silinecek" -> "silinecek"
        else -> "değişecek"
    }

    private fun countLabel(count: Int, one: String, many: String): String =
        if (count == 1) "1 $one" else "$count $many"

    private fun join(vararg parts: String): String =
        parts.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" · ")

    private data class Fact(val id: String, val label: String)

    private data class Record(
        val id: String,
        val group: String,
        val name: String,
        val detail: String,
        val facts: List<Fact> = emptyList(),
        val note: String = "",
        val extra: String = "",
    )

    private data class Clip(
        val id: String,
        val title: String,
        val uri: String,
        val originUrl: String,
        val path: String,
    )

    private data class PreviewList(val id: String, val title: String, val path: String, val clips: List<Clip>)

    private fun listed(
        sections: List<BackupArchive.Section>,
        titles: Map<String, String>,
        forDiff: Boolean,
    ): List<Record> {
        val out = ArrayList<Record>()
        sections.forEach { section -> out += records(section, titles, sections, forDiff) }
        if (sections.none { it.id == "video_collections" } && sections.any { it.id == "folder_playlists" }) {
            out += collections(JSONObject(), readLists(sections))
        }
        return out
    }

    private fun pack(
        sections: List<BackupArchive.Section>,
        titles: Map<String, String>,
        forDiff: Boolean,
    ): Map<String, List<Record>> {
        val out = linkedMapOf<String, List<Record>>()
        sections.forEach { section -> out[section.id] = records(section, titles, sections, forDiff) }
        if ("video_collections" !in out && sections.any { it.id == "folder_playlists" }) {
            out["video_collections"] = collections(JSONObject(), readLists(sections))
        }
        return out
    }

    private fun records(
        section: BackupArchive.Section,
        titles: Map<String, String>,
        siblings: List<BackupArchive.Section>,
        forDiff: Boolean,
    ): List<Record> {
        val root = jsonObject(section.body) ?: JSONObject()
        return when (section.id) {
            "playback" -> listOfNotNull(playback(root))
            "video_collections" -> collections(root, readLists(siblings) + offlineLists(siblings))
            "folder_playlists" -> playlists(root)
            "downloads" -> downloads(root)
            "streams" -> streams(root)
            "shared_folders" -> folders(root)
            "watch" -> watches(root, titles, readLists(siblings))
            "grok_link" -> devices(root)
            "library" -> library(root, titles, forDiff)
            "watchlist" -> watchlist(root)
            else -> emptyList()
        }
    }

    private fun diffRecord(old: Record?, new: Record?, lines: MutableList<Line>) {
        when {
            old == null && new != null -> lines += placed(new, "Eklenecek", "eklenecek")
            old != null && new == null -> lines += placed(old, "Silinecek", "silinecek")
            old != null && new != null -> {
                if (old.name != new.name) {
                    lines += Line(
                        new.name,
                        join(new.group, new.note, "değişecek", "ad: ${old.name} → ${new.name}"),
                        "Değişecek",
                    )
                }
                if (old.extra != new.extra) {
                    lines += Line(
                        new.name,
                        join(new.group, new.note, "değişecek", "${old.extra.ifBlank { "yok" }} → ${new.extra.ifBlank { "yok" }}"),
                        "Değişecek",
                    )
                }
                if (old.facts.isNotEmpty() || new.facts.isNotEmpty()) {
                    diffFacts(old, new, lines)
                } else if (old.detail != new.detail) {
                    lines += Line(
                        new.name,
                        join(new.group, new.note, "değişecek", "${old.detail} → ${new.detail}"),
                        "Değişecek",
                    )
                }
            }
        }
    }

    private fun placed(record: Record, action: String, word: String): Line {
        val names = record.facts.joinToString(", ") { it.label }
        return Line(record.name, join(record.group, record.note, word, record.detail, names, record.extra), action)
    }

    private fun diffFacts(old: Record, new: Record, lines: MutableList<Line>) {
        val left = old.facts.associateBy { it.id }
        val right = new.facts.associateBy { it.id }
        (left.keys + right.keys).distinct().forEach { id ->
            val before = left[id]
            val after = right[id]
            when {
                before == null && after != null ->
                    lines += Line(after.label, join(new.group, placeOf(new), "eklenecek"), "Eklenecek")
                before != null && after == null ->
                    lines += Line(before.label, join(old.group, placeOf(old), "silinecek"), "Silinecek")
                before != null && after != null && before.label != after.label ->
                    lines += Line(
                        after.label,
                        join(new.group, placeOf(new), "değişecek", "${before.label} → ${after.label}"),
                        "Değişecek",
                    )
            }
        }
        val oldIds = old.facts.map { it.id }
        val newIds = new.facts.map { it.id }
        if (oldIds.size > 1 && oldIds.toSet() == newIds.toSet() && oldIds != newIds) {
            lines += Line(new.name, join(new.group, placeOf(new), "değişecek", "sıra değişti"), "Değişecek")
        }
    }

    private fun placeOf(record: Record): String =
        listOf(record.note, record.name).filter { it.isNotBlank() }.distinct().joinToString(" · ")

    private fun orderLine(sectionId: String, old: List<Record>, new: List<Record>, lines: MutableList<Line>) {
        val prefix = when (sectionId) {
            "folder_playlists" -> "list:"
            "downloads" -> "download:"
            "streams" -> "stream:"
            "shared_folders" -> "folder:"
            "library" -> "opened:"
            else -> return
        }
        val title = when (sectionId) {
            "folder_playlists" -> "Listelerin sırası"
            "downloads" -> "İndirmelerin sırası"
            "streams" -> "Yayınların sırası"
            "shared_folders" -> "Klasörlerin sırası"
            else -> "Son açılanların sırası"
        }
        val left = old.filter { it.id.startsWith(prefix) }.map { it.id }
        val right = new.filter { it.id.startsWith(prefix) }.map { it.id }
        if (left.size > 1 && left.toSet() == right.toSet() && left != right) {
            lines += Line(title, join(title, "değişecek", "sıra değişti"), "Değişecek")
        }
    }

    private fun playbackChanges(current: String?, incoming: String?): List<Line> {
        val left = jsonObject(current) ?: JSONObject()
        val right = jsonObject(incoming) ?: JSONObject()
        if (left.length() == 0 && right.length() == 0) return emptyList()
        return playbackValues(left).zip(playbackValues(right)).mapNotNull { (before, after) ->
            if (before.detail == after.detail) null
            else Line(after.name, "Oynatma · değişecek · ${before.detail} → ${after.detail}", "Değişecek")
        }
    }

    private fun playbackValues(root: JSONObject): List<Record> = listOf(
        Record("resume", "Oynatma", "Kaldığın yerden devam et", onOff(root.optBoolean("resume", true))),
        Record("auto_next", "Oynatma", "Sonraki videoyu otomatik oynat", onOff(root.optBoolean("auto_next", true))),
        Record("seek_step", "Oynatma", "İleri / geri sarma adımı", "${root.optInt("seek_step", 10)} saniye"),
        Record("speed", "Oynatma", "Oynatma hızı", number(root, "speed")?.let { formatSpeedLabel(it) } ?: "1×"),
        Record("hide_controls", "Oynatma", "Kontrolleri gizleme süresi", "${root.optInt("hide_controls", 2)} saniye"),
        Record("start", "Oynatma", "Açılış ekranı", if (root.optString("start") == "Videos") "Videolar" else "Ana sayfa"),
        Record("fit_mode", "Oynatma", "Görüntü sığdırma", when (root.optInt("fit_mode", 0)) {
            1 -> "Doldur"
            2 -> "Kırp"
            else -> "Sığdır"
        }),
        Record("max_height", "Oynatma", "Üst çözünürlük", when (root.optInt("max_height", 0)) {
            720 -> "720p"
            1080 -> "1080p"
            else -> "Sınırsız"
        }),
        Record("audio_lang", "Oynatma", "Tercih edilen ses dili", PlaybackSettings.langLabel(root.optString("audio_lang"), "Otomatik")),
        Record("stereo_only", "Oynatma", "Yalnızca stereo", onOff(root.optBoolean("stereo_only", true))),
        Record("captions_on", "Oynatma", "Altyazıyı varsayılan aç", onOff(root.optBoolean("captions_on", false))),
        Record("caption_lang", "Oynatma", "Tercih edilen altyazı dili", PlaybackSettings.langLabel(root.optString("caption_lang", "tr"), "Türkçe")),
        Record("caption_size", "Oynatma", "Altyazı boyutu", when (root.optInt("caption_size", 1)) {
            0 -> "Küçük"
            2 -> "Büyük"
            else -> "Orta"
        }),
        Record("dl_height", "Oynatma", "İndirme kalitesi", when (root.optInt("dl_height", 720)) {
            480 -> "480p"
            0 -> "En iyi"
            else -> "720p"
        }),
    )

    private fun onOff(value: Boolean): String = if (value) "açık" else "kapalı"

    private fun playback(root: JSONObject): Record? {
        if (root.length() == 0 || (root.length() == 1 && root.has("_types"))) return null
        val speed = number(root, "speed")?.let { formatSpeedLabel(it) } ?: "1×"
        val resume = if (root.optBoolean("resume", true)) "kaldığın yerden devam açık" else "kaldığın yerden devam kapalı"
        return Record("playback", "Oynatma", "Oynatma ayarları", "$speed · $resume")
    }

    private fun collections(root: JSONObject, lists: List<PreviewList>): List<Record> {
        val names = stringMap(root.opt("names"))
        val homes = setMap(root.opt("homes"))
        val users = lines(root.opt("users")).toSet()
        val known = lines(root.opt("known")).toSet()
        val excluded = setMap(root.opt("excluded"))
        val produced = linkedMapOf<String, Record>()
        lists.forEach { list ->
            val clips = list.clips
            val buckets = CollectionGrouper.group(
                items = clips,
                titleOf = { it.title },
                idOf = { it.id },
                renameOf = { id -> names[id]?.takeIf { it.isNotBlank() } },
                homesOf = { id ->
                    val clip = clips.firstOrNull { it.id == id }
                    val keys = listOfNotNull(id, clip?.let(::clipKey)).distinct()
                    keys.flatMap { homes[it].orEmpty() }
                        .filter { CollectionGrouper.inScope(it, list.id) }
                        .distinct()
                },
                scope = list.id,
                keepIds = known,
            )
            val live = buckets.map { bucket ->
                bucket.copy(
                    items = bucket.items.filter { clip ->
                        listOf(clip.id, clipKey(clip)).none { key -> excluded[key]?.contains(bucket.id) == true }
                    },
                )
            }.filter { it.items.isNotEmpty() }.toMutableList()
            users.filter { CollectionGrouper.inScope(it, list.id) }.forEach { id ->
                if (live.none { it.id == id }) {
                    live += CollectionGrouper.Bucket(id, null, names[id] ?: "Koleksiyon", emptyList())
                }
            }
            live.forEach { bucket -> produced[bucket.id] = collectionRecord(bucket, list.title) }
        }
        val allClips = lists.flatMap { it.clips }
        (names.keys + users + known).forEach { id ->
            if (id in produced) return@forEach
            if (id !in names && id !in users && !id.startsWith("auto:") && !id.startsWith("user:") && !id.startsWith("general:")) {
                return@forEach
            }
            val members = allClips.filter { clip ->
                listOf(clip.id, clipKey(clip)).any { key -> homes[key]?.contains(id) == true }
            }
            val stem = CollectionGrouper.stemOf(id)
            val name = names[id]?.takeIf { it.isNotBlank() }
                ?: stem?.let { MediaOrder.prettyTitle(it) }
                ?: if (CollectionGrouper.isGeneralId(id)) CollectionGrouper.GENERAL_NAME else "Koleksiyon"
            val note = if (CollectionGrouper.inScope(id, OfflineCollections.SCOPE)) "Çevrimdışı" else ""
            produced[id] = collectionRecord(
                CollectionGrouper.Bucket(id, stem, name, members),
                note,
            )
        }
        return produced.values.sortedBy { it.name.lowercase() }
    }

    private fun collectionRecord(bucket: CollectionGrouper.Bucket<Clip>, playlistTitle: String): Record {
        val facts = bucket.items.map { Fact(it.id, it.title.ifBlank { "Video" }) }
        return Record(
            id = "collection:${bucket.id}",
            group = "Koleksiyon",
            name = bucket.name.ifBlank { "Koleksiyon" },
            detail = videos(facts.size),
            facts = facts,
            note = playlistTitle,
        )
    }

    private fun playlists(root: JSONObject): List<Record> {
        val lists = readLists(root)
        return buildList {
            lists.forEach { list ->
                add(
                    Record(
                        id = "list:${list.id}",
                        group = "Liste",
                        name = list.title.ifBlank { "Liste" },
                        detail = videos(list.clips.size),
                        facts = list.clips.map { Fact(it.id, it.title.ifBlank { "Video" }) },
                        extra = list.path,
                    ),
                )
            }
            stringSet(root.opt("hidden")).forEach { key ->
                val label = key.substringAfter('|').substringAfterLast('/').substringAfterLast('\\').ifBlank { key }
                add(Record("playlist-hidden:$key", "Liste", label, "gizlendi"))
            }
        }
    }

    private fun readLists(sections: List<BackupArchive.Section>): List<PreviewList> {
        val body = sections.firstOrNull { it.id == "folder_playlists" }?.body ?: return emptyList()
        return readLists(jsonObject(body) ?: return emptyList())
    }

    private fun readLists(root: JSONObject): List<PreviewList> {
        val items = jsonArray(root.opt("items")) ?: return emptyList()
        val videos = jsonObject(root.opt("videos"))
        return buildList {
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val id = item.optString("id")
                if (id.isBlank()) continue
                add(
                    PreviewList(
                        id = id,
                        title = item.optString("title").ifBlank { "Liste" },
                        path = item.optString("path"),
                        clips = clipsOf(videos?.opt(id)),
                    ),
                )
            }
        }
    }

    private fun clipsOf(value: Any?): List<Clip> {
        val rows = jsonArray(value) ?: return emptyList()
        return buildList {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val id = row.optString("id")
                if (id.isBlank()) continue
                add(
                    Clip(
                        id = id,
                        title = row.optString("title"),
                        uri = row.optString("uri"),
                        originUrl = row.optString("originUrl"),
                        path = row.optString("path"),
                    ),
                )
            }
        }
    }

    private fun clipKey(clip: Clip): String {
        val url = clip.originUrl.ifBlank { clip.uri }
        return if (url.startsWith("http", ignoreCase = true)) {
            DownloadPolicy.normalizeUrl(url)
        } else {
            clip.path.replace('\\', '/').lowercase().ifBlank { clip.id }
        }
    }

    private fun watchlist(root: JSONObject): List<Record> {
        val items = jsonArray(root.opt("items")) ?: return emptyList()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val id = item.optString("id")
                if (id.isBlank()) continue
                val duration = item.optLong("durationMs")
                add(
                    Record(
                        id = "watchlist:$id",
                        group = "İzleme listesi",
                        name = item.optString("title").ifBlank { "Video" },
                        detail = if (duration > 0L) duration.formatClock() else "",
                    ),
                )
            }
        }
    }

    private fun downloads(root: JSONObject): List<Record> {
        val items = jsonArray(root.opt("items")) ?: return emptyList()
        return buildList {
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val id = item.optString("id")
                if (id.isBlank()) continue
                val link = item.optString("pageUrl").ifBlank { item.optString("url") }
                add(
                    Record(
                        id = "download:$id",
                        group = "İndirme",
                        name = item.optString("title").ifBlank { "İndirme" },
                        detail = join(downloadState(item.optString("status")), item.optString("error")),
                        extra = link,
                    ),
                )
            }
        }
    }

    private fun streams(root: JSONObject): List<Record> {
        val items = jsonArray(root.opt("items")) ?: return emptyList()
        return buildList {
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val id = item.optString("id")
                if (id.isBlank()) continue
                val kind = if (item.optString("kind").equals("Live", true)) "canlı yayın" else "video"
                val favorite = if (item.optBoolean("favorite")) "favori" else ""
                add(
                    Record(
                        id = "stream:$id",
                        group = "Yayın",
                        name = item.optString("title").ifBlank { "Yayın" },
                        detail = join(kind, favorite),
                        extra = item.optString("url"),
                    ),
                )
            }
        }
    }

    private fun folders(root: JSONObject): List<Record> {
        return strings(root.opt("paths")).map { path ->
            Record("folder:$path", "Klasör", File(path).name.ifBlank { path }, path)
        }.sortedBy { it.name.lowercase() }
    }

    private fun watches(root: JSONObject, titles: Map<String, String>, lists: List<PreviewList>): List<Record> {
        val items = root.optJSONObject("items")
        val rows = ArrayList<Record>()
        items?.keys()?.forEach { key ->
            val record = items.optJSONObject(key)
            rows += Record("watch:$key", "İzleme", titles[key] ?: watchName(key), watchDetail(record))
        }
        val cursors = root.optJSONObject("cursors")
        val listNames = lists.associate { it.id to it.title }
        cursors?.keys()?.forEach { listId ->
            val key = cursors.optString(listId)
            if (key.isBlank()) return@forEach
            val video = titles[key] ?: watchName(key)
            rows += Record(
                "cursor:$listId",
                "İzleme",
                listNames[listId] ?: "Liste",
                "kaldığın video: $video",
            )
        }
        return rows.sortedBy { it.name.lowercase() }
    }

    private fun library(root: JSONObject, titles: Map<String, String>, forDiff: Boolean): List<Record> {
        if (root.length() == 0) return emptyList()
        val out = ArrayList<Record>()
        stringSet(root.opt("hidden")).forEach { id ->
            out += Record("hidden:$id", "Kitaplık", named(titles, id), "gizli")
        }
        stringSet(root.opt("folders")).forEach { path ->
            out += Record("libfolder:$path", "Klasör", File(path).name.ifBlank { path }, path)
        }
        val opened = readOpened(root)
        if (opened.isNotEmpty()) {
            opened.forEach { item ->
                out += Record(
                    "opened:${item.id}",
                    "Son açılan",
                    item.title.ifBlank { "Video" },
                    item.path.ifBlank { "açık" },
                )
            }
        } else {
            pipeList(root.opt("recents")).forEach { id ->
                out += Record("recent:$id", "Son açılan", named(titles, id), "açık")
            }
        }
        if (!forDiff) return out
        val recents = pipeList(root.opt("recents"))
        if (recents.isNotEmpty()) {
            out += Record(
                "recents",
                "Kitaplık",
                "Son açılan sırası",
                videos(recents.size),
                recents.map { Fact(it, named(titles, it)) },
            )
        }
        pairs(root.opt("progress")).forEach { (key, value) ->
            out += Record("progress:$key", "İlerleme", named(titles, key), clock(value))
        }
        pairs(root.opt("kprogress")).forEach { (key, value) ->
            out += Record("kprogress:$key", "İlerleme", named(titles, key), clock(value))
        }
        return out
    }

    private data class Opened(val id: String, val title: String, val path: String)

    private fun readOpened(root: JSONObject): List<Opened> {
        val rows = jsonArray(root.opt("opened_json")) ?: return emptyList()
        return buildList {
            for (i in 0 until rows.length()) {
                val item = rows.optJSONObject(i) ?: continue
                val id = item.optString("id")
                if (id.isBlank()) continue
                add(Opened(id, item.optString("title"), item.optString("path")))
            }
        }
    }

    private fun devices(root: JSONObject): List<Record> {
        val paired = jsonArray(root.opt("paired")) ?: return emptyList()
        return buildList {
            for (i in 0 until paired.length()) {
                val item = paired.optJSONObject(i) ?: continue
                val id = item.optString("id")
                if (id.isBlank()) continue
                val name = item.optString("name").ifBlank { item.optString("host").ifBlank { "Cihaz" } }
                add(Record("device:$id", "Cihaz", name, item.optString("host")))
            }
        }
    }

    private fun titles(sections: List<BackupArchive.Section>): Map<String, String> {
        val out = linkedMapOf<String, String>()
        sections.forEach { section ->
            val root = jsonObject(section.body) ?: return@forEach
            when (section.id) {
                "folder_playlists" -> {
                    val videos = jsonObject(root.opt("videos")) ?: return@forEach
                    videos.keys().forEach { listId ->
                        val rows = videos.optJSONArray(listId) ?: return@forEach
                        for (i in 0 until rows.length()) {
                            val row = rows.optJSONObject(i) ?: continue
                            val title = row.optString("title")
                            if (title.isBlank()) continue
                            putTitle(out, row.optString("id"), title)
                            putTitle(out, row.optString("originUrl"), title)
                            putTitle(out, row.optString("uri"), title)
                        }
                    }
                }
                "downloads" -> jsonArray(root.opt("items"))?.let { items ->
                    for (i in 0 until items.length()) {
                        val item = items.optJSONObject(i) ?: continue
                        putTitle(out, item.optString("id"), item.optString("title"))
                        putTitle(out, item.optString("url"), item.optString("title"))
                        putTitle(out, item.optString("pageUrl"), item.optString("title"))
                    }
                }
                "streams" -> jsonArray(root.opt("items"))?.let { items ->
                    for (i in 0 until items.length()) {
                        val item = items.optJSONObject(i) ?: continue
                        putTitle(out, item.optString("id"), item.optString("title"))
                        putTitle(out, item.optString("url"), item.optString("title"))
                    }
                }
                "library" -> jsonArray(root.opt("opened_json"))?.let { items ->
                    for (i in 0 until items.length()) {
                        val item = items.optJSONObject(i) ?: continue
                        putTitle(out, item.optString("id"), item.optString("title"))
                        putTitle(out, item.optString("path"), item.optString("title"))
                        putTitle(out, item.optString("originUrl"), item.optString("title"))
                        putTitle(out, item.optString("uri"), item.optString("title"))
                    }
                }
            }
        }
        return out
    }

    private fun putTitle(out: MutableMap<String, String>, raw: String?, title: String) {
        if (raw.isNullOrBlank() || title.isBlank()) return
        out.putIfAbsent(raw, title)
        val youtube = com.grokplayer.tv.data.scan.YouTubeResolver.videoId(raw)
        if (youtube != null) out.putIfAbsent("youtube:$youtube", title)
    }

    private fun watchName(key: String): String {
        val plain = key.substringBefore('|').substringAfterLast('/').substringAfterLast('\\')
        return when {
            key.startsWith("youtube:") -> "YouTube ${key.removePrefix("youtube:")}"
            plain.isNotBlank() -> plain
            else -> "Video"
        }
    }

    private fun watchDetail(record: JSONObject?): String {
        if (record == null) return ""
        val manual = when (record.optString("manual").lowercase()) {
            "watched" -> "izlendi"
            "unwatched" -> "izlenmedi"
            else -> ""
        }
        val feedback = when (record.optString("feedback").lowercase()) {
            "liked" -> "beğenildi"
            "disliked" -> "beğenilmedi"
            else -> ""
        }
        val position = record.optLong("positionMs")
        val duration = record.optLong("durationMs")
        val clock = if (manual.isNotEmpty()) {
            manual
        } else if (duration > 0L) {
            "${position.formatClock()} / ${duration.formatClock()}"
        } else if (position > 0L) {
            position.formatClock()
        } else {
            ""
        }
        return join(clock, feedback)
    }

    private fun offlineLists(sections: List<BackupArchive.Section>): List<PreviewList> {
        val clips = ArrayList<Clip>()
        val seen = HashSet<String>()
        fun addClip(clip: Clip) {
            val key = OfflineCollections.fileKey(clip.path.ifBlank { null }, clip.uri.ifBlank { null }, clip.id)
            if (seen.add(key)) clips += clip
        }
        jsonObject(sections.firstOrNull { it.id == "downloads" }?.body)?.let { root ->
            val items = jsonArray(root.opt("items")) ?: return@let
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                if (!item.optString("status").equals("Done", true)) continue
                val id = item.optString("id")
                if (id.isBlank()) continue
                addClip(
                    Clip(
                        id = "download:$id",
                        title = item.optString("title"),
                        uri = item.optString("url"),
                        originUrl = item.optString("pageUrl"),
                        path = item.optString("localPath"),
                    ),
                )
            }
        }
        jsonObject(sections.firstOrNull { it.id == "offline_library" }?.body)?.let { root ->
            val items = jsonArray(root.opt("items")) ?: return@let
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val id = item.optString("id")
                val path = item.optString("path")
                val uri = item.optString("uri")
                if (id.isBlank() || !OfflineCollections.isLocalFile(path, uri.substringBefore(':', "").takeIf { ':' in uri })) continue
                if (item.optBoolean("isLive") || item.optBoolean("isStream")) continue
                addClip(Clip(id, item.optString("title"), uri, item.optString("originUrl"), path))
            }
        }
        if (clips.isEmpty()) return emptyList()
        return listOf(PreviewList(OfflineCollections.SCOPE, "Çevrimdışı", "", clips))
    }

    private fun downloadState(status: String): String = when (status) {
        "Done" -> "indirildi"
        "Running" -> "indiriliyor"
        "Queued" -> "sırada"
        "Failed" -> "başarısız"
        else -> ""
    }

    private fun videos(count: Int): String = if (count == 1) "1 video" else "$count video"

    private fun named(titles: Map<String, String>, key: String): String {
        titles[key]?.let { return it }
        val file = key.substringBefore('|').substringAfterLast('/').substringAfterLast('\\')
        return when {
            file.isNotBlank() && file != key -> file
            key.isNotBlank() -> key
            else -> "Video"
        }
    }

    private fun clock(raw: String): String = raw.toLongOrNull()?.formatClock() ?: raw

    private fun number(root: JSONObject, key: String): Float? {
        if (!root.has(key)) return null
        val value = root.opt(key)
        return (value as? Number)?.toFloat()
    }

    private fun jsonObject(value: Any?): JSONObject? = when (value) {
        is JSONObject -> value
        is String -> runCatching { JSONObject(value) }.getOrNull()
        else -> null
    }

    private fun jsonArray(value: Any?): JSONArray? = when (value) {
        is JSONArray -> value
        is String -> runCatching { JSONArray(value) }.getOrNull()
        else -> null
    }

    private fun strings(value: Any?): List<String> {
        val array = jsonArray(value) ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val text = array.optString(i)
                if (text.isNotBlank()) add(text)
            }
        }
    }

    private fun stringMap(value: Any?): Map<String, String> {
        val obj = jsonObject(value) ?: return emptyMap()
        return buildMap {
            obj.keys().forEach { key ->
                if (key.isNotBlank()) put(key, obj.optString(key))
            }
        }
    }

    private fun setMap(value: Any?): Map<String, Set<String>> {
        val obj = jsonObject(value) ?: return emptyMap()
        return buildMap {
            obj.keys().forEach { key ->
                val raw = obj.opt(key)
                val cols = when (raw) {
                    is String -> if (raw.trim().startsWith("[")) strings(raw) else listOf(raw).filter { it.isNotBlank() }
                    else -> strings(raw)
                }.toSet()
                if (key.isNotBlank() && cols.isNotEmpty()) put(key, cols)
            }
        }
    }

    private fun lines(value: Any?): List<String> = when (value) {
        is String -> value.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        else -> strings(value)
    }

    private fun stringSet(value: Any?): List<String> = when (value) {
        is JSONArray -> strings(value)
        is String -> {
            val parsed = jsonArray(value)
            when {
                parsed != null -> strings(parsed)
                value.isBlank() -> emptyList()
                else -> value.split('|').map { it.trim() }.filter { it.isNotEmpty() }
            }
        }
        else -> emptyList()
    }

    private fun pipeList(value: Any?): List<String> {
        val text = value as? String ?: return emptyList()
        return text.split('|').map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun pairs(value: Any?): List<Pair<String, String>> {
        val text = value as? String ?: return emptyList()
        return text.split('|').mapNotNull { part ->
            val index = part.indexOf('=')
            if (index <= 0) null else part.substring(0, index) to part.substring(index + 1)
        }
    }
}
