package dev.clankyard.ai.secret

import dev.clankyard.core.model.WorkspacePath

object SecretLimits {
    const val MAX_FILE_BYTES = 256L * 1024
    const val MAX_PACKET_BYTES = 1024L * 1024
    const val BINARY_PROBE_BYTES = 8 * 1024
}

data class FilterDecision(
    val allowed: Boolean,
    val reason: String?,
    val redacted: Boolean,
)

data class FilteredText(
    val text: String,
    val redacted: Boolean,
    val omissions: List<String>,
)

interface SecretFilter {
    fun decide(path: WorkspacePath, mimeHint: String?, sizeBytes: Long): FilterDecision
    fun filterText(path: WorkspacePath, text: String): FilteredText
    /** Mandatory before a ToolResult is appended to ChatMessages. */
    fun filterToolResult(result: String): FilteredText
    fun withIgnoreRules(rules: IgnoreRules): SecretFilter = this
    fun withExtraDenyGlobs(globs: List<String>): SecretFilter = this
}
