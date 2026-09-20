package com.grokplayer.tv.ui

import android.net.Uri
import androidx.compose.material3.Text
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.grokplayer.tv.data.LibraryVideo
import com.grokplayer.tv.data.StorageSource
import com.grokplayer.tv.data.WatchLogic
import com.grokplayer.tv.data.WatchStats
import com.grokplayer.tv.data.WatchStore
import com.grokplayer.tv.ui.components.ListResumeBar
import com.grokplayer.tv.ui.components.VideoPoster
import com.grokplayer.tv.ui.lists.VideoMenuHost
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WatchUiTest {
    @get:Rule
    val rule = createComposeRule()

    private val video = LibraryVideo(
        id = "1",
        title = "Dizi S01E03",
        uri = Uri.parse("https://ex/a"),
        durationMs = 60_000L,
        format = "MP4",
        source = StorageSource.Internal,
        dateAdded = 1L,
        lastModified = 1L,
        originUrl = "https://ex/a",
    )

    private val live = video.copy(
        id = "live-1",
        title = "Canlı maç",
        isLive = true,
        format = "CANLI",
        originUrl = "https://ex/live",
    )

    @Test
    fun resumeBarShowsLastWatchedAndActions() {
        val resume = FocusRequester()
        val restart = FocusRequester()
        val up = FocusRequester()
        val down = FocusRequester()
        rule.setContent {
            ListResumeBar(
                video = video,
                positionMs = 12_000L,
                onResume = {},
                onRestart = {},
                resumeFocus = resume,
                restartFocus = restart,
                up = up,
                down = down,
            )
        }
        rule.onNodeWithText("Kaldığın yer").assertIsDisplayed()
        rule.onNodeWithText("Dizi S01E03").assertIsDisplayed()
        rule.onNodeWithText("Devam et").assertIsDisplayed()
        rule.onNodeWithText("Baştan oynat").assertIsDisplayed()
        rule.onNodeWithText("00:12 / 01:00").assertIsDisplayed()
    }

    @Test
    fun collectionRowShowsWatchedEpisodeCount() {
        rule.setContent {
            Text(WatchLogic.collectionMeta(false, 8, WatchStats(3, 8)))
        }
        rule.onNodeWithText("Koleksiyon · 8 video · 3 izlendi").assertIsDisplayed()
    }

    @Test
    fun liveHasNoWatchUiCopy() {
        rule.setContent {
            Text(WatchLogic.listWatchedLine(WatchStats(0, 0)) ?: "none")
        }
        rule.onNodeWithText("none").assertIsDisplayed()
    }

    @Test
    fun vodPosterShowsProgress() {
        rule.setContent {
            VideoPoster(
                uri = video.uri,
                title = video.title,
                focused = false,
                progress = 0.4f,
                durationMs = video.durationMs,
            )
        }
        rule.onNodeWithTag("watch-progress").assertIsDisplayed()
    }

    @Test
    fun livePosterHidesProgress() {
        rule.setContent {
            VideoPoster(
                uri = live.uri,
                title = live.title,
                focused = false,
                progress = 0.4f,
                isLive = true,
                format = "CANLI",
            )
        }
        rule.onAllNodesWithTag("watch-progress").assertCountEquals(0)
    }

    @Test
    fun vodMenuMarksWatchedAndTogglesLike() {
        val file = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "watch-ui-${System.nanoTime()}.json",
        )
        val store = WatchStore(file)
        rule.setContent {
            VideoMenuHost(
                video = video,
                extraActions = emptyList(),
                onDismiss = {},
                showAddToList = false,
                watch = store,
            )
        }
        rule.onNodeWithText("İzlendi olarak işaretle").assertIsDisplayed()
        rule.onNodeWithText("Beğendim").assertIsDisplayed()
        rule.onNodeWithText("Beğenmedim").assertIsDisplayed()
        rule.onNodeWithText("İzlendi olarak işaretle").performClick()
        rule.onNodeWithText("İzlenmedi olarak işaretle").assertIsDisplayed()
        rule.onNodeWithText("Beğendim").performClick()
        rule.onNodeWithText("Beğeniyi kaldır").assertIsDisplayed()
        file.delete()
    }

    @Test
    fun liveMenuHasNoWatchActions() {
        val file = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "watch-live-${System.nanoTime()}.json",
        )
        val store = WatchStore(file)
        rule.setContent {
            VideoMenuHost(
                video = live,
                extraActions = emptyList(),
                onDismiss = {},
                showAddToList = false,
                watch = store,
            )
        }
        rule.onAllNodesWithText("İzlendi olarak işaretle").assertCountEquals(0)
        rule.onAllNodesWithText("Beğendim").assertCountEquals(0)
        rule.onAllNodesWithText("Beğenmedim").assertCountEquals(0)
        file.delete()
    }
}
