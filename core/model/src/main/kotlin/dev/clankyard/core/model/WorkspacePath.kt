package dev.clankyard.core.model

/**
 * Path relative to the workspace root. `/`-separated.
 * [ROOT] is the only empty value (`relative == ""`) and is legal for list/metadata.
 * Intermediate segments must be non-empty and must not be `.` or `..`.
 */
@JvmInline
value class WorkspacePath private constructor(val relative: String) {
    val isRoot: Boolean get() = relative.isEmpty()

    companion object {
        val ROOT = WorkspacePath("")

        /**
         * Parse a tool/UI string. Rejects `\`, NUL, ISO control, `:`, leading `/`,
         * trailing `/` (except root), `.` / `..` / empty intermediate segments,
         * Unicode dots U+2024 / U+FF0E.
         * Tools must parse **only** through this function, never `File(args["path"])`.
         */
        fun parse(raw: String): WorkspacePath {
            if (raw.isEmpty() || raw == "/") return ROOT
            require(!raw.startsWith("/")) { "absolute paths forbidden" }
            require('\\' !in raw && ':' !in raw && '\u0000' !in raw) { "illegal path char" }
            require(raw.none { it.isISOControl() || it == '\u2024' || it == '\uFF0E' }) { "illegal path char" }
            require(!raw.endsWith("/")) { "trailing slash forbidden" }
            val parts = raw.split("/")
            require(parts.none { it.isEmpty() || it == "." || it == ".." }) { "illegal path segment" }
            return WorkspacePath(raw)
        }
    }
}
