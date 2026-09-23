package com.grokplayer.tv.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.grokplayer.tv.ui.player.SeekStrip
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreviewStripUiTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun stripShowsTheFrameForThatTimeOnly() {
        val open = ImageBitmap(8, 8)
        val ending = ImageBitmap(8, 8)
        rule.setContent {
            SeekStrip(
                times = listOf(0L, 10_000L, 20_000L),
                current = 20_000L,
                frames = mapOf(20_000L to ending, 0L to open),
            )
        }
        rule.onNodeWithTag("preview-image-20000").assertIsDisplayed()
        rule.onNodeWithTag("preview-image-0").assertIsDisplayed()
        rule.onAllNodesWithTag("preview-image-10000").assertCountEquals(0)
    }
}
