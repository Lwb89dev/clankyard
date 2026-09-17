package dev.clankyard.ai.provider.http

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
}
