package dev.clankyard.ai.provider.http

import dev.clankyard.ai.provider.ChatEvent
import dev.clankyard.ai.provider.ChatMessage
import dev.clankyard.ai.provider.ChatRequest
import dev.clankyard.ai.provider.ChatRole
import dev.clankyard.ai.provider.ContentPart
import dev.clankyard.core.model.Credential
import dev.clankyard.core.model.RequestId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.milliseconds
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpenAICompletionsAdapterTest {
    private val server = MockWebServer()
    private val client = OkHttpClient()
    private val credential = Credential.ApiKey("sk-test-secret-value")

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
    fun chatSseEmitsDeltaUsageCompleted() = runBlocking {
        server.enqueue(sse(OPENAI_SSE))
        val events = adapter().chat(sampleRequest(), credential).toList()
        val deltas = events.filterIsInstance<ChatEvent.Delta>().joinToString("") { it.text }
        assertEquals("Hello", deltas)
        val usage = events.filterIsInstance<ChatEvent.Usage>().single()
        assertEquals(5, usage.inputTokens)
        assertEquals(2, usage.outputTokens)
        assertEquals(ChatEvent.Completed, events.last())
        val recorded = server.takeRequest()
        assertEquals("/v1/chat/completions", recorded.path)
        assertEquals("Bearer sk-test-secret-value", recorded.getHeader("Authorization"))
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"max_tokens\":128"))
        assertTrue(body.contains("\"stream\":true"))
        assertFalse(body.contains("max_completion_tokens"))
    }

    @Test
    fun listModels() = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"data":[{"id":"gpt-4o","object":"model","owned_by":"openai"}]}"""),
        )
        val models = adapter().listModels(credential)
        assertEquals("gpt-4o", models.single().id)
        val recorded = server.takeRequest()
        assertEquals("/v1/models", recorded.path)
        assertEquals("GET", recorded.method)
        assertEquals("Bearer sk-test-secret-value", recorded.getHeader("Authorization"))
    }

    @Test
    fun unauthorizedBecomesRedactedError() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error":{"message":"Invalid API key sk-test-secret-value"}}"""),
        )
        val events = adapter().chat(sampleRequest(), credential).toList()
        val error = events.filterIsInstance<ChatEvent.Error>().single()
        assertFalse(error.retryable)
        assertFalse(error.message.contains("sk-test-secret-value"))
        assertTrue(error.message.contains("401"))
        assertTrue(error.message.contains("[REDACTED]") || error.message.contains("HTTP 401"))
    }

    @Test
    fun rateLimitIsRetryable() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setBody("""{"error":{"message":"Rate limit; key sk-test-secret-value"}}"""),
        )
        val events = adapter().chat(sampleRequest(), credential).toList()
        val error = events.filterIsInstance<ChatEvent.Error>().single()
        assertTrue(error.retryable)
        assertFalse(error.message.contains("sk-test-secret-value"))
        assertTrue(error.message.contains("429"))
    }

    @Test
    fun cancelAbortsOkHttpCall() = runBlocking {
        val released = CountDownLatch(1)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                released.await(30, TimeUnit.SECONDS)
                return sse(OPENAI_SSE)
            }
        }
        val adapter = adapter()
        val request = sampleRequest()
        val events = mutableListOf<ChatEvent>()
        val job = launch(Dispatchers.IO) {
            adapter.chat(request, credential).collect { events += it }
        }
        try {
            assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
            adapter.cancel(request.requestId)
            withTimeout(5_000) { job.join() }
            assertTrue(events.none { it is ChatEvent.Completed })
            assertTrue(events.none { it is ChatEvent.Delta })
        } finally {
            released.countDown()
        }
    }

    @Test
    fun compatibleKeyIn401BodyIsRedacted() = runBlocking {
        val key = "compat-secret-key"
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error":{"message":"bad key $key"}}"""),
        )
        val events = adapter().chat(sampleRequest(), Credential.ApiKey(key)).toList()
        val error = events.filterIsInstance<ChatEvent.Error>().single()
        assertFalse(error.message.contains(key))
        assertTrue(error.message.contains("[REDACTED]"))
        assertFalse(error.retryable)
    }

    @Test
    fun hangingSseTimesOut() = runBlocking {
        val released = CountDownLatch(1)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                released.await(30, TimeUnit.SECONDS)
                return sse(OPENAI_SSE)
            }
        }
        val adapter = OpenAICompletionsAdapter(
            client,
            server.url("/v1"),
            streamWallClock = 200.milliseconds,
        )
        try {
            val events = adapter.chat(sampleRequest(), credential).toList()
            val error = events.filterIsInstance<ChatEvent.Error>().single()
            assertTrue(error.retryable)
            assertTrue(error.message.contains("timed out"))
        } finally {
            released.countDown()
        }
    }

    @Test
    fun toolCallChunksAssemble() = runBlocking {
        server.enqueue(sse(OPENAI_TOOL_SSE))
        val events = adapter().chat(sampleRequest(), credential).toList()
        val call = events.filterIsInstance<ChatEvent.ToolCall>().single()
        assertEquals("call_1", call.id)
        assertEquals("read_file", call.name)
        assertEquals("""{"path":"a.kt"}""", call.argumentsJson)
        assertEquals(ChatEvent.Completed, events.last())
    }

    private fun adapter() = OpenAICompletionsAdapter(client, server.url("/v1"))
}

private fun sse(body: String): MockResponse =
    MockResponse()
        .setHeader("Content-Type", "text/event-stream")
        .setBody(body)

private fun sampleRequest() = ChatRequest(
    requestId = RequestId("req-1"),
    model = "gpt-4o",
    messages = listOf(
        ChatMessage(ChatRole.User, listOf(ContentPart.Text("hi"))),
    ),
    maxTokens = 128,
)

private const val OPENAI_SSE = """
data: {"choices":[{"index":0,"delta":{"content":"Hel"}}]}

data: {"choices":[{"index":0,"delta":{"content":"lo"}}]}

data: {"choices":[{"index":0,"delta":{},"finish_reason":"stop"}],"usage":{"prompt_tokens":5,"completion_tokens":2}}

data: [DONE]

"""

private const val OPENAI_TOOL_SSE = """
data: {"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"call_1","type":"function","function":{"name":"read_file","arguments":""}}]}}]}

data: {"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\"path\":"}}]}}]}

data: {"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\"a.kt\"}"}}]}}]}

data: [DONE]

"""
