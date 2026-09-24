package dev.clankyard.feature.explorer

import dev.clankyard.core.model.WorkspaceId
import dev.clankyard.core.model.WorkspacePath
import dev.clankyard.workspace.DiskFileBackedWorkspace
import dev.clankyard.workspace.WriteRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ExplorerViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun confirmDeleteCopyNamesFolderContents() {
        val file = WorkspacePath.parse("a.txt")
        val dir = WorkspacePath.parse("src")
        assertEquals(
            "Delete a.txt? This cannot be undone from the explorer.",
            confirmDeleteCopy(file, isDirectory = false),
        )
        assertEquals(
            "Delete src and all contents? This cannot be undone from the explorer.",
            confirmDeleteCopy(dir, isDirectory = true),
        )
    }

    @Test
    fun listShowsRootChildren() = runBlocking {
        val ws = openWs()
        seed(ws)
        val vm = ExplorerViewModel(ws, this)
        vm.handle(ExplorerUiEvent.Refresh)
        assertEquals(listOf("a.txt", "src"), vm.state.value.rows.map { it.name })
        assertFalse(vm.state.value.rows.first { it.name == "src" }.expanded)
        assertEquals(0, vm.state.value.rows.count { it.path.relative == "src/Main.kt" })
    }

    @Test
    fun expandDirectoryShowsChildren() = runBlocking {
        val ws = openWs()
        seed(ws)
        val vm = ExplorerViewModel(ws, this)
        vm.handle(ExplorerUiEvent.Refresh)
        val src = WorkspacePath.parse("src")
        vm.handle(ExplorerUiEvent.Toggle(src))
        val names = vm.state.value.rows.map { it.name }
        assertEquals(listOf("a.txt", "src", "Main.kt"), names)
        assertTrue(vm.state.value.rows.first { it.path == src }.expanded)
        vm.handle(ExplorerUiEvent.Toggle(src))
        assertEquals(listOf("a.txt", "src"), vm.state.value.rows.map { it.name })
    }

    @Test
    fun newFileGoesThroughWriteAtomic() = runBlocking {
        val ws = openWs()
        val vm = ExplorerViewModel(ws, this)
        vm.handle(ExplorerUiEvent.Refresh)
        vm.handle(ExplorerUiEvent.RequestNewFile(WorkspacePath.ROOT))
        vm.handle(ExplorerUiEvent.SubmitName("hello.kt"))
        assertTrue(File(ws.root, "hello.kt").isFile)
        assertEquals(listOf("hello.kt"), vm.state.value.rows.map { it.name })
        assertNull(vm.state.value.namePrompt)
    }

    @Test
    fun newFolderUsesWriteAtomicParents() = runBlocking {
        val ws = openWs()
        val vm = ExplorerViewModel(ws, this)
        vm.handle(ExplorerUiEvent.Refresh)
        vm.handle(ExplorerUiEvent.RequestNewFolder(WorkspacePath.ROOT))
        vm.handle(ExplorerUiEvent.SubmitName("lib"))
        assertTrue(File(ws.root, "lib").isDirectory)
        assertFalse(File(ws.root, "lib/.clankyard-dir").exists())
        assertEquals(listOf("lib"), vm.state.value.rows.map { it.name })
        assertTrue(vm.state.value.rows.first().isDirectory)
    }

    @Test
    fun newFileDoesNotClobber() = runBlocking {
        val ws = openWs()
        seed(ws)
        val vm = ExplorerViewModel(ws, this)
        vm.handle(ExplorerUiEvent.Refresh)
        vm.handle(ExplorerUiEvent.RequestNewFile(WorkspacePath.ROOT))
        vm.handle(ExplorerUiEvent.SubmitName("a.txt"))
        assertEquals("root", File(ws.root, "a.txt").readText())
        assertEquals("file exists", vm.state.value.message)
        assertNotNull(vm.state.value.namePrompt)
    }

    @Test
    fun requestDeleteDoesNotDeleteUntilConfirm() = runBlocking {
        val ws = openWs()
        seed(ws)
        val vm = ExplorerViewModel(ws, this)
        vm.handle(ExplorerUiEvent.Refresh)
        val path = WorkspacePath.parse("a.txt")
        vm.handle(ExplorerUiEvent.RequestDelete(path))
        assertTrue(File(ws.root, "a.txt").isFile)
        assertNotNull(vm.state.value.pendingDelete)
        vm.handle(ExplorerUiEvent.DismissDelete)
        assertTrue(File(ws.root, "a.txt").isFile)
        assertNull(vm.state.value.pendingDelete)
        vm.handle(ExplorerUiEvent.ConfirmDelete(path))
        assertTrue(File(ws.root, "a.txt").isFile)
        assertEquals("delete requires confirm", vm.state.value.message)
    }

    @Test
    fun longPressContextMenuTargetsThePressedRow() = runBlocking {
        val ws = openWs()
        seed(ws)
        val vm = ExplorerViewModel(ws, this)
        vm.handle(ExplorerUiEvent.Refresh)
        val path = WorkspacePath.parse("a.txt")

        vm.handle(ExplorerUiEvent.ShowContextMenu(path))
        assertEquals(path, vm.state.value.contextMenuPath)
        assertEquals(path, vm.state.value.selected)

        vm.handle(ExplorerUiEvent.DismissContextMenu)
        assertNull(vm.state.value.contextMenuPath)
    }

    @Test
    fun confirmDeleteRemovesFile() = runBlocking {
        val ws = openWs()
        seed(ws)
        val vm = ExplorerViewModel(ws, this)
        vm.handle(ExplorerUiEvent.Refresh)
        val path = WorkspacePath.parse("a.txt")
        vm.handle(ExplorerUiEvent.RequestDelete(path))
        vm.handle(ExplorerUiEvent.ConfirmDelete(path))
        assertFalse(File(ws.root, "a.txt").exists())
        assertNull(vm.state.value.pendingDelete)
        assertEquals(listOf("src"), vm.state.value.rows.map { it.name })
    }

    @Test
    fun renameAndSaveAsUseExpectedHash() = runBlocking {
        val ws = openWs()
        seed(ws)
        val vm = ExplorerViewModel(ws, this)
        vm.handle(ExplorerUiEvent.Refresh)
        vm.handle(ExplorerUiEvent.RequestRename(WorkspacePath.parse("a.txt")))
        vm.handle(ExplorerUiEvent.SubmitName("b.txt"))
        assertFalse(File(ws.root, "a.txt").exists())
        assertEquals("root", File(ws.root, "b.txt").readText())
        vm.handle(ExplorerUiEvent.RequestSaveAs(WorkspacePath.parse("b.txt")))
        vm.handle(ExplorerUiEvent.SubmitName("copy.txt"))
        assertEquals("root", File(ws.root, "copy.txt").readText())
        vm.handle(ExplorerUiEvent.RequestSaveAs(WorkspacePath.parse("b.txt")))
        vm.handle(ExplorerUiEvent.SubmitName("copy.txt"))
        assertEquals("file exists", vm.state.value.message)
        assertEquals("root", File(ws.root, "copy.txt").readText())
    }

    private suspend fun seed(ws: DiskFileBackedWorkspace) {
        ws.writeAtomic(WriteRequest(WorkspacePath.parse("a.txt"), "root".toByteArray(), null))
        ws.writeAtomic(WriteRequest(WorkspacePath.parse("src/Main.kt"), "fun main() {}".toByteArray(), null))
    }

    private fun openWs(): DiskFileBackedWorkspace =
        DiskFileBackedWorkspace(
            WorkspaceId("ws"),
            "ws",
            tmp.newFolder("root"),
            tmp.newFolder("journal"),
        )
}
