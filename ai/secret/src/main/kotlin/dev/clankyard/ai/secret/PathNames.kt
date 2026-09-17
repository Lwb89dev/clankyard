package dev.clankyard.ai.secret

import dev.clankyard.core.model.WorkspacePath

internal fun WorkspacePath.fileName(): String =
    if (isRoot) "" else relative.substringAfterLast('/')

internal fun WorkspacePath.parentPath(): WorkspacePath {
    if (isRoot) return WorkspacePath.ROOT
    val slash = relative.lastIndexOf('/')
    if (slash < 0) return WorkspacePath.ROOT
    return WorkspacePath.parse(relative.substring(0, slash))
}

internal fun extensionOf(name: String): String {
    if (name.startsWith('.') && name.indexOf('.', 1) < 0) return name.drop(1).lowercase()
    val dot = name.lastIndexOf('.')
    if (dot <= 0 || dot == name.lastIndex) return ""
    return name.substring(dot + 1).lowercase()
}
