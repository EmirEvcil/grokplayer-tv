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

enum class StorageSource { Internal, Usb }

data class LibraryVideo(
    val title: String,
    val duration: String,
    val format: String,
    val source: StorageSource,
    @DrawableRes val artwork: Int,
    val progress: Float? = null,
)

data class SavedStream(
    val title: String,
    val live: Boolean,
    val duration: String? = null,
    @DrawableRes val artwork: Int,
    val progress: Float? = null,
    val favorite: Boolean = true,
)

enum class DownloadStatus { Running, Queued, Paused, Done }

data class DownloadItem(
    val title: String,
    val status: DownloadStatus,
    val quality: String,
    @DrawableRes val artwork: Int,
    val progress: Float? = null,
    val sizeLabel: String? = null,
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

    val videos = listOf(
        LibraryVideo("Kuzeyin İzinde", "52:40", "MKV", StorageSource.Usb, R.drawable.thumb_kuzey, 0.46f),
        LibraryVideo("Mavi Derinlik", "38:12", "MP4", StorageSource.Usb, R.drawable.thumb_mavi),
        LibraryVideo("Gece Yolculuğu", "24:06", "MP4", StorageSource.Internal, R.drawable.thumb_gece, 0.22f),
        LibraryVideo("Ormanın Sesi", "46:20", "MP4", StorageSource.Internal, R.drawable.thumb_orman, 0.18f),
        LibraryVideo("Ufkun Ötesi", "31:44", "MKV", StorageSource.Usb, R.drawable.thumb_ufuk, 0.12f),
        LibraryVideo("Yol Hikâyeleri", "18:30", "MP4", StorageSource.Internal, R.drawable.thumb_yol, 0.08f),
    )

    val streams = listOf(
        SavedStream("Kıyı Kamerası", live = true, artwork = R.drawable.thumb_kiyi),
        SavedStream("Orman Kamerası", live = true, artwork = R.drawable.thumb_dere),
        SavedStream("Kuzeyin İzinde", live = false, duration = "52:40", artwork = R.drawable.thumb_kuzey, progress = 0.46f),
        SavedStream("Mavi Derinlik", live = false, duration = "38:12", artwork = R.drawable.thumb_mavi),
        SavedStream("Gece Yolculuğu", live = false, duration = "24:06", artwork = R.drawable.thumb_gece),
        SavedStream("Ufkun Ötesi", live = false, duration = "31:44", artwork = R.drawable.thumb_ufuk),
    )

    val downloads = listOf(
        DownloadItem("Kuzeyin İzinde", DownloadStatus.Running, "1080p", R.drawable.thumb_kuzey, 0.60f, "1,2 GB / 2,0 GB · %60"),
        DownloadItem("Mavi Derinlik", DownloadStatus.Queued, "1080p", R.drawable.thumb_mavi),
        DownloadItem("Gece Yolculuğu", DownloadStatus.Paused, "720p", R.drawable.thumb_gece, 0.30f, "240 MB / 800 MB · %30"),
        DownloadItem("Ormanın Sesi", DownloadStatus.Done, "1080p", R.drawable.thumb_orman, sizeLabel = "1,4 GB"),
    )
}
