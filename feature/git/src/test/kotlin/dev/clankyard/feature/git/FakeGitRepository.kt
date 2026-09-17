package dev.clankyard.feature.git

import dev.clankyard.core.model.WorkspacePath
import dev.clankyard.git.GitDiff
import dev.clankyard.git.GitHandle
import dev.clankyard.git.GitIdentity
import dev.clankyard.git.GitRepository
import dev.clankyard.git.GitStatus
import dev.clankyard.git.requireComplete
import dev.clankyard.workspace.FileBackedWorkspace

class FakeGitRepository : GitRepository {
    var inited = false
    val handle = FakeGitHandle()
    var initCalls = 0
    var openCalls = 0

    override suspend fun open(workspace: FileBackedWorkspace): GitHandle? {
        openCalls++
        return if (inited) handle else null
    }

    override suspend fun init(workspace: FileBackedWorkspace, identity: GitIdentity): GitHandle {
        identity.requireComplete()
        initCalls++
        inited = true
        handle.identity = identity
        return handle
    }
}

class FakeGitHandle : GitHandle {
    var identity: GitIdentity? = null
    val staged = mutableListOf<WorkspacePath>()
    val unstaged = mutableListOf<WorkspacePath>()
    val untracked = mutableListOf<WorkspacePath>()
    val commits = mutableListOf<Pair<String, GitIdentity>>()
    var diffs: List<GitDiff> = emptyList()
    var branch: String? = "main"

    override suspend fun status(): GitStatus =
        GitStatus(branch, staged.toList(), unstaged.toList(), untracked.toList(), emptyList())

    override suspend fun diff(path: WorkspacePath?): List<GitDiff> =
        if (path == null) diffs else diffs.filter { it.path == path }

    override suspend fun stage(paths: List<WorkspacePath>) {
        for (path in paths) {
            untracked.remove(path)
            unstaged.remove(path)
            if (path !in staged) staged += path
        }
    }

    override suspend fun unstage(paths: List<WorkspacePath>) {
        for (path in paths) {
            staged.remove(path)
            if (path !in untracked) untracked += path
        }
    }

    override suspend fun commit(message: String, identity: GitIdentity) {
        identity.requireComplete()
        require(message.isNotBlank()) { "commit message is required" }
        require(staged.isNotEmpty()) { "nothing staged" }
        this.identity = identity
        commits += message to identity
        staged.clear()
    }

    override suspend fun currentBranch(): String? = branch
}
