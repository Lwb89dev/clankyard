package dev.clankyard.ai.secret

import dev.clankyard.core.model.WorkspacePath

class DefaultSecretFilter(
    private val extraDenyGlobs: List<String> = emptyList(),
    private val ignoreRules: IgnoreRules = IgnoreRules.NONE,
    private val maxFileBytes: Long = SecretLimits.MAX_FILE_BYTES,
) : SecretFilter {
    override fun withIgnoreRules(rules: IgnoreRules): SecretFilter =
        DefaultSecretFilter(extraDenyGlobs, rules, maxFileBytes)

    override fun withExtraDenyGlobs(globs: List<String>): SecretFilter =
        DefaultSecretFilter(globs, ignoreRules, maxFileBytes)

    override fun decide(path: WorkspacePath, mimeHint: String?, sizeBytes: Long): FilterDecision {
        denyReason(path, mimeHint, sizeBytes)?.let { return deny(it) }
        return ALLOW
    }

    override fun filterText(path: WorkspacePath, text: String): FilteredText {
        val bytes = text.length.toLong()
        val decision = decide(path, mimeHint = "text/plain", sizeBytes = bytes)
        if (!decision.allowed) return FilteredText("", redacted = true, omissions = listOf(decision.reason ?: "denied"))
        if (containsNulInProbe(text)) return FilteredText("", redacted = true, omissions = listOf("binary"))
        return withOversize(redactSecrets(text, isProperties(path)), oversize = false)
    }

    override fun filterToolResult(result: String): FilteredText {
        if (containsNulInProbe(result)) return FilteredText("", redacted = true, omissions = listOf("binary"))
        val oversize = result.length > maxFileBytes
        val clipped = if (oversize) result.substring(0, maxFileBytes.toInt()) else result
        return withOversize(redactSecrets(clipped, propertiesStyle = false), oversize)
    }

    private fun denyReason(path: WorkspacePath, mimeHint: String?, sizeBytes: Long): String? {
        if (sizeBytes < 0) return "unknown size"
        if (sizeBytes > maxFileBytes) return "oversize"
        if (path.isRoot) return rootReason(mimeHint)
        val relative = path.relative
        if (mimeHint.equals("inode/symlink", ignoreCase = true)) return "symlink"
        if (deniedDirectory(relative)) return "secret directory"
        if (deniedByGlob(relative)) return "secret filename"
        if (ignoreRules.denies(relative)) return "ignored"
        if (isBinaryMime(mimeHint)) return "binary"
        if (!isKnownText(path, mimeHint)) return "unknown type"
        return null
    }

    private fun rootReason(mimeHint: String?): String? {
        if (isBinaryMime(mimeHint)) return "binary"
        return null
    }

    private fun deniedByGlob(relative: String): Boolean {
        var prefix = relative
        while (true) {
            if (matchesDenyList(fileNameOf(prefix), prefix)) return true
            val slash = prefix.lastIndexOf('/')
            if (slash < 0) return false
            prefix = prefix.substring(0, slash)
        }
    }

    private fun matchesDenyList(name: String, relative: String): Boolean {
        for (glob in DEFAULT_DENY_GLOBS) {
            if (matchesDenyGlob(name, relative, glob)) return true
        }
        for (glob in extraDenyGlobs) {
            if (matchesDenyGlob(name, relative, glob)) return true
        }
        return false
    }

    private fun withOversize(filtered: FilteredText, oversize: Boolean): FilteredText {
        if (!oversize) return filtered
        return FilteredText(filtered.text, redacted = true, omissions = filtered.omissions + "oversize")
    }
}

private fun deny(reason: String) = FilterDecision(allowed = false, reason = reason, redacted = false)

private val ALLOW = FilterDecision(allowed = true, reason = null, redacted = false)

