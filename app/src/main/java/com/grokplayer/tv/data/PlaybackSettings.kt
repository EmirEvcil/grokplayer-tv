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
    var defaultSpeed by mutableFloatStateOf(prefs.getFloat("speed", 1f))
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
        defaultSpeed = if (defaultSpeed < 1.1f) 1.25f else 1f
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

    val seekStepLabel: String get() = "$seekStepSeconds saniye"
    val speedLabel: String get() = if (defaultSpeed == 1.25f) "1,25×" else "1×"
    val hideControlsLabel: String get() = "$hideControlsSeconds saniye"
    val startScreenLabel: String get() = if (startScreen == Destination.Videos) "Videolar" else "Ana sayfa"
}
