package dev.clankyard.ai.providers.anthropic

import dev.clankyard.ai.provider.ChatEvent
import dev.clankyard.ai.provider.ChatMessage
import dev.clankyard.ai.provider.ChatRequest
import dev.clankyard.ai.provider.ChatRole
import dev.clankyard.ai.provider.ContentPart
import dev.clankyard.core.model.AuthenticationKind
import dev.clankyard.core.model.Credential
import dev.clankyard.core.model.ProviderId
import dev.clankyard.core.model.RequestId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AnthropicProviderTest {
    private val server = MockWebServer()
    private val client = OkHttpClient()
    private val credential = Credential.ApiKey("sk-ant-test-secret")

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
    fun identityIsApiKeyNotOAuth() {
        val provider = AnthropicProvider(client)
        assertEquals(ProviderId("anthropic"), provider.id)
        assertEquals(AuthenticationKind.ApiKey, provider.authenticationKind)
        assertEquals("2023-06-01", AnthropicProvider.ANTHROPIC_VERSION)
        assertEquals("https://api.anthropic.com", AnthropicProvider.DEFAULT_BASE_URL)
    }

    @Test
    fun messagesSseUsesXApiKeyAndVersion() = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(ANTHROPIC_SSE),
        )
        val provider = AnthropicProvider(client, server.url("/v1"))
        val events = provider.chat(sampleRequest(), credential).toList()
        val text = events.filterIsInstance<ChatEvent.Delta>().joinToString("") { it.text }
        assertEquals("Hi", text)
        val usage = events.filterIsInstance<ChatEvent.Usage>().single()
        assertEquals(9, usage.inputTokens)
        assertEquals(1, usage.outputTokens)
        assertEquals(ChatEvent.Completed, events.last())
        val recorded = server.takeRequest()
        assertEquals("/v1/messages", recorded.path)
        assertEquals("sk-ant-test-secret", recorded.getHeader("x-api-key"))
        assertEquals("2023-06-01", recorded.getHeader("anthropic-version"))
        assertNull(recorded.getHeader("Authorization"))
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"max_tokens\":256"))
        assertFalse(body.contains("oauth"))
        assertFalse(body.contains("Bearer"))
    }

    @Test
    fun listModels() = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"data":[{"id":"claude-sonnet-4-20250514","display_name":"Claude Sonnet 4"}]}""",
                ),
        )
        val models = AnthropicProvider(client, server.url("/v1")).listModels(credential)
        assertEquals("claude-sonnet-4-20250514", models.single().id)
        assertEquals("Claude Sonnet 4", models.single().displayName)
        val recorded = server.takeRequest()
        assertEquals("/v1/models", recorded.path)
        assertEquals("sk-ant-test-secret", recorded.getHeader("x-api-key"))
        assertEquals("2023-06-01", recorded.getHeader("anthropic-version"))
    }

    @Test
    fun unauthorizedIsRedacted() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error":{"message":"invalid x-api-key sk-ant-test-secret"}}"""),
        )
        val events = AnthropicProvider(client, server.url("/v1"))
            .chat(sampleRequest(), credential)
            .toList()
        val error = events.filterIsInstance<ChatEvent.Error>().single()
        assertFalse(error.retryable)
        assertFalse(error.message.contains("sk-ant-test-secret"))
    }

    @Test
    fun cancelAbortsOkHttpCall() = runBlocking {
        val released = CountDownLatch(1)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                released.await(30, TimeUnit.SECONDS)
                return MockResponse()
                    .setHeader("Content-Type", "text/event-stream")
                    .setBody(ANTHROPIC_SSE)
            }
        }
        val provider = AnthropicProvider(client, server.url("/v1"))
        val request = sampleRequest()
        val events = mutableListOf<ChatEvent>()
        val job = launch(Dispatchers.IO) {
            provider.chat(request, credential).collect { events += it }
        }
        try {
            assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
            provider.cancel(request.requestId)
            withTimeout(5_000) { job.join() }
            assertTrue(events.none { it is ChatEvent.Completed })
            assertTrue(events.none { it is ChatEvent.Delta })
        } finally {
            released.countDown()
        }
    }
}

private fun sampleRequest() = ChatRequest(
    requestId = RequestId("req-ant"),
    model = "claude-sonnet-4-20250514",
    messages = listOf(ChatMessage(ChatRole.User, listOf(ContentPart.Text("hi")))),
    maxTokens = 256,
)

private const val ANTHROPIC_SSE = """
event: message_start
data: {"type":"message_start","message":{"id":"msg_1","usage":{"input_tokens":9,"output_tokens":1}}}

event: content_block_start
data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}

event: content_block_delta
data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hi"}}

event: content_block_stop
data: {"type":"content_block_stop","index":0}

event: message_delta
data: {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":1}}

event: message_stop
data: {"type":"message_stop"}

"""
