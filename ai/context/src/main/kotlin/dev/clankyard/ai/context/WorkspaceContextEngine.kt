package dev.clankyard.ai.context

import dev.clankyard.ai.secret.FilterDecision
import dev.clankyard.ai.secret.FilteredText
import dev.clankyard.ai.secret.IgnoreRules
import dev.clankyard.ai.secret.SecretFilter
import dev.clankyard.ai.secret.SecretLimits
import dev.clankyard.core.common.RedactingLogger
import dev.clankyard.core.model.WorkspacePath
import dev.clankyard.workspace.BinaryFileException
import dev.clankyard.workspace.FileMetadata
import dev.clankyard.workspace.FileTooLargeException
import dev.clankyard.workspace.Utf8BomDetectedException
import dev.clankyard.workspace.Workspace
import java.nio.charset.StandardCharsets

class WorkspaceContextEngine(
    private val workspace: Workspace,
    private val secretFilter: SecretFilter,
    private val logger: RedactingLogger? = null,
) : ContextEngine {
    override suspend fun assemble(request: ContextRequest): ContextPacket {
        val filter = secretFilter.withIgnoreRules(loadIgnoreRules())
        val budget = Budget(effectiveMax(request.maxChars))
        val chunks = ArrayList<ContextChunk>()
        val notes = ArrayList<String>()
        val ambiguous = ArrayList<AmbiguousMention>()
        val included = HashSet<String>()
        addPrompt(request, filter, budget, chunks, notes)
        addSelection(request, filter, budget, chunks, notes)
        addCurrentFile(request, filter, budget, chunks, notes, included)
        addPinned(request, filter, budget, chunks, notes, included)
        addMentions(request, filter, budget, chunks, notes, ambiguous, included)
        return toPacket(chunks, notes, ambiguous)
    }

    private suspend fun loadIgnoreRules(): IgnoreRules {
        val git = readOptional(GITIGNORE)
        val clank = readOptional(CLANKYARD_IGNORE)
        return IgnoreRules.parse(git, clank)
    }

    private suspend fun readOptional(relative: String): String {
        val path = runCatching { WorkspacePath.parse(relative) }.getOrNull() ?: return ""
        return try {
            workspace.readUtf8(path, SecretLimits.MAX_FILE_BYTES)
        } catch (e: Utf8BomDetectedException) {
            e.strippedUtf8
        } catch (_: Exception) {
            ""
        }
    }

    private fun addPrompt(
        request: ContextRequest,
        filter: SecretFilter,
        budget: Budget,
        chunks: MutableList<ContextChunk>,
        notes: MutableList<String>,
    ) {
        if (request.prompt.isEmpty()) return
        val filtered = filter.filterToolResult(request.prompt)
        addBlob("Prompt", null, filtered, budget, chunks, notes, capHeadTail = true)
    }

    private fun addSelection(
        request: ContextRequest,
        filter: SecretFilter,
        budget: Budget,
        chunks: MutableList<ContextChunk>,
        notes: MutableList<String>,
    ) {
        val selection = request.selection ?: return
        if (selection.isEmpty()) return
        val filtered = filter.filterToolResult(selection)
        addBlob("Selection", request.currentFile, filtered, budget, chunks, notes, capHeadTail = true)
    }

    private suspend fun addCurrentFile(
        request: ContextRequest,
        filter: SecretFilter,
        budget: Budget,
        chunks: MutableList<ContextChunk>,
        notes: MutableList<String>,
        included: MutableSet<String>,
    ) {
        val path = request.currentFile ?: return
        addPath(path, path.fileName().ifEmpty { "current" }, filter, budget, chunks, notes, included, capHeadTail = true)
    }

    private suspend fun addPinned(
        request: ContextRequest,
        filter: SecretFilter,
        budget: Budget,
        chunks: MutableList<ContextChunk>,
        notes: MutableList<String>,
        included: MutableSet<String>,
    ) {
        for (path in request.pinned) {
            addPath(path, path.fileName().ifEmpty { path.relative }, filter, budget, chunks, notes, included)
        }
    }

    private suspend fun addMentions(
        request: ContextRequest,
        filter: SecretFilter,
        budget: Budget,
        chunks: MutableList<ContextChunk>,
        notes: MutableList<String>,
        ambiguous: MutableList<AmbiguousMention>,
        included: MutableSet<String>,
    ) {
        val mentions = MentionParser.merge(request.prompt, request.mentions)
        for (mention in mentions) {
            addMention(mention, request.currentFile, filter, budget, chunks, notes, ambiguous, included)
        }
    }

    private suspend fun addMention(
        mention: String,
        currentFile: WorkspacePath?,
        filter: SecretFilter,
        budget: Budget,
        chunks: MutableList<ContextChunk>,
        notes: MutableList<String>,
        ambiguous: MutableList<AmbiguousMention>,
        included: MutableSet<String>,
    ) {
        when (val resolved = resolveMention(mention, currentFile, filter)) {
            is MentionResolution.Missing -> {
                notes += "missing @$mention"
                chunks += omittedChunk(null, "@$mention")
            }
            is MentionResolution.Denied -> {
                refused(resolved.path, resolved.reason, notes)
                chunks += omittedChunk(resolved.path, "@$mention")
            }
            is MentionResolution.Ambiguous -> {
                ambiguous += AmbiguousMention(mention, resolved.candidates)
                notes += "ambiguous @$mention — pick one of: ${resolved.candidates.joinToString { it.relative }}"
                chunks += omittedChunk(null, "@$mention")
            }
            is MentionResolution.File -> {
                addPath(resolved.path, "@$mention", filter, budget, chunks, notes, included)
            }
            is MentionResolution.Directory -> {
                addDirectory(resolved.path, mention, filter, budget, chunks, notes, included)
            }
        }
    }

    private suspend fun addPath(
        path: WorkspacePath,
        label: String,
        filter: SecretFilter,
        budget: Budget,
        chunks: MutableList<ContextChunk>,
        notes: MutableList<String>,
        included: MutableSet<String>,
        capHeadTail: Boolean = false,
    ) {
        if (!included.add(path.relative)) return
        val read = readFiltered(path, filter)
        when (read) {
            is ReadOutcome.Ok -> addBlob(label, path, read.text, budget, chunks, notes, capHeadTail)
            is ReadOutcome.Directory -> addDirectory(path, label, filter, budget, chunks, notes, included)
            is ReadOutcome.Denied -> {
                refused(path, read.reason, notes)
                chunks += omittedChunk(path, label)
            }
            ReadOutcome.Missing -> {
                notes += "${path.relative}: missing"
                chunks += omittedChunk(path, label)
            }
        }
    }

    private suspend fun addDirectory(
        path: WorkspacePath,
        mention: String,
        filter: SecretFilter,
        budget: Budget,
        chunks: MutableList<ContextChunk>,
        notes: MutableList<String>,
        included: MutableSet<String>,
    ) {
        included.add(path.relative)
        val listing = buildTreeListing(path, filter)
        if (listing.denied) {
            refused(path, listing.reason ?: "denied", notes)
            chunks += omittedChunk(path, "@$mention")
            return
        }
        val body = StringBuilder(listing.tree)
        if (path.isRoot) {
            notes += "root mention is a listing, not a whole-repo dump"
        } else {
            appendSmallDirFiles(listing.files, filter, budget, body, notes, included)
        }
        val filtered = filter.filterToolResult(body.toString())
        addBlob("@$mention", path, filtered, budget, chunks, notes)
    }

    private suspend fun appendSmallDirFiles(
        files: List<FileMetadata>,
        filter: SecretFilter,
        budget: Budget,
        body: StringBuilder,
        notes: MutableList<String>,
        included: MutableSet<String>,
    ) {
        var added = 0
        for (file in files) {
            if (added >= MAX_DIR_FILES) break
            if (file.sizeBytes > SMALL_DIR_FILE_BYTES) continue
            if (!included.add(file.path.relative)) continue
            val read = readFiltered(file.path, filter)
            if (read !is ReadOutcome.Ok) continue
            if (read.text.text.length > SMALL_DIR_FILE_BYTES) continue
            if (budget.remainingChars <= body.length) break
            body.append("\n\n// file: ").append(file.path.relative).append('\n')
            body.append(read.text.text)
            added++
            if (read.text.redacted) notes += "${file.path.relative}: redacted"
        }
    }

    private fun addBlob(
        label: String,
        path: WorkspacePath?,
        filtered: FilteredText,
        budget: Budget,
        chunks: MutableList<ContextChunk>,
        notes: MutableList<String>,
        capHeadTail: Boolean = false,
    ) {
        for (omission in filtered.omissions) {
            val rel = path?.relative ?: label
            notes += "$rel: $omission"
        }
        if (filtered.text.isEmpty() && filtered.redacted) {
            chunks += omittedChunk(path, label)
            return
        }
        val taken = budget.take(filtered.text, capHeadTail)
        if (taken == null) {
            notes += "${path?.relative ?: label}: budget"
            chunks += omittedChunk(path, label)
            return
        }
        if (taken.truncated) notes += "${path?.relative ?: label}: truncated"
        chunks += ContextChunk(path, label, taken.text, omitted = false)
    }

    private suspend fun resolveMention(
        mention: String,
        currentFile: WorkspacePath?,
        filter: SecretFilter,
    ): MentionResolution {
        val spec = MentionParser.normalize(mention)
        if (spec.isEmpty()) return MentionResolution.Missing(mention)
        val dirIntent = spec.endsWith('/')
        val body = spec.trimEnd('/')
        val exact = exactHits(body, currentFile)
        if (exact.isNotEmpty()) return finishExact(mention, exact, dirIntent, filter)
        if ('/' in body) return MentionResolution.Missing(mention)
        return finishSearch(mention, body, dirIntent, filter)
    }

    private suspend fun exactHits(spec: String, currentFile: WorkspacePath?): List<FileMetadata> {
        val out = ArrayList<FileMetadata>(2)
        addIfExists(out, resolveAgainst(WorkspacePath.ROOT, spec))
        val currentDir = currentFile?.parentPath() ?: WorkspacePath.ROOT
        if (!currentDir.isRoot) addIfExists(out, resolveAgainst(currentDir, spec))
        return out.distinctBy { it.path.relative }
    }

    private suspend fun addIfExists(out: MutableList<FileMetadata>, path: WorkspacePath?) {
        if (path == null) return
        val meta = workspace.metadata(path) ?: return
        out += meta
    }

    private suspend fun finishExact(
        mention: String,
        hits: List<FileMetadata>,
        dirIntent: Boolean,
        filter: SecretFilter,
    ): MentionResolution {
        val allowed = hits.filter { !isDenied(it, filter) }
        if (allowed.isEmpty()) {
            val first = hits.first()
            return MentionResolution.Denied(first.path, denyReason(first, filter))
        }
        if (allowed.size > 1) {
            return MentionResolution.Ambiguous(mention, allowed.map { it.path })
        }
        return toResolved(allowed.single(), dirIntent)
    }

    private suspend fun finishSearch(
        mention: String,
        name: String,
        dirIntent: Boolean,
        filter: SecretFilter,
    ): MentionResolution {
        val found = findByName(name, filter)
        if (found.isEmpty()) {
            val secretHits = findByName(name, filter, includeDenied = true)
            if (secretHits.isNotEmpty()) {
                return MentionResolution.Denied(secretHits.first().path, "secret filename")
            }
            return MentionResolution.Missing(mention)
        }
        if (found.size > 1) {
            return MentionResolution.Ambiguous(mention, found.map { it.path })
        }
        return toResolved(found.single(), dirIntent)
    }

    private fun toResolved(meta: FileMetadata, dirIntent: Boolean): MentionResolution {
        if (meta.isDirectory || dirIntent) return MentionResolution.Directory(meta.path)
        return MentionResolution.File(meta.path)
    }

    private suspend fun findByName(
        name: String,
        filter: SecretFilter,
        includeDenied: Boolean = false,
    ): List<FileMetadata> {
        val matches = ArrayList<FileMetadata>()
        val queue = ArrayDeque<WorkspacePath>()
        queue.add(WorkspacePath.ROOT)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_WALK) {
            val children = workspace.list(queue.removeFirst())
            visited = collectNamed(children, name, filter, includeDenied, queue, matches, visited)
        }
        return matches
    }

    private fun collectNamed(
        children: List<FileMetadata>,
        name: String,
        filter: SecretFilter,
        includeDenied: Boolean,
        queue: ArrayDeque<WorkspacePath>,
        matches: MutableList<FileMetadata>,
        visited: Int,
    ): Int {
        var count = visited
        for (child in children) {
            count++
            if (count > MAX_WALK) break
            considerChild(child, name, filter, includeDenied, queue, matches)
        }
        return count
    }

    private fun considerChild(
        child: FileMetadata,
        name: String,
        filter: SecretFilter,
        includeDenied: Boolean,
        queue: ArrayDeque<WorkspacePath>,
        matches: MutableList<FileMetadata>,
    ) {
        val denied = isDenied(child, filter)
        if (shouldWalk(child, denied, includeDenied)) queue.add(child.path)
        if (!child.path.fileName().equals(name, ignoreCase = true)) return
        if (denied && !includeDenied) return
        matches += child
    }

    private suspend fun readFiltered(path: WorkspacePath, filter: SecretFilter): ReadOutcome {
        val meta = workspace.metadata(path) ?: return ReadOutcome.Missing
        if (meta.isDirectory) return ReadOutcome.Directory
        val decision = filter.decide(path, mimeHint = null, sizeBytes = meta.sizeBytes)
        if (!decision.allowed) return ReadOutcome.Denied(decision.reason ?: "denied")
        return readAllowed(path, filter)
    }

    private suspend fun readAllowed(path: WorkspacePath, filter: SecretFilter): ReadOutcome {
        val text = try {
            workspace.readUtf8(path, SecretLimits.MAX_FILE_BYTES)
        } catch (e: Utf8BomDetectedException) {
            e.strippedUtf8
        } catch (_: BinaryFileException) {
            return ReadOutcome.Denied("binary")
        } catch (_: FileTooLargeException) {
            return ReadOutcome.Denied("oversize")
        } catch (_: Exception) {
            return ReadOutcome.Missing
        }
        return ReadOutcome.Ok(filter.filterText(path, text))
    }

    private suspend fun buildTreeListing(path: WorkspacePath, filter: SecretFilter): DirListing {
        val decision = filter.decide(path, "inode/directory", 0)
        if (!decision.allowed) return DirListing(denied = true, reason = decision.reason)
        val files = ArrayList<FileMetadata>()
        val lines = ArrayList<String>()
        walkTree(path, filter, files, lines, 0)
        val header = if (path.isRoot) "/" else path.relative.trimEnd('/') + "/"
        val tree = (listOf(header) + lines).joinToString("\n")
        return DirListing(tree = tree, files = files)
    }

    private suspend fun walkTree(
        dir: WorkspacePath,
        filter: SecretFilter,
        files: MutableList<FileMetadata>,
        lines: MutableList<String>,
        depth: Int,
    ) {
        if (depth > MAX_DIR_DEPTH || lines.size >= MAX_TREE_LINES) return
        val children = workspace.list(dir)
        for (child in children) {
            if (lines.size >= MAX_TREE_LINES) return
            appendTreeChild(child, filter, files, lines, depth)
        }
    }

    private suspend fun appendTreeChild(
        child: FileMetadata,
        filter: SecretFilter,
        files: MutableList<FileMetadata>,
        lines: MutableList<String>,
        depth: Int,
    ) {
        if (child.path.fileName().equals(".git", ignoreCase = true)) return
        if (isDenied(child, filter)) return
        val indent = "  ".repeat(depth + 1)
        if (child.isDirectory) {
            lines += indent + child.path.fileName() + "/"
            walkTree(child.path, filter, files, lines, depth + 1)
            return
        }
        lines += indent + child.path.fileName()
        files += child
    }

    private fun shouldWalk(
        child: FileMetadata,
        denied: Boolean,
        includeDenied: Boolean,
    ): Boolean {
        if (!child.isDirectory) return false
        if (child.path.fileName().equals(".git", ignoreCase = true) && !includeDenied) return false
        return !denied || includeDenied
    }

    private fun isDenied(meta: FileMetadata, filter: SecretFilter): Boolean {
        val mime = if (meta.isDirectory) "inode/directory" else null
        return !filter.decide(meta.path, mime, meta.sizeBytes).allowed
    }

    private fun denyReason(meta: FileMetadata, filter: SecretFilter): String {
        val mime = if (meta.isDirectory) "inode/directory" else null
        val decision: FilterDecision = filter.decide(meta.path, mime, meta.sizeBytes)
        return decision.reason ?: "denied"
    }

    private fun refused(path: WorkspacePath?, reason: String, notes: MutableList<String>) {
        val rel = path?.relative ?: "?"
        notes += "$rel: $reason"
        logger?.i("secret", "Clanker asked for $rel and was refused")
    }

    private fun toPacket(
        chunks: List<ContextChunk>,
        notes: List<String>,
        ambiguous: List<AmbiguousMention>,
    ): ContextPacket {
        val chars = chunks.sumOf { if (it.omitted) 0 else it.text.length }
        return ContextPacket(
            chunks = chunks,
            estimatedTokens = (chars + 3) / 4,
            filterNotes = notes,
            ambiguous = ambiguous,
        )
    }
}

