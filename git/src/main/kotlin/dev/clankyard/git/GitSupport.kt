package dev.clankyard.git

import kotlinx.coroutines.CancellationException
import org.eclipse.jgit.errors.LockFailedException
import org.eclipse.jgit.lib.ConfigConstants
import org.eclipse.jgit.lib.CoreConfig
import org.eclipse.jgit.lib.Repository
import java.io.File

internal const val FUSE_MESSAGE =
    "This folder isn't POSIX-safe for Git. Copy into the workshop?"

internal fun isPublicFusePath(canonicalPath: String): Boolean {
    val path = canonicalPath.replace('\\', '/')
    val emulated = path.contains("/storage/emulated/")
    val sdcard = path == "/sdcard" || path.startsWith("/sdcard/")
    if (!emulated && !sdcard) return false
    if (path.contains("/Android/data/") || path.contains("/Android/obb/")) return false
    return true
}

internal fun refusePublicFuse(root: File) {
    val path = runCatching { root.canonicalPath }.getOrElse { root.absolutePath }
    if (isPublicFusePath(path)) throw FuseExclException(FUSE_MESSAGE)
}

internal fun forceCoreConfig(repo: Repository) {
    val config = repo.config
    config.setBoolean(
        ConfigConstants.CONFIG_CORE_SECTION,
        null,
        ConfigConstants.CONFIG_KEY_FILEMODE,
        false,
    )
    config.setEnum(
        ConfigConstants.CONFIG_CORE_SECTION,
        null,
        ConfigConstants.CONFIG_KEY_AUTOCRLF,
        CoreConfig.AutoCRLF.FALSE,
    )
    config.save()
}

internal fun wrapGitFailure(error: Throwable, workspaceRoot: File): Nothing {
    if (error is CancellationException) throw error
    if (error is FuseExclException) throw error
    if (error is GitIdentityRequiredException) throw error
    val fuseRoot = isPublicFusePath(
        runCatching { workspaceRoot.canonicalPath }.getOrElse { workspaceRoot.absolutePath },
    )
    val text = generateSequence(error) { it.cause }.mapNotNull { it.message }.joinToString(" ")
    val excl = error is LockFailedException || text.contains("EXCL", ignoreCase = true)
    if (fuseRoot && excl) throw FuseExclException(FUSE_MESSAGE, error)
    throw error
}

internal fun newFileUnified(pathLabel: String, content: String): String {
    val split = splitForUntracked(content)
    val out = StringBuilder()
    out.append("--- /dev/null\n")
    out.append("+++ b/").append(pathLabel).append('\n')
    if (split.lines.isEmpty()) return out.toString()
    out.append("@@ -0,0 +1,").append(split.lines.size).append(" @@\n")
    for ((index, line) in split.lines.withIndex()) {
        out.append('+').append(line).append('\n')
        if (index == split.lines.lastIndex && !split.endsWithNewline) {
            out.append("\\ No newline at end of file\n")
        }
    }
    return out.toString()
}

private data class UntrackedSplit(val lines: List<String>, val endsWithNewline: Boolean)

private fun splitForUntracked(text: String): UntrackedSplit {
    if (text.isEmpty()) return UntrackedSplit(emptyList(), true)
    val ends = text.endsWith('\n')
    val raw = text.split('\n')
    val lines = if (ends) raw.subList(0, raw.lastIndex) else raw
    return UntrackedSplit(lines, ends)
}
