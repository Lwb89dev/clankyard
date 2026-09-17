package dev.clankyard.ai.providers.fake

import dev.clankyard.ai.provider.ChatEvent
import dev.clankyard.ai.provider.ChatRequest
import dev.clankyard.ai.provider.LlmProvider
import dev.clankyard.ai.provider.ModelInfo
import dev.clankyard.core.model.AuthenticationKind
import dev.clankyard.core.model.Credential
import dev.clankyard.core.model.ProviderId
import dev.clankyard.core.model.RequestId
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.job

/**
 * Scripted [LlmProvider] for JVM tests. Never opens a socket (KD-15).
 *
 * Higher-layer Hilt tests replace production providers with:
 *
 * ```
 * @Module
 * @TestInstallIn(
 *     components = [SingletonComponent::class],
 *     replaces = [ProvidersModule::class],
 * )
 * object FakeProvidersModule {
 *     @Provides
 *     @Singleton
 *     fun fake(): LlmProvider = FakeLlmProvider()
 * }
 * ```
 *
 * Production implementations must not depend on `SecureCredentialStore`.
 * Callers pass [Credential] into [listModels] / [chat].
 */
class FakeLlmProvider(
    private val events: List<ChatEvent> = DEFAULT_EVENTS,
    var models: List<ModelInfo> = listOf(DEFAULT_MODEL),
    private val betweenEvents: suspend () -> Unit = {},
) : LlmProvider {
    override val id: ProviderId = ProviderId("fake")
    override val displayName: String = "Fake"
    override val authenticationKind: AuthenticationKind = AuthenticationKind.ApiKey

    private val inFlight = ConcurrentHashMap<RequestId, Job>()

    override suspend fun listModels(credential: Credential): List<ModelInfo> {
        requireApiKeyDropped(credential)
        return models
    }

    override fun chat(request: ChatRequest, credential: Credential): Flow<ChatEvent> {
        requireApiKeyDropped(credential)
        return flow {
            val job = currentCoroutineContext().job
            inFlight[request.requestId] = job
            try {
                emitScript()
            } finally {
                inFlight.remove(request.requestId, job)
            }
        }
    }

    override suspend fun cancel(requestId: RequestId) {
        inFlight[requestId]?.cancel()
    }

    private suspend fun FlowCollector<ChatEvent>.emitScript() {
        for (event in events) {
            currentCoroutineContext().ensureActive()
            emit(event)
            betweenEvents()
        }
    }

    companion object {
        val DEFAULT_MODEL = ModelInfo(
            id = "fake-model",
            displayName = "Fake",
            contextWindowTokens = 128_000,
            supportsTools = true,
            supportsStreaming = true,
        )
        val DEFAULT_EVENTS = listOf(
            ChatEvent.Delta("hello from fake"),
            ChatEvent.Completed,
        )
    }
}

private fun requireApiKeyDropped(credential: Credential) {
    val secret = (credential as? Credential.ApiKey)?.secret
    require(!secret.isNullOrBlank()) { "API key required" }
}
