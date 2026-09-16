package dev.clankyard.core.common

object SecretRedactor {
    private val pem = Regex(
        """-----BEGIN [A-Z0-9 ]+-----.*?-----END [A-Z0-9 ]+-----""",
        setOf(RegexOption.DOT_MATCHES_ALL),
    )
    private val tokens = listOf(
        Regex("""sk-ant-[^\s"'\\]+"""),
        Regex("""sk-proj-[^\s"'\\]+"""),
        Regex("""sk-[^\s"'\\]+"""),
        Regex("""xai-[^\s"'\\]+"""),
        Regex("""Bearer\s+\S+"""),
        Regex("""(?i)api[_-]?key\s*[:=]\s*\S+"""),
    )

    fun redact(text: String): String {
        var out = pem.replace(text, "[REDACTED_PEM]")
        for (pattern in tokens) {
            out = pattern.replace(out, "[REDACTED]")
        }
        return out
    }

    fun wrap(error: Throwable): Throwable {
        val cause = error.cause?.let(::wrap)
        return Throwable(redact(error.message.orEmpty()), cause).also { copy ->
            copy.stackTrace = error.stackTrace
        }
    }
}