private fun omittedChunk(path: WorkspacePath?, label: String) =
    ContextChunk(path, label, text = "", omitted = true)

private fun effectiveMax(maxChars: Int): Int =
    if (maxChars > 0) maxChars else DEFAULT_MAX_CHARS

private class Budget(initialChars: Int) {
    var remainingChars: Int = initialChars
        private set
    var remainingPacket: Long = SecretLimits.MAX_PACKET_BYTES
        private set

    fun take(text: String, capHeadTail: Boolean): Taken? {
        if (remainingChars <= 0 || remainingPacket <= 0L) return null
        val utf8 = text.toByteArray(StandardCharsets.UTF_8).size.toLong()
        val fitsChars = text.length <= remainingChars
        val fitsPacket = utf8 <= remainingPacket
        if (fitsChars && fitsPacket) {
            remainingChars -= text.length
            remainingPacket -= utf8
            return Taken(text, truncated = false)
        }
        val cap = minOf(remainingChars, remainingPacket.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        if (cap <= 0) return null
        val clipped = if (capHeadTail) headTail(text, cap) else text.take(cap)
        val clippedBytes = clipped.toByteArray(StandardCharsets.UTF_8).size.toLong()
        remainingChars -= clipped.length
        remainingPacket -= clippedBytes
        return Taken(clipped, truncated = true)
    }
}

private fun headTail(text: String, maxChars: Int): String {
    if (text.length <= maxChars) return text
    if (maxChars <= ELLIPSIS.length) return text.take(maxChars)
    val keep = maxChars - ELLIPSIS.length
    val head = keep / 2
    val tail = keep - head
    return text.take(head) + ELLIPSIS + text.takeLast(tail)
}

private data class Taken(val text: String, val truncated: Boolean)

private sealed interface ReadOutcome {
    data class Ok(val text: FilteredText) : ReadOutcome
    data object Directory : ReadOutcome
    data class Denied(val reason: String) : ReadOutcome
    data object Missing : ReadOutcome
}

private sealed interface MentionResolution {
    data class File(val path: WorkspacePath) : MentionResolution
    data class Directory(val path: WorkspacePath) : MentionResolution
    data class Ambiguous(val mention: String, val candidates: List<WorkspacePath>) : MentionResolution
    data class Denied(val path: WorkspacePath?, val reason: String) : MentionResolution
    data class Missing(val mention: String) : MentionResolution
}

private data class DirListing(
    val tree: String = "",
    val files: List<FileMetadata> = emptyList(),
    val denied: Boolean = false,
    val reason: String? = null,
)

private const val GITIGNORE = ".gitignore"
private const val CLANKYARD_IGNORE = ".clankyardignore"
private const val MAX_WALK = 4_000
private const val MAX_DIR_DEPTH = 6
private const val MAX_TREE_LINES = 200
private const val MAX_DIR_FILES = 20
private const val SMALL_DIR_FILE_BYTES = 8L * 1024
private const val ELLIPSIS = "\n…\n"
