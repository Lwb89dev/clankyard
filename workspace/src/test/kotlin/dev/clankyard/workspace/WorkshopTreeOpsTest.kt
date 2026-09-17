package dev.clankyard.workspace

import dev.clankyard.core.model.WorkspaceId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WorkshopTreeOpsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun requireFreeSpaceRefusesWhenUsableBelowUncompressedPlus64MiB() {
        val dest = tmp.root
        assertTrue(dest.usableSpace > 0)
        val ops = DiskWorkshopTreeOps { null }
        try {
            ops.requireFreeSpace(dest, dest.usableSpace)
            throw AssertionError("expected InsufficientSpaceException")
        } catch (e: InsufficientSpaceException) {
            assertTrue(e.required > e.usable)
            assertEquals(e.usable + FREE_SPACE_MARGIN_BYTES, e.required)
        }
    }

    @Test
    fun requireFreeSpaceAcceptsWhenMarginFits() {
        val dest = tmp.root
        if (dest.usableSpace < FREE_SPACE_MARGIN_BYTES) return
        DiskWorkshopTreeOps { null }.requireFreeSpace(dest, 0L)
    }

    @Test
    fun treeExportPlanCountsCreatedOverwrittenAndExtra() {
        val registry = FileWorkspaceRegistry(tmp.newFolder("workspaces"), tmp.newFolder("journal"))
        val ws = registry.create("demo")
        File(ws.root, "a.txt").writeText("new-a")
        File(ws.root, "sub").mkdirs()
        File(ws.root, "sub/b.txt").writeText("new-b")
        val dest = tmp.newFolder("dest")
        File(dest, "sub").mkdirs()
        File(dest, "sub/b.txt").writeText("old-b")
        File(dest, "extra.txt").writeText("leave-me")
        val plan = runBlocking { registry.treeOps.planExportToTree(ws.id, dest) }
        assertEquals(TreeExportPlan(created = 1, overwritten = 1, extraDest = 1), plan)
        val exported = registry.treeOps.exportToFileTree(ws.id, dest)
        assertEquals(plan, exported)
        assertEquals("new-a", File(dest, "a.txt").readText())
        assertEquals("new-b", File(dest, "sub/b.txt").readText())
        assertEquals("leave-me", File(dest, "extra.txt").readText())
    }

    @Test
    fun unknownWorkspaceId() {
        val ops = DiskWorkshopTreeOps { null }
        try {
            runBlocking { ops.planExportToTree(WorkspaceId("missing"), tmp.root) }
            throw AssertionError("expected error")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("unknown workspace"))
        }
    }
}
