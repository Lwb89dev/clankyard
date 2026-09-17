package dev.clankyard.feature.git

import dev.clankyard.core.model.WorkspaceId
import dev.clankyard.core.model.WorkspacePath
import dev.clankyard.git.GitDiff
import dev.clankyard.git.GitIdentity
import dev.clankyard.workspace.DiskFileBackedWorkspace
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class GitViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun loadsExistingRepoOnStart() = runTest {
        val git = FakeGitRepository().also { it.inited = true }
        val vm = viewModel(git, this)
        advanceUntilIdle()
        assertTrue(vm.state.value.repoPresent)
        assertEquals(1, git.openCalls)
        assertEquals(0, git.initCalls)
    }

    @Test
    fun closesHandleOnWorkspaceSwitch() = runTest {
        val git = FakeGitRepository().also { it.inited = true }
        val first = DiskFileBackedWorkspace(
            WorkspaceId("one"),
            "one",
            tmp.newFolder("one"),
            tmp.newFolder("one-j"),
        )
        val second = DiskFileBackedWorkspace(
            WorkspaceId("two"),
            "two",
            tmp.newFolder("two"),
            tmp.newFolder("two-j"),
        )
        var current: DiskFileBackedWorkspace? = first
        val vm = GitViewModel(git, { current }, this)
        advanceUntilIdle()
        assertEquals(1, git.openCalls)
        current = second
        vm.onEvent(GitUiEvent.Refresh)
        advanceUntilIdle()
        assertEquals(2, git.openCalls)
        assertEquals(1, git.handle.closed)
        current = null
        vm.onEvent(GitUiEvent.Refresh)
        advanceUntilIdle()
        assertFalse(vm.state.value.repoPresent)
        assertEquals(2, git.handle.closed)
    }

    @Test
    fun initIsNoOpWhenRepoAlreadyOpen() = runTest {
        val git = FakeGitRepository().also { it.inited = true }
        val vm = viewModel(git, this, GitIdentity("Ada", "ada@clankyard.dev"))
        advanceUntilIdle()
        vm.onEvent(GitUiEvent.Init)
        advanceUntilIdle()
        assertEquals(0, git.initCalls)
        assertTrue(vm.state.value.repoPresent)
    }

    @Test
    fun commitBlockedWithoutIdentity() = runTest {
        val git = FakeGitRepository().also { it.inited = true }
        git.handle.staged += WorkspacePath.parse("a.txt")
        val vm = viewModel(git, this)
        vm.onEvent(GitUiEvent.Refresh)
        advanceUntilIdle()
        assertTrue(vm.state.value.repoPresent)
        assertFalse(vm.state.value.canCommit)
        vm.onEvent(GitUiEvent.SetCommitMessage("msg"))
        vm.onEvent(GitUiEvent.Commit)
        advanceUntilIdle()
        assertTrue(git.handle.commits.isEmpty())
        assertTrue(vm.state.value.error != null)
    }

    @Test
    fun stageCommitRequiresGestureAndIdentity() = runTest {
        val git = FakeGitRepository().also { it.inited = true }
        val path = WorkspacePath.parse("a.txt")
        git.handle.untracked += path
        val vm = viewModel(git, this, GitIdentity("Ada", "ada@clankyard.dev"))
        vm.onEvent(GitUiEvent.Refresh)
        advanceUntilIdle()
        vm.onEvent(GitUiEvent.Stage(listOf(path)))
        advanceUntilIdle()
        assertEquals(listOf(path), git.handle.staged)
        assertTrue(git.handle.commits.isEmpty())
        vm.onEvent(GitUiEvent.SetCommitMessage("add a"))
        assertTrue(vm.state.value.canCommit)
        vm.onEvent(GitUiEvent.Commit)
        advanceUntilIdle()
        assertEquals(1, git.handle.commits.size)
        assertEquals("add a", git.handle.commits.single().first)
        assertTrue(git.handle.staged.isEmpty())
    }

    @Test
    fun initRequiresIdentity() = runTest {
        val git = FakeGitRepository()
        val vm = viewModel(git, this)
        vm.onEvent(GitUiEvent.Init)
        advanceUntilIdle()
        assertEquals(0, git.initCalls)
        assertFalse(git.inited)
        vm.onEvent(GitUiEvent.SetIdentityName("Ada"))
        vm.onEvent(GitUiEvent.SetIdentityEmail("ada@clankyard.dev"))
        vm.onEvent(GitUiEvent.Init)
        advanceUntilIdle()
        assertEquals(1, git.initCalls)
        assertTrue(vm.state.value.repoPresent)
    }

    @Test
    fun selectLoadsDiffFromFake() = runTest {
        val git = FakeGitRepository().also { it.inited = true }
        val path = WorkspacePath.parse("a.txt")
        git.handle.diffs = listOf(GitDiff(path, "--- a/a.txt\n+++ b/a.txt\n+hi\n"))
        val vm = viewModel(git, this, GitIdentity("Ada", "ada@clankyard.dev"))
        vm.onEvent(GitUiEvent.Refresh)
        vm.onEvent(GitUiEvent.Select(path))
        advanceUntilIdle()
        assertEquals(path, vm.state.value.selectedPath)
        assertTrue(vm.state.value.diffs.single().unified.contains("+hi"))
    }

    private fun viewModel(
        git: FakeGitRepository,
        scope: TestScope,
        identity: GitIdentity? = null,
    ): GitViewModel {
        val ws = DiskFileBackedWorkspace(
            WorkspaceId("ws"),
            "ws",
            tmp.newFolder("root"),
            tmp.newFolder("journal"),
        )
        return GitViewModel(git, { ws }, scope, identity)
    }
}
