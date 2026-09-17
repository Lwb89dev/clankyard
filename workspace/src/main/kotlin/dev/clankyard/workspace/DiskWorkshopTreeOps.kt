package dev.clankyard.workspace

import dev.clankyard.core.model.WorkspaceId
import dev.clankyard.core.model.WorkspacePath
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DiskWorkshopTreeOps(
    private val lookup: (WorkspaceId) -> DiskFileBackedWorkspace?,
) : WorkshopTreeOps {
    override fun requireFreeSpace(destVolume: File, uncompressedSize: Long) {
        val usable = destVolume.usableSpace
        val required = requiredBytesForCopy(uncompressedSize)
        if (usable < required) throw InsufficientSpaceException(usable, required)
    }

    override suspend fun planExportToTree(id: WorkspaceId, destRoot: File): TreeExportPlan =
        planExportToTreeBlocking(id, destRoot)

    private fun planExportToTreeBlocking(id: WorkspaceId, destRoot: File): TreeExportPlan {
        val ws = lookup(id) ?: error("unknown workspace ${id.value}")
        val src = relativeFileSet(ws)
        val dest = relativeFileSet(destRoot)
        var created = 0
        var overwritten = 0
        for (rel in src) {
            if (rel in dest) overwritten++ else created++
        }
        val extra = dest.count { it !in src }
        return TreeExportPlan(created, overwritten, extra)
    }

    internal fun exportToFileTree(id: WorkspaceId, destRoot: File): TreeExportPlan {
        val ws = lookup(id) ?: error("unknown workspace ${id.value}")
        val plan = planExportToTreeBlocking(id, destRoot)
        destRoot.mkdirs()
        val destCanon = destRoot.canonicalFile
        for ((path, file) in ws.walkFiles()) {
            copyExportFile(file, destCanon, path)
        }
        return plan
    }

    fun zipTo(id: WorkspaceId, output: OutputStream) {
        val ws = lookup(id) ?: error("unknown workspace ${id.value}")
        ZipOutputStream(output).use { zip ->
            for ((path, file) in ws.walkFiles()) {
                zip.putNextEntry(ZipEntry(path.relative))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    fun relativeFileSet(ws: DiskFileBackedWorkspace): Set<String> =
        ws.walkFiles().map { it.first.relative }.toSet()

    fun relativeFileSet(root: File): Set<String> =
        relativeFiles(root).map { it.first }.toSet()

    private fun copyExportFile(file: File, destRoot: File, path: WorkspacePath) {
        val dest = File(destRoot, path.relative)
        val canon = dest.canonicalFile
        if (!containsCanonical(destRoot, canon)) return
        dest.parentFile?.mkdirs()
        file.inputStream().use { input ->
            dest.outputStream().use { input.copyTo(it) }
        }
    }

    private fun relativeFiles(root: File): List<Pair<String, File>> {
        if (!root.isDirectory) return emptyList()
        val out = ArrayList<Pair<String, File>>()
        val canon = runCatching { root.canonicalFile }.getOrNull() ?: return emptyList()
        collectFiles(canon, canon, "", out)
        return out
    }

    private fun collectFiles(
        walkRoot: File,
        dir: File,
        rel: String,
        out: MutableList<Pair<String, File>>,
    ) {
        val children = dir.listFiles() ?: return
        for (child in children) {
            collectChild(walkRoot, child, rel, out)
        }
    }

    private fun collectChild(
        walkRoot: File,
        child: File,
        parentRel: String,
        out: MutableList<Pair<String, File>>,
    ) {
        if (!containsCanonical(walkRoot, child)) return
        val name = child.name
        val rel = if (parentRel.isEmpty()) name else "$parentRel/$name"
        if (runCatching { WorkspacePath.parse(rel) }.isFailure) return
        if (child.isDirectory && !java.nio.file.Files.isSymbolicLink(child.toPath())) {
            collectFiles(walkRoot, child, rel, out)
            return
        }
        if (child.isFile) out += rel to child
    }
}
