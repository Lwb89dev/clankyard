package dev.clankyard.git.slf4j

import dev.clankyard.core.common.SecretRedactor
import org.slf4j.ILoggerFactory
import org.slf4j.IMarkerFactory
import org.slf4j.Logger
import org.slf4j.event.Level
import org.slf4j.helpers.BasicMarkerFactory
import org.slf4j.helpers.LegacyAbstractLogger
import org.slf4j.helpers.MessageFormatter
import org.slf4j.helpers.NOPMDCAdapter
import org.slf4j.spi.MDCAdapter
import org.slf4j.spi.SLF4JServiceProvider
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.LogRecord

class GitSlf4jServiceProvider : SLF4JServiceProvider {
    private val factory = RedactingLoggerFactory()
    private val markers = BasicMarkerFactory()
    private val mdc = NOPMDCAdapter()

    override fun getLoggerFactory(): ILoggerFactory = factory

    override fun getMarkerFactory(): IMarkerFactory = markers

    override fun getMDCAdapter(): MDCAdapter = mdc

    override fun getRequestedApiVersion(): String = "2.0.99"

    override fun initialize() {}
}

private class RedactingLoggerFactory : ILoggerFactory {
    private val loggers = ConcurrentHashMap<String, Logger>()

    override fun getLogger(name: String): Logger =
        loggers.computeIfAbsent(name) { RedactingSlf4jLogger(it) }
}

private class RedactingSlf4jLogger(name: String) : LegacyAbstractLogger() {
    private val jul = java.util.logging.Logger.getLogger(name)

    init {
        this.name = name
    }

    override fun getFullyQualifiedCallerName(): String = name

    override fun isTraceEnabled(): Boolean = false

    override fun isDebugEnabled(): Boolean = false

    override fun isInfoEnabled(): Boolean = true

    override fun isWarnEnabled(): Boolean = true

    override fun isErrorEnabled(): Boolean = true

    override fun handleNormalizedLoggingCall(
        level: Level,
        marker: org.slf4j.Marker?,
        messagePattern: String?,
        arguments: Array<out Any>?,
        throwable: Throwable?,
    ) {
        val args = arguments?.toList()?.toTypedArray()
        val raw = MessageFormatter.basicArrayFormat(messagePattern, args).orEmpty()
        val msg = SecretRedactor.redact(raw)
        val record = LogRecord(julLevel(level), msg)
        record.loggerName = name
        record.thrown = throwable?.let(SecretRedactor::wrap)
        jul.log(record)
    }

    private fun julLevel(level: Level): java.util.logging.Level = when (level) {
        Level.ERROR -> java.util.logging.Level.SEVERE
        Level.WARN -> java.util.logging.Level.WARNING
        Level.INFO -> java.util.logging.Level.INFO
        else -> java.util.logging.Level.FINE
    }
}
