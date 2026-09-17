package dev.clankyard.feature.workspacepicker

import dev.clankyard.workspace.TreeExportPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportPlanCopyTest {
    @Test
    fun describeMatchesSheetCopy() {
        val plan = TreeExportPlan(created = 3, overwritten = 2, extraDest = 4)
        assertEquals(
            "3 files will be created, 2 overwritten, 4 extra dest files left untouched.",
            ExportPlanCopy.describe(plan),
        )
        assertTrue(ExportPlanCopy.requiresOverwriteConfirm(plan))
        assertFalse(ExportPlanCopy.requiresOverwriteConfirm(TreeExportPlan(1, 0, 5)))
    }

    @Test
    fun settingsPathIsNotUserVisible() {
        val note = WorkshopLocationCopy.settingsPath("/data/user/0/dev.clankyard.app/files/workspaces/abc")
        assertTrue(note.contains("app-specific"))
        assertTrue(note.contains("Files-by-Google"))
        assertTrue(note.contains("MTP"))
    }
}
