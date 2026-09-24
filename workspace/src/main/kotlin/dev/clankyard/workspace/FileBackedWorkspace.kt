package dev.clankyard.workspace

import dev.clankyard.core.model.WorkspacePath
import java.io.File

/**
 * Internal. Only `:git`, `:terminal:local`, `:build:engine`, and workspace I/O.
 * Never injected into `:ai:tools`.
 */
interface FileBackedWorkspace : Workspace {
    val root: File

    fun resolve(path: WorkspacePath): File

    /**
     * After symlink resolution:
     * `val c = file.canonicalFile; val r = root.canonicalFile`
     * `c == r || c.path.startsWith(r.path + File.separatorChar)`
     * Prefix-sibling `/workspaces/1` vs `/workspaces/1-evil` is **not** contained.
     */
    fun containsCanonical(file: File): Boolean
}
