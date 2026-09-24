package dev.clankyard.ai.provider.http

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CompletionsRootTest {
    @Test
    fun emptyPathAppendsV1() {
        val root = normalizeCompletionsRoot("https://api.x.ai")
        assertEquals("https://api.x.ai/v1".toHttpUrl(), root)
        assertEquals("https://api.x.ai/v1/chat/completions".toHttpUrl(), root.chatCompletionsUrl())
        assertEquals("https://api.x.ai/v1/models".toHttpUrl(), root.modelsUrl())
    }

    @Test
    fun existingV1Kept() {
        val a = normalizeCompletionsRoot("https://host.example/v1")
        val b = normalizeCompletionsRoot("https://host.example")
        assertEquals(a, b)
        assertEquals("https://host.example/v1".toHttpUrl(), a)
    }

    @Test
    fun stripsTrailingChatCompletions() {
        val root = normalizeCompletionsRoot("https://api.openai.com/v1/chat/completions")
        assertEquals("https://api.openai.com/v1".toHttpUrl(), root)
    }

    @Test
    fun firstPartyOpenAiDefault() {
        val root = normalizeCompletionsRoot("https://api.openai.com/v1")
        assertEquals("https://api.openai.com/v1".toHttpUrl(), root)
    }

    @Test
    fun httpRejectedWithLocalModelProviderCopy() {
        val error = assertThrows(CleartextEndpointException::class.java) {
            normalizeCompletionsRoot("http://192.168.1.10:11434")
        }
        assertTrue(error.message!!.contains("LocalModelProvider"))
        assertTrue(error.message!!.contains("http://"))
    }

    @Test
    fun httpHostRejected() {
        assertThrows(CleartextEndpointException::class.java) {
            normalizeCompletionsRoot("http://api.openai.com/v1")
        }
    }

    @Test
    fun loopbackHttpAllowedForLocalModels() {
        val root = normalizeCompletionsRoot("http://127.0.0.1:11434", allowLoopbackHttp = true)
        assertEquals("http://127.0.0.1:11434/v1".toHttpUrl(), root)
        assertThrows(CleartextEndpointException::class.java) {
            normalizeCompletionsRoot("http://192.168.1.10:11434", allowLoopbackHttp = true)
        }
    }

    @Test
    fun invalidUrlRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            normalizeCompletionsRoot("not a url")
        }
    }
}
