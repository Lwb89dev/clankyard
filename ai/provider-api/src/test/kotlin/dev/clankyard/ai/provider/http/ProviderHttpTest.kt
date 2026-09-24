package dev.clankyard.ai.provider.http

import java.io.IOException
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class ProviderHttpTest {
    @Test
    fun timeoutsMatchSpec() {
        val client = ProviderHttp.client()
        assertEquals(15_000, client.connectTimeoutMillis)
        assertEquals(15_000, client.writeTimeoutMillis)
        assertEquals(30_000, client.readTimeoutMillis)
        val stream = ProviderHttp.streamingClient(client)
        assertEquals(0, stream.readTimeoutMillis)
        assertEquals(15_000, stream.connectTimeoutMillis)
        assertEquals(10.minutes, ProviderHttp.STREAM_WALL_CLOCK)
        assertEquals(ProviderHttp.MAX_REQUESTS, client.dispatcher.maxRequests)
        assertEquals(ProviderHttp.MAX_REQUESTS_PER_HOST, client.dispatcher.maxRequestsPerHost)
        assertFalse(client.followRedirects)
        assertFalse(client.followSslRedirects)
        assertEquals(okhttp3.Authenticator.NONE, client.authenticator)
        assertEquals(okhttp3.CookieJar.NO_COOKIES, client.cookieJar)
    }

    @Test
    fun noBodyLoggingInterceptors() {
        val client = ProviderHttp.client()
        val names = (client.interceptors + client.networkInterceptors).map { it.javaClass.name }
        assertTrue(names.none { it.contains("HttpLogging", ignoreCase = true) })
        assertTrue(names.none { it.contains("logging", ignoreCase = true) })
    }

    @Test
    fun tofuProbeClientIsClean() {
        val probe = ProviderHttp.tofuProbeClient()
        assertTrue(probe.interceptors.isEmpty())
        assertTrue(probe.networkInterceptors.isEmpty())
        assertEquals(okhttp3.Authenticator.NONE, probe.authenticator)
        assertEquals(okhttp3.CookieJar.NO_COOKIES, probe.cookieJar)
        assertFalse(probe.followRedirects)
        assertFalse(probe.followSslRedirects)
    }

    @Test
    fun boundedBodyRejectsOversizedPayload() {
        val body = "12345".toResponseBody("text/plain".toMediaType())
        val error = runCatching { body.readUtf8Limited(4) }.exceptionOrNull()
        assertTrue(error is IOException)
    }

    @Test
    fun sseReaderRejectsOversizedLine() = runBlocking {
        val source = Buffer().writeUtf8(
            "data: ${"x".repeat(ResponseLimits.SSE_LINE_BYTES.toInt())}\n\n",
        )
        val error = runCatching { source.consumeSse { _, _ -> } }.exceptionOrNull()
        assertTrue(error is IOException)
    }
}
