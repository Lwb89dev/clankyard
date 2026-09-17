package dev.clankyard.ai.providers.xai

import dev.clankyard.ai.provider.ChatEvent
import dev.clankyard.ai.provider.ChatMessage
import dev.clankyard.ai.provider.ChatRequest
import dev.clankyard.ai.provider.ChatRole
import dev.clankyard.ai.provider.ContentPart
import dev.clankyard.ai.provider.http.normalizeCompletionsRoot
import dev.clankyard.core.model.AuthenticationKind
import dev.clankyard.core.model.Credential
import dev.clankyard.core.model.ProviderId
import dev.clankyard.core.model.RequestId
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class XAIProviderTest {
    private val server = MockWebServer()
    private val client = OkHttpClient()
    private val credential = Credential.ApiKey("xai-test-secret")

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun defaultOriginNormalizesToV1() {
        val fromOrigin = normalizeCompletionsRoot(XAIProvider.DEFAULT_ORIGIN)
        val fromV1 = normalizeCompletionsRoot("https://api.x.ai/v1")
        assertEquals("https://api.x.ai/v1".toHttpUrl(), fromOrigin)
        assertEquals(fromOrigin, fromV1)
        val provider = XAIProvider(client)
        assertEquals(ProviderId("xai"), provider.id)
        assertEquals(AuthenticationKind.ApiKey, provider.authenticationKind)
        assertEquals("https://api.x.ai", XAIProvider.DEFAULT_ORIGIN)
    }

    @Test
    fun sharedCompletionsAdapter() = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    """
                    data: {"choices":[{"delta":{"content":"grok"}}]}

                    data: [DONE]

                    """.trimIndent(),
                ),
        )
        val events = XAIProvider(client, server.url("/v1"))
            .chat(sampleRequest(), credential)
            .toList()
        assertEquals("grok", events.filterIsInstance<ChatEvent.Delta>().single().text)
        val recorded = server.takeRequest()
        assertEquals("/v1/chat/completions", recorded.path)
        assertEquals("Bearer xai-test-secret", recorded.getHeader("Authorization"))
        assertTrue(recorded.body.readUtf8().contains("\"max_tokens\":32"))
    }
}

private fun sampleRequest() = ChatRequest(
    requestId = RequestId("req-xai"),
    model = "grok-2",
    messages = listOf(ChatMessage(ChatRole.User, listOf(ContentPart.Text("hi")))),
    maxTokens = 32,
)
