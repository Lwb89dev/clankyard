package dev.clankyard.git

import dev.clankyard.core.model.WorkspacePath
import dev.clankyard.workspace.FileBackedWorkspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.dircache.DirCacheEditor
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.treewalk.EmptyTreeIterator
import org.eclipse.jgit.treewalk.filter.PathFilter
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets

class JGitHandle(
    private val git: Git,
    private val workspace: FileBackedWorkspace,
) : GitHandle, AutoCloseable {
    private val lock = Any()

    override suspend fun status(): GitStatus = ioLocked {
        val raw = git.status().call()
        GitStatus(
            branch = branchName(),
            staged = toPaths(raw.added + raw.changed + raw.removed),
            unstaged = toPaths(raw.modified + raw.missing),
            untracked = toPaths(raw.untracked + raw.untrackedFolders),
            conflicts = toPaths(raw.conflicting),
        )
    }

    override suspend fun diff(path: WorkspacePath?): List<GitDiff> = ioLocked {
        val filter = path?.takeUnless { it.isRoot }?.let { PathFilter.create(it.relative) }
        formatDiffs(cached = true, filter) +
            formatDiffs(cached = false, filter) +
            untrackedDiffs(path)
    }

    override suspend fun stage(paths: List<WorkspacePath>) = ioLocked {
        for (path in paths) {
            if (path.isRoot) continue
            val file = containedFile(path)
            if (file.exists()) {
                git.add().addFilepattern(path.relative).call()
            } else {
                git.rm().setCached(true).addFilepattern(path.relative).call()
            }
        }
    }

    override suspend fun unstage(paths: List<WorkspacePath>) = ioLocked {
        val hasHead = git.repository.resolve(Constants.HEAD) != null
        for (path in paths) {
            if (path.isRoot) continue
            containedFile(path)
            if (hasHead) {
                git.reset().addPath(path.relative).call()
            } else {
                unstageFromIndex(path.relative)
            }
        }
    }

    override suspend fun commit(message: String, identity: GitIdentity) = ioLocked {
        identity.requireComplete()
        require(message.isNotBlank()) { "commit message is required" }
        val raw = git.status().call()
        val staged = raw.added + raw.changed + raw.removed
        require(staged.isNotEmpty()) { "nothing staged" }
        val config = git.repository.config
        config.setString("user", null, "name", identity.name)
        config.setString("user", null, "email", identity.email)
        config.save()
        git.commit()
            .setMessage(message)
            .setAuthor(identity.name, identity.email)
            .setCommitter(identity.name, identity.email)
            .call()
        Unit
    }

    override suspend fun currentBranch(): String? = ioLocked { branchName() }

    override fun close() {
        git.close()
    }

    private fun branchName(): String? {
        val head = git.repository.exactRef(Constants.HEAD) ?: return null
        if (head.isSymbolic) return Repository.shortenRefName(head.target.name)
        return git.repository.branch
    }

    private fun formatDiffs(cached: Boolean, filter: PathFilter?): List<GitDiff> {
        val entries = diffCommand(cached, filter).call()
        return entries.map { entry ->
            val relative = entryPath(entry).relative
            val out = ByteArrayOutputStream()
            diffCommand(cached, PathFilter.create(relative)).setOutputStream(out).call()
            GitDiff(entryPath(entry), out.toString(StandardCharsets.UTF_8))
        }
    }

    private fun diffCommand(cached: Boolean, filter: PathFilter?) =
        git.diff().setCached(cached).also { command ->
            if (filter != null) command.setPathFilter(filter)
            if (cached && git.repository.resolve(Constants.HEAD) == null) {
                command.setOldTree(EmptyTreeIterator())
            }
        }

    private fun untrackedDiffs(path: WorkspacePath?): List<GitDiff> {
        val raw = git.status().call()
        val names = raw.untracked
        val wanted = if (path == null || path.isRoot) names else names.filter { it == path.relative }
        return wanted.mapNotNull { relative ->
            val parsed = parseGitPath(relative) ?: return@mapNotNull null
            val file = workspace.resolve(parsed)
            if (!workspace.containsCanonical(file) || !file.isFile) return@mapNotNull null
            val text = runCatching { file.readText() }.getOrNull() ?: return@mapNotNull null
            GitDiff(parsed, newFileUnified(relative, text))
        }
    }

    private fun containedFile(path: WorkspacePath): File {
        val file = workspace.resolve(path)
        check(workspace.containsCanonical(file)) { "path escapes workspace: ${path.relative}" }
        return file
    }

    private fun unstageFromIndex(path: String) {
        val cache = git.repository.lockDirCache()
        try {
            val editor = cache.editor()
            editor.add(DirCacheEditor.DeletePath(path))
            if (!editor.commit()) error("failed to write index while unstaging $path")
        } finally {
            cache.unlock()
        }
    }

    private suspend fun <T> ioLocked(block: () -> T): T =
        withContext(Dispatchers.IO) { locked(block) }

    private fun <T> locked(block: () -> T): T = synchronized(lock) {
        try {
            block()
        } catch (e: Exception) {
            wrapGitFailure(e, workspace.root)
        }
    }
}

internal fun toPaths(names: Iterable<String>): List<WorkspacePath> =
    names.mapNotNull(::parseGitPath).distinct().sortedBy { it.relative }

internal fun parseGitPath(raw: String): WorkspacePath? {
    val trimmed = raw.replace('\\', '/').trimEnd('/')
    if (trimmed.isEmpty() || trimmed == "." || trimmed == DiffEntry.DEV_NULL) return null
    if (trimmed.startsWith("/")) return null
    return runCatching { WorkspacePath.parse(trimmed) }.getOrNull()
}

internal fun entryPath(entry: DiffEntry): WorkspacePath {
    val raw = when {
        entry.newPath != DiffEntry.DEV_NULL -> entry.newPath
        else -> entry.oldPath
    }
    return parseGitPath(raw) ?: WorkspacePath.parse("unknown")
}
