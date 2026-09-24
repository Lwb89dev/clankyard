package dev.clankyard.build.api

import org.junit.Assert.assertEquals
import org.junit.Test

class BuildApiTest {
    @Test
    fun proposedBuildIsTaskOnly() {
        val proposed = ProposedBuild(BuildTask.AssembleDebug)
        assertEquals(BuildTask.AssembleDebug, proposed.task)
    }

    @Test
    fun rejectedCarriesStatus() {
        val rejected = BuildStartResult.Rejected(BuildStatus.Untrusted, "not trusted")
        assertEquals(BuildStatus.Untrusted, rejected.status)
    }
}
