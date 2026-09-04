package com.grokplayer.tv.ui.home

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.grokplayer.tv.R
import com.grokplayer.tv.ui.theme.GrokPink
import com.grokplayer.tv.ui.theme.GrokYellow

data class RecentItem(
    val title: String,
    @StringRes val sourceRes: Int,
    @DrawableRes val artwork: Int,
    val progress: Float? = null,
    val progressColor: Color = GrokYellow,
)

data class ContinueWatching(
    val title: String,
    val genre: String,
    val position: String,
    val duration: String,
    @DrawableRes val artwork: Int,
)

object HomeCatalog {
    val hero = ContinueWatching(
        title = "Kuzeyin İzinde",
        genre = "Belgesel",
        position = "24:18",
        duration = "52:40",
        artwork = R.drawable.hero_kuzey,
    )

    val recent = listOf(
        RecentItem("Kuzeyin İzinde", R.string.source_local, R.drawable.thumb_kuzey, 0.46f, GrokYellow),
        RecentItem("Mavi Derinlik", R.string.source_stream, R.drawable.thumb_mavi),
        RecentItem("Gece Yolculuğu", R.string.source_stream, R.drawable.thumb_gece, 0.38f, GrokPink),
        RecentItem("Ormanın Sesi", R.string.source_local, R.drawable.thumb_orman),
    )
}
