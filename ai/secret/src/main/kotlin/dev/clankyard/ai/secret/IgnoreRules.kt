package dev.clankyard.ai.secret

/**
 * Gitignore-style extra denials. Negation (`!`) lines are ignored:
 * ignore files never become an allow-list.
 */
class IgnoreRules internal constructor(
    private val patterns: List<IgnorePattern>,
) {
    fun denies(relativePath: String): Boolean {
        if (relativePath.isEmpty() || patterns.isEmpty()) return false
        if (matchesAny(relativePath, asDirectory = false)) return true
        return ancestorDenied(relativePath)
    }

    private fun ancestorDenied(relativePath: String): Boolean {
        var prefix = relativePath
        while (true) {
            val slash = prefix.lastIndexOf('/')
            if (slash < 0) return matchesAny(prefix, asDirectory = true)
            prefix = prefix.substring(0, slash)
            if (matchesAny(prefix, asDirectory = true)) return true
        }
    }

    private fun matchesAny(relativePath: String, asDirectory: Boolean): Boolean {
        for (pattern in patterns) {
            if (pattern.matches(relativePath, asDirectory)) return true
        }
        return false
    }

    companion object {
        val NONE = IgnoreRules(emptyList())

        fun parse(gitignore: String, clankyardIgnore: String): IgnoreRules {
            val patterns = parsePatterns(gitignore) + parsePatterns(clankyardIgnore)
            if (patterns.isEmpty()) return NONE
            return IgnoreRules(patterns)
        }

        private fun parsePatterns(text: String): List<IgnorePattern> {
            val out = ArrayList<IgnorePattern>()
            for (raw in text.split('\n')) {
                parseLine(raw)?.let { out += it }
            }
            return out
        }

        private fun parseLine(raw: String): IgnorePattern? {
            var line = raw.trimEnd()
            if (line.endsWith('\\')) {
                line = line.dropLast(1) + " "
            } else {
                line = line.trimEnd()
            }
            val trimmed = line.trimStart()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return null
            if (trimmed.startsWith("!")) return null
            return IgnorePattern.parse(trimmed)
        }
    }
}

internal data class IgnorePattern(
    val glob: String,
    val dirOnly: Boolean,
    val anyDirectory: Boolean,
) {
    fun matches(relativePath: String, asDirectory: Boolean): Boolean {
        if (dirOnly && !asDirectory) return false
        if (anyDirectory) return basenameGlobMatches(fileNameOf(relativePath), glob)
        return pathGlobMatches(relativePath, glob, caseSensitive = true)
    }

    companion object {
        fun parse(raw: String): IgnorePattern {
            var glob = raw
            val dirOnly = glob.endsWith('/')
            if (dirOnly) glob = glob.trimEnd('/')
            val anchored = glob.startsWith('/')
            if (anchored) glob = glob.removePrefix("/")
            val anyDirectory = !anchored && '/' !in glob
            return IgnorePattern(glob, dirOnly, anyDirectory)
        }
    }
}

private fun fileNameOf(relative: String): String {
    val slash = relative.lastIndexOf('/')
    if (slash < 0) return relative
    return relative.substring(slash + 1)
}
