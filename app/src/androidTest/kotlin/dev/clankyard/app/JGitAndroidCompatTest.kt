package dev.clankyard.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.clankyard.core.model.WorkspaceId
import dev.clankyard.core.model.WorkspacePath
import dev.clankyard.git.GitIdentity
import dev.clankyard.git.JGitRepository
import dev.clankyard.workspace.DiskFileBackedWorkspace
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * CLANK-014 — JGit Android compatibility suite.
 *
 * Run on **API 29 and API 36** against app-specific dirs (`filesDir`).
 * Public FUSE is not the default root. Catalog pin is provisional until both pass.
 */
@RunWith(AndroidJUnit4::class)
class JGitAndroidCompatTest {
    @Test
    fun initStatusCommitDiffOnAppFilesDir() {
        val filesDir = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
        val root = File(filesDir, "clank-014").apply { mkdirs() }
        val journal = File(filesDir, "clank-014-journal").apply { mkdirs() }
        val ws = DiskFileBackedWorkspace(WorkspaceId("clank-014"), "compat", root, journal)
        val git = JGitRepository()
        val identity = GitIdentity("Workshop User", "user@clankyard.dev")
        val handle = runBlocking { git.init(ws, identity) }
        File(ws.root, "a.txt").writeText("one\n")
        val path = WorkspacePath.parse("a.txt")
        runBlocking {
            handle.stage(listOf(path))
            handle.commit("init", identity)
            File(ws.root, "a.txt").writeText("one\ntwo\n")
            val status = handle.status()
            assertTrue(status.unstaged.contains(path))
            val diffs = handle.diff(path)
            assertTrue(diffs.any { it.unified.contains("+two") })
        }
        (handle as AutoCloseable).close()
    }
}