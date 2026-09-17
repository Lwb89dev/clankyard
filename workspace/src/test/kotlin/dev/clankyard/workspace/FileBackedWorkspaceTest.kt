package dev.clankyard.workspace

import dev.clankyard.core.model.WorkspaceId
import dev.clankyard.core.model.WorkspacePath
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class FileBackedWorkspaceTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun rootListIsLegal() {
        val ws = openWs()
        val nested = File(ws.root, "dir").apply { mkdirs() }
        File(nested, "b.txt").writeText("b")
        File(ws.root, "a.txt").writeText("a")
        val listed = runBlocking { ws.list(WorkspacePath.ROOT) }
        assertEquals(listOf("a.txt", "dir"), listed.map { it.path.relative })
        assertFalse(listed[0].isDirectory)
        assertTrue(listed[1].isDirectory)
        assertNull(listed[1].hash)
        val inner = runBlocking { ws.list(WorkspacePath.parse("dir")) }
        assertEquals(listOf("dir/b.txt"), inner.map { it.path.relative })
    }

    @Test
    fun atomicWriteUsesSameDirTemp() {
        val dest = tmp.newFile("out.txt")
        val temp = newSameDirTemp(dest)
        assertEquals(dest.parentFile, temp.parentFile)
        assertTrue(temp.name.startsWith(".${dest.name}.tmp-"))
        temp.delete()

        val ws = openWs()
        val path = WorkspacePath.parse("same.txt")
        val result = runBlocking {
            ws.writeAtomic(WriteRequest(path, "hello".toByteArray(), expectedHash = null))
        }
        assertTrue(result is WriteResult.Applied)
        assertEquals("hello", File(ws.root, "same.txt").readText())
        val leftovers = ws.root.listFiles { _, name -> name.contains(".tmp-") } ?: emptyArray()
        assertEquals(0, leftovers.size)
    }

    @Test
    fun expectedHashMismatchIsConflict() {
        val ws = openWs()
        val path = WorkspacePath.parse("f.txt")
        val created = runBlocking {
            ws.writeAtomic(WriteRequest(path, "v1".toByteArray(), expectedHash = null))
        } as WriteResult.Applied
        val conflict = runBlocking {
            ws.writeAtomic(WriteRequest(path, "v2".toByteArray(), expectedHash = hashBytes("nope".toByteArray())))
        }
        assertTrue(conflict is WriteResult.Conflict)
        assertEquals(created.newHash, (conflict as WriteResult.Conflict).actualHash)
        assertEquals("v1", File(ws.root, "f.txt").readText())
        val replaced = runBlocking {
            ws.writeAtomic(WriteRequest(path, "v2".toByteArray(), expectedHash = created.newHash))
        }
        assertTrue(replaced is WriteResult.Applied)
        assertEquals("v2", File(ws.root, "f.txt").readText())
    }

    @Test
    fun createConflictsWhenFileExists() {
        val ws = openWs()
        val path = WorkspacePath.parse("f.txt")
        runBlocking { ws.writeAtomic(WriteRequest(path, "a".toByteArray(), null)) }
        val again = runBlocking { ws.writeAtomic(WriteRequest(path, "b".toByteArray(), null)) }
        assertTrue(again is WriteResult.Conflict)
        assertEquals("a", File(ws.root, "f.txt").readText())
    }

    @Test
    fun prefixSiblingIsNotContained() {
        val parent = tmp.newFolder("workspaces")
        val root = File(parent, "1").apply { mkdirs() }
        val evil = File(parent, "1-evil").apply { mkdirs() }
        val secret = File(evil, "pwn.txt").apply { writeText("x") }
        val ws = DiskFileBackedWorkspace(
            WorkspaceId("1"),
            "one",
            root,
            tmp.newFolder("journal-1"),
        )
        assertTrue(ws.containsCanonical(root))
        assertTrue(ws.containsCanonical(File(root, "nested")))
        assertFalse(ws.containsCanonical(evil))
        assertFalse(ws.containsCanonical(secret))
        assertFalse(secret.canonicalPath.startsWith(root.canonicalPath + File.separatorChar))
    }

    @Test
    fun symlinkOutIsRejectedIfOsAllows() {
        val ws = openWs()
        val outside = tmp.newFolder("outside")
        val secret = File(outside, "secret.txt").apply { writeText("nope") }
        val link = File(ws.root, "escape")
        val created = try {
            Files.createSymbolicLink(link.toPath(), secret.toPath())
            true
        } catch (_: Exception) {
            false
        }
        Assume.assumeTrue("symlinks not permitted on this OS/FS", created)
        assertFalse(ws.containsCanonical(link))
        val write = runBlocking {
            ws.writeAtomic(WriteRequest(WorkspacePath.parse("escape"), "x".toByteArray(), null))
        }
        assertTrue(write is WriteResult.Rejected)
        val listed = runBlocking { ws.list(WorkspacePath.ROOT) }
        assertTrue(listed.none { it.path.relative == "escape" })
    }

    @Test
    fun createRenameAndUserDelete() {
        val ws = openWs()
        val src = WorkspacePath.parse("src/Main.kt")
        val created = runBlocking {
            ws.writeAtomic(WriteRequest(src, "fun main() {}".toByteArray(), null))
        } as WriteResult.Applied
        assertTrue(File(ws.root, "src/Main.kt").isFile)
        val dest = WorkspacePath.parse("src/App.kt")
        val renamed = runBlocking { ws.rename(src, dest, created.newHash) }
        assertTrue(renamed is WriteResult.Applied)
        assertFalse(File(ws.root, "src/Main.kt").exists())
        assertTrue(File(ws.root, "src/App.kt").isFile)
        val newHash = (renamed as WriteResult.Applied).newHash
        val deleted = runBlocking { ws.delete(dest, newHash) }
        assertTrue(deleted is WriteResult.Applied)
        assertFalse(File(ws.root, "src/App.kt").exists())
    }

    @Test
    fun deleteFileWithoutHashIsRejected() {
        val ws = openWs()
        val path = WorkspacePath.parse("x.txt")
        runBlocking { ws.writeAtomic(WriteRequest(path, "x".toByteArray(), null)) }
        val result = runBlocking { ws.delete(path, null) }
        assertTrue(result is WriteResult.Rejected)
        assertTrue(File(ws.root, "x.txt").isFile)
    }

    @Test
    fun nulInFirst8KiBFailsClosed() {
        val ws = openWs()
        val path = WorkspacePath.parse("bin.dat")
        val bytes = ByteArray(100) { 1 }.also { it[10] = 0 }
        runBlocking { ws.writeAtomic(WriteRequest(path, bytes, null)) }
        try {
            runBlocking { ws.readUtf8(path, 1024) }
            throw AssertionError("expected BinaryFileException")
        } catch (e: BinaryFileException) {
            assertEquals(path, e.path)
        }
    }

    @Test
    fun utf8BomThrowsDialogHook() {
        val ws = openWs()
        val path = WorkspacePath.parse("bom.txt")
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "hi".toByteArray()
        runBlocking { ws.writeAtomic(WriteRequest(path, bytes, null)) }
        try {
            runBlocking { ws.readUtf8(path, 1024) }
            throw AssertionError("expected Utf8BomDetectedException")
        } catch (e: Utf8BomDetectedException) {
            assertEquals("hi", e.strippedUtf8)
        }
    }

    @Test
    fun deleteDoesNotFollowSymlinkOut() {
        val ws = openWs()
        val outside = tmp.newFolder("outside")
        val secret = File(outside, "secret.txt").apply { writeText("keep") }
        val sub = File(ws.root, "sub").apply { mkdirs() }
        File(sub, "inside.txt").writeText("gone")
        val link = File(sub, "escape")
        val created = try {
            Files.createSymbolicLink(link.toPath(), outside.toPath())
            true
        } catch (_: Exception) {
            false
        }
        Assume.assumeTrue("symlinks not permitted on this OS/FS", created)
        val deleted = runBlocking { ws.delete(WorkspacePath.parse("sub"), null) }
        assertTrue(deleted is WriteResult.Applied)
        assertFalse(sub.exists())
        assertTrue(secret.isFile)
        assertEquals("keep", secret.readText())
    }

    @Test
    fun listSkipsAtomicTempsAndOpenDeletesOrphans() {
        val root = tmp.newFolder("root")
        val journal = tmp.newFolder("journal")
        File(root, "keep.txt").writeText("ok")
        val orphan = File(root, ".keep.txt.tmp-orphan").apply { writeText("tmp") }
        val ws = DiskFileBackedWorkspace(WorkspaceId("ws"), "ws", root, journal)
        assertFalse(orphan.exists())
        File(ws.root, ".keep.txt.tmp-live").writeText("skip")
        val listed = runBlocking { ws.list(WorkspacePath.ROOT) }
        assertEquals(listOf("keep.txt"), listed.map { it.path.relative })
    }

    @Test
    fun metadataRootIsDirectory() {
        val ws = openWs()
        val meta = runBlocking { ws.metadata(WorkspacePath.ROOT) }
        assertNotNull(meta)
        assertTrue(meta!!.isDirectory)
        assertTrue(meta.path.isRoot)
    }

    private fun openWs(): DiskFileBackedWorkspace {
        return DiskFileBackedWorkspace(
            WorkspaceId("ws"),
            "ws",
            tmp.newFolder("root"),
            tmp.newFolder("journal"),
        )
    }
}
