package dev.clankyard.editor

class LanguageSupportRegistry(
    supports: List<LanguageSupport>,
    private val fallback: LanguageSupport = PlaintextLanguageSupport,
) {
    private val ordered = supports.filter { it.id != fallback.id }

    fun all(): List<LanguageSupport> = ordered

    fun forFileName(fileName: String): LanguageSupport =
        ordered.firstOrNull { it.handles(fileName) } ?: fallback

    companion object {
        val Default: LanguageSupportRegistry by lazy {
            LanguageSupportRegistry(BuiltinLanguageSupports.list)
        }
    }
}
