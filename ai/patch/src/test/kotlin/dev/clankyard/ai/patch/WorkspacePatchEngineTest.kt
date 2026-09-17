package dev.clankyard.ai.patch

import dev.clankyard.core.model.ContentHash
import dev.clankyard.core.model.PatchSetId
import dev.clankyard.core.model.WorkspaceId
import dev.clankyard.core.model.WorkspacePath
import dev.clankyard.diff.MyersDiffEngine
import dev.clankyard.workspace.DiskFileBackedWorkspace
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class WorkspacePatchEngineTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun replaceCreateRename() {
        val env = open()
        val replacePath = path("src/a.txt")
        File(env.root, "src").mkdirs()
        File(env.root, "src/a.txt").writeText("old\n")
        val replaceHash = env.hash(replacePath)
        val set = env.validate(
            ProposedEdit.parse("src/a.txt", EditKind.Replace, replaceHash, "new\n"),
            ProposedEdit.parse("src/b.txt", EditKind.Create, null, "created\n"),
            ProposedEdit.parse("src/a.txt", EditKind.Rename, replaceHash, renameTo = "src/renamed.txt"),
        )
        assertEquals(3, set.diffs.size)
        assertTrue(set.diffs[0].unified.contains("-old"))
        assertTrue(set.diffs[0].unified.contains("+new"))
        assertFalse(set.diffs[0].conflict)
        assertFalse(set.diffs[1].conflict)
        assertTrue(set.diffs[2].conflict)
        assertEquals("duplicate path", set.diffs[2].conflictReason)

        val createdOnly = env.validate(
            ProposedEdit.parse("src/b.txt", EditKind.Create, null, "created\n"),
        )
        val created = env.apply(createdOnly.id, setOf(path("src/b.txt")))
        assertTrue(created is ApplyResult.Applied)
        assertEquals("created\n", File(env.root, "src/b.txt").readText())

        val replaced = env.validate(
            ProposedEdit.parse("src/a.txt", EditKind.Replace, env.hash(replacePath), "new\n"),
        )
        assertTrue(env.apply(replaced.id, setOf(replacePath)) is ApplyResult.Applied)
        assertEquals("new\n", File(env.root, "src/a.txt").readText())

        val renamed = env.validate(
            ProposedEdit.parse("src/a.txt", EditKind.Rename, env.hash(replacePath), renameTo = "src/moved.txt"),
        )
        assertTrue(env.apply(renamed.id, setOf(replacePath)) is ApplyResult.Applied)
        assertFalse(File(env.root, "src/a.txt").exists())
        assertEquals("new\n", File(env.root, "src/moved.txt").readText())
    }

    @Test
    fun unifiedWithoutAfterUtf8IsRejected() {
        val env = open()
        File(env.root, "a.txt").writeText("old\n")
        val unified = "--- a/a.txt\n+++ b/a.txt\n@@ -1 +1 @@\n-old\n+new\n"
        val set = env.validate(
            ProposedEdit.parse(
                path = "a.txt",
                kind = EditKind.Replace,
                expectedHash = env.hash(path("a.txt")),
                afterUtf8 = null,
                unifiedDiff = unified,
            ),
        )
        val diff = set.diffs.single()
        assertTrue(diff.conflict)
        assertTrue(diff.conflictReason!!.contains("GNU-patch"))
        val result = env.apply(set.id, setOf(path("a.txt")))
        assertTrue(result is ApplyResult.Failed)
        assertEquals("old\n", File(env.root, "a.txt").readText())
    }

    @Test
    fun pathParseRejectsEscape() {
        assertThrows(IllegalArgumentException::class.java) {
            ProposedEdit.parse("../secret.txt", EditKind.Create, null, afterUtf8 = "x")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProposedEdit.parse("ok.txt", EditKind.Rename, ContentHash("ab"), renameTo = "../out.txt")
        }
    }

    @Test
    fun symlinkEscapeIsConflictAndOmitted() {
        val env = open()
        val outside = tmp.newFolder("outside")
        val secret = File(outside, "secret.txt").apply { writeText("nope") }
        val link = File(env.root, "escape")
        val created = try {
            Files.createSymbolicLink(link.toPath(), secret.toPath())
            true
        } catch (_: Exception) {
            false
        }
        Assume.assumeTrue("symlinks not permitted on this OS/FS", created)
        assertFalse(env.ws.containsCanonical(link))
        val set = env.validate(
            ProposedEdit.parse("escape", EditKind.Replace, ContentHash("00"), "pwned"),
        )
        assertTrue(set.diffs.single().conflict)
        assertEquals("path escapes workspace", set.diffs.single().conflictReason)
        val result = env.apply(set.id, setOf(path("escape")))
        assertTrue(result is ApplyResult.Failed)
        assertEquals("nope", secret.readText())
    }

    @Test
    fun hashMismatchOmittedFromApplySet() {
        val env = open()
        File(env.root, "ok.txt").writeText("ok\n")
        File(env.root, "stale.txt").writeText("stale\n")
        val ok = path("ok.txt")
        val stale = path("stale.txt")
        val set = env.validate(
            ProposedEdit.parse("ok.txt", EditKind.Replace, env.hash(ok), "OK\n"),
            ProposedEdit.parse("stale.txt", EditKind.Replace, ContentHash("deadbeef"), "STALE\n"),
        )
        assertFalse(set.diffs[0].conflict)
        assertTrue(set.diffs[1].conflict)
        assertEquals("expectedHash mismatch", set.diffs[1].conflictReason)
        val result = env.apply(set.id, setOf(ok, stale))
        assertTrue(result is ApplyResult.Applied)
        assertEquals(listOf(ok), (result as ApplyResult.Applied).files)
        assertEquals("OK\n", File(env.root, "ok.txt").readText())
        assertEquals("stale\n", File(env.root, "stale.txt").readText())
    }

    @Test
    fun subsetIsAllOrNothing() {
        val env = open()
        File(env.root, "a.txt").writeText("a0")
        File(env.root, "b.txt").writeText("b0")
        File(env.root, "c.txt").writeText("c0")
        val a = path("a.txt")
        val b = path("b.txt")
        val c = path("c.txt")
        val set = env.validate(
            ProposedEdit.parse("a.txt", EditKind.Replace, env.hash(a), "A"),
            ProposedEdit.parse("b.txt", EditKind.Replace, env.hash(b), "B"),
            ProposedEdit.parse("c.txt", EditKind.Replace, env.hash(c), "C"),
        )
        val applied = env.apply(set.id, setOf(a, b))
        assertTrue(applied is ApplyResult.Applied)
        assertEquals("A", File(env.root, "a.txt").readText())
        assertEquals("B", File(env.root, "b.txt").readText())
        assertEquals("c0", File(env.root, "c.txt").readText())
    }

    @Test
    fun dirtyBufferRefusesWholeApply() {
        val env = open()
        File(env.root, "a.txt").writeText("a0")
        File(env.root, "b.txt").writeText("b0")
        val a = path("a.txt")
        val b = path("b.txt")
        val set = env.validate(
            ProposedEdit.parse("a.txt", EditKind.Replace, env.hash(a), "A"),
            ProposedEdit.parse("b.txt", EditKind.Replace, env.hash(b), "B"),
        )
        val refused = env.apply(set.id, setOf(a, b), dirty = setOf(b))
        assertTrue(refused is ApplyResult.Failed)
        assertTrue((refused as ApplyResult.Failed).reason.contains("dirty"))
        assertEquals("a0", File(env.root, "a.txt").readText())
        assertEquals("b0", File(env.root, "b.txt").readText())
        val later = env.apply(set.id, setOf(a, b), dirty = emptySet())
        assertTrue(later is ApplyResult.Applied)
        assertEquals("A", File(env.root, "a.txt").readText())
        assertEquals("B", File(env.root, "b.txt").readText())
    }

    @Test
    fun rollbackOnSecondFileFailureNeverPartial() {
        val env = open()
        File(env.root, "a.txt").writeText("a0")
        File(env.root, "b.txt").writeText("b0")
        val a = path("a.txt")
        val b = path("b.txt")
        val set = env.validate(
            ProposedEdit.parse("a.txt", EditKind.Replace, env.hash(a), "A"),
            ProposedEdit.parse("b.txt", EditKind.Replace, env.hash(b), "B"),
        )
        File(env.root, "b.txt").writeText("b-changed")
        val result = env.apply(set.id, setOf(a, b))
        assertTrue(result is ApplyResult.Failed)
        assertEquals("a0", File(env.root, "a.txt").readText())
        assertEquals("b-changed", File(env.root, "b.txt").readText())
    }

    @Test
    fun undoRestoresSnapshots() {
        val env = open()
        File(env.root, "a.txt").writeText("before")
        val a = path("a.txt")
        val set = env.validate(
            ProposedEdit.parse("a.txt", EditKind.Replace, env.hash(a), "after"),
        )
        assertTrue(set.label.startsWith("Clanker change #"))
        val applied = env.apply(set.id, setOf(a)) as ApplyResult.Applied
        assertEquals("after", File(env.root, "a.txt").readText())
        val undone = runBlocking { env.engine.undo(applied.id) }
        assertTrue(undone is ApplyResult.Applied)
        assertEquals("before", File(env.root, "a.txt").readText())
        assertTrue(env.ws.journal.listChanges().isEmpty())
    }

    @Test
    fun renameWalUndoUnrenamesWithoutDuplicate() {
        val env = open()
        File(env.root, "from.txt").writeText("body")
        val from = path("from.txt")
        val to = path("to.txt")
        val set = env.validate(
            ProposedEdit.parse("from.txt", EditKind.Rename, env.hash(from), renameTo = "to.txt"),
        )
        assertFalse(set.diffs.single().conflict)
        assertTrue(set.diffs.single().unified.contains("from.txt"))
        assertTrue(set.diffs.single().unified.contains("to.txt"))
        val applied = env.apply(set.id, setOf(from))
        assertTrue(applied is ApplyResult.Applied)
        assertFalse(File(env.root, "from.txt").exists())
        assertEquals("body", File(env.root, "to.txt").readText())
        val undone = runBlocking { env.engine.undo(set.id) }
        assertTrue(undone is ApplyResult.Applied)
        assertTrue(File(env.root, "from.txt").isFile)
        assertEquals("body", File(env.root, "from.txt").readText())
        assertFalse(File(env.root, "to.txt").exists())
    }

    @Test
    fun rejectDropsPendingSet() {
        val env = open()
        File(env.root, "a.txt").writeText("a")
        val a = path("a.txt")
        val set = env.validate(ProposedEdit.parse("a.txt", EditKind.Replace, env.hash(a), "A"))
        runBlocking { env.engine.reject(set.id) }
        val result = env.apply(set.id, setOf(a))
        assertTrue(result is ApplyResult.Failed)
        assertEquals("a", File(env.root, "a.txt").readText())
    }

    @Test
    fun unknownPatchSetFails() {
        val env = open()
        val result = env.apply(PatchSetId("missing"), emptySet())
        assertTrue(result is ApplyResult.Failed)
    }

    @Test
    fun factoryCreatesEngine() {
        val env = open()
        File(env.root, "f.txt").writeText("x")
        val factory = PatchEngineFactory { WorkspacePatchEngine(it, MyersDiffEngine()) }
        val engine = factory.create(env.ws)
        val set = runBlocking {
            engine.validateAndDiff(
                listOf(ProposedEdit.parse("f.txt", EditKind.Replace, env.hash(path("f.txt")), "y")),
            )
        }
        assertNotNull(set.id)
        assertEquals(1, set.diffs.size)
        assertFalse(set.diffs.single().conflict)
    }

    private fun open(): Env {
        val root = tmp.newFolder("root")
        val journal = tmp.newFolder("journal")
        val ws = DiskFileBackedWorkspace(WorkspaceId("ws"), "ws", root, journal)
        val engine = WorkspacePatchEngine(ws, MyersDiffEngine())
        return Env(ws, engine, ApplyPatchUseCase(engine), root)
    }

    private fun path(raw: String): WorkspacePath = WorkspacePath.parse(raw)

    private class Env(
        val ws: DiskFileBackedWorkspace,
        val engine: PatchEngine,
        val applyUseCase: ApplyPatchUseCase,
        val root: File,
    ) {
        fun hash(path: WorkspacePath): ContentHash =
            runBlocking { ws.metadata(path) }!!.hash!!

        fun validate(vararg edits: ProposedEdit): PatchSet =
            runBlocking { engine.validateAndDiff(edits.toList()) }

        fun apply(
            id: PatchSetId,
            accepted: Set<WorkspacePath>,
            dirty: Set<WorkspacePath> = emptySet(),
        ): ApplyResult = runBlocking { applyUseCase(id, accepted, dirty) }
    }
}
