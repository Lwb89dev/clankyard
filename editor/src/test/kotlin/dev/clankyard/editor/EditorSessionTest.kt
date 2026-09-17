package dev.clankyard.editor

import dev.clankyard.core.model.WorkspaceId
import dev.clankyard.core.model.WorkspacePath
import dev.clankyard.workspace.DiskFileBackedWorkspace
import dev.clankyard.workspace.WriteRequest
import dev.clankyard.workspace.WriteResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class EditorSessionTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun dirtyOnDiskHashConflictDoesNotOverwrite() = runBlocking {
        val ws = openWs()
        val path = WorkspacePath.parse("f.txt")
        ws.writeAtomic(WriteRequest(path, "v1".toByteArray(), null))
        val session = EditorSession(tmp.newFolder("drafts"), ws, this, draftDebounceMs = 0)
        val opened = session.open(path)
        assertEquals("v1", opened!!.text)
        assertFalse(opened.dirty)
        session.edit(path, "v2")
        assertTrue(session.dirtyPaths().contains(path))
        File(ws.root, "f.txt").writeText("v3")
        val result = session.save(path)
        assertTrue(result is WriteResult.Conflict)
        assertEquals("v3", File(ws.root, "f.txt").readText())
        assertEquals("v2", session.document(path)!!.text)
        assertTrue(session.document(path)!!.dirty)
        assertEquals("expectedHash mismatch", session.document(path)!!.conflict)
    }

    @Test
    fun saveWritesWhenHashMatches() = runBlocking {
        val ws = openWs()
        val path = WorkspacePath.parse("f.txt")
        ws.writeAtomic(WriteRequest(path, "v1".toByteArray(), null))
        val session = EditorSession(tmp.newFolder("drafts"), ws, this, draftDebounceMs = 0)
        session.open(path)
        session.edit(path, "v2")
        val result = session.save(path)
        assertTrue(result is WriteResult.Applied)
        assertEquals("v2", File(ws.root, "f.txt").readText())
        assertFalse(session.document(path)!!.dirty)
        assertTrue(session.dirtyPaths().isEmpty())
    }

    @Test
    fun saveAsDoesNotClobberExisting() = runBlocking {
        val ws = openWs()
        ws.writeAtomic(WriteRequest(WorkspacePath.parse("a.txt"), "aaa".toByteArray(), null))
        ws.writeAtomic(WriteRequest(WorkspacePath.parse("b.txt"), "bbb".toByteArray(), null))
        val session = EditorSession(tmp.newFolder("drafts"), ws, this, draftDebounceMs = 0)
        session.open(WorkspacePath.parse("a.txt"))
        session.edit(WorkspacePath.parse("a.txt"), "changed")
        val result = session.saveAs(WorkspacePath.parse("a.txt"), WorkspacePath.parse("b.txt"))
        assertTrue(result is WriteResult.Conflict)
        assertEquals("bbb", File(ws.root, "b.txt").readText())
        assertEquals("aaa", File(ws.root, "a.txt").readText())
        val created = session.saveAs(WorkspacePath.parse("a.txt"), WorkspacePath.parse("c.txt"))
        assertTrue(created is WriteResult.Applied)
        assertEquals("changed", File(ws.root, "c.txt").readText())
        assertEquals("aaa", File(ws.root, "a.txt").readText())
        assertFalse(session.dirtyPaths().contains(WorkspacePath.parse("c.txt")))
    }

    @Test
    fun draftRestoresDirtyBuffer() = runBlocking {
        val ws = openWs()
        val drafts = tmp.newFolder("drafts")
        val path = WorkspacePath.parse("src/Main.kt")
        ws.writeAtomic(WriteRequest(path, "fun main() {}".toByteArray(), null))
        val first = EditorSession(drafts, ws, this, draftDebounceMs = 0)
        first.open(path)
        first.edit(path, "fun main() { TODO() }")
        first.closeSession()
        val second = EditorSession(drafts, ws, this, draftDebounceMs = 0)
        val opened = second.open(path)!!
        assertEquals("fun main() { TODO() }", opened.text)
        assertTrue(opened.dirty)
        assertTrue(second.dirtyPaths().contains(path))
    }

    @Test
    fun saveAllSavesEveryDirtyTab() = runBlocking {
        val ws = openWs()
        ws.writeAtomic(WriteRequest(WorkspacePath.parse("a.txt"), "a".toByteArray(), null))
        ws.writeAtomic(WriteRequest(WorkspacePath.parse("b.txt"), "b".toByteArray(), null))
        val session = EditorSession(tmp.newFolder("drafts"), ws, this, draftDebounceMs = 0)
        session.open(WorkspacePath.parse("a.txt"))
        session.open(WorkspacePath.parse("b.txt"))
        session.edit(WorkspacePath.parse("a.txt"), "A")
        session.edit(WorkspacePath.parse("b.txt"), "B")
        val results = session.saveAll()
        assertEquals(2, results.size)
        assertTrue(results.values.all { it is WriteResult.Applied })
        assertEquals("A", File(ws.root, "a.txt").readText())
        assertEquals("B", File(ws.root, "b.txt").readText())
        assertTrue(session.dirtyPaths().isEmpty())
    }

    @Test
    fun restoredDraftWithChangedDiskDoesNotOverwrite() = runBlocking {
        val ws = openWs()
        val drafts = tmp.newFolder("drafts")
        val path = WorkspacePath.parse("f.txt")
        ws.writeAtomic(WriteRequest(path, "v1".toByteArray(), null))
        val first = EditorSession(drafts, ws, this, draftDebounceMs = 0)
        first.open(path)
        first.edit(path, "v2")
        first.closeSession()
        File(ws.root, "f.txt").writeText("v3")
        val second = EditorSession(drafts, ws, this, draftDebounceMs = 0)
        val opened = second.open(path)!!
        assertEquals("v2", opened.text)
        assertTrue(opened.dirty)
        assertEquals("on-disk hash changed", opened.conflict)
        val result = second.save(path)
        assertTrue(result is WriteResult.Conflict)
        assertEquals("v3", File(ws.root, "f.txt").readText())
        assertEquals("v2", second.document(path)!!.text)
    }

    @Test
    fun bomIsStrippedAndSurfaced() = runBlocking {
        val ws = openWs()
        val path = WorkspacePath.parse("bom.txt")
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "hi".toByteArray()
        ws.writeAtomic(WriteRequest(path, bytes, null))
        val session = EditorSession(tmp.newFolder("drafts"), ws, this, draftDebounceMs = 0)
        val opened = session.open(path)!!
        assertEquals("hi", opened.text)
        assertFalse(opened.dirty)
        assertEquals(EditorSession.BOM_CONFLICT, opened.conflict)
    }

    private fun openWs(): DiskFileBackedWorkspace =
        DiskFileBackedWorkspace(
            WorkspaceId("ws"),
            "ws",
            tmp.newFolder("root"),
            tmp.newFolder("journal"),
        )
}
