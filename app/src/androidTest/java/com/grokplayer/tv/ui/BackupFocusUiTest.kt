@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.grokplayer.tv.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.grokplayer.tv.data.BackupArchive
import com.grokplayer.tv.data.BackupStore
import com.grokplayer.tv.data.SharedRoots
import com.grokplayer.tv.ui.settings.BackupSection
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupFocusUiTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun edgesKeepFocusAndHeaderStaysVisible() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val backup = BackupStore(context).list().firstOrNull()
        val first = FocusRequester()
        val left = FocusRequester()
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                BackupSection(firstFocus = first, leftFocus = left, onEnterDetails = {})
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("Yedek oluştur").performSemanticsAction(SemanticsActions.RequestFocus)
        waitFocused("Yedek oluştur")
        rule.onNodeWithText("Yedek oluştur").performKeyInput { pressKey(Key.DirectionUp) }
        rule.onNodeWithText("Yedek oluştur").assertIsFocused()

        rule.onNodeWithText("Yedek oluştur").performClick()
        waitFocused("Kaydet")
        assertHeaderOnScreen("Yedeğin adı")
        rule.onNodeWithText("Kaydet").performKeyInput { pressKey(Key.DirectionDown) }
        waitFocused("Vazgeç")
        rule.onNodeWithText("Vazgeç").performKeyInput { pressKey(Key.DirectionDown) }
        rule.onNodeWithText("Vazgeç").assertIsFocused()
        rule.onNodeWithText("Vazgeç").performClick()
        waitFocused("Yedek oluştur")

        rule.onNodeWithText("Yedek oluştur").performClick()
        waitFocused("Kaydet")
        pressBack()
        waitFocused("Yedek oluştur")

        if (backup == null) return
        rule.onNodeWithText(backup.manifest.name, substring = false).performClick()
        rule.waitUntil(timeoutMillis = 4_000) {
            try {
                rule.onNodeWithText("Geri yüklemeyi incele", substring = false).assertExists()
                true
            } catch (_: AssertionError) {
                false
            }
        }
        rule.onNodeWithText("Geri yüklemeyi incele", substring = false).performSemanticsAction(SemanticsActions.RequestFocus)
        waitFocused("Geri yüklemeyi incele")
        assertHeaderOnScreen(backup.manifest.name)
        rule.onNodeWithText("Geri yüklemeyi incele").performKeyInput { pressKey(Key.DirectionLeft) }
        waitFocused("Sil")
        rule.onNodeWithText("Sil").performKeyInput { pressKey(Key.DirectionRight) }
        waitFocused("Geri yüklemeyi incele")
        pressBack()
        waitFocused(backup.manifest.name)

        rule.onNodeWithText(backup.manifest.name, substring = false).performClick()
        rule.waitUntil(timeoutMillis = 4_000) {
            try {
                rule.onNodeWithText("Geri yüklemeyi incele", substring = false).assertExists()
                true
            } catch (_: AssertionError) {
                false
            }
        }
        rule.onNodeWithText("Geri yüklemeyi incele", substring = false).performClick()
        waitFocused("Vazgeç")
        assertHeaderOnScreen("Geri yükleme")
        rule.onNodeWithText("Vazgeç").performKeyInput { pressKey(Key.DirectionRight) }
        waitFocused("Geri yükle")
        rule.onNodeWithText("Geri yükle").performKeyInput { pressKey(Key.DirectionLeft) }
        waitFocused("Vazgeç")
        pressBack()
        waitFocused("Geri yüklemeyi incele")
        pressBack()
        waitFocused(backup.manifest.name)
    }

    @Test
    fun mergeSelectionChecksWithOkAndBackKeepsTheLevel() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val stored = BackupStore(context).list()
        if (stored.size < 2) return
        fun clickBackup(index: Int) {
            val name = stored[index].manifest.name
            val occurrence = stored.take(index + 1).count { it.manifest.name == name } - 1
            rule.onAllNodesWithText(name, substring = false)[occurrence].performClick()
        }
        val left = FocusRequester()
        val start = FocusRequester()
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                BackupSection(firstFocus = start, leftFocus = left, onEnterDetails = {})
            }
        }
        rule.waitForIdle()
        rule.onNodeWithText("Birleştir", substring = false).performClick()
        rule.onNodeWithText("Seçmek için Tamam", substring = false).assertIsDisplayed()
        clickBackup(0)
        rule.onNodeWithText("1 yedek seçili", substring = false).assertIsDisplayed()
        clickBackup(1)
        rule.onNodeWithText("2 yedek seçili", substring = false).assertIsDisplayed()
        rule.onNodeWithText("Devam et", substring = false).performClick()
        rule.onNodeWithText("Birleştirme", substring = false).assertIsDisplayed()
        pressBack()
        waitFocused("Devam et")
        pressBack()
        waitFocused("Birleştir")
    }

    @Test
    fun longBackupListScrollsToTheLastRow() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(SharedRoots.backups(context), "backup-scroll-test.gpb")
        val names = JSONObject()
        repeat(40) { index -> names.put("c$index", "ScrollKol %02d".format(index + 1)) }
        val body = JSONObject()
            .put("names", names.toString())
            .put("homes", "{}")
            .put("users", "")
            .put("known", "")
            .put("excluded", "{}")
            .toString()
        BackupArchive.write(
            file,
            BackupArchive.Manifest("Scroll test yedek", System.currentTimeMillis(), "0.2.70", 72, emptyList()),
            listOf(BackupArchive.Section("video_collections", "Koleksiyonlar", body)),
        )
        try {
            val first = FocusRequester()
            val left = FocusRequester()
            rule.setContent {
                Box(Modifier.fillMaxSize()) {
                    BackupSection(firstFocus = first, leftFocus = left, onEnterDetails = {})
                }
            }
            rule.waitForIdle()
            rule.onNodeWithText("Yedek oluştur", substring = false).performSemanticsAction(SemanticsActions.RequestFocus)
            waitFocused("Yedek oluştur")
            for (step in 0 until 6) {
                if (isFocused("Scroll test yedek")) break
                rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
                rule.waitForIdle()
            }
            rule.onNodeWithText("Scroll test yedek", substring = false).performClick()
            rule.waitUntil(timeoutMillis = 4_000) {
                try {
                    rule.onNodeWithText("Koleksiyonlar", substring = false).assertExists()
                    true
                } catch (_: AssertionError) {
                    false
                }
            }
            rule.onNodeWithText("Koleksiyonlar", substring = false).performClick()
            rule.waitUntil(timeoutMillis = 4_000) {
                try {
                    rule.onNodeWithText("ScrollKol 01", substring = false).assertExists()
                    true
                } catch (_: AssertionError) {
                    false
                }
            }
            rule.onNodeWithText("ScrollKol 01", substring = false).performSemanticsAction(SemanticsActions.RequestFocus)
            waitFocused("ScrollKol 01")
            assertHeaderOnScreen("Koleksiyonlar")
            for (step in 0 until 80) {
                if (isFocused("ScrollKol 40")) break
                rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
                rule.waitForIdle()
            }
            rule.onNodeWithText("ScrollKol 40", substring = false).assertIsFocused()
            rule.onNodeWithText("ScrollKol 40", substring = false).assertIsDisplayed()
            assertHeaderOnScreen("Koleksiyonlar")
            pressBack()
            waitFocused("Koleksiyonlar")
            pressBack()
            waitFocused("Scroll test yedek")
        } finally {
            file.delete()
        }
    }

    private fun pressBack() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        rule.waitForIdle()
    }

    private fun isFocused(text: String): Boolean = try {
        rule.onNodeWithText(text, substring = false).assertIsFocused()
        true
    } catch (_: AssertionError) {
        false
    }

    private fun waitFocused(text: String) {
        rule.waitUntil(timeoutMillis = 4_000) {
            try {
                rule.onNodeWithText(text, substring = false).assertIsFocused()
                true
            } catch (_: AssertionError) {
                false
            }
        }
        rule.onNodeWithText(text, substring = false).assertIsFocused()
    }

    private fun assertHeaderOnScreen(text: String) {
        rule.onNodeWithText(text, substring = false).assertIsDisplayed()
        val top = rule.onNodeWithText(text, substring = false).getBoundsInRoot().top
        assertTrue("header scrolled off: $text top=$top", top >= 0.dp && top < 280.dp)
    }
}
