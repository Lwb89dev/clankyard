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
            describeExportPlan(plan),
        )
        assertTrue(plan.overwritten > 0)
        assertFalse(TreeExportPlan(1, 0, 5).overwritten > 0)
    }
}
