package dev.clankyard.git

import dev.clankyard.core.model.WorkspaceId
import dev.clankyard.core.model.WorkspacePath
import dev.clankyard.workspace.DiskFileBackedWorkspace
import kotlinx.coroutines.runBlocking
import org.eclipse.jgit.lib.ConfigConstants
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class JGitRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val git = JGitRepository()
    private val identity = GitIdentity("Workshop User", "user@clankyard.dev")

    @Test
    fun openReturnsNullWithoutRepo() {
        val ws = openWs()
        val handle = runBlocking { git.open(ws) }
        assertNull(handle)
    }

    @Test
    fun initCommitStatusDiffStageUnstage() {
        val ws = openWs()
        val handle = runBlocking { git.init(ws, identity) }
        val readme = File(ws.root, "README.md").apply { writeText("hello\n") }
        val path = WorkspacePath.parse("README.md")

        val beforeStage = runBlocking { handle.status() }
        assertEquals("main", beforeStage.branch)
        assertTrue(beforeStage.untracked.contains(path))
        assertTrue(beforeStage.staged.isEmpty())

        runBlocking { handle.stage(listOf(path)) }
        val staged = runBlocking { handle.status() }
        assertTrue(staged.staged.contains(path))
        assertTrue(staged.untracked.none { it == path })

        val stagedDiff = runBlocking { handle.diff(path) }
        assertTrue(stagedDiff.any { it.path == path && it.unified.contains("+hello") })

        runBlocking { handle.unstage(listOf(path)) }
        val unstaged = runBlocking { handle.status() }
        assertTrue(unstaged.untracked.contains(path) || unstaged.unstaged.contains(path))
        assertTrue(unstaged.staged.none { it == path })

        runBlocking { handle.stage(listOf(path)) }
        runBlocking { handle.commit("add readme", identity) }
        val clean = runBlocking { handle.status() }
        assertTrue(clean.staged.isEmpty())
        assertTrue(clean.unstaged.isEmpty())
        assertTrue(clean.untracked.none { it == path })

        readme.writeText("hello\nworld\n")
        val dirty = runBlocking { handle.status() }
        assertTrue(dirty.unstaged.contains(path))
        val workDiff = runBlocking { handle.diff(path) }
        assertTrue(workDiff.any { it.unified.contains("+world") })

        runBlocking { handle.stage(listOf(path)) }
        runBlocking { handle.commit("add world", identity) }
        val after = runBlocking { handle.status() }
        assertTrue(after.staged.isEmpty())
        assertTrue(after.unstaged.isEmpty())
    }

    @Test
    fun filemodeFalseIgnoresExecutableBit() {
        val ws = openWs()
        val handle = runBlocking { git.init(ws, identity) }
        val script = File(ws.root, "run.sh").apply { writeText("#!/bin/sh\necho hi\n") }
        val path = WorkspacePath.parse("run.sh")
        runBlocking {
            handle.stage(listOf(path))
            handle.commit("add script", identity)
        }
        assertTrue(script.setExecutable(true))
        val status = runBlocking { handle.status() }
        assertTrue(status.unstaged.isEmpty())
        assertTrue(status.staged.isEmpty())
        assertConfig(ws.root, ConfigConstants.CONFIG_KEY_FILEMODE, "false")
        assertConfig(ws.root, ConfigConstants.CONFIG_KEY_AUTOCRLF, "false")
    }

    @Test
    fun openReappliesForcedConfig() {
        val ws = openWs()
        (runBlocking { git.init(ws, identity) } as AutoCloseable).close()
        val reopened = runBlocking { git.open(ws) }
        assertNotNull(reopened)
        assertEquals("main", runBlocking { reopened!!.currentBranch() })
        assertConfig(ws.root, ConfigConstants.CONFIG_KEY_FILEMODE, "false")
        assertConfig(ws.root, ConfigConstants.CONFIG_KEY_AUTOCRLF, "false")
        (reopened as AutoCloseable).close()
    }

    @Test
    fun commitRequiresIdentityAndDoesNotAutoCommitOnStage() {
        val ws = openWs()
        val handle = runBlocking { git.init(ws, identity) }
        File(ws.root, "a.txt").writeText("a\n")
        val path = WorkspacePath.parse("a.txt")
        runBlocking { handle.stage(listOf(path)) }
        val before = logCount(ws.root)
        assertEquals(0, before)
        try {
            runBlocking { handle.commit("msg", GitIdentity("", "")) }
            throw AssertionError("expected GitIdentityRequiredException")
        } catch (e: GitIdentityRequiredException) {
            assertTrue(e.message!!.contains("required"))
        }
        assertEquals(0, logCount(ws.root))
        runBlocking { handle.commit("manual", identity) }
        assertEquals(1, logCount(ws.root))
    }

    @Test
    fun mvpApiHasNoClonePullPush() {
        val handleNames = GitHandle::class.java.methods.map { it.name }.toSet()
        val repoNames = GitRepository::class.java.methods.map { it.name }.toSet()
        for (forbidden in listOf("clone", "pull", "push")) {
            assertFalse(handleNames.contains(forbidden))
            assertFalse(repoNames.contains(forbidden))
        }
    }

    @Test
    fun untrackedDiffIncluded() {
        val ws = openWs()
        val handle = runBlocking { git.init(ws, identity) }
        File(ws.root, "fresh.txt").writeText("brand\n")
        val diffs = runBlocking { handle.diff(WorkspacePath.parse("fresh.txt")) }
        assertTrue(diffs.any { it.unified.contains("+brand") })
    }

    private fun openWs(): DiskFileBackedWorkspace =
        DiskFileBackedWorkspace(
            WorkspaceId("ws"),
            "ws",
            tmp.newFolder("root"),
            tmp.newFolder("journal"),
        )

    private fun assertConfig(root: File, key: String, expected: String) {
        val repo = FileRepositoryBuilder()
            .setGitDir(File(root, ".git"))
            .setWorkTree(root)
            .setMustExist(true)
            .build()
        repo.use {
            val value = it.config.getString(ConfigConstants.CONFIG_CORE_SECTION, null, key)
            assertEquals(expected, value)
        }
    }

    private fun logCount(root: File): Int {
        val repo = FileRepositoryBuilder()
            .setGitDir(File(root, ".git"))
            .setWorkTree(root)
            .setMustExist(true)
            .build()
        repo.use { built ->
            if (built.resolve("HEAD") == null) return 0
            return org.eclipse.jgit.api.Git(built).use { g ->
                runCatching { g.log().call().count() }.getOrDefault(0)
            }
        }
    }
}
