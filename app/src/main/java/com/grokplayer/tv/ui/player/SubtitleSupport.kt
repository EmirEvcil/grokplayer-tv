package com.grokplayer.tv.ui.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import com.grokplayer.tv.data.DownloadOwnership
import com.grokplayer.tv.data.LibraryVideo
import java.io.File
import java.util.Locale

internal data class SubtitleOption(
    val key: String,
    val label: String,
    val group: TrackGroup? = null,
    val trackIndex: Int = -1,
    val language: String? = null,
    val forced: Boolean = false,
) {
    val isOff: Boolean get() = group == null

    companion object {
        val Off = SubtitleOption(key = "off", label = "Kapalı")
    }
}

internal fun sidecarSubtitleConfigs(video: LibraryVideo): List<MediaItem.SubtitleConfiguration> {
    val videoFile = resolveVideoFile(video) ?: return emptyList()
    val stem = videoFile.nameWithoutExtension
    return DownloadOwnership.sidecarsBeside(videoFile, subtitlesOnly = true).map { file ->
        val lang = DownloadOwnership.sidecarLanguage(stem, file)
        MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(file))
            .setMimeType(mimeForSubtitle(file.extension))
            .setLanguage(lang)
            .setLabel(sidecarLabel(file, lang))
            .setSelectionFlags(0)
            .build()
    }
}

internal fun listSubtitleOptions(tracks: Tracks): List<SubtitleOption> {
    val options = mutableListOf(SubtitleOption.Off)
    tracks.groups.forEachIndexed { groupIndex, group ->
        if (group.type != C.TRACK_TYPE_TEXT) return@forEachIndexed
        for (trackIndex in 0 until group.length) {
            if (!group.isTrackSupported(trackIndex)) continue
            val format = group.getTrackFormat(trackIndex)
            val forced = format.selectionFlags and C.SELECTION_FLAG_FORCED != 0
            options += SubtitleOption(
                key = "${groupIndex}:${trackIndex}:${format.id.orEmpty()}:${format.language.orEmpty()}:${format.label.orEmpty()}",
                label = subtitleLabel(format),
                group = group.mediaTrackGroup,
                trackIndex = trackIndex,
                language = format.language,
                forced = forced,
            )
        }
    }
    return options
}

internal fun activeSubtitleKey(tracks: Tracks, options: List<SubtitleOption>): String {
    val selected = options.firstOrNull { option ->
        !option.isOff && option.group != null && tracks.groups.any { group ->
            group.type == C.TRACK_TYPE_TEXT &&
                group.mediaTrackGroup == option.group &&
                group.isTrackSelected(option.trackIndex)
        }
    }
    return selected?.key ?: SubtitleOption.Off.key
}

internal fun applySubtitleChoice(player: Player, option: SubtitleOption) {
    val builder = player.trackSelectionParameters.buildUpon()
        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
        .setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT or C.SELECTION_FLAG_FORCED)
        .setSelectUndeterminedTextLanguage(false)
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
    if (option.isOff || option.group == null) {
        player.currentTracks.groups
            .filter { it.type == C.TRACK_TYPE_TEXT }
            .forEach { group ->
                builder.addOverride(TrackSelectionOverride(group.mediaTrackGroup, emptyList()))
            }
    } else {
        builder.setOverrideForType(
            TrackSelectionOverride(option.group, listOf(option.trackIndex)),
        )
    }
    player.trackSelectionParameters = builder.build()
}

internal fun muteDefaultTextTracks(player: Player) {
    applySubtitleChoice(player, SubtitleOption.Off)
}

internal fun preferredSubtitle(options: List<SubtitleOption>, lang: String): SubtitleOption? {
    val want = lang.trim().lowercase()
    if (want.isEmpty()) return options.firstOrNull { !it.isOff && !it.forced }
    return options.firstOrNull { option ->
        !option.isOff && !option.forced && option.language?.lowercase()?.startsWith(want) == true
    } ?: options.firstOrNull { option ->
        !option.isOff && option.language?.lowercase()?.startsWith(want) == true
    }
}

private fun resolveVideoFile(video: LibraryVideo): File? {
    video.path?.let { path ->
        File(path).takeIf { it.exists() }?.let { return it }
    }
    if (video.uri.scheme == "file") {
        video.uri.path?.let { path ->
            File(path).takeIf { it.exists() }?.let { return it }
        }
    }
    return null
}

private fun sidecarLabel(file: File, language: String?): String {
    languageLabel(language)?.let { return it }
    return "Dosya · ${file.extension.uppercase(Locale.US)}"
}

private fun subtitleLabel(format: Format): String {
    val forced = format.selectionFlags and C.SELECTION_FLAG_FORCED != 0
    val named = format.label?.trim().orEmpty()
    val language = languageLabel(format.language)
    val base = when {
        named.isNotEmpty() -> named
        language != null -> language
        else -> "Altyazı"
    }
    return if (forced && !base.contains("forced", ignoreCase = true) && !base.contains("zorunlu", ignoreCase = true)) {
        "$base (Forced)"
    } else {
        base
    }
}

private fun languageLabel(code: String?): String? {
    val raw = code?.trim().orEmpty()
    if (raw.isEmpty() || raw.equals("und", ignoreCase = true)) return null
    LANGUAGE_NAMES[raw.lowercase(Locale.US)]?.let { return it }
    LANGUAGE_NAMES[raw.take(2).lowercase(Locale.US)]?.let { return it }
    return raw
}

private fun mimeForSubtitle(extension: String): String {
    return when (extension.lowercase(Locale.US)) {
        "vtt", "webvtt" -> MimeTypes.TEXT_VTT
        "ass", "ssa" -> MimeTypes.TEXT_SSA
        else -> MimeTypes.APPLICATION_SUBRIP
    }
}

private val LANGUAGE_NAMES = mapOf(
    "tr" to "Türkçe",
    "tur" to "Türkçe",
    "en" to "English",
    "eng" to "English",
    "fr" to "Français",
    "fra" to "Français",
    "fre" to "Français",
    "es" to "Español",
    "spa" to "Español",
    "ja" to "日本語",
    "jpn" to "日本語",
    "de" to "Deutsch",
    "ger" to "Deutsch",
    "deu" to "Deutsch",
    "it" to "Italiano",
    "ita" to "Italiano",
    "pt" to "Português",
    "por" to "Português",
    "ru" to "Русский",
    "rus" to "Русский",
    "ar" to "العربية",
    "ara" to "العربية",
    "ko" to "한국어",
    "kor" to "한국어",
    "zh" to "中文",
    "chi" to "中文",
    "zho" to "中文",
    "nl" to "Nederlands",
    "sv" to "Svenska",
    "pl" to "Polski",
    "hi" to "हिन्दी",
)
