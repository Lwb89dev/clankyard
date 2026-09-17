package dev.clankyard.editor

import io.github.rosemoe.sora.lang.Language

interface LanguageSupport {
    val id: String
    val displayName: String
    fun handles(fileName: String): Boolean
    fun createLanguage(): Language
}
