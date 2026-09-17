package dev.clankyard.ai.secret

internal fun basenameGlobMatches(name: String, glob: String): Boolean {
    if (glob.isEmpty()) return false
    return globToRegex(glob, starCrossesSlash = true).matches(name)
}

internal fun pathGlobMatches(relative: String, glob: String, caseSensitive: Boolean): Boolean {
    val regex = globToRegex(glob, starCrossesSlash = false, caseSensitive = caseSensitive)
    return regex.matches(relative)
}

internal fun globToRegex(
    glob: String,
    starCrossesSlash: Boolean,
    caseSensitive: Boolean = false,
): Regex {
    val source = buildGlobSource(glob, starCrossesSlash)
    val options = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
    return Regex("^$source$", options)
}

private fun buildGlobSource(glob: String, starCrossesSlash: Boolean): String {
    val sb = StringBuilder()
    var i = 0
    while (i < glob.length) {
        i += appendGlobToken(sb, glob, i, starCrossesSlash)
    }
    return sb.toString()
}

private fun appendGlobToken(
    sb: StringBuilder,
    glob: String,
    i: Int,
    starCrossesSlash: Boolean,
): Int {
    if (glob.startsWith("**/", i)) {
        sb.append("(?:.*/)?")
        return 3
    }
    if (glob.startsWith("**", i)) {
        sb.append(".*")
        return 2
    }
    val c = glob[i]
    when (c) {
        '*' -> sb.append(if (starCrossesSlash) ".*" else "[^/]*")
        '?' -> sb.append(if (starCrossesSlash) "." else "[^/]")
        else -> sb.append(Regex.escape(c.toString()))
    }
    return 1
}
