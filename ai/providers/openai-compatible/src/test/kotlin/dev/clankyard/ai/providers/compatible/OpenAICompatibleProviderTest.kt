package dev.clankyard.ai.providers.compatible

import dev.clankyard.ai.provider.ChatEvent
import dev.clankyard.ai.provider.ChatMessage
import dev.clankyard.ai.provider.ChatRequest
import dev.clankyard.ai.provider.ChatRole
import dev.clankyard.ai.provider.ContentPart
import dev.clankyard.ai.provider.http.CleartextEndpointException
import dev.clankyard.ai.provider.http.normalizeCompletionsRoot
import dev.clankyard.core.model.AuthenticationKind
import dev.clankyard.core.model.Credential
import dev.clankyard.core.model.ProviderId
import dev.clankyard.core.model.RequestId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpenAICompatibleProviderTest {
    private val server = MockWebServer()
    private val client = OkHttpClient()
    private val credential = Credential.ApiKey("compat-secret-key")

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
        server.shutdown()
    }

    @Test
    fun httpRejectedWithLocalModelProviderCopy() {
        val error = assertThrows(CleartextEndpointException::class.java) {
            OpenAICompatibleProvider(client, "http://192.168.0.5:11434")
        }
        assertTrue(error.message!!.contains("LocalModelProvider"))
        assertTrue(error.message!!.contains("http://"))
    }

    @Test
    fun httpsHostAndV1BothNormalize() {
        val host = normalizeCompletionsRoot("https://llm.example")
        val v1 = normalizeCompletionsRoot("https://llm.example/v1")
        assertEquals("https://llm.example/v1".toHttpUrl(), host)
        assertEquals(host, v1)
    }

    @Test
    fun tofuProbeHasNoAuthorizationHeader() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
        val provider = OpenAICompatibleProvider(client, server.url("/v1"))
        runCatching { runBlocking { provider.probeTls() } }
        val recorded = server.takeRequest(2, TimeUnit.SECONDS)
        assertNotNull(recorded)
        assertNull(recorded!!.getHeader("Authorization"))
        assertNull(recorded.getHeader("x-api-key"))
        assertFalse(recorded.headers.names().any { it.equals("Authorization", ignoreCase = true) })
    }

    @Test
    fun completionsHappyPath() = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    """
                    data: {"choices":[{"delta":{"content":"compat"}}]}

                    data: [DONE]

                    """.trimIndent(),
                ),
        )
        val provider = OpenAICompatibleProvider(client, server.url("/v1"))
        assertEquals(ProviderId("openai-compatible"), provider.id)
        assertEquals(AuthenticationKind.ApiKey, provider.authenticationKind)
        val events = provider.chat(sampleRequest(), credential).toList()
        assertEquals("compat", events.filterIsInstance<ChatEvent.Delta>().single().text)
        val recorded = server.takeRequest()
        assertEquals("/v1/chat/completions", recorded.path)
        assertEquals("Bearer compat-secret-key", recorded.getHeader("Authorization"))
    }
}

private fun sampleRequest() = ChatRequest(
    requestId = RequestId("req-compat"),
    model = "local-model",
    messages = listOf(ChatMessage(ChatRole.User, listOf(ContentPart.Text("hi")))),
    maxTokens = 16,
)
