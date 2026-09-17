package dev.clankyard.feature.git

import dev.clankyard.git.GitHandle
import dev.clankyard.git.GitIdentity
import dev.clankyard.git.GitIdentityRequiredException
import dev.clankyard.git.GitRepository
import dev.clankyard.git.requireComplete
import dev.clankyard.workspace.FileBackedWorkspace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GitViewModel(
    private val git: GitRepository,
    private val workspaceProvider: () -> FileBackedWorkspace?,
    private val scope: CoroutineScope,
    initialIdentity: GitIdentity? = null,
) {
    private val _state = MutableStateFlow(
        GitUiState(
            identityName = initialIdentity?.name.orEmpty(),
            identityEmail = initialIdentity?.email.orEmpty(),
        ),
    )
    val state: StateFlow<GitUiState> = _state.asStateFlow()

    private var handle: GitHandle? = null

    fun onEvent(event: GitUiEvent) {
        when (event) {
            GitUiEvent.Refresh -> run("refresh") { refresh() }
            GitUiEvent.Init -> run("init") { initRepo() }
            is GitUiEvent.Select -> run("diff") { select(event) }
            is GitUiEvent.Stage -> run("stage") {
                requireHandle().stage(event.paths)
                refresh()
            }
            is GitUiEvent.Unstage -> run("unstage") {
                requireHandle().unstage(event.paths)
                refresh()
            }
            is GitUiEvent.SetIdentityName -> _state.update { it.copy(identityName = event.name) }
            is GitUiEvent.SetIdentityEmail -> _state.update { it.copy(identityEmail = event.email) }
            is GitUiEvent.SetCommitMessage -> _state.update { it.copy(commitMessage = event.message) }
            GitUiEvent.Commit -> run("commit") { commit() }
        }
    }

    fun close() {
        (handle as? AutoCloseable)?.close()
        handle = null
    }

    private fun run(label: String, block: suspend () -> Unit) {
        scope.launch {
            _state.update { it.copy(busy = true, error = null) }
            try {
                block()
            } catch (e: GitIdentityRequiredException) {
                _state.update { it.copy(error = e.message, busy = false) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: label, busy = false) }
            }
            _state.update { it.copy(busy = false) }
        }
    }

    private suspend fun initRepo() {
        val ws = workspaceProvider() ?: error("no workspace")
        val created = git.init(ws, currentIdentity())
        handle = created
        refresh()
    }

    private suspend fun select(event: GitUiEvent.Select) {
        val diffs = requireHandle().diff(event.path)
        _state.update { it.copy(selectedPath = event.path, diffs = diffs) }
    }

    private suspend fun commit() {
        val identity = currentIdentity()
        val message = _state.value.commitMessage
        requireHandle().commit(message, identity)
        _state.update { it.copy(commitMessage = "") }
        refresh()
    }

    private suspend fun refresh() {
        val ws = workspaceProvider()
        if (ws == null) {
            _state.update { it.copy(repoPresent = false, status = null, branch = null, diffs = emptyList()) }
            return
        }
        val opened = handle ?: git.open(ws)
        handle = opened
        if (opened == null) {
            _state.update {
                it.copy(repoPresent = false, status = null, branch = null, diffs = emptyList())
            }
            return
        }
        val status = opened.status()
        val selected = _state.value.selectedPath
        val diffs = if (selected != null) opened.diff(selected) else emptyList()
        _state.update {
            it.copy(
                repoPresent = true,
                branch = status.branch,
                status = status,
                diffs = diffs,
            )
        }
    }

    private fun currentIdentity(): GitIdentity {
        val identity = GitIdentity(_state.value.identityName, _state.value.identityEmail)
        identity.requireComplete()
        return identity
    }

    private fun requireHandle(): GitHandle =
        handle ?: error("no git repository — init first")
}
