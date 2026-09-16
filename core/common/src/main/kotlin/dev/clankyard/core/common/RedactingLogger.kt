package dev.clankyard.core.common

interface RedactingLogger {
    fun d(tag: String, message: String)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String, t: Throwable? = null)
    fun e(tag: String, message: String, t: Throwable? = null)
}

class DefaultRedactingLogger(
    private val emit: (level: String, tag: String, message: String, throwable: Throwable?) -> Unit,
) : RedactingLogger {
    override fun d(tag: String, message: String) = log("DEBUG", tag, message, null)

    override fun i(tag: String, message: String) = log("INFO", tag, message, null)

    override fun w(tag: String, message: String, t: Throwable?) = log("WARN", tag, message, t)

    override fun e(tag: String, message: String, t: Throwable?) = log("ERROR", tag, message, t)

    private fun log(level: String, tag: String, message: String, t: Throwable?) {
        emit(level, prefix(tag), SecretRedactor.redact(message), t?.let(SecretRedactor::wrap))
    }
}

private fun prefix(tag: String): String =
    if (tag.startsWith(TAG_PREFIX)) tag else TAG_PREFIX + tag

private const val TAG_PREFIX = "clankyard."
