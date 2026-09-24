package dev.clankyard.core.ui

import dev.clankyard.core.model.WorkspaceId
import dev.clankyard.core.model.WorkspacePath

data class OpenTab(
    val path: WorkspacePath,
    val cursorLine: Int,
    val cursorCol: Int,
)

data class WorkspaceUiState(
    val workspaceId: WorkspaceId? = null,
    val tabs: List<OpenTab> = emptyList(),
    val activePath: WorkspacePath? = null,
    /** 0f..1f vertical split Files|Editor and Editor|Clanker. Ignored on Compact. */
    val filesWeight: Float = 0.22f,
    val editorWeight: Float = 0.50f,
    val clankerWeight: Float = 0.28f,
    val bottomWeight: Float = 0.28f,
    val filesCollapsed: Boolean = false,
    val clankerVisible: Boolean = true,
    val bottomCollapsed: Boolean = true,
    val bottomTab: BottomTab = BottomTab.Terminal,
    val lastSizeClass: SizeClass = SizeClass.Compact,
)

enum class BottomTab { Terminal, Problems, Git, Output }

enum class SizeClass { Compact, Medium, Expanded }

/** DataStore holds [WorkspaceUiState]. SavedStateHandle holds workspaceId + activePath only. */
interface WorkspaceUiStore {
    val state: kotlinx.coroutines.flow.StateFlow<WorkspaceUiState>
    suspend fun update(transform: (WorkspaceUiState) -> WorkspaceUiState)
    /** Waits for the on-disk snapshot. Clanker transcript is not stored. */
    suspend fun snapshot(): WorkspaceUiState
}
