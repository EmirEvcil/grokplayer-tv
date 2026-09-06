package com.grokplayer.tv.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.grokplayer.tv.ui.Destination

class PlaybackSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("playback", Context.MODE_PRIVATE)

    var resumeEnabled by mutableStateOf(prefs.getBoolean("resume", true))
        private set
    var autoNext by mutableStateOf(prefs.getBoolean("auto_next", true))
        private set
    var seekStepSeconds by mutableIntStateOf(prefs.getInt("seek_step", 10))
        private set
    var defaultSpeed by mutableFloatStateOf(snapSpeed(prefs.getFloat("speed", 1f)))
        private set
    var hideControlsSeconds by mutableIntStateOf(prefs.getInt("hide_controls", 2))
        private set
    var startScreen by mutableStateOf(
        if (prefs.getString("start", Destination.Home.name) == Destination.Videos.name) {
            Destination.Videos
        } else {
            Destination.Home
        },
    )
        private set
    var fitMode by mutableIntStateOf(prefs.getInt("fit_mode", 0))
        private set
    var maxHeight by mutableIntStateOf(prefs.getInt("max_height", 0))
        private set
    var audioLang by mutableStateOf(prefs.getString("audio_lang", "").orEmpty())
        private set
    var stereoOnly by mutableStateOf(prefs.getBoolean("stereo_only", true))
        private set
    var captionsOn by mutableStateOf(prefs.getBoolean("captions_on", false))
        private set
    var captionLang by mutableStateOf(prefs.getString("caption_lang", "tr").orEmpty())
        private set
    var captionSize by mutableIntStateOf(prefs.getInt("caption_size", 1))
        private set
    var downloadHeight by mutableIntStateOf(prefs.getInt("dl_height", 720))
        private set

    fun toggleResume() {
        resumeEnabled = !resumeEnabled
        prefs.edit().putBoolean("resume", resumeEnabled).apply()
    }

    fun toggleAutoNext() {
        autoNext = !autoNext
        prefs.edit().putBoolean("auto_next", autoNext).apply()
    }

    fun cycleSeekStep() {
        seekStepSeconds = if (seekStepSeconds == 10) 5 else 10
        prefs.edit().putInt("seek_step", seekStepSeconds).apply()
    }

    fun cycleSpeed() {
        val index = SPEEDS.indexOfFirst { kotlin.math.abs(it - defaultSpeed) < 0.01f }
        defaultSpeed = SPEEDS[(index.coerceAtLeast(0) + 1) % SPEEDS.size]
        prefs.edit().putFloat("speed", defaultSpeed).apply()
    }

    fun setSpeed(value: Float) {
        defaultSpeed = snapSpeed(value)
        prefs.edit().putFloat("speed", defaultSpeed).apply()
    }

    fun cycleHideControls() {
        hideControlsSeconds = if (hideControlsSeconds == 2) 5 else 2
        prefs.edit().putInt("hide_controls", hideControlsSeconds).apply()
    }

    fun cycleStartScreen() {
        startScreen = if (startScreen == Destination.Home) Destination.Videos else Destination.Home
        prefs.edit().putString("start", startScreen.name).apply()
    }

    fun cycleFitMode() {
        fitMode = (fitMode + 1) % 3
        prefs.edit().putInt("fit_mode", fitMode).apply()
    }

    fun cycleMaxHeight() {
        maxHeight = when (maxHeight) {
            0 -> 720
            720 -> 1080
            else -> 0
        }
        prefs.edit().putInt("max_height", maxHeight).apply()
    }

    fun cycleAudioLang() {
        audioLang = nextLang(audioLang, AUDIO_LANGS)
        prefs.edit().putString("audio_lang", audioLang).apply()
    }

    fun toggleStereoOnly() {
        stereoOnly = !stereoOnly
        prefs.edit().putBoolean("stereo_only", stereoOnly).apply()
    }

    fun toggleCaptionsOn() {
        captionsOn = !captionsOn
        prefs.edit().putBoolean("captions_on", captionsOn).apply()
    }

    fun cycleCaptionLang() {
        captionLang = nextLang(captionLang.ifBlank { "tr" }, CAPTION_LANGS)
        prefs.edit().putString("caption_lang", captionLang).apply()
    }

    fun cycleCaptionSize() {
        captionSize = (captionSize + 1) % 3
        prefs.edit().putInt("caption_size", captionSize).apply()
    }

    fun cycleDownloadHeight() {
        downloadHeight = when (downloadHeight) {
            720 -> 480
            480 -> 0
            else -> 720
        }
        prefs.edit().putInt("dl_height", downloadHeight).apply()
    }

    val seekStepLabel: String get() = "$seekStepSeconds saniye"
    val speedLabel: String get() = formatSpeedLabel(defaultSpeed)
    val hideControlsLabel: String get() = "$hideControlsSeconds saniye"
    val startScreenLabel: String get() = if (startScreen == Destination.Videos) "Videolar" else "Ana sayfa"
    val fitModeLabel: String get() = when (fitMode) {
        1 -> "Doldur"
        2 -> "Kırp"
        else -> "Sığdır"
    }
    val maxHeightLabel: String get() = when (maxHeight) {
        720 -> "720p"
        1080 -> "1080p"
        else -> "Sınırsız"
    }
    val audioLangLabel: String get() = langLabel(audioLang, "Otomatik")
    val captionLangLabel: String get() = langLabel(captionLang, "Türkçe")
    val captionSizeLabel: String get() = when (captionSize) {
        0 -> "Küçük"
        2 -> "Büyük"
        else -> "Orta"
    }
    val captionSp: Float get() = when (captionSize) {
        0 -> 22f
        2 -> 34f
        else -> 28f
    }
    val downloadHeightLabel: String get() = when (downloadHeight) {
        720 -> "720p"
        480 -> "480p"
        else -> "En iyi"
    }

    companion object {
        val SPEEDS = floatArrayOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
        private val AUDIO_LANGS = listOf("", "tr", "en", "fr", "es")
        private val CAPTION_LANGS = listOf("tr", "en", "fr", "es", "ja")

        fun snapSpeed(value: Float): Float =
            SPEEDS.minBy { kotlin.math.abs(it - value) }

        fun langLabel(code: String, emptyLabel: String): String = when (code.trim().lowercase()) {
            "tr", "tur" -> "Türkçe"
            "en", "eng" -> "English"
            "fr", "fra", "fre" -> "Français"
            "es", "spa" -> "Español"
            "de", "deu", "ger" -> "Deutsch"
            "it", "ita" -> "Italiano"
            "ja", "jpn" -> "日本語"
            else -> emptyLabel
        }

        private fun nextLang(current: String, values: List<String>): String {
            val index = values.indexOf(current).let { if (it < 0) 0 else it }
            return values[(index + 1) % values.size]
        }
    }
}
