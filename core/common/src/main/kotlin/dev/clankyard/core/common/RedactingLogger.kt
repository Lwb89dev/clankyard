package dev.clankyard.core.common

interface RedactingLogger {
    fun d(tag: String, message: String)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String, t: Throwable? = null)
    fun e(tag: String, message: String, t: Throwable? = null)
}

fun interface LogSink {
    enum class Level { DEBUG, INFO, WARN, ERROR }

    fun log(level: Level, tag: String, message: String, throwable: Throwable?)
}

class DefaultRedactingLogger(
    private val sink: LogSink,
) : RedactingLogger {
    override fun d(tag: String, message: String) {
        sink.log(LogSink.Level.DEBUG, prefix(tag), SecretRedactor.redact(message), null)
    }

    override fun i(tag: String, message: String) {
        sink.log(LogSink.Level.INFO, prefix(tag), SecretRedactor.redact(message), null)
    }

    override fun w(tag: String, message: String, t: Throwable?) {
        sink.log(LogSink.Level.WARN, prefix(tag), SecretRedactor.redact(message), t?.let(SecretRedactor::wrap))
    }

    override fun e(tag: String, message: String, t: Throwable?) {
        sink.log(LogSink.Level.ERROR, prefix(tag), SecretRedactor.redact(message), t?.let(SecretRedactor::wrap))
    }
}

private fun prefix(tag: String): String =
    if (tag.startsWith(TAG_PREFIX)) tag else TAG_PREFIX + tag

private const val TAG_PREFIX = "clankyard."
