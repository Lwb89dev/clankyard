package dev.clankyard.build.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BuildResourceGatesTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun reportsFreeSpaceAgainstMinimum() {
        val dir = tmp.newFolder("env")
        assertTrue(BuildResourceGates.hasFreeSpace(dir, minBytes = 1))
        assertFalse(BuildResourceGates.hasFreeSpace(dir, minBytes = Long.MAX_VALUE))
    }
}
