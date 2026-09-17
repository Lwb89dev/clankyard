package dev.clankyard.ai.patch

import dev.clankyard.core.model.WorkspaceId
import dev.clankyard.diff.DiffEngine
import dev.clankyard.workspace.FileBackedWorkspace

/** One stateful engine per [WorkspaceId] so pending/applied survive a second create(). */
class CachingPatchEngineFactory(
    private val diffEngine: DiffEngine,
) : PatchEngineFactory {
    private val lock = Any()
    private val engines = HashMap<WorkspaceId, PatchEngine>()

    override fun create(workspace: FileBackedWorkspace): PatchEngine {
        synchronized(lock) {
            return engines.getOrPut(workspace.id) {
                WorkspacePatchEngine(workspace, diffEngine)
            }
        }
    }
}
