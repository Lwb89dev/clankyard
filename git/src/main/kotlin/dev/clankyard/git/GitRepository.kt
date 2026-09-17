package dev.clankyard.git

import dev.clankyard.core.model.WorkspacePath
import dev.clankyard.workspace.FileBackedWorkspace

data class GitIdentity(val name: String, val email: String)

data class GitStatus(
    val branch: String?,
    val staged: List<WorkspacePath>,
    val unstaged: List<WorkspacePath>,
    val untracked: List<WorkspacePath>,
    val conflicts: List<WorkspacePath>,
)

data class GitDiff(val path: WorkspacePath, val unified: String)

class FuseExclException(message: String, cause: Throwable? = null) : Exception(message, cause)

class GitIdentityRequiredException(message: String = "user.name and user.email required before commit") :
    Exception(message)

interface GitRepository {
    /** Injects core.filemode=false and core.autocrlf=false on open/init. */
    suspend fun open(workspace: FileBackedWorkspace): GitHandle?

    suspend fun init(workspace: FileBackedWorkspace, identity: GitIdentity): GitHandle
}

interface GitHandle {
    suspend fun status(): GitStatus
    suspend fun diff(path: WorkspacePath?): List<GitDiff>
    suspend fun stage(paths: List<WorkspacePath>)
    suspend fun unstage(paths: List<WorkspacePath>)
    suspend fun commit(message: String, identity: GitIdentity)
    suspend fun currentBranch(): String?
}

fun GitIdentity.requireComplete() {
    if (name.isBlank() || email.isBlank()) throw GitIdentityRequiredException()
}
