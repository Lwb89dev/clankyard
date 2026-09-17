package dev.clankyard.workspace

import dev.clankyard.core.model.ContentHash
import dev.clankyard.core.model.WorkspaceId
import dev.clankyard.core.model.WorkspacePath
import kotlinx.coroutines.flow.Flow
import java.io.InputStream

data class FileMetadata(
    val path: WorkspacePath,
    val sizeBytes: Long,
    val isDirectory: Boolean,
    val hash: ContentHash?,
    val lastModifiedEpochMs: Long,
)

data class WriteRequest(
    val path: WorkspacePath,
    val bytes: ByteArray,
    /** null **only** for create-if-absent. Replace/delete/rename of existing files require a hash. */
    val expectedHash: ContentHash?,
    val createParents: Boolean = true,
)

sealed interface WriteResult {
    data class Applied(val newHash: ContentHash) : WriteResult
    data class Conflict(val actualHash: ContentHash?, val reason: String) : WriteResult
    data class Rejected(val reason: String) : WriteResult
}

sealed interface WorkspaceChange {
    data class Changed(val path: WorkspacePath) : WorkspaceChange
    data class Deleted(val path: WorkspacePath) : WorkspaceChange
}

class PathEscapesWorkspaceException(val path: WorkspacePath) :
    Exception("path escapes workspace: ${path.relative}")

class BinaryFileException(val path: WorkspacePath) :
    Exception("NUL in first 8 KiB: ${path.relative}")

class Utf8BomDetectedException(
    val path: WorkspacePath,
    val strippedUtf8: String,
) : Exception("UTF-8 BOM in ${path.relative}")

class FileTooLargeException(val sizeBytes: Long, val maxBytes: Long) :
    Exception("file is $sizeBytes bytes, max $maxBytes")

/**
 * Domain API. Tools, search, explorer, and PatchEngine see **this**.
 * No `java.io.File`. Implementations still canonicalize internally.
 */
interface Workspace {
    val id: WorkspaceId
    val displayName: String
    val journal: ChangeJournal
    suspend fun metadata(path: WorkspacePath): FileMetadata?
    /** [WorkspacePath.ROOT] lists the workspace root. */
    suspend fun list(path: WorkspacePath): List<FileMetadata>
    suspend fun openRead(path: WorkspacePath): InputStream
    /**
     * UTF-8. BOM → [Utf8BomDetectedException] so the caller can dialog (reject or strip).
     * NUL in first 8 KiB → [BinaryFileException] (fail closed).
     */
    suspend fun readUtf8(path: WorkspacePath, maxBytes: Long): String
    /**
     * Atomic write. Temp file is created in the **same directory** as the destination
     * (`.<name>.tmp-*`), fsynced, then renamed. Re-reads hash immediately before rename (TOCTOU).
     */
    suspend fun writeAtomic(request: WriteRequest): WriteResult
    suspend fun rename(from: WorkspacePath, to: WorkspacePath, expectedHash: ContentHash?): WriteResult
    /** User-initiated delete (explorer). expectedHash required for files. Not an agent tool in MVP. */
    suspend fun delete(path: WorkspacePath, expectedHash: ContentHash?): WriteResult
    /** Best-effort. Poll ~1s on JVM. */
    fun watch(): Flow<WorkspaceChange>
}
