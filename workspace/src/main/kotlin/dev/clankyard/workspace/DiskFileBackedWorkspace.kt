package dev.clankyard.workspace

import dev.clankyard.core.model.ContentHash
import dev.clankyard.core.model.WorkspaceId
import dev.clankyard.core.model.WorkspacePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest

class DiskFileBackedWorkspace private constructor(
    override val id: WorkspaceId,
    override val displayName: String,
    root: File,
    journalDir: File,
    crashPoint: JournalCrashPoint?,
    beforePerform: (() -> Unit)?,
) : FileBackedWorkspace {
    constructor(
        id: WorkspaceId,
        displayName: String,
        root: File,
        journalDir: File,
    ) : this(id, displayName, root, journalDir, null, null)

    override val root: File = root.apply { mkdirs() }.canonicalFile
    override val journal: ChangeJournal = DiskChangeJournal(journalDir, this, crashPoint, beforePerform)

    init {
        (journal as DiskChangeJournal).recover()
        deleteOrphanTemps()
    }

    internal companion object {
        fun forTest(
            id: WorkspaceId,
            displayName: String,
            root: File,
            journalDir: File,
            crashPoint: JournalCrashPoint? = null,
            beforePerform: (() -> Unit)? = null,
        ): DiskFileBackedWorkspace =
            DiskFileBackedWorkspace(id, displayName, root, journalDir, crashPoint, beforePerform)
    }

    override fun resolve(path: WorkspacePath): File {
        if (path.isRoot) return root
        return File(root, path.relative)
    }

    override fun containsCanonical(file: File): Boolean = containsCanonical(root, file)

    override suspend fun metadata(path: WorkspacePath): FileMetadata? =
        withContext(Dispatchers.IO) { metadataBlocking(path) }

    override suspend fun list(path: WorkspacePath): List<FileMetadata> =
        withContext(Dispatchers.IO) { listBlocking(path) }

    override suspend fun openRead(path: WorkspacePath): InputStream =
        withContext(Dispatchers.IO) {
            val dest = resolve(path)
            if (!containsCanonical(dest)) throw PathEscapesWorkspaceException(path)
            if (!dest.isFile) throw FileNotFoundException(path.relative)
            dest.inputStream()
        }

    override suspend fun readUtf8(path: WorkspacePath, maxBytes: Long): String =
        withContext(Dispatchers.IO) { readUtf8Blocking(path, maxBytes) }

    override suspend fun writeAtomic(request: WriteRequest): WriteResult =
        withContext(Dispatchers.IO) { writeAtomicBlocking(request) }

    override suspend fun rename(
        from: WorkspacePath,
        to: WorkspacePath,
        expectedHash: ContentHash?,
    ): WriteResult = withContext(Dispatchers.IO) { renameBlocking(from, to, expectedHash) }

    override suspend fun delete(path: WorkspacePath, expectedHash: ContentHash?): WriteResult =
        withContext(Dispatchers.IO) { deleteBlocking(path, expectedHash) }

    override fun watch(): Flow<WorkspaceChange> = flow {
        var prev = snapshotTree()
        while (true) {
            delay(1_000)
            val now = snapshotTree()
            emitTreeDiff(prev, now)
            prev = now
        }
    }.flowOn(Dispatchers.IO)

    internal fun writeAtomicBlocking(request: WriteRequest): WriteResult {
        if (request.path.isRoot) return WriteResult.Rejected("cannot write workspace root")
        val dest = resolve(request.path)
        parentError(dest, request.createParents)?.let { return it }
        if (dest.isDirectory) return WriteResult.Rejected("is a directory")
        checkExpectedHash(dest, request.expectedHash)?.let { return it }
        return finishAtomicWrite(dest, request.bytes, request.expectedHash)
    }

    internal fun renameBlocking(
        from: WorkspacePath,
        to: WorkspacePath,
        expectedHash: ContentHash?,
    ): WriteResult {
        if (from.isRoot || to.isRoot) return WriteResult.Rejected("cannot rename workspace root")
        if (from == to) return WriteResult.Rejected("source and destination are the same")
        val src = resolve(from)
        val dst = resolve(to)
        if (!containsCanonical(src) || !containsCanonical(dst)) {
            return WriteResult.Rejected("path escapes workspace")
        }
        if (!src.exists()) return WriteResult.Conflict(null, "file missing")
        hashConflictForExisting(src, expectedHash)?.let { return it }
        if (dst.exists()) {
            val actual = if (dst.isFile) hashFile(dst) else null
            return WriteResult.Conflict(actual, "destination exists")
        }
        dst.parentFile?.mkdirs()
        if (!containsCanonical(dst.parentFile ?: dst)) return WriteResult.Rejected("path escapes workspace")
        if (!src.renameTo(dst)) return WriteResult.Rejected("rename failed")
        val newHash = if (dst.isFile) hashFile(dst) else EMPTY_HASH
        return WriteResult.Applied(newHash)
    }

    internal fun deleteBlocking(path: WorkspacePath, expectedHash: ContentHash?): WriteResult {
        if (path.isRoot) return WriteResult.Rejected("cannot delete workspace root")
        val dest = resolve(path)
        if (!containsCanonical(dest)) return WriteResult.Rejected("path escapes workspace")
        if (!dest.exists()) return WriteResult.Conflict(null, "file missing")
        if (dest.isFile) {
            if (expectedHash == null) return WriteResult.Rejected("expectedHash required for files")
            val actual = hashFile(dest)
            if (actual != expectedHash) return WriteResult.Conflict(actual, "expectedHash mismatch")
            if (!dest.delete()) return WriteResult.Rejected("delete failed")
            return WriteResult.Applied(actual)
        }
        if (!deleteUnfollowed(dest)) return WriteResult.Rejected("delete failed")
        return WriteResult.Applied(EMPTY_HASH)
    }

    fun walkFiles(): List<Pair<WorkspacePath, File>> {
        val out = ArrayList<Pair<WorkspacePath, File>>()
        walkDir(root, WorkspacePath.ROOT, out)
        return out
    }

    private fun metadataBlocking(path: WorkspacePath): FileMetadata? {
        val dest = resolve(path)
        if (!containsCanonical(dest) || !dest.exists()) return null
        return toMetadata(path, dest)
    }

    private fun listBlocking(path: WorkspacePath): List<FileMetadata> {
        val dir = resolve(path)
        if (!containsCanonical(dir) || !dir.isDirectory) return emptyList()
        val children = dir.listFiles() ?: return emptyList()
        return children.mapNotNull { child -> childMetadata(path, child) }
            .sortedBy { it.path.relative }
    }

    private fun childMetadata(parent: WorkspacePath, child: File): FileMetadata? {
        if (isAtomicTempName(child.name)) return null
        if (!containsCanonical(child)) return null
        val rel = childRelative(parent, child.name) ?: return null
        return toMetadata(rel, child)
    }

    private fun readUtf8Blocking(path: WorkspacePath, maxBytes: Long): String {
        val dest = resolve(path)
        if (!containsCanonical(dest)) throw PathEscapesWorkspaceException(path)
        if (!dest.isFile) throw FileNotFoundException(path.relative)
        if (dest.length() > maxBytes) throw FileTooLargeException(dest.length(), maxBytes)
        return decodeUtf8OrThrow(path, dest.readBytes())
    }

    private fun decodeUtf8OrThrow(path: WorkspacePath, bytes: ByteArray): String {
        val n = minOf(bytes.size, 8192)
        for (i in 0 until n) {
            if (bytes[i] == 0.toByte()) throw BinaryFileException(path)
        }
        val bom = bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        if (bom) {
            val stripped = String(bytes, 3, bytes.size - 3, StandardCharsets.UTF_8)
            throw Utf8BomDetectedException(path, stripped)
        }
        return String(bytes, StandardCharsets.UTF_8)
    }

    private fun parentError(dest: File, createParents: Boolean): WriteResult? {
        if (!containsCanonical(dest)) return WriteResult.Rejected("path escapes workspace")
        val parent = dest.parentFile ?: return WriteResult.Rejected("destination has no parent")
        if (!containsCanonical(parent)) return WriteResult.Rejected("path escapes workspace")
        if (parent.isFile) return WriteResult.Rejected("parent is a file")
        if (parent.isDirectory) return null
        if (!createParents) return WriteResult.Rejected("parent missing")
        if (!parent.mkdirs() && !parent.isDirectory) return WriteResult.Rejected("cannot create parents")
        return null
    }

    private fun checkExpectedHash(dest: File, expected: ContentHash?): WriteResult.Conflict? {
        val actual = if (dest.exists() && dest.isFile) hashFile(dest) else null
        return hashConflict(actual, expected)
    }

    private fun hashConflictForExisting(src: File, expected: ContentHash?): WriteResult? {
        if (!src.isFile) return null
        if (expected == null) return WriteResult.Rejected("expectedHash required for files")
        val actual = hashFile(src)
        if (actual != expected) return WriteResult.Conflict(actual, "expectedHash mismatch")
        return null
    }

    private fun finishAtomicWrite(dest: File, bytes: ByteArray, expected: ContentHash?): WriteResult {
        val temp = newSameDirTemp(dest)
        try {
            writeAndFsync(temp, bytes)
            checkExpectedHash(dest, expected)?.let {
                temp.delete()
                return it
            }
            if (!renameOver(temp, dest)) {
                temp.delete()
                return WriteResult.Rejected("rename failed")
            }
            return WriteResult.Applied(hashFile(dest))
        } catch (e: Exception) {
            temp.delete()
            return WriteResult.Rejected(e.message ?: "write failed")
        }
    }

    private fun toMetadata(path: WorkspacePath, file: File): FileMetadata {
        val link = Files.isSymbolicLink(file.toPath())
        val isDir = file.isDirectory && !link
        val hash = if (isDir || !file.isFile) null else hashFile(file)
        return FileMetadata(
            path = path,
            sizeBytes = if (isDir) 0L else file.length(),
            isDirectory = isDir,
            hash = hash,
            lastModifiedEpochMs = file.lastModified(),
        )
    }

    private fun walkDir(
        dir: File,
        parent: WorkspacePath,
        out: MutableList<Pair<WorkspacePath, File>>,
    ) {
        val children = dir.listFiles() ?: return
        for (child in children) {
            addWalkedChild(child, parent, out)
        }
    }

    private fun addWalkedChild(
        child: File,
        parent: WorkspacePath,
        out: MutableList<Pair<WorkspacePath, File>>,
    ) {
        if (isAtomicTempName(child.name)) return
        if (GeneratedDirNames.hides(child.name)) return
        if (!containsCanonical(child)) return
        val rel = childRelative(parent, child.name) ?: return
        val link = Files.isSymbolicLink(child.toPath())
        if (child.isDirectory && !link) {
            walkDir(child, rel, out)
            return
        }
        if (child.isFile) out += rel to child
    }

    private suspend fun FlowCollector<WorkspaceChange>.emitTreeDiff(
        prev: Map<String, Long>,
        now: Map<String, Long>,
    ) {
        for ((rel, stamp) in now) {
            if (prev[rel] != stamp) emit(WorkspaceChange.Changed(WorkspacePath.parse(rel)))
        }
        for (rel in prev.keys) {
            if (rel !in now) emit(WorkspaceChange.Deleted(WorkspacePath.parse(rel)))
        }
    }

    private fun snapshotTree(): Map<String, Long> {
        val out = LinkedHashMap<String, Long>()
        for ((path, file) in walkFiles()) {
            out[path.relative] = file.lastModified() xor file.length()
        }
        return out
    }

    private fun deleteOrphanTemps() {
        val orphans = ArrayList<File>()
        collectTemps(root, orphans)
        for (file in orphans) deleteUnfollowed(file)
    }

    private fun collectTemps(dir: File, out: MutableList<File>) {
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (isAtomicTempName(child.name)) {
                out += child
                continue
            }
            if (Files.isSymbolicLink(child.toPath())) continue
            if (child.isDirectory) collectTemps(child, out)
        }
    }

    private fun childRelative(parent: WorkspacePath, name: String): WorkspacePath? {
        val raw = if (parent.isRoot) name else "${parent.relative}/$name"
        return runCatching { WorkspacePath.parse(raw) }.getOrNull()
    }

    private fun hashConflict(actual: ContentHash?, expected: ContentHash?): WriteResult.Conflict? {
        if (expected == null) {
            if (actual != null) return WriteResult.Conflict(actual, "file exists")
            return null
        }
        if (actual == null) return WriteResult.Conflict(null, "file missing")
        if (actual != expected) return WriteResult.Conflict(actual, "expectedHash mismatch")
        return null
    }

    private fun hashFile(file: File): ContentHash {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return ContentHash(toHex(md.digest()))
    }
}

private val EMPTY_HASH = ContentHash("")
private const val HEX = "0123456789abcdef"

private fun toHex(bytes: ByteArray): String {
    val out = CharArray(bytes.size * 2)
    for (i in bytes.indices) {
        val v = bytes[i].toInt() and 0xff
        out[i * 2] = HEX[v ushr 4]
        out[i * 2 + 1] = HEX[v and 0x0f]
    }
    return String(out)
}
