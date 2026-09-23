package com.grokplayer.tv.data

import org.json.JSONObject
import kotlin.math.abs

data class StoryboardLevel(
    val index: Int,
    val template: String,
    val width: Int,
    val height: Int,
    val count: Int,
    val columns: Int,
    val rows: Int,
    val intervalMs: Int,
) {
    val framesPerSheet: Int get() = (columns * rows).coerceAtLeast(1)

    fun stepMs(durationMs: Long): Long {
        if (intervalMs > 0) return intervalMs.toLong()
        if (durationMs > 0L && count > 0) return (durationMs / count).coerceAtLeast(1L)
        return 10_000L
    }

    fun cellAt(timeMs: Long, durationMs: Long): StoryboardCell? {
        val step = stepMs(durationMs)
        if (step <= 0L) return null
        val max = (count - 1).coerceAtLeast(0)
        val index = ((timeMs.coerceAtLeast(0L) / step).toInt()).coerceIn(0, max)
        val sheet = index / framesPerSheet
        val cell = index % framesPerSheet
        val url = template.replace("\$M", sheet.toString())
        return StoryboardCell(
            url = url,
            column = cell % columns,
            row = cell / columns,
            width = width,
            height = height,
            timeMs = index * step,
        )
    }
}

data class StoryboardCell(
    val url: String,
    val column: Int,
    val row: Int,
    val width: Int,
    val height: Int,
    val timeMs: Long,
)

object StoryboardSpec {
    fun fromPlayerJson(json: String?): String? {
        if (json.isNullOrBlank()) return null
        val boards = runCatching { JSONObject(json).optJSONObject("storyboards") }.getOrNull() ?: return null
        boards.optJSONObject("playerStoryboardSpecRenderer")?.optString("spec")
            ?.takeIf { it.length > 8 }?.let { return it }
        return boards.optJSONObject("playerLiveStoryboardSpecRenderer")?.optString("spec")?.takeIf { it.length > 8 }
    }

    fun parse(spec: String?): List<StoryboardLevel> {
        val text = spec?.trim().orEmpty()
        if (text.isEmpty()) return emptyList()
        val parts = text.split('|').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size < 2 || !parts[0].startsWith("http", ignoreCase = true)) return emptyList()
        val levels = ArrayList<StoryboardLevel>()
        for (i in 1 until parts.size) {
            val fields = parts[i].split('#')
            if (fields.size < 5) continue
            val width = fields[0].toIntOrNull() ?: continue
            val height = fields[1].toIntOrNull() ?: continue
            val count = fields[2].toIntOrNull() ?: continue
            val columns = fields[3].toIntOrNull() ?: continue
            val rows = fields[4].toIntOrNull() ?: continue
            val interval = fields.getOrNull(5)?.toIntOrNull() ?: 0
            val name = fields.getOrNull(6)?.takeIf { it.isNotBlank() } ?: "M\$M"
            val sigh = fields.getOrNull(7).orEmpty()
            val template = buildTemplate(parts[0], i - 1, name, sigh) ?: continue
            levels += StoryboardLevel(i - 1, template, width, height, count, columns, rows, interval)
        }
        return levels
    }

    fun bestLevel(levels: List<StoryboardLevel>): StoryboardLevel? {
        val fit = levels.filter { it.width >= 80 }
        return fit.maxWithOrNull(compareBy<StoryboardLevel> { it.width }.thenByDescending { it.intervalMs })
            ?: levels.lastOrNull()
    }

    fun fastLevel(levels: List<StoryboardLevel>): StoryboardLevel? {
        val fit = levels.filter { it.width >= 120 }
        return fit.minByOrNull { abs(it.width - 160) } ?: bestLevel(levels)
    }

    private fun buildTemplate(baseUrl: String, level: Int, name: String, sigh: String): String? {
        var url = baseUrl.replace("\$L", level.toString()).replace("\$N", name)
        if (url.isBlank()) return null
        if (sigh.isNotBlank() && !url.contains("sigh=", ignoreCase = true)) {
            val encoded = java.net.URLEncoder.encode(sigh, "UTF-8").replace("+", "%20")
            url += (if ('?' in url) "&" else "?") + "sigh=$encoded"
        }
        return url
    }
}
