package dev.clankyard.workspace

import dev.clankyard.core.model.WorkspaceId
import dev.clankyard.core.model.WorkspacePath
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class JournalCrashRecoveryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun crashAfterSnapshotLeavesDiskUnchanged() {
        val root = tmp.newFolder("root")
        val journal = tmp.newFolder("journal")
        File(root, "a.txt").writeText("old")
        val preHash = hashBytes("old".toByteArray())
        val crashing = open(root, journal, JournalCrashPoint.AFTER_SNAPSHOT)
        try {
            runBlocking {
                crashing.journal.apply(
                    listOf(JournalOp.Replace(WorkspacePath.parse("a.txt"), "new".toByteArray(), preHash)),
                )
            }
            throw AssertionError("expected simulated crash")
        } catch (e: SimulatedJournalCrash) {
            assertEquals(JournalCrashPoint.AFTER_SNAPSHOT, e.point)
        }
        assertEquals("old", File(root, "a.txt").readText())
        val recovered = open(root, journal)
        assertEquals("old", File(root, "a.txt").readText())
        assertEquals("old", runBlocking { recovered.readUtf8(WorkspacePath.parse("a.txt"), 1024) })
    }

    @Test
    fun crashAfterContentBeforeAppliedRollsBackOnOpen() {
        val root = tmp.newFolder("root")
        val journal = tmp.newFolder("journal")
        File(root, "a.txt").writeText("old")
        val preHash = hashBytes("old".toByteArray())
        val crashing = open(root, journal, JournalCrashPoint.AFTER_CONTENT_BEFORE_APPLIED)
        try {
            runBlocking {
                crashing.journal.apply(
                    listOf(JournalOp.Replace(WorkspacePath.parse("a.txt"), "new".toByteArray(), preHash)),
                )
            }
            throw AssertionError("expected simulated crash")
        } catch (e: SimulatedJournalCrash) {
            assertEquals(JournalCrashPoint.AFTER_CONTENT_BEFORE_APPLIED, e.point)
        }
        assertEquals("new", File(root, "a.txt").readText())
        val recovered = open(root, journal)
        assertEquals("old", File(root, "a.txt").readText())
        assertEquals("old", runBlocking { recovered.readUtf8(WorkspacePath.parse("a.txt"), 1024) })
    }

    @Test
    fun renameCrashAfterContentUnrenamesWithoutDuplicate() {
        val root = tmp.newFolder("root")
        val journal = tmp.newFolder("journal")
        File(root, "from.txt").writeText("body")
        val preHash = hashBytes("body".toByteArray())
        val crashing = open(root, journal, JournalCrashPoint.AFTER_CONTENT_BEFORE_APPLIED)
        try {
            runBlocking {
                crashing.journal.apply(
                    listOf(
                        JournalOp.Rename(
                            WorkspacePath.parse("from.txt"),
                            WorkspacePath.parse("to.txt"),
                            preHash,
                        ),
                    ),
                )
            }
            throw AssertionError("expected simulated crash")
        } catch (_: SimulatedJournalCrash) {
        }
        assertFalse(File(root, "from.txt").exists())
        assertTrue(File(root, "to.txt").isFile)
        open(root, journal)
        assertTrue(File(root, "from.txt").isFile)
        assertEquals("body", File(root, "from.txt").readText())
        assertFalse(File(root, "to.txt").exists())
    }

    @Test
    fun twoWritesThenUndo() {
        val ws = open(tmp.newFolder("root"), tmp.newFolder("journal"))
        val a = WorkspacePath.parse("a.txt")
        val b = WorkspacePath.parse("b.txt")
        val ha = (runBlocking { ws.writeAtomic(WriteRequest(a, "a".toByteArray(), null)) } as WriteResult.Applied).newHash
        val hb = (runBlocking { ws.writeAtomic(WriteRequest(b, "b".toByteArray(), null)) } as WriteResult.Applied).newHash
        val applied = runBlocking {
            ws.journal.apply(
                listOf(
                    JournalOp.Replace(a, "A".toByteArray(), ha),
                    JournalOp.Replace(b, "B".toByteArray(), hb),
                ),
            )
        } as JournalApplyResult.Applied
        assertEquals("A", File(ws.root, "a.txt").readText())
        assertEquals("B", File(ws.root, "b.txt").readText())
        assertEquals("Clanker change #${applied.changeNumber}", ws.journal.listChanges().single().label)
        val undone = runBlocking { ws.journal.undo(applied.changeNumber) }
        assertTrue(undone is JournalApplyResult.Applied)
        assertEquals("a", File(ws.root, "a.txt").readText())
        assertEquals("b", File(ws.root, "b.txt").readText())
        assertTrue(ws.journal.listChanges().isEmpty())
    }

    @Test
    fun twoFileCrashAfterFirstAppliedRollsBackBoth() {
        val root = tmp.newFolder("root")
        val journal = tmp.newFolder("journal")
        File(root, "a.txt").writeText("old-a")
        File(root, "b.txt").writeText("old-b")
        val ha = hashBytes("old-a".toByteArray())
        val hb = hashBytes("old-b".toByteArray())
        val crashing = open(root, journal, JournalCrashPoint.AFTER_FIRST_APPLIED_SECOND_PENDING)
        try {
            runBlocking {
                crashing.journal.apply(
                    listOf(
                        JournalOp.Replace(WorkspacePath.parse("a.txt"), "new-a".toByteArray(), ha),
                        JournalOp.Replace(WorkspacePath.parse("b.txt"), "new-b".toByteArray(), hb),
                    ),
                )
            }
            throw AssertionError("expected simulated crash")
        } catch (e: SimulatedJournalCrash) {
            assertEquals(JournalCrashPoint.AFTER_FIRST_APPLIED_SECOND_PENDING, e.point)
        }
        assertEquals("new-a", File(root, "a.txt").readText())
        assertEquals("old-b", File(root, "b.txt").readText())
        open(root, journal)
        assertEquals("old-a", File(root, "a.txt").readText())
        assertEquals("old-b", File(root, "b.txt").readText())
    }

    @Test
    fun renameWhenDestAppearsConcurrentlyLeavesDestIntact() {
        val root = tmp.newFolder("root")
        val journal = tmp.newFolder("journal")
        File(root, "from.txt").writeText("body")
        val dest = File(root, "to.txt")
        val ws = DiskFileBackedWorkspace.forTest(
            WorkspaceId("ws"),
            "ws",
            root,
            journal,
            beforePerform = { dest.writeText("concurrent") },
        )
        val result = runBlocking {
            ws.journal.apply(
                listOf(
                    JournalOp.Rename(
                        WorkspacePath.parse("from.txt"),
                        WorkspacePath.parse("to.txt"),
                        hashBytes("body".toByteArray()),
                    ),
                ),
            )
        }
        assertTrue(result is JournalApplyResult.Failed)
        assertEquals("body", File(root, "from.txt").readText())
        assertEquals("concurrent", dest.readText())
    }

    @Test
    fun secondFileFailureRollsBackFirstNeverPartial() {
        val ws = open(tmp.newFolder("root"), tmp.newFolder("journal"))
        val a = WorkspacePath.parse("a.txt")
        val b = WorkspacePath.parse("b.txt")
        val ha = (runBlocking { ws.writeAtomic(WriteRequest(a, "a".toByteArray(), null)) } as WriteResult.Applied).newHash
        runBlocking { ws.writeAtomic(WriteRequest(b, "b".toByteArray(), null)) }
        val result = runBlocking {
            ws.journal.apply(
                listOf(
                    JournalOp.Replace(a, "A".toByteArray(), ha),
                    JournalOp.Replace(b, "B".toByteArray(), hashBytes("wrong".toByteArray())),
                ),
            )
        }
        assertTrue(result is JournalApplyResult.Failed)
        assertEquals("a", File(ws.root, "a.txt").readText())
        assertEquals("b", File(ws.root, "b.txt").readText())
    }

    private fun open(
        root: File,
        journal: File,
        crash: JournalCrashPoint? = null,
    ): DiskFileBackedWorkspace {
        return if (crash == null) {
            DiskFileBackedWorkspace(WorkspaceId("ws"), "ws", root, journal)
        } else {
            DiskFileBackedWorkspace.forTest(WorkspaceId("ws"), "ws", root, journal, crash)
        }
    }
}
