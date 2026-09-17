package dev.clankyard.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileWorkspaceRegistryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun unpublishedNotInList() {
        val registry = registry()
        val ws = registry.prepareCopy("draft")
        File(ws.root, "a.txt").writeText("x")
        assertTrue(ws.root.exists())
        assertTrue(registry.list().none { it.id == ws.id })
        assertTrue(ws.root.exists())
    }

    @Test
    fun discardRemovesStagingAndJournal() {
        val workspaces = tmp.newFolder("workspaces")
        val journals = tmp.newFolder("journal")
        val registry = FileWorkspaceRegistry(workspaces, journals)
        val ws = registry.prepareCopy("draft")
        val staging = ws.root
        val journal = File(journals, ws.id.value)
        assertTrue(staging.isDirectory)
        assertTrue(journal.isDirectory)
        registry.discardUnpublished(ws)
        assertFalse(staging.exists())
        assertFalse(journal.exists())
        assertTrue(registry.list().isEmpty())
    }

    @Test
    fun publishIsFirstRegistryRow() {
        val registry = registry()
        val ws = registry.prepareCopy("demo")
        File(ws.root, "a.txt").writeText("ok")
        val published = registry.publish(ws)
        val listed = registry.list()
        assertEquals(1, listed.size)
        assertEquals(published.id, listed.first().id)
        assertEquals("demo", listed.first().displayName)
        assertEquals(published.root.canonicalFile, listed.first().root.canonicalFile)
        assertTrue(File(published.root, "a.txt").isFile)
    }

    @Test
    fun constructReapsLeftoverStagingAndJournal() {
        val workspaces = tmp.newFolder("workspaces")
        val journals = tmp.newFolder("journal")
        val first = FileWorkspaceRegistry(workspaces, journals)
        val ws = first.prepareCopy("abandoned")
        File(ws.root, "leak.txt").writeText("x")
        val staging = ws.root
        val journal = File(journals, ws.id.value)
        assertTrue(staging.isDirectory)
        val second = FileWorkspaceRegistry(workspaces, journals)
        assertFalse(staging.exists())
        assertFalse(journal.exists())
        assertTrue(second.list().isEmpty())
    }

    @Test
    fun constructReapsUnregisteredTreeAfterRename() {
        val workspaces = tmp.newFolder("workspaces")
        val journals = tmp.newFolder("journal")
        val first = FileWorkspaceRegistry(workspaces, journals)
        val ws = first.prepareCopy("half")
        File(ws.root, "a.txt").writeText("x")
        val dest = File(workspaces, ws.id.value)
        assertTrue(ws.root.renameTo(dest))
        val journal = File(journals, ws.id.value)
        val second = FileWorkspaceRegistry(workspaces, journals)
        assertFalse(dest.exists())
        assertFalse(journal.exists())
        assertTrue(second.list().isEmpty())
    }

    private fun registry(): FileWorkspaceRegistry =
        FileWorkspaceRegistry(tmp.newFolder("workspaces"), tmp.newFolder("journal"))
}
