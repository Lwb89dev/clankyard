package dev.clankyard.build.engine

import dev.clankyard.build.api.BuildDiagnostic
import dev.clankyard.build.api.DiagnosticSeverity
import dev.clankyard.core.model.WorkspacePath

object DiagnosticParser {
    private val KOTLIN = Regex("""^e: file://(.+):(\d+):(\d+) (.+)$""")
    private val JAVAC = Regex("""^(.+\.java):(\d+): error: (.+)$""")
    private val AAPT = Regex("""^(.+\.xml):(\d+): error: (.+)$""")

    fun parse(line: String, workshopRoot: String? = null): BuildDiagnostic? {
        KOTLIN.matchEntire(line.trim())?.let { m ->
            return diagnostic(m.groupValues[1], m.groupValues[2], m.groupValues[3], m.groupValues[4], "kotlinc", workshopRoot)
        }
        JAVAC.matchEntire(line.trim())?.let { m ->
            return diagnostic(m.groupValues[1], m.groupValues[2], "1", m.groupValues[3], "javac", workshopRoot)
        }
        AAPT.matchEntire(line.trim())?.let { m ->
            return diagnostic(m.groupValues[1], m.groupValues[2], "1", m.groupValues[3], "aapt2", workshopRoot)
        }
        return null
    }

    private fun diagnostic(
        file: String,
        line: String,
        col: String,
        message: String,
        source: String,
        workshopRoot: String?,
    ): BuildDiagnostic {
        val path = toWorkspacePath(file, workshopRoot)
        val lineIndex = (line.toIntOrNull() ?: 1).coerceAtLeast(1) - 1
        val colIndex = (col.toIntOrNull() ?: 1).coerceAtLeast(1) - 1
        return BuildDiagnostic(
            path = path,
            line = lineIndex,
            column = colIndex,
            severity = DiagnosticSeverity.Error,
            message = message,
            source = source,
        )
    }

    private fun toWorkspacePath(file: String, workshopRoot: String?): WorkspacePath? {
        val cleaned = file.removePrefix("file://")
        val relative = if (workshopRoot != null && cleaned.startsWith(workshopRoot)) {
            cleaned.removePrefix(workshopRoot).trimStart('/')
        } else {
            cleaned.substringAfterLast('/')
        }
        return runCatching { WorkspacePath.parse(relative) }.getOrNull()
    }
}
