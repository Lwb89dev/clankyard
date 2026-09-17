package dev.clankyard.editor

/**
 * Filename → language id. Pure JVM; used by [LanguageSupport] and unit tests.
 * Unknown extensions map to [PLAINTEXT].
 */
object LanguageFileNames {
    const val KOTLIN = "kotlin"
    const val JAVA = "java"
    const val PYTHON = "python"
    const val JAVASCRIPT = "javascript"
    const val TYPESCRIPT = "typescript"
    const val JSON = "json"
    const val YAML = "yaml"
    const val MARKDOWN = "markdown"
    const val BASH = "bash"
    const val C = "c"
    const val CPP = "cpp"
    const val PLAINTEXT = "plaintext"

    fun idFor(fileName: String): String {
        val base = fileName.substringAfterLast('/').substringAfterLast('\\').lowercase()
        return idForBaseName(base)
    }

    internal fun idForBaseName(base: String): String {
        if (base.endsWith(".gradle.kts") || base.endsWith(".kts") || base.endsWith(".kt")) {
            return KOTLIN
        }
        val ext = base.substringAfterLast('.', missingDelimiterValue = "")
        return when (ext) {
            "java" -> JAVA
            "py", "pyi", "pyw" -> PYTHON
            "js", "mjs", "cjs", "jsx" -> JAVASCRIPT
            "ts", "mts", "cts", "tsx" -> TYPESCRIPT
            "json" -> JSON
            "yaml", "yml" -> YAML
            "md", "markdown", "mdown" -> MARKDOWN
            "sh", "bash", "zsh", "ksh" -> BASH
            "c", "h" -> C
            "cpp", "cc", "cxx", "hpp", "hh", "hxx", "c++", "h++" -> CPP
            else -> PLAINTEXT
        }
    }
}
