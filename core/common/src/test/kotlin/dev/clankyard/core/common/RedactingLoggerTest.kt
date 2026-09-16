package dev.clankyard.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedactingLoggerTest {
    @Test
    fun redactsSkAnt() {
        val out = SecretRedactor.redact("key=sk-ant-api03-abcdefghijklmnopqrstuvwxyz")
        assertFalse(out.contains("sk-ant-api03"))
        assertTrue(out.contains("[REDACTED]"))
    }

    @Test
    fun redactsSkAndXai() {
        val out = SecretRedactor.redact("sk-proj-abc123 and xai-xyz789")
        assertFalse(out.contains("sk-proj-abc123"))
        assertFalse(out.contains("xai-xyz789"))
        assertTrue(out.contains("[REDACTED]"))
    }

    @Test
    fun redactsBearer() {
        val out = SecretRedactor.redact("Authorization: Bearer tok_live_secret")
        assertFalse(out.contains("tok_live_secret"))
        assertTrue(out.contains("[REDACTED]"))
    }

    @Test
    fun redactsPemBlock() {
        val pem = """
            -----BEGIN PRIVATE KEY-----
            MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC7
            -----END PRIVATE KEY-----
        """.trimIndent()
        val out = SecretRedactor.redact("cert:\n$pem")
        assertFalse(out.contains("MIIEvQIBADANBgkqhkiG9w0BAQE"))
        assertFalse(out.contains("BEGIN PRIVATE KEY"))
        assertTrue(out.contains("[REDACTED_PEM]"))
    }

    @Test
    fun redactsApiKeyAssignment() {
        val out = SecretRedactor.redact("""api_key=supersecret json "api_key": "also-secret"""")
        assertFalse(out.contains("supersecret"))
        assertTrue(out.contains("[REDACTED]"))
    }

    @Test
    fun redactsCauseMessage() {
        val cause = IllegalStateException("upstream Bearer sk-ant-secret-value failed")
        val wrapped = SecretRedactor.wrap(RuntimeException("call failed", cause))
        assertFalse(wrapped.cause!!.message!!.contains("sk-ant-secret-value"))
        assertFalse(wrapped.cause!!.message!!.contains("Bearer sk-ant"))
        assertTrue(wrapped.cause!!.message!!.contains("[REDACTED]"))
    }

    @Test
    fun loggerRedactsAndPrefixesTag() {
        val records = mutableListOf<Record>()
        val logger = DefaultRedactingLogger { level, tag, message, throwable ->
            records += Record(level, tag, message, throwable)
        }
        logger.e("http", "Authorization: Bearer sk-live-abc", IllegalStateException("api_key=leak"))
        assertEquals(1, records.size)
        val rec = records.single()
        assertEquals("clankyard.http", rec.tag)
        assertFalse(rec.message.contains("sk-live-abc"))
        assertTrue(rec.message.contains("[REDACTED]"))
        val thrown = rec.throwable ?: error("expected throwable")
        assertFalse(thrown.message.orEmpty().contains("leak"))
        assertTrue(thrown.message.orEmpty().contains("[REDACTED]"))
    }

    @Test
    fun keepsOrdinaryText() {
        assertEquals("opened src/Main.kt", SecretRedactor.redact("opened src/Main.kt"))
    }

    private data class Record(
        val level: LogSink.Level,
        val tag: String,
        val message: String,
        val throwable: Throwable?,
    )
}
