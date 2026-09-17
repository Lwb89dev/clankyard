package dev.clankyard.feature.git

import dev.clankyard.core.model.WorkspacePath
import dev.clankyard.git.GitDiff
import dev.clankyard.git.GitStatus

data class GitUiState(
    val repoPresent: Boolean = false,
    val branch: String? = null,
    val status: GitStatus? = null,
    val selectedPath: WorkspacePath? = null,
    val diffs: List<GitDiff> = emptyList(),
    val identityName: String = "",
    val identityEmail: String = "",
    val commitMessage: String = "",
    val error: String? = null,
    val busy: Boolean = false,
) {
    val identityReady: Boolean
        get() = identityName.isNotBlank() && identityEmail.isNotBlank()

    val canCommit: Boolean
        get() = repoPresent &&
            identityReady &&
            commitMessage.isNotBlank() &&
            !status?.staged.isNullOrEmpty() &&
            !busy
}

sealed interface GitUiEvent {
    data object Refresh : GitUiEvent
    data object Init : GitUiEvent
    data class Select(val path: WorkspacePath) : GitUiEvent
    data class Stage(val paths: List<WorkspacePath>) : GitUiEvent
    data class Unstage(val paths: List<WorkspacePath>) : GitUiEvent
    data class SetIdentityName(val name: String) : GitUiEvent
    data class SetIdentityEmail(val email: String) : GitUiEvent
    data class SetCommitMessage(val message: String) : GitUiEvent
    data object Commit : GitUiEvent
}
