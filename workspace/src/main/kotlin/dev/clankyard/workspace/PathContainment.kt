package dev.clankyard.workspace

import java.io.File

internal fun containsCanonical(root: File, file: File): Boolean {
    val c = runCatching { file.canonicalFile }.getOrNull() ?: return false
    val r = runCatching { root.canonicalFile }.getOrNull() ?: return false
    return c == r || c.path.startsWith(r.path + File.separatorChar)
}
