package dev.clankyard.ai.providers.fake

import dev.clankyard.ai.provider.ChatEvent
import dev.clankyard.ai.provider.ChatMessage
import dev.clankyard.ai.provider.ChatRequest
import dev.clankyard.ai.provider.ChatRole
import dev.clankyard.ai.provider.ContentPart
import dev.clankyard.core.model.AuthenticationKind
import dev.clankyard.core.model.Credential
import dev.clankyard.core.model.ProviderId
import dev.clankyard.core.model.RequestId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeLlmProviderTest {
    private val credential = Credential.ApiKey("sk-fake-not-used")
    private val request = ChatRequest(
        requestId = RequestId("req-fake"),
        model = "fake-model",
        messages = listOf(ChatMessage(ChatRole.User, listOf(ContentPart.Text("hi")))),
    )

    @Test
    fun scriptedFlowEmitsInOrder() = runBlocking {
        val fake = FakeLlmProvider(
            listOf(ChatEvent.Delta("a"), ChatEvent.Delta("b"), ChatEvent.Completed),
        )
        val events = fake.chat(request, credential).toList()
        assertEquals(
            listOf(ChatEvent.Delta("a"), ChatEvent.Delta("b"), ChatEvent.Completed),
            events,
        )
        assertEquals(ProviderId("fake"), fake.id)
        assertEquals(AuthenticationKind.ApiKey, fake.authenticationKind)
        assertEquals("fake-model", fake.listModels(credential).single().id)
        assertEquals("ApiKey(****)", credential.toString())
    }

    @Test
    fun cancelStopsRemainingEvents() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val fake = FakeLlmProvider(
            events = listOf(ChatEvent.Delta("a"), ChatEvent.Delta("b"), ChatEvent.Completed),
            betweenEvents = { gate.await() },
        )
        val events = mutableListOf<ChatEvent>()
        val job = launch { fake.chat(request, credential).collect { events += it } }
        while (events.isEmpty()) yield()
        fake.cancel(request.requestId)
        job.join()
        assertEquals(listOf(ChatEvent.Delta("a")), events)
        assertTrue(!gate.isCompleted)
    }

    @Test
    fun defaultScriptHasNoNetwork() = runBlocking {
        val fake = FakeLlmProvider()
        val events = fake.chat(request, credential).toList()
        assertEquals(ChatEvent.Delta("hello from fake"), events.first())
        assertEquals(ChatEvent.Completed, events.last())
    }
}
