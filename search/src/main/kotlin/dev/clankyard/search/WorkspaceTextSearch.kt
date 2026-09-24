package dev.clankyard.search

import dev.clankyard.core.model.WorkspacePath
import dev.clankyard.workspace.BinaryFileException
import dev.clankyard.workspace.FileTooLargeException
import dev.clankyard.workspace.Utf8BomDetectedException
import dev.clankyard.workspace.Workspace
import kotlinx.coroutines.CancellationException

class WorkspaceTextSearch : TextSearch {
    override suspend fun search(workspace: Workspace, query: SearchQuery): List<SearchHit> {
        if (query.pattern.isEmpty() || query.maxHits <= 0) return emptyList()
        val hits = ArrayList<SearchHit>()
        collect(workspace, query.path ?: WorkspacePath.ROOT, query, hits)
        return hits
    }

    private suspend fun collect(
        workspace: Workspace,
        path: WorkspacePath,
        query: SearchQuery,
        hits: MutableList<SearchHit>,
    ) {
        if (hits.size >= query.maxHits) return
        val meta = workspace.metadata(path) ?: return
        if (!meta.isDirectory) {
            searchFile(workspace, path, query, hits)
            return
        }
        if (isSkippedDir(path)) return
        for (child in workspace.list(path)) {
            if (hits.size >= query.maxHits) return
            collect(workspace, child.path, query, hits)
        }
    }

    private suspend fun searchFile(
        workspace: Workspace,
        path: WorkspacePath,
        query: SearchQuery,
        hits: MutableList<SearchHit>,
    ) {
        val text = readSearchable(workspace, path, query.maxFileBytes) ?: return
        var lineNo = 1
        for (line in text.lineSequence()) {
            addLineHits(path, lineNo, line, query, hits)
            if (hits.size >= query.maxHits) return
            lineNo++
        }
    }

    private fun addLineHits(
        path: WorkspacePath,
        lineNo: Int,
        line: String,
        query: SearchQuery,
        hits: MutableList<SearchHit>,
    ) {
        var from = 0
        while (from <= line.length - query.pattern.length) {
            val at = line.indexOf(query.pattern, from)
            if (at < 0) return
            hits += SearchHit(path, lineNo, at + 1, line)
            from = at + query.pattern.length
            if (hits.size >= query.maxHits) return
        }
    }

    private suspend fun readSearchable(
        workspace: Workspace,
        path: WorkspacePath,
        maxFileBytes: Long,
    ): String? {
        return try {
            workspace.readUtf8(path, maxFileBytes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Utf8BomDetectedException) {
            e.strippedUtf8
        } catch (_: BinaryFileException) {
            null
        } catch (_: FileTooLargeException) {
            null
        } catch (_: Exception) {
            null
        }
    }
}

internal fun isSkippedDir(path: WorkspacePath): Boolean {
    if (path.isRoot) return false
    return path.relative == ".git" || path.relative.endsWith("/.git")
}
