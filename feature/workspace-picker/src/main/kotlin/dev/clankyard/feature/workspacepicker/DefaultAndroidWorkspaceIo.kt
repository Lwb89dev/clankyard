package dev.clankyard.feature.workspacepicker

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import dev.clankyard.core.model.WorkspaceId
import dev.clankyard.core.model.WorkspacePath
import dev.clankyard.workspace.DiskFileBackedWorkspace
import dev.clankyard.workspace.FileWorkspaceRegistry
import dev.clankyard.workspace.TreeExportPlan
import dev.clankyard.workspace.WriteRequest
import dev.clankyard.workspace.WriteResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DefaultAndroidWorkspaceIo(
    private val context: Context,
    private val registry: FileWorkspaceRegistry,
) : AndroidWorkspaceIo {
    override suspend fun copyInFromTree(
        treeUri: Uri,
        onProgress: (copied: Long, total: Long) -> Unit,
    ): WorkspaceId = withContext(Dispatchers.IO) {
        val src = DocumentFile.fromTreeUri(context, treeUri) ?: error("bad tree URI")
        val files = collectSafFiles(src)
        val total = files.sumOf { documentLength(it.doc) }
        registry.treeOps.requireFreeSpace(context.filesDir, total)
        val ws = registry.prepareCopy(src.name ?: "project")
        try {
            copyFiles(files, ws, total, onProgress)
            registry.publish(ws).id
        } catch (e: Exception) {
            registry.discardUnpublished(ws)
            throw e
        }
    }

    override suspend fun openInPlaceIfSafe(file: File): WorkspaceId? = withContext(Dispatchers.IO) {
        if (!isOexclSafeAppSpecific(file)) return@withContext null
        registry.openRoot(file)?.id ?: registry.registerInPlace(file, file.name).id
    }

    private fun isOexclSafeAppSpecific(file: File): Boolean {
        val c = runCatching { file.canonicalFile }.getOrNull() ?: return false
        if (!c.isDirectory) return false
        val roots = listOfNotNull(
            context.filesDir,
            context.noBackupFilesDir,
            context.getExternalFilesDir(null),
        ).map { it.canonicalFile }
        return roots.any { c == it || c.path.startsWith(it.path + File.separatorChar) }
    }

    override suspend fun exportZip(id: WorkspaceId, destUri: Uri) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(destUri)?.use { out ->
            registry.treeOps.zipTo(id, out)
        } ?: error("cannot write zip")
    }

    override suspend fun planExportToTree(id: WorkspaceId, treeUri: Uri): TreeExportPlan =
        withContext(Dispatchers.IO) { planBlocking(id, treeUri) }

    override suspend fun exportToTree(id: WorkspaceId, treeUri: Uri): TreeExportPlan =
        withContext(Dispatchers.IO) {
            val ws = registry.open(id) ?: error("unknown workspace ${id.value}")
            val dest = DocumentFile.fromTreeUri(context, treeUri) ?: error("bad tree URI")
            val plan = planBlocking(id, treeUri)
            for ((path, file) in ws.walkFiles()) {
                val doc = ensureFile(dest, path.relative)
                context.contentResolver.openOutputStream(doc.uri, "wt")?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                } ?: error("cannot write ${path.relative}")
            }
            plan
        }

    private fun planBlocking(id: WorkspaceId, treeUri: Uri): TreeExportPlan {
        val ws = registry.open(id) ?: error("unknown workspace ${id.value}")
        val dest = DocumentFile.fromTreeUri(context, treeUri) ?: error("bad tree URI")
        val src = registry.treeOps.relativeFileSet(ws)
        val destRels = collectSafFiles(dest).map { it.rel }.toSet()
        var created = 0
        var overwritten = 0
        for (rel in src) {
            if (rel in destRels) overwritten++ else created++
        }
        return TreeExportPlan(created, overwritten, destRels.count { it !in src })
    }

    private suspend fun copyFiles(
        files: List<SafFile>,
        ws: DiskFileBackedWorkspace,
        total: Long,
        onProgress: (Long, Long) -> Unit,
    ) {
        notifyCopy(0L, total)
        var copied = 0L
        try {
            for (item in files) {
                val remaining = (total - copied).coerceAtLeast(0L)
                registry.treeOps.requireFreeSpace(context.filesDir, remaining)
                copied += copyOne(item, ws)
                onProgress(copied, total)
                notifyCopy(copied, total)
            }
        } finally {
            notifyCopy(copied, total, done = true)
        }
    }

    private suspend fun copyOne(item: SafFile, ws: DiskFileBackedWorkspace): Long {
        val path = WorkspacePath.parse(item.rel)
        val bytes = context.contentResolver.openInputStream(item.doc.uri)?.use { it.readBytes() }
            ?: error("cannot read ${item.rel}")
        val result = ws.writeAtomic(WriteRequest(path, bytes, expectedHash = null))
        if (result !is WriteResult.Applied) error("copy ${item.rel}: $result")
        return bytes.size.toLong()
    }

    private fun notifyCopy(copied: Long, total: Long, done: Boolean = false) {
        val intent = Intent(context, CopyInForegroundService::class.java)
            .putExtra(CopyInForegroundService.EXTRA_COPIED, copied)
            .putExtra(CopyInForegroundService.EXTRA_TOTAL, total)
            .putExtra(CopyInForegroundService.EXTRA_DONE, done)
        runCatching { context.startForegroundService(intent) }
    }
}

internal data class SafFile(val rel: String, val doc: DocumentFile)

internal fun collectSafFiles(root: DocumentFile): List<SafFile> {
    val out = ArrayList<SafFile>()
    collectSaf(root, "", out)
    return out
}

private fun collectSaf(doc: DocumentFile, rel: String, out: MutableList<SafFile>) {
    if (doc.isFile) {
        if (rel.isNotEmpty()) out += SafFile(rel, doc)
        return
    }
    if (!doc.isDirectory) return
    for (child in doc.listFiles()) {
        val name = child.name ?: continue
        val childRel = if (rel.isEmpty()) name else "$rel/$name"
        collectSaf(child, childRel, out)
    }
}

private fun documentLength(doc: DocumentFile): Long {
    val n = doc.length()
    return if (n < 0) 0 else n
}

private fun ensureFile(root: DocumentFile, relative: String): DocumentFile {
    val parts = relative.split('/')
    var dir = root
    for (i in 0 until parts.lastIndex) {
        dir = findOrCreateDir(dir, parts[i])
    }
    return findOrCreateFile(dir, parts.last())
}

private fun findOrCreateDir(parent: DocumentFile, name: String): DocumentFile {
    parent.listFiles().find { it.isDirectory && it.name == name }?.let { return it }
    return parent.createDirectory(name) ?: error("cannot create directory $name")
}

private fun findOrCreateFile(parent: DocumentFile, name: String): DocumentFile {
    parent.listFiles().find { it.isFile && it.name == name }?.let { return it }
    val created = parent.createFile(mimeForName(name), name) ?: error("cannot create file $name")
    if (created.name != name) {
        created.delete()
        error("SAF renamed $name to ${created.name}")
    }
    return created
}

private val TEXT_EXT = setOf(
    "kt", "kts", "java", "js", "ts", "tsx", "json", "xml", "md", "txt",
    "gradle", "properties", "yml", "yaml", "sh", "c", "h", "cpp", "hpp",
    "py", "rb", "go", "rs", "toml", "css", "html", "htm",
)

private fun mimeForName(name: String): String {
    val ext = name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    if (ext in TEXT_EXT) return "text/plain"
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
}