internal val DEFAULT_DENY_GLOBS = listOf(
    ".env",
    ".env.*",
    "*.pem",
    "*.key",
    "*.p12",
    "*.pfx",
    "*.jks",
    "*.keystore",
    "id_rsa",
    "id_ed25519",
    "*.kdbx",
    "google-services.json",
    "local.properties",
    "secrets.xml",
    "*.secret",
    "credentials.json",
    "service-account*.json",
    "*.apk",
    "*.aab",
    "*.dex",
    "*.class",
    "**/build/**",
    "**/.gradle/**",
)

private val TEXT_EXTENSIONS = setOf(
    "kt", "kts", "java", "py", "js", "mjs", "cjs", "ts", "tsx", "jsx",
    "json", "yml", "yaml", "md", "markdown", "sh", "bash", "zsh", "c", "cc", "cpp",
    "h", "hpp", "xml", "gradle", "properties", "txt", "toml", "html", "htm", "css",
    "rs", "go", "rb", "proto", "sql", "csv", "ini", "cfg", "conf", "gitignore",
    "clankyardignore", "swift", "m", "mm", "dart", "lua", "r", "pl", "php", "scala",
    "groovy", "cmake", "mk", "svg", "editorconfig", "ktm",
)

private val TEXT_BASENAMES = setOf(
    "makefile", "dockerfile", "license", "notice", "readme", "jenkinsfile",
    "gemfile", "rakefile", "procfile", "vagrantfile", ".gitignore", ".clankyardignore",
    ".editorconfig", ".gitattributes",
)

private val TEXT_APP_MIMES = setOf(
    "application/json",
    "application/xml",
    "application/javascript",
    "application/x-javascript",
    "application/yaml",
    "application/x-yaml",
    "application/toml",
    "application/sql",
    "application/x-sh",
    "application/graphql",
    "application/ld+json",
    "image/svg+xml",
)

private fun matchesDenyGlob(name: String, relative: String, glob: String): Boolean {
    val trimmed = glob.trim()
    if (trimmed.isEmpty()) return false
    val slashGlob = trimmed.trimEnd('/')
    if ('/' in slashGlob) return pathGlobMatches(relative, slashGlob, caseSensitive = false)
    return basenameGlobMatches(name, trimmed)
}

private fun deniedDirectory(relative: String): Boolean {
    val parts = relative.split('/')
    for (part in parts) {
        if (part.equals(".ssh", ignoreCase = true)) return true
        if (part.equals(".gnupg", ignoreCase = true)) return true
        if (part.equals("build", ignoreCase = true)) return true
        if (part.equals(".gradle", ignoreCase = true)) return true
        if (part.equals("captures", ignoreCase = true)) return true
        if (part.endsWith(".xcuserdata", ignoreCase = true)) return true
    }
    val lower = relative.lowercase()
    return lower == ".git/hooks" || lower.startsWith(".git/hooks/")
}

private fun isBinaryMime(mimeHint: String?): Boolean {
    if (mimeHint.isNullOrBlank()) return false
    val mime = mimeHint.lowercase()
    if (mime == "inode/directory") return false
    if (mime.startsWith("text/")) return false
    if (mime in TEXT_APP_MIMES) return false
    return true
}

private fun isKnownText(path: WorkspacePath, mimeHint: String?): Boolean {
    if (!mimeHint.isNullOrBlank()) {
        val mime = mimeHint.lowercase()
        if (mime.startsWith("text/") || mime in TEXT_APP_MIMES || mime == "inode/directory") return true
        return false
    }
    val name = fileNameOf(path.relative)
    if (name.lowercase() in TEXT_BASENAMES) return true
    return extensionOf(name) in TEXT_EXTENSIONS
}

private fun isProperties(path: WorkspacePath): Boolean =
    fileNameOf(path.relative).endsWith(".properties", ignoreCase = true)

private fun fileNameOf(relative: String): String {
    val slash = relative.lastIndexOf('/')
    if (slash < 0) return relative
    return relative.substring(slash + 1)
}

private fun extensionOf(name: String): String {
    if (name.startsWith('.') && name.indexOf('.', 1) < 0) return name.drop(1).lowercase()
    val dot = name.lastIndexOf('.')
    if (dot <= 0 || dot == name.lastIndex) return ""
    return name.substring(dot + 1).lowercase()
}
