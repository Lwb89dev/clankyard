package dev.clankyard.ai.context

import dev.clankyard.core.model.WorkspacePath

const val DEFAULT_MAX_CHARS = 32_768

enum class AgentMode { Ask, Plan, Edit }

data class ContextRequest(
    val mode: AgentMode,
    val prompt: String,
    val currentFile: WorkspacePath?,
    val selection: String?,
    val pinned: List<WorkspacePath>,
    val mentions: List<String>,
    val maxChars: Int = DEFAULT_MAX_CHARS,
)

data class ContextChunk(
    val path: WorkspacePath?,
    val label: String,
    val text: String,
    val omitted: Boolean,
)

data class AmbiguousMention(
    val mention: String,
    val candidates: List<WorkspacePath>,
)

data class ContextPacket(
    val chunks: List<ContextChunk>,
    val estimatedTokens: Int,
    val filterNotes: List<String>,
    val ambiguous: List<AmbiguousMention> = emptyList(),
)

interface ContextEngine {
    suspend fun assemble(request: ContextRequest): ContextPacket
}
