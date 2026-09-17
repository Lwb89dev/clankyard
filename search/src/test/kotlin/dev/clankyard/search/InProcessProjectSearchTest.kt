package dev.clankyard.search

import dev.clankyard.core.model.WorkspaceId
import dev.clankyard.core.model.WorkspacePath
import dev.clankyard.workspace.DiskFileBackedWorkspace
import dev.clankyard.workspace.WriteRequest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class InProcessProjectSearchTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun matchesNestedFiles() {
        val ws = openWs()
        runBlocking {
            ws.writeAtomic(WriteRequest(WorkspacePath.parse("a.txt"), "hello clanker\n".toByteArray(), null))
            ws.writeAtomic(WriteRequest(WorkspacePath.parse("src/Main.kt"), "fun clanker() {}\n".toByteArray(), null))
            ws.writeAtomic(WriteRequest(WorkspacePath.parse("src/skip.txt"), "nope\n".toByteArray(), null))
        }
        val hits = runBlocking {
            InProcessProjectSearch().search(ws, SearchQuery("clanker"))
        }
        assertEquals(2, hits.size)
        assertEquals("a.txt", hits[0].path.relative)
        assertEquals(1, hits[0].lineNumber)
        assertEquals(6, hits[0].column)
        assertEquals("hello clanker", hits[0].lineText)
        assertEquals("src/Main.kt", hits[1].path.relative)
        assertEquals("fun clanker() {}", hits[1].lineText)
    }

    @Test
    fun caseSensitiveDoesNotMatchWrongCase() {
        val ws = openWs()
        runBlocking {
            ws.writeAtomic(WriteRequest(WorkspacePath.parse("a.txt"), "Clanker\n".toByteArray(), null))
        }
        val insensitive = runBlocking {
            InProcessProjectSearch().search(ws, SearchQuery("clanker", caseSensitive = false))
        }
        val sensitive = runBlocking {
            InProcessProjectSearch().search(ws, SearchQuery("clanker", caseSensitive = true))
        }
        assertEquals(1, insensitive.size)
        assertTrue(sensitive.isEmpty())
    }

    @Test
    fun skipsBinaryNulInFirst8KiB() {
        val ws = openWs()
        val binary = ByteArray(64) { 1 }.also { it[8] = 0 }
        runBlocking {
            ws.writeAtomic(WriteRequest(WorkspacePath.parse("a.bin"), binary, null))
            ws.writeAtomic(WriteRequest(WorkspacePath.parse("a.txt"), "needle\n".toByteArray(), null))
        }
        val hits = runBlocking {
            InProcessProjectSearch().search(ws, SearchQuery("needle"))
        }
        assertEquals(listOf("a.txt"), hits.map { it.path.relative })
    }

    @Test
    fun skipsOversizeFiles() {
        val ws = openWs()
        runBlocking {
            ws.writeAtomic(WriteRequest(WorkspacePath.parse("big.txt"), "needle in hay\n".toByteArray(), null))
            ws.writeAtomic(WriteRequest(WorkspacePath.parse("small.txt"), "needle\n".toByteArray(), null))
        }
        val hits = runBlocking {
            InProcessProjectSearch().search(ws, SearchQuery("needle", maxFileBytes = 8))
        }
        assertEquals(listOf("small.txt"), hits.map { it.path.relative })
    }

    @Test
    fun emptyPatternReturnsNothing() {
        val ws = openWs()
        runBlocking {
            ws.writeAtomic(WriteRequest(WorkspacePath.parse("a.txt"), "x\n".toByteArray(), null))
        }
        val hits = runBlocking { InProcessProjectSearch().search(ws, SearchQuery("")) }
        assertTrue(hits.isEmpty())
    }

    private fun openWs(): DiskFileBackedWorkspace =
        DiskFileBackedWorkspace(
            WorkspaceId("ws"),
            "ws",
            tmp.newFolder("root"),
            tmp.newFolder("journal"),
        )
}
