package dev.clankyard.editor

import io.github.rosemoe.sora.lang.EmptyLanguage
import io.github.rosemoe.sora.lang.Language
import io.github.rosemoe.sora.langs.java.JavaLanguage
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage

internal class IdLanguageSupport(
    override val id: String,
    override val displayName: String,
    private val factory: () -> Language,
) : LanguageSupport {
    override fun handles(fileName: String): Boolean = LanguageFileNames.idFor(fileName) == id

    override fun createLanguage(): Language = factory()
}

internal object PlaintextLanguageSupport : LanguageSupport {
    override val id: String = LanguageFileNames.PLAINTEXT
    override val displayName: String = "Plain text"
    override fun handles(fileName: String): Boolean =
        LanguageFileNames.idFor(fileName) == LanguageFileNames.PLAINTEXT
    override fun createLanguage(): Language = EmptyLanguage()
}

internal object BuiltinLanguageSupports {
    val list: List<LanguageSupport> = listOf(
        textMate(LanguageFileNames.KOTLIN, "Kotlin", "source.kotlin"),
        IdLanguageSupport(LanguageFileNames.JAVA, "Java") { JavaLanguage() },
        textMate(LanguageFileNames.PYTHON, "Python", "source.python"),
        textMate(LanguageFileNames.JAVASCRIPT, "JavaScript", "source.tsx"),
        textMate(LanguageFileNames.TYPESCRIPT, "TypeScript", "source.tsx"),
        textMate(LanguageFileNames.JSON, "JSON", "source.json"),
        textMate(LanguageFileNames.YAML, "YAML", "source.yaml"),
        textMate(LanguageFileNames.MARKDOWN, "Markdown", "text.html.markdown"),
        textMate(LanguageFileNames.BASH, "Bash", "source.shell"),
        textMate(LanguageFileNames.C, "C", "source.c"),
        textMate(LanguageFileNames.CPP, "C++", "source.cpp"),
        PlaintextLanguageSupport,
    )

    private fun textMate(id: String, displayName: String, scopeName: String): LanguageSupport =
        IdLanguageSupport(id, displayName) { TextMateLanguage.create(scopeName, true) }
}
