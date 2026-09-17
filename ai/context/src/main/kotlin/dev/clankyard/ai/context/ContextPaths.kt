package dev.clankyard.ai.context

import dev.clankyard.core.model.WorkspacePath

internal fun WorkspacePath.fileName(): String =
    if (isRoot) "" else relative.substringAfterLast('/')

internal fun WorkspacePath.parentPath(): WorkspacePath {
    if (isRoot) return WorkspacePath.ROOT
    val slash = relative.lastIndexOf('/')
    if (slash < 0) return WorkspacePath.ROOT
    return WorkspacePath.parse(relative.substring(0, slash))
}

internal fun resolveAgainst(baseDir: WorkspacePath, spec: String): WorkspacePath? {
    if (spec == "/" || spec == ".") return baseDir
    val cleaned = spec.removePrefix("./").trimEnd('/')
    if (cleaned.isEmpty()) return if (spec.endsWith('/')) baseDir else null
    val raw = if (baseDir.isRoot) cleaned else "${baseDir.relative}/$cleaned"
    return runCatching { WorkspacePath.parse(raw) }.getOrNull()
}
