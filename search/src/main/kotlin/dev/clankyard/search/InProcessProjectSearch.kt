package dev.clankyard.search

import dev.clankyard.core.model.WorkspacePath
import dev.clankyard.workspace.BinaryFileException
import dev.clankyard.workspace.FileMetadata
import dev.clankyard.workspace.FileTooLargeException
import dev.clankyard.workspace.Utf8BomDetectedException
import dev.clankyard.workspace.Workspace
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        val text = readSearchText(workspace, meta.path, query.maxFileBytes) ?: return
        scanText(meta.path, text, query, hits)
    }
}

private suspend fun readSearchText(
    workspace: Workspace,
    path: WorkspacePath,
    maxFileBytes: Long,
): String? =
    try {
        workspace.readUtf8(path, maxFileBytes)
    } catch (e: CancellationException) {
        throw e
    } catch (bom: Utf8BomDetectedException) {
        bom.strippedUtf8
    } catch (_: BinaryFileException) {
        null
    } catch (_: FileTooLargeException) {
        null
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
