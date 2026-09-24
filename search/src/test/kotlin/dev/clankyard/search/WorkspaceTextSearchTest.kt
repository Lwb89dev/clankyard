package dev.clankyard.search

import dev.clankyard.core.model.WorkspaceId
import dev.clankyard.core.model.WorkspacePath
import dev.clankyard.workspace.DiskFileBackedWorkspace
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WorkspaceTextSearchTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val search = WorkspaceTextSearch()

    @Test
    fun matchesLiteralAcrossFiles() = runBlocking {
        val ws = openWs()
        write(ws, "src/a.kt", "fun alpha() = 1\n")
        write(ws, "src/b.kt", "fun beta() = 2\n")
        val hits = search.search(ws, SearchQuery("alpha"))
        assertEquals(1, hits.size)
        assertEquals("src/a.kt", hits.single().path.relative)
        assertEquals(1, hits.single().line)
        assertTrue(hits.single().preview.contains("alpha"))
    }

    @Test
    fun skipsBinaryFiles() = runBlocking {
        val ws = openWs()
        write(ws, "ok.txt", "needle in hay")
        val binary = File(ws.root, "blob.bin")
        binary.writeBytes(byteArrayOf(1, 2, 0, 3) + "needle".toByteArray())
        val hits = search.search(ws, SearchQuery("needle"))
        assertEquals(listOf("ok.txt"), hits.map { it.path.relative })
    }

    @Test
    fun skipsOversizeFiles() = runBlocking {
        val ws = openWs()
        write(ws, "small.txt", "needle")
        write(ws, "big.txt", "xxneedlexx")
        val hits = search.search(ws, SearchQuery("needle", maxFileBytes = 6))
        assertEquals(listOf("small.txt"), hits.map { it.path.relative })
    }

    @Test
    fun pathRestrictsToSubtree() = runBlocking {
        val ws = openWs()
        write(ws, "src/a.kt", "hit")
        write(ws, "other/a.kt", "hit")
        val hits = search.search(ws, SearchQuery("hit", path = WorkspacePath.parse("src")))
        assertEquals(listOf("src/a.kt"), hits.map { it.path.relative })
    }

    private fun write(ws: DiskFileBackedWorkspace, relative: String, content: String) {
        val dest = File(ws.root, relative)
        dest.parentFile?.mkdirs()
        dest.writeText(content)
    }

    private fun openWs(): DiskFileBackedWorkspace =
        DiskFileBackedWorkspace(
            WorkspaceId("ws"),
            "ws",
            tmp.newFolder("root"),
            tmp.newFolder("journal"),
        )
}
