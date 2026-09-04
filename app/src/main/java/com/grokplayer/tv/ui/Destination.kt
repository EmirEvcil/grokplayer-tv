package com.grokplayer.tv.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Podcasts
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector
import com.grokplayer.tv.R

enum class Destination(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Home(R.string.nav_home, Icons.Outlined.Home),
    Videos(R.string.nav_videos, Icons.Outlined.VideoLibrary),
    Streams(R.string.nav_streams, Icons.Outlined.Podcasts),
    Downloads(R.string.nav_downloads, Icons.Outlined.Download),
    Settings(R.string.nav_settings, Icons.Outlined.Settings),
}
