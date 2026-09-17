package dev.clankyard.ai.secret

/**
 * Gitignore-style extra denials. Negation (`!`) lines are ignored:
 * ignore files never become an allow-list.
 */
class IgnoreRules internal constructor(
    private val groups: List<IgnoreGroup>,
) {
    fun denies(relativePath: String): Boolean {
        if (relativePath.isEmpty() || groups.isEmpty()) return false
        if (matchesAny(relativePath, asDirectory = false)) return true
        return ancestorDenied(relativePath)
    }

    fun isEmpty(): Boolean = groups.isEmpty()

    fun plus(other: IgnoreRules): IgnoreRules {
        if (other.groups.isEmpty()) return this
        if (groups.isEmpty()) return other
        return IgnoreRules(groups + other.groups)
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
        for (group in groups) {
            if (group.matches(relativePath, asDirectory)) return true
        }
        return false
    }

    companion object {
        val NONE = IgnoreRules(emptyList())

        fun parse(gitignore: String, clankyardIgnore: String, baseDir: String = ""): IgnoreRules {
            val groups = ArrayList<IgnoreGroup>(2)
            addGroup(groups, baseDir, gitignore)
            addGroup(groups, baseDir, clankyardIgnore)
            if (groups.isEmpty()) return NONE
            return IgnoreRules(groups)
        }

        fun of(baseDir: String, text: String): IgnoreRules {
            val patterns = parsePatterns(text)
            if (patterns.isEmpty()) return NONE
            return IgnoreRules(listOf(IgnoreGroup(baseDir.trim('/'), patterns)))
        }

        private fun addGroup(out: MutableList<IgnoreGroup>, baseDir: String, text: String) {
            val patterns = parsePatterns(text)
            if (patterns.isEmpty()) return
            out += IgnoreGroup(baseDir.trim('/'), patterns)
        }

        internal fun parsePatterns(text: String): List<IgnorePattern> {
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

internal data class IgnoreGroup(
    val baseDir: String,
    val patterns: List<IgnorePattern>,
) {
    fun matches(relativePath: String, asDirectory: Boolean): Boolean {
        val local = relativize(relativePath, baseDir) ?: return false
        for (pattern in patterns) {
            if (pattern.matches(local, asDirectory)) return true
        }
        return false
    }
}

internal data class IgnorePattern(
    val glob: String,
    val dirOnly: Boolean,
    val anchored: Boolean,
    val anyDirectory: Boolean,
) {
    fun matches(relativePath: String, asDirectory: Boolean): Boolean {
        if (dirOnly && !asDirectory) return false
        if (anyDirectory) return basenameGlobMatches(fileNameOf(relativePath), glob)
        if (anchored) return pathGlobMatches(relativePath, glob, caseSensitive = true)
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
            return IgnorePattern(glob, dirOnly, anchored, anyDirectory)
        }
    }
}

private fun relativize(relativePath: String, baseDir: String): String? {
    if (baseDir.isEmpty()) return relativePath
    if (relativePath == baseDir) return ""
    val prefix = "$baseDir/"
    if (relativePath.startsWith(prefix)) return relativePath.substring(prefix.length)
    return null
}

private fun fileNameOf(relative: String): String {
    val slash = relative.lastIndexOf('/')
    if (slash < 0) return relative
    return relative.substring(slash + 1)
}
