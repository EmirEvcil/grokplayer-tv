package com.grokplayer.tv.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupCreateTest {
    @Test
    fun createsNamedCompressedBackup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = BackupStore(context)
        val created = store.create("Manuel yedek", "0.2.70", 72)
        val manifest = BackupArchive.readManifest(created.file)
        assertEquals("Manuel yedek", manifest.name)
        assertTrue(created.bytes > 32L)
        assertTrue(created.file.parentFile?.absolutePath?.contains("GrokPlayer") == true)
        assertTrue(store.list().any { it.manifest.name == "Manuel yedek" })
    }
}
