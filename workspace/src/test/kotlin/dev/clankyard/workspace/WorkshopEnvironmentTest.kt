package dev.clankyard.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class WorkshopEnvironmentTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun firstLaunchCreatesSandboxTree() {
        val files = tmp.newFolder("files")
        val env = WorkshopEnvironment.ensure(files)
        assertTrue(env.isDirectory)
        assertTrue(File(env, "README.txt").isFile)
        val ws = WorkshopEnvironment.workspacesDir(files)
        assertTrue(ws.isDirectory)
        assertTrue(WorkshopEnvironment.contains(env, ws))
        assertFalse(WorkshopEnvironment.contains(env, File(files, "credentials")))
    }

    @Test
    fun migratesLegacyWorkspaces() {
        val files = tmp.newFolder("files")
        val legacy = File(files, "workspaces").apply { mkdirs() }
        File(legacy, "old").mkdirs()
        val nested = WorkshopEnvironment.workspacesDir(files)
        assertTrue(File(nested, "old").isDirectory)
        assertEquals(File(files, "environment/workspaces").canonicalFile, nested.canonicalFile)
    }
}