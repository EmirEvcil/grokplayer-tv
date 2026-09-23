package com.grokplayer.tv.data

import android.os.Environment
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SegmentFrameProbeTest {
    @Test
    fun downloadedSegmentsDecodeToDifferentFrames() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        val segments = root?.walkTopDown()
            ?.filter { it.isFile && it.name.startsWith("v-") && it.name.endsWith(".seg") }
            ?.sortedBy { it.name }
            ?.toList()
            .orEmpty()
        assumeTrue("no downloaded video segments on this device", segments.size >= 2)
        val playlist = segments.first().parentFile?.resolve("v.m3u8")
        assumeTrue(playlist?.isFile == true)
        val opening = MediaProbe.previewFrameAt(context, playlist!!, 0L, 160)
        val atFourEight = MediaProbe.previewFrameAt(context, playlist, 248_000L, 160)
        val atFourTen = MediaProbe.previewFrameAt(context, playlist, 250_000L, 160)
        assertNotNull(opening)
        assertNotNull(atFourEight)
        assertNotNull(atFourTen)
        assertTrue(opening!!.width >= 16)
        assertFalse(opening.sameAs(atFourTen))
        assertFalse(atFourEight!!.sameAs(atFourTen))
        assertTrue(playlist.exists())
        assertTrue(segments.first().exists())
    }
}
