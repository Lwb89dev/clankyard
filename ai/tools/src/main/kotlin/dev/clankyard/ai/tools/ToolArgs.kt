package dev.clankyard.ai.tools

import dev.clankyard.core.model.WorkspacePath
import dev.clankyard.workspace.BinaryFileException
import dev.clankyard.workspace.FileTooLargeException
import dev.clankyard.workspace.Utf8BomDetectedException
import dev.clankyard.workspace.Workspace
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal const val SAFE_READ_MAX_BYTES = 256L * 1024
internal const val EDIT_READ_MAX_BYTES = 32L * 1024 * 1024

internal sealed interface PathArg {
    data class Ok(val path: WorkspacePath) : PathArg
    data class Invalid(val message: String) : PathArg
    data object Missing : PathArg
}

internal fun JsonObject.string(key: String): String? {
    val el = this[key] as? JsonPrimitive ?: return null
    return el.content
}

internal fun JsonObject.pathArg(key: String): PathArg {
    val raw = string(key) ?: return PathArg.Missing
    return try {
        PathArg.Ok(WorkspacePath.parse(raw))
    } catch (e: IllegalArgumentException) {
        PathArg.Invalid(e.message ?: "illegal path")
    }
}

internal fun JsonObject.requiredPath(key: String): PathArg {
    val parsed = pathArg(key)
    if (parsed is PathArg.Missing) return PathArg.Invalid("$key is required")
    return parsed
}

internal suspend fun readWorkspaceUtf8(
    workspace: Workspace,
    path: WorkspacePath,
    maxBytes: Long,
): String {
    return try {
        workspace.readUtf8(path, maxBytes)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Utf8BomDetectedException) {
        e.strippedUtf8
    }
}

internal fun Throwable.readErrorMessage(): String = when (this) {
    is BinaryFileException -> "binary file: ${path.relative}"
    is FileTooLargeException -> message ?: "file too large"
    else -> message ?: "read failed"
}

internal fun countOccurrences(haystack: String, needle: String): Int {
    if (needle.isEmpty()) return 0
    var count = 0
    var from = 0
    while (from <= haystack.length - needle.length) {
        val at = haystack.indexOf(needle, from)
        if (at < 0) return count
        count++
        from = at + needle.length
    }
    return count
}
