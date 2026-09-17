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
    var models: List<ModelInfo> = listOf(DEFAULT_MODEL),
    private val produce: suspend FlowCollector<ChatEvent>.(ChatRequest) -> Unit = DefaultScript,
) : LlmProvider {
    constructor(events: List<ChatEvent>) : this(
        produce = {
            for (event in events) emit(event)
        },
    )

    constructor(
        events: List<ChatEvent>,
        betweenEvents: suspend () -> Unit,
    ) : this(produce = scripted(events, betweenEvents))

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
                produce(request)
            } finally {
                inFlight.remove(request.requestId, job)
            }
        }
    }

    override suspend fun cancel(requestId: RequestId) {
        inFlight[requestId]?.cancel()
    }

    companion object {
        val DEFAULT_MODEL = ModelInfo(
            id = "fake-model",
            displayName = "Fake",
            contextWindowTokens = 128_000,
            supportsTools = true,
            supportsStreaming = true,
        )
        val DefaultScript: suspend FlowCollector<ChatEvent>.(ChatRequest) -> Unit = {
            emit(ChatEvent.Delta("hello from fake"))
            emit(ChatEvent.Completed)
        }
    }
}

private fun requireApiKeyDropped(credential: Credential) {
    val secret = (credential as? Credential.ApiKey)?.secret
    require(!secret.isNullOrBlank()) { "API key required" }
}

internal fun scripted(
    events: List<ChatEvent>,
    betweenEvents: suspend () -> Unit,
): suspend FlowCollector<ChatEvent>.(ChatRequest) -> Unit = {
    for (event in events) {
        currentCoroutineContext().ensureActive()
        emit(event)
        betweenEvents()
    }
}
