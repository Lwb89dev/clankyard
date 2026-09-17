package dev.clankyard.feature.workspacepicker

import android.content.Context
import java.io.File

internal fun isOexclSafeAppSpecific(context: Context, file: File): Boolean {
    val c = runCatching { file.canonicalFile }.getOrNull() ?: return false
    if (!c.isDirectory) return false
    val roots = listOfNotNull(
        context.filesDir,
        context.noBackupFilesDir,
        context.getExternalFilesDir(null),
    ).map { it.canonicalFile }
    return roots.any { c == it || c.path.startsWith(it.path + File.separatorChar) }
}
