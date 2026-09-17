package dev.clankyard.app.session

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.clankyard.core.model.WorkspaceId
import dev.clankyard.core.model.WorkspacePath
import dev.clankyard.editor.EditorSession
import dev.clankyard.workspace.Workspace
import dev.clankyard.workspace.WriteResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject

/** Activity-retained [EditorSession]. Drafts: filesDir/drafts/. Dirty set blocks PatchEngine.apply. */
@HiltViewModel
class EditorSessionViewModel @Inject constructor(
    @ApplicationContext context: Context,
) : ViewModel() {
    private val draftsRoot = File(context.filesDir, "drafts")
    private val _session = MutableStateFlow<EditorSession?>(null)
    val session: StateFlow<EditorSession?> = _session.asStateFlow()
    private var boundId: WorkspaceId? = null

    fun boundWorkspaceId(): WorkspaceId? = boundId

    fun bind(workspace: Workspace): EditorSession {
        val existing = _session.value
        if (boundId == workspace.id && existing != null) return existing
        existing?.closeSession()
        val created = EditorSession(draftsRoot, workspace, viewModelScope)
        boundId = workspace.id
        _session.value = created
        return created
    }

    /** Exposed so PatchEngine.apply can refuse dirty buffers. */
    fun dirtyPaths(): Set<WorkspacePath> = _session.value?.dirtyPaths().orEmpty()

    suspend fun saveActive(): WriteResult =
        _session.value?.saveActive() ?: WriteResult.Rejected("no session")

    suspend fun saveAll(): Map<WorkspacePath, WriteResult> =
        _session.value?.saveAll().orEmpty()

    suspend fun saveAs(from: WorkspacePath, to: WorkspacePath): WriteResult =
        _session.value?.saveAs(from, to) ?: WriteResult.Rejected("no session")

    override fun onCleared() {
        _session.value?.closeSession()
        super.onCleared()
    }
}
