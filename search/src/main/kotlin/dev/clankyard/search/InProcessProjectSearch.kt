package dev.clankyard.search

import dev.clankyard.core.model.WorkspacePath
import dev.clankyard.workspace.FileMetadata
import dev.clankyard.workspace.Workspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets

class InProcessProjectSearch : ProjectSearch {
    override suspend fun search(workspace: Workspace, query: SearchQuery): List<SearchHit> {
        if (query.pattern.isEmpty() || query.maxMatches <= 0) return emptyList()
        return withContext(Dispatchers.IO) {
            val hits = ArrayList<SearchHit>()
            walk(workspace, WorkspacePath.ROOT, query, hits)
            hits
        }
    }

    private suspend fun walk(
        workspace: Workspace,
        dir: WorkspacePath,
        query: SearchQuery,
        hits: MutableList<SearchHit>,
    ) {
        if (hits.size >= query.maxMatches) return
        val children = workspace.list(dir)
        for (child in children) {
            if (hits.size >= query.maxMatches) return
            addChild(workspace, child, query, hits)
        }
    }

    private suspend fun addChild(
        workspace: Workspace,
        child: FileMetadata,
        query: SearchQuery,
        hits: MutableList<SearchHit>,
    ) {
        if (child.isDirectory) {
            walk(workspace, child.path, query, hits)
            return
        }
        searchFile(workspace, child, query, hits)
    }

    private suspend fun searchFile(
        workspace: Workspace,
        meta: FileMetadata,
        query: SearchQuery,
        hits: MutableList<SearchHit>,
    ) {
        if (meta.sizeBytes > query.maxFileBytes) return
        val bytes = runCatching { workspace.openRead(meta.path).use { it.readBytes() } }.getOrNull()
            ?: return
        if (isBinary(bytes)) return
        scanText(meta.path, decodeUtf8(bytes), query, hits)
    }
}

internal fun isBinary(bytes: ByteArray): Boolean {
    val n = minOf(bytes.size, 8192)
    for (i in 0 until n) {
        if (bytes[i] == 0.toByte()) return true
    }
    return false
}

internal fun decodeUtf8(bytes: ByteArray): String {
    val bom = bytes.size >= 3 &&
        bytes[0] == 0xEF.toByte() &&
        bytes[1] == 0xBB.toByte() &&
        bytes[2] == 0xBF.toByte()
    val start = if (bom) 3 else 0
    return String(bytes, start, bytes.size - start, StandardCharsets.UTF_8)
}

internal fun scanText(
    path: WorkspacePath,
    text: String,
    query: SearchQuery,
    hits: MutableList<SearchHit>,
) {
    val lines = text.split('\n')
    for (index in lines.indices) {
        if (hits.size >= query.maxMatches) return
        scanLine(path, lines[index].trimEnd('\r'), index + 1, query, hits)
    }
}

private fun scanLine(
    path: WorkspacePath,
    line: String,
    lineNumber: Int,
    query: SearchQuery,
    hits: MutableList<SearchHit>,
) {
    var start = 0
    while (hits.size < query.maxMatches) {
        val at = line.indexOf(query.pattern, start, ignoreCase = !query.caseSensitive)
        if (at < 0) return
        hits += SearchHit(path, lineNumber, at, line, query.pattern.length)
        start = at + query.pattern.length
        if (start > line.length) return
    }
}
