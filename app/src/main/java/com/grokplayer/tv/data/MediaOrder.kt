package com.grokplayer.tv.data

/**
 * Playlist order for episode codes (S01E03, 01x06) and numbered films
 * (Toy Story 1, Toy Story 2). Falls back to natural alphanumeric order.
 */
object MediaOrder {
    private val SERIES = Regex(
        """^(.*?)[\s._-]*[\[(]?(?:s(\d{1,2})[\s._-]*e(\d{1,3})|(\d{1,2})\s*[x×]\s*(\d{1,3}))[\])]?""",
        RegexOption.IGNORE_CASE,
    )
    private val FILM_NUM = Regex(
        """^(.*?)[\s._-]+(?:part|pt|bölüm|bolum|episode|ep)?[\s._-]*(\d{1,3})\s*$""",
        RegexOption.IGNORE_CASE,
    )

    fun compare(left: String, right: String): Int {
        val a = key(left)
        val b = key(right)
        val bySeries = a.series.compareTo(b.series)
        if (bySeries != 0) return bySeries
        val bySeason = a.season.compareTo(b.season)
        if (bySeason != 0) return bySeason
        val byEpisode = a.episode.compareTo(b.episode)
        if (byEpisode != 0) return byEpisode
        val byFilm = a.film.compareTo(b.film)
        if (byFilm != 0) return byFilm
        return natural(a.raw, b.raw)
    }

    fun <T> sortByTitle(items: List<T>, titleOf: (T) -> String): List<T> =
        items.sortedWith { a, b -> compare(titleOf(a), titleOf(b)) }

    internal fun key(name: String): SortKey {
        val stem = name.substringAfterLast('/', name.substringAfterLast('\\'))
            .substringBeforeLast('.')
            .trim()
        val series = SERIES.find(stem)
        if (series != null) {
            val season = (series.groupValues[2].ifBlank { series.groupValues[4] }).toIntOrNull() ?: 0
            val episode = (series.groupValues[3].ifBlank { series.groupValues[5] }).toIntOrNull() ?: 0
            val prefix = series.groupValues[1].trim().lowercase()
            return SortKey(prefix.ifBlank { stem.lowercase() }, season, episode, 0, stem.lowercase())
        }
        val film = FILM_NUM.find(stem)
        if (film != null) {
            val prefix = film.groupValues[1].trim().lowercase()
            val num = film.groupValues[2].toIntOrNull() ?: 0
            if (prefix.length >= 2) {
                return SortKey(prefix, 0, 0, num, stem.lowercase())
            }
        }
        return SortKey("", 0, 0, 0, stem.lowercase())
    }

    private fun natural(left: String, right: String): Int {
        val a = left.lowercase()
        val b = right.lowercase()
        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val da = a[i].isDigit()
            val db = b[j].isDigit()
            if (da && db) {
                var ia = i
                while (ia < a.length && a[ia].isDigit()) ia++
                var ib = j
                while (ib < b.length && b[ib].isDigit()) ib++
                val na = a.substring(i, ia).trimStart('0').ifBlank { "0" }.toLong()
                val nb = b.substring(j, ib).trimStart('0').ifBlank { "0" }.toLong()
                if (na != nb) return na.compareTo(nb)
                i = ia
                j = ib
            } else {
                val cmp = a[i].compareTo(b[j])
                if (cmp != 0) return cmp
                i++
                j++
            }
        }
        return (a.length - i).compareTo(b.length - j)
    }

    data class SortKey(
        val series: String,
        val season: Int,
        val episode: Int,
        val film: Int,
        val raw: String,
    )
}
