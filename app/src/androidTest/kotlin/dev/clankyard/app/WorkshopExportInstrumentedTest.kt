package dev.clankyard.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.clankyard.workspace.FileWorkspaceRegistry
import java.io.File
import java.util.zip.ZipFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * CLANK-034 — zip export + free-space gate on app-specific storage.
 * The SAF picker itself is a system activity; this covers the IO path.
 */
@RunWith(AndroidJUnit4::class)
class WorkshopExportInstrumentedTest {
    @Test
    fun zipRoundTripOnAppFilesDir() {
        val filesDir = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
        val registry = FileWorkspaceRegistry(
            workspacesDir = File(filesDir, "clank-034-ws"),
            journalRoot = File(filesDir, "clank-034-journal"),
        )
        val ws = registry.create("export")
        File(ws.root, "hello.txt").writeText("weld\n")
        registry.treeOps.requireFreeSpace(filesDir, 1024)
        val zipFile = File(filesDir, "clank-034.zip")
        zipFile.outputStream().use { registry.treeOps.zipTo(ws.id, it) }
        assertTrue(zipFile.length() > 0)
        ZipFile(zipFile).use { zip ->
            val entry = zip.getEntry("hello.txt")
            assertTrue(entry != null)
            zip.getInputStream(entry).bufferedReader().use { reader ->
                assertEquals("weld\n", reader.readText())
            }
        }
    }
}
