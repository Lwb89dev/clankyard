package dev.clankyard.editor

import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.langs.java.JavaLanguage
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage

internal class ExtensionLanguageSupport(
    override val id: String,
    override val displayName: String,
    private val extensions: Set<String>,
    private val factory: () -> Language,
) : LanguageSupport {
    override fun handles(fileName: String): Boolean = fileExtension(fileName) in extensions

    override fun createLanguage(): Language = factory()
}

internal object PlaintextLanguageSupport : LanguageSupport {
    override val id: String = "plaintext"
    override val displayName: String = "Plain text"
    override fun handles(fileName: String): Boolean = false
    override fun createLanguage(): Language = EmptyLanguage()
}

internal object BuiltinLanguageSupports {
    val list: List<LanguageSupport> = listOf(
        textMate("kotlin", "Kotlin", "source.kotlin", "kt", "kts"),
        ext("java", "Java", "java") { JavaLanguage() },
        textMate("python", "Python", "source.python", "py", "pyi", "pyw"),
        textMate("javascript", "JavaScript", "source.ts", "js", "mjs", "cjs"),
        textMate("typescript", "TypeScript", "source.ts", "ts", "mts", "cts"),
        textMate("tsx", "TSX", "source.tsx", "tsx", "jsx"),
        textMate("json", "JSON", "source.json", "json"),
        textMate("markdown", "Markdown", "text.html.markdown", "md", "markdown", "mdown"),
        textMate("bash", "Bash", "source.shell", "sh", "bash", "zsh", "ksh"),
        textMate("c", "C", "source.c", "c", "h"),
        textMate("cpp", "C++", "source.cpp", "cpp", "cc", "cxx", "hpp", "hh", "hxx", "c++", "h++"),
    )

    private fun textMate(
        id: String,
        displayName: String,
        scopeName: String,
        vararg extensions: String,
    ): LanguageSupport = ext(id, displayName, *extensions) {
        TextMateLanguage.create(scopeName, true)
    }

    private fun ext(
        id: String,
        displayName: String,
        vararg extensions: String,
        factory: () -> Language,
    ): LanguageSupport = ExtensionLanguageSupport(id, displayName, extensions.toSet(), factory)
}

internal fun fileExtension(fileName: String): String {
    val base = fileName.substringAfterLast('/').substringAfterLast('\\')
    return base.substringAfterLast('.', missingDelimiterValue = "").lowercase()
}
