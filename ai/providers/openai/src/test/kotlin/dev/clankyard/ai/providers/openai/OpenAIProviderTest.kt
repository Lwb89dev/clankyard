package dev.clankyard.ai.providers.openai

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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpenAIProviderTest {
    private val server = MockWebServer()
    private val client = OkHttpClient()
    private val credential = Credential.ApiKey("sk-test-openai-key")

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun identityAndDefaultRoot() {
        val provider = OpenAIProvider(client)
        assertEquals(ProviderId("openai"), provider.id)
        assertEquals("OpenAI", provider.displayName)
        assertEquals(AuthenticationKind.ApiKey, provider.authenticationKind)
        assertEquals(
            "https://api.openai.com/v1".toHttpUrl(),
            normalizeCompletionsRoot(OpenAIProvider.DEFAULT_BASE_URL),
        )
    }

    @Test
    fun chatCompletionsBearerAndMaxTokens() = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    """
                    data: {"choices":[{"delta":{"content":"ok"}}]}

                    data: [DONE]

                    """.trimIndent(),
                ),
        )
        val provider = OpenAIProvider(client, server.url("/v1"))
        val events = provider.chat(sampleRequest(), credential).toList()
        assertEquals("ok", events.filterIsInstance<ChatEvent.Delta>().single().text)
        assertEquals(ChatEvent.Completed, events.last())
        val recorded = server.takeRequest()
        assertEquals("/v1/chat/completions", recorded.path)
        assertEquals("Bearer sk-test-openai-key", recorded.getHeader("Authorization"))
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"max_tokens\":64"))
        assertFalse(body.contains("max_completion_tokens"))
    }

    @Test
    fun listModelsFixture() = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"data":[{"id":"gpt-4o-mini"}]}"""),
        )
        val models = OpenAIProvider(client, server.url("/v1")).listModels(credential)
        assertEquals("gpt-4o-mini", models.single().id)
        assertEquals("/v1/models", server.takeRequest().path)
    }
}

private fun sampleRequest() = ChatRequest(
    requestId = RequestId("req-oai"),
    model = "gpt-4o",
    messages = listOf(ChatMessage(ChatRole.User, listOf(ContentPart.Text("hi")))),
    maxTokens = 64,
)
