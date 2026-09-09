package com.grokplayer.tv.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaProbeTest {
    @Test
    fun parseFpsReadsIntegerAndFraction() {
        assertEquals(24f, MediaProbe.parseFps("24"), 0.01f)
        assertEquals(29.97f, MediaProbe.parseFps("30000/1001"), 0.02f)
        assertEquals(0f, MediaProbe.parseFps(null), 0f)
        assertEquals(0f, MediaProbe.parseFps(""), 0f)
        assertEquals(0f, MediaProbe.parseFps("999"), 0f)
    }

    @Test
    fun fpsLabelDashWhenUnknown() {
        assertEquals("—", MediaDetails(1000L, 1920, 1080, 0f).fpsLabel)
        assertEquals("24 fps", MediaDetails(1000L, 1920, 1080, 24f).fpsLabel)
        assertEquals("29.97 fps", MediaDetails(1000L, 1920, 1080, 29.97f).fpsLabel)
    }

    @Test
    fun aviHeaderYieldsFpsAndResolution() {
        val bytes = aviHeader(microsPerFrame = 40_000L, frames = 250, width = 1280, height = 720)
        val details = MediaProbe.aviHeaderDetails(bytes)
        assertEquals(1280, details.width)
        assertEquals(720, details.height)
        assertEquals(25f, details.fps, 0.05f)
        assertEquals(10_000L, details.durationMs)
        assertEquals("25 fps", details.fpsLabel)
        assertEquals("1280×720", details.resolution)
    }

    private fun aviHeader(microsPerFrame: Long, frames: Long, width: Int, height: Int): ByteArray {
        val bytes = ByteArray(80)
        writeAscii(bytes, 0, "RIFF")
        writeAscii(bytes, 8, "AVI ")
        writeAscii(bytes, 12, "avih")
        writeU32(bytes, 20, microsPerFrame)
        writeU32(bytes, 36, frames)
        writeU32(bytes, 44, width.toLong())
        writeU32(bytes, 48, height.toLong())
        return bytes
    }

    private fun writeAscii(bytes: ByteArray, offset: Int, text: String) {
        text.forEachIndexed { i, ch -> bytes[offset + i] = ch.code.toByte() }
    }

    private fun writeU32(bytes: ByteArray, offset: Int, value: Long) {
        bytes[offset] = (value and 0xFF).toByte()
        bytes[offset + 1] = ((value shr 8) and 0xFF).toByte()
        bytes[offset + 2] = ((value shr 16) and 0xFF).toByte()
        bytes[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }
}
