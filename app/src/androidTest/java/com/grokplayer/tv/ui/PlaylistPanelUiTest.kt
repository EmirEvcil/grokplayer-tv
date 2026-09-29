package com.grokplayer.tv.ui

import android.net.Uri
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.grokplayer.tv.data.LibraryVideo
import com.grokplayer.tv.data.PlaybackQueue
import com.grokplayer.tv.data.StorageSource
import com.grokplayer.tv.data.insertCopyAfterIndex
import com.grokplayer.tv.ui.player.PlaylistPanel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistPanelUiTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun openingTheListFocusesThePlayingRow() {
        rule.setContent {
            PlaylistPanel(
                videos = listOf(clip("a", "Alfa"), clip("b", "Beta"), clip("c", "Cem")),
                currentId = "b",
                playingIndex = 1,
                onSelectList = {},
                onClose = {},
            )
        }
        rule.waitForIdle()
        rule.onNodeWithText("Beta").assertIsFocused()
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        rule.onNodeWithText("Cem").assertIsFocused()
        key(KeyEvent.KEYCODE_DPAD_UP)
        rule.onNodeWithText("Beta").assertIsFocused()
    }

    @Test
    fun focusedMenuRowIsYellowWithDarkText() {
        rule.setContent {
            PlaylistPanel(
                videos = listOf(clip("a", "Alfa")),
                currentId = "a",
                onSelectList = {},
                onClose = {},
            )
        }
        rule.onNodeWithText("Alfa").performTouchInput { longClick() }
        rule.onNodeWithText("Sonra oynat").assertIsFocused()
        val focused = rule.onNodeWithText("Sonra oynat").captureToImage().toPixelMap()
        assertTrue(count(focused, ::yellow) > count(focused, ::white))
        assertTrue(count(focused, ::dark) > 0)
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        rule.onNodeWithText("Sıraya ekle").assertIsFocused()
        val idle = rule.onNodeWithText("Sonra oynat").captureToImage().toPixelMap()
        assertTrue(count(idle, ::yellow) < count(idle, ::white))
        assertTrue(count(idle, ::dark) > count(idle, ::yellow))
    }

    @Test
    fun focusedListCardKeepsAWhiteTitle() {
        rule.setContent {
            PlaylistPanel(
                videos = listOf(clip("a", "Alfa"), clip("b", "Beta")),
                currentId = "a",
                playingIndex = 0,
                onSelectList = {},
                onClose = {},
            )
        }
        rule.onNodeWithText("Alfa").assertIsFocused()
        val card = rule.onNodeWithText("Alfa").captureToImage().toPixelMap()
        val inset = 8.coerceAtMost(card.width / 4).coerceAtMost(card.height / 4)
        assertTrue(count(card, ::white, inset, step = 1) > 0)
        assertTrue(count(card, ::yellow, inset, step = 1) == 0)
    }

    @Test
    fun leftFromTheOpenListKeepsTheRowFocused() {
        rule.setContent {
            PlaylistPanel(
                videos = listOf(clip("a", "Alfa"), clip("b", "Beta")),
                currentId = "a",
                playingIndex = 0,
                onSelectList = {},
                onClose = {},
            )
        }
        rule.waitForIdle()
        rule.onNodeWithText("Alfa").assertIsFocused()
        key(KeyEvent.KEYCODE_DPAD_LEFT)
        rule.onNodeWithText("Alfa").assertIsFocused()
    }

    @Test
    fun playlistHoldShowsOptionsAndPlayNextKeepsTheOldOrder() {
        val start = listOf("a", "b", "c", "d").map { clip(it, it.uppercase()) }
        var order by mutableStateOf(start)
        rule.setContent {
            PlaylistPanel(
                videos = order,
                currentId = "b",
                playingIndex = 1,
                onSelectList = {},
                onPlayNextInList = { picked -> order = insertCopyAfterIndex(order, 1, picked) },
                onClose = {},
            )
        }
        rule.onNodeWithText("D").performTouchInput { longClick() }
        rule.onNodeWithText("D").assertIsDisplayed()
        rule.onNodeWithText("01:00 · Dahili").assertIsDisplayed()
        rule.onNodeWithText("Sonra oynat").assertIsDisplayed()
        rule.onNodeWithText("Sıraya ekle").assertIsDisplayed()
        rule.onNodeWithText("İzleme listesine ekle").assertIsDisplayed()
        rule.onNodeWithText("Beğendim").assertIsDisplayed()
        rule.onNodeWithText("İzlendi olarak işaretle").assertIsDisplayed()
        rule.onNodeWithText("Koleksiyona ekle").assertIsDisplayed()
        rule.onAllNodesWithText("Sırayı değiştir").assertCountEquals(0)
        rule.onNodeWithText("Sonra oynat").performClick()
        rule.onNodeWithText("Sonra oynat").assertIsDisplayed()
        back()
        rule.onAllNodesWithText("Sonra oynat").assertCountEquals(0)
        rule.onAllNodesWithText("D").assertCountEquals(2)
        assertEquals(listOf("A", "B", "D", "C", "D"), order.map { it.title })
        rule.onAllNodes(hasText("D") and hasText("5")).filter(isFocused()).assertCountEquals(1)
    }

    @Test
    fun queuePlayNextMovesTheVideoAndShowsItsPosition() {
        val queue = PlaybackQueue()
        listOf("a", "b", "c", "d").map { clip(it, it.uppercase()) }.forEach { queue.addLast(it) }
        queue.moveTo("b")
        rule.setContent {
            PlaylistPanel(
                videos = emptyList(),
                queue = queue.items,
                currentId = "b",
                playingOnQueue = true,
                playingIndex = 1,
                onSelectList = {},
                onPlayNextInQueue = { picked -> queue.addNext(picked, "b") },
                onClose = {},
            )
        }
        rule.onNodeWithText("Sıra").performClick()
        rule.onNodeWithText("D").performTouchInput { longClick() }
        rule.onNodeWithText("4/4").assertIsDisplayed()
        rule.onNodeWithText("01:00 · Dahili").assertIsDisplayed()
        rule.onNodeWithText("Sırayı değiştir").assertIsDisplayed()
        rule.onNodeWithText("Sıradan çıkar").assertIsDisplayed()
        rule.onNodeWithText("Sonra oynat").performClick()
        assertEquals(listOf("a", "b", "d", "c"), queue.items.map { it.id })
        rule.onNodeWithText("3/4").assertIsDisplayed()
    }

    @Test
    fun removingTheFocusedLastRowKeepsThePreviousRowFocused() {
        var videos by mutableStateOf(listOf(clip("a", "Alfa"), clip("b", "Beta"), clip("c", "Cem")))
        rule.setContent {
            PlaylistPanel(
                videos = videos,
                currentId = "c",
                playingIndex = 2,
                onSelectList = {},
                onClose = {},
            )
        }
        rule.waitForIdle()
        rule.onNodeWithText("Cem").assertIsFocused()
        videos = listOf(clip("a", "Alfa"), clip("b", "Beta"))
        rule.mainClock.advanceTimeBy(300)
        rule.waitForIdle()
        rule.onAllNodesWithText("Cem").assertCountEquals(0)
        rule.onNodeWithText("Beta").assertIsFocused()
    }

    @Test
    fun removingAFocusedCopyDoesNotCrash() {
        var videos by mutableStateOf(listOf(clip("a", "Alfa"), clip("b", "Beta"), clip("b", "Beta")))
        rule.setContent {
            PlaylistPanel(
                videos = videos,
                currentId = "b",
                playingIndex = 2,
                onSelectList = {},
                onClose = {},
            )
        }
        rule.waitForIdle()
        rule.onAllNodes(hasText("Beta") and isFocused()).assertCountEquals(1)
        videos = listOf(clip("a", "Alfa"), clip("b", "Beta"))
        rule.mainClock.advanceTimeBy(300)
        rule.waitForIdle()
        rule.onAllNodesWithText("Beta").assertCountEquals(1)
        rule.onNodeWithText("Beta").assertIsFocused()
    }

    @Test
    fun releasingHeldOkDoesNotEnterChangeOrder() {
        val queue = PlaybackQueue()
        listOf("a", "b", "c", "d").map { clip(it, it.uppercase()) }.forEach { queue.addLast(it) }
        rule.setContent {
            PlaylistPanel(
                videos = emptyList(),
                queue = queue.items,
                currentId = "b",
                playingOnQueue = true,
                playingIndex = 1,
                onSelectList = {},
                onMoveQueue = { from, to -> queue.move(from, to) },
                onClose = {},
            )
        }
        rule.onNodeWithText("Sıra").performClick()
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        repeat(3) { key(KeyEvent.KEYCODE_DPAD_DOWN) }
        rule.onNodeWithText("D").assertIsFocused()
        holdOk()
        rule.onNodeWithText("Sırayı değiştir").assertIsDisplayed()
        rule.onAllNodesWithText("Taşınıyor · D").assertCountEquals(0)
        assertEquals(listOf("a", "b", "c", "d"), queue.items.map { it.id })
    }

    @Test
    fun collectionPageTakesFocus() {
        val queue = PlaybackQueue()
        queue.addLast(clip("a", "Alfa"))
        rule.setContent {
            PlaylistPanel(
                videos = emptyList(),
                queue = queue.items,
                currentId = "a",
                collections = listOf(
                    Triple("p", "c1", "Gece"),
                    Triple("p", "c2", "Sabah"),
                ),
                onSelectList = {},
                onClose = {},
            )
        }
        rule.onNodeWithText("Sıra").performClick()
        rule.onNodeWithText("Alfa").performTouchInput { longClick() }
        rule.onNodeWithText("Koleksiyona ekle").performClick()
        rule.onNodeWithText("Gece").assertIsFocused()
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        rule.onNodeWithText("Sabah").assertIsFocused()
    }

    @Test
    fun changeOrderPlacesTheCardAndReturnsToTheSameOption() {
        val queue = PlaybackQueue()
        listOf("a", "b", "c", "d").map { clip(it, it.uppercase()) }.forEach { queue.addLast(it) }
        rule.setContent {
            PlaylistPanel(
                videos = emptyList(),
                queue = queue.items,
                currentId = "b",
                playingOnQueue = true,
                playingIndex = 1,
                onSelectList = {},
                onMoveQueue = { from, to -> queue.move(from, to) },
                onClose = {},
            )
        }
        rule.onNodeWithText("Sıra").performClick()
        rule.onNodeWithText("D").performTouchInput { longClick() }
        rule.onNodeWithText("Sırayı değiştir").performClick()
        rule.onNodeWithText("Taşınıyor · D").assertIsDisplayed()
        key(KeyEvent.KEYCODE_DPAD_UP)
        rule.onNodeWithText("Taşınıyor · D").assertIsDisplayed()
        key(KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals(listOf("a", "b", "d", "c"), queue.items.map { it.id })
        rule.onNodeWithText("Sırayı değiştir").assertIsFocused()
        back()
        rule.onAllNodesWithText("Taşınıyor · D").assertCountEquals(0)
        rule.onNodeWithText("D").assertIsDisplayed()
    }

    @Test
    fun backCancelsMoveModeWithoutReordering() {
        val queue = PlaybackQueue()
        listOf("a", "b", "c", "d").map { clip(it, it.uppercase()) }.forEach { queue.addLast(it) }
        rule.setContent {
            PlaylistPanel(
                videos = emptyList(),
                queue = queue.items,
                currentId = "a",
                onSelectList = {},
                onMoveQueue = { from, to -> queue.move(from, to) },
                onClose = {},
            )
        }
        rule.onNodeWithText("Sıra").performClick()
        rule.onNodeWithText("D").performTouchInput { longClick() }
        rule.onNodeWithText("Sırayı değiştir").performClick()
        key(KeyEvent.KEYCODE_DPAD_UP)
        back()
        rule.onNodeWithText("Sırayı değiştir").assertIsDisplayed()
        assertEquals(listOf("a", "b", "c", "d"), queue.items.map { it.id })
    }

    @Test
    fun closingTheMenuKeepsTheScrolledRowFocused() {
        val videos = (1..16).map { clip("v$it", "Klip %02d".format(it)) }
        rule.setContent {
            PlaylistPanel(
                videos = videos,
                currentId = "v1",
                onSelectList = {},
                onClose = {},
            )
        }
        rule.onNodeWithTag("panel-list").performScrollToIndex(15)
        rule.onNodeWithText("Klip 16").assertIsDisplayed()
        rule.onNodeWithText("Klip 16").performTouchInput { longClick() }
        rule.onNodeWithText("Sonra oynat").assertIsDisplayed()
        back()
        rule.onNodeWithText("Klip 16").assertIsDisplayed()
        rule.onNodeWithText("Klip 16").assertIsFocused()
    }

    @Test
    fun downFromTheRowAboveTheSeparatorReachesWatchlist() {
        rule.setContent {
            PlaylistPanel(
                videos = listOf(clip("a", "Alfa")),
                currentId = "a",
                onSelectList = {},
                onClose = {},
            )
        }
        rule.onNodeWithText("Alfa").performTouchInput { longClick() }
        rule.onNodeWithText("Sonra oynat").assertIsFocused()
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        rule.onNodeWithText("Sıraya ekle").assertIsFocused()
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        rule.onNodeWithText("İzleme listesine ekle").assertIsFocused()
    }

    private fun count(map: PixelMap, match: (Color) -> Boolean, inset: Int = 0, step: Int = 0): Int {
        var hits = 0
        val stepX = if (step > 0) step else ((map.width - inset * 2) / 32).coerceAtLeast(1)
        val stepY = if (step > 0) step else ((map.height - inset * 2) / 16).coerceAtLeast(1)
        var y = inset
        while (y < map.height - inset) {
            var x = inset
            while (x < map.width - inset) {
                if (match(map[x, y])) hits++
                x += stepX
            }
            y += stepY
        }
        return hits
    }

    private fun yellow(color: Color): Boolean =
        color.alpha > 0.8f && color.red > 0.85f && color.green > 0.65f && color.blue < 0.45f

    private fun white(color: Color): Boolean =
        color.alpha > 0.8f && color.red > 0.85f && color.green > 0.85f && color.blue > 0.85f

    private fun dark(color: Color): Boolean =
        color.alpha > 0.8f && color.red < 0.2f && color.green < 0.2f && color.blue < 0.2f

    private fun back() {
        rule.waitForIdle()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        rule.waitForIdle()
    }

    private fun holdOk() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        fun event(action: Int, repeat: Int, flags: Int = 0): KeyEvent {
            val now = android.os.SystemClock.uptimeMillis()
            return KeyEvent(now, now, action, KeyEvent.KEYCODE_DPAD_CENTER, repeat, 0, 0, 0, flags)
        }
        instrumentation.sendKeySync(event(KeyEvent.ACTION_DOWN, 0))
        rule.mainClock.advanceTimeBy(800)
        rule.waitForIdle()
        instrumentation.sendKeySync(event(KeyEvent.ACTION_DOWN, 2, KeyEvent.FLAG_LONG_PRESS))
        rule.waitForIdle()
        instrumentation.sendKeySync(event(KeyEvent.ACTION_UP, 0))
        rule.waitForIdle()
    }

    private fun key(code: Int) {
        rule.waitForIdle()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(code)
        rule.waitForIdle()
    }

    private fun clip(id: String, title: String) = LibraryVideo(
        id = id,
        title = title,
        uri = Uri.parse("file:///tmp/$id.mp4"),
        durationMs = 60_000L,
        format = "MP4",
        source = StorageSource.Internal,
        dateAdded = 1L,
        lastModified = 1L,
    )
}
