package dev.clankyard.ai.patch

import dev.clankyard.core.model.PatchSetId
import dev.clankyard.core.model.WorkspacePath

/** Explicit user-gesture entry for [PatchEngine.apply]. Not on AgentOrchestrator. */
class ApplyPatchUseCase(private val engine: PatchEngine) {
    suspend operator fun invoke(
        id: PatchSetId,
        accepted: Set<WorkspacePath>,
        dirty: Set<WorkspacePath> = emptySet(),
    ): ApplyResult = engine.apply(id, accepted, dirty)
}
