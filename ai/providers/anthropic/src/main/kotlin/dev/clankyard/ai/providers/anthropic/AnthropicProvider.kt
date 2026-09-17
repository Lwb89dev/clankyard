package dev.clankyard.ai.providers.anthropic

import dev.clankyard.ai.provider.ChatEvent
import dev.clankyard.ai.provider.ChatRequest
import dev.clankyard.ai.provider.LlmProvider
import dev.clankyard.ai.provider.ModelInfo
import dev.clankyard.ai.provider.http.normalizeCompletionsRoot
import dev.clankyard.core.model.AuthenticationKind
import dev.clankyard.core.model.Credential
import dev.clankyard.core.model.ProviderId
import dev.clankyard.core.model.RequestId
import kotlinx.coroutines.flow.Flow
import okhttp3.HttpUrl
import okhttp3.OkHttpClient

/**
 * Anthropic Messages API (`POST /v1/messages` SSE).
 * Headers: `x-api-key` + `anthropic-version: 2023-06-01`.
 * **No OAuth**, no custom URL schemes, no WebView login.
 * Does not retain [Credential]. Not the Completions adapter.
 */
class AnthropicProvider internal constructor(
    private val adapter: AnthropicMessagesAdapter,
) : LlmProvider {
    constructor(
        client: OkHttpClient,
        baseUrl: String = DEFAULT_BASE_URL,
    ) : this(AnthropicMessagesAdapter(client, normalizeCompletionsRoot(baseUrl)))

    constructor(client: OkHttpClient, root: HttpUrl) : this(
        AnthropicMessagesAdapter(client, root),
    )

    override val id: ProviderId = ProviderId("anthropic")
    override val displayName: String = "Anthropic"
    override val authenticationKind: AuthenticationKind = AuthenticationKind.ApiKey

    override suspend fun listModels(credential: Credential): List<ModelInfo> =
        adapter.listModels(credential)

    override fun chat(request: ChatRequest, credential: Credential): Flow<ChatEvent> =
        adapter.chat(request, credential)

    override suspend fun cancel(requestId: RequestId) = adapter.cancel(requestId)

    companion object {
        const val DEFAULT_BASE_URL = "https://api.anthropic.com"
        const val ANTHROPIC_VERSION = "2023-06-01"
    }
}
