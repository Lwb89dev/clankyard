package dev.clankyard.ai.providers.openai

import dev.clankyard.ai.provider.ChatEvent
import dev.clankyard.ai.provider.ChatRequest
import dev.clankyard.ai.provider.LlmProvider
import dev.clankyard.ai.provider.ModelInfo
import dev.clankyard.ai.provider.http.OpenAICompletionsAdapter
import dev.clankyard.ai.provider.http.normalizeCompletionsRoot
import dev.clankyard.core.model.AuthenticationKind
import dev.clankyard.core.model.Credential
import dev.clankyard.core.model.ProviderId
import dev.clankyard.core.model.RequestId
import kotlinx.coroutines.flow.Flow
import okhttp3.HttpUrl
import okhttp3.OkHttpClient

/**
 * First-party OpenAI Chat Completions (`POST /v1/chat/completions` SSE).
 * Bearer API key. No Responses client. No OAuth. Does not retain [Credential].
 */
class OpenAIProvider internal constructor(
    private val adapter: OpenAICompletionsAdapter,
) : LlmProvider {
    constructor(
        client: OkHttpClient,
        baseUrl: String = DEFAULT_BASE_URL,
    ) : this(OpenAICompletionsAdapter(client, normalizeCompletionsRoot(baseUrl)))

    constructor(client: OkHttpClient, root: HttpUrl) : this(
        OpenAICompletionsAdapter(client, root),
    )

    override val id: ProviderId = ProviderId("openai")
    override val displayName: String = "OpenAI"
    override val authenticationKind: AuthenticationKind = AuthenticationKind.ApiKey

    override suspend fun listModels(credential: Credential): List<ModelInfo> =
        adapter.listModels(credential)

    override fun chat(request: ChatRequest, credential: Credential): Flow<ChatEvent> =
        adapter.chat(request, credential)

    override suspend fun cancel(requestId: RequestId) = adapter.cancel(requestId)

    companion object {
        const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
    }
}
