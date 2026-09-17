package dev.clankyard.ai.providers.xai

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
 * xAI Completions adapter. Default origin `https://api.x.ai` — the helper
 * appends `/v1`. Bearer API key. No OAuth. Does not retain [Credential].
 */
class XAIProvider internal constructor(
    private val adapter: OpenAICompletionsAdapter,
) : LlmProvider {
    constructor(
        client: OkHttpClient,
        origin: String = DEFAULT_ORIGIN,
    ) : this(OpenAICompletionsAdapter(client, normalizeCompletionsRoot(origin)))

    constructor(client: OkHttpClient, root: HttpUrl) : this(
        OpenAICompletionsAdapter(client, root),
    )

    override val id: ProviderId = ProviderId("xai")
    override val displayName: String = "xAI"
    override val authenticationKind: AuthenticationKind = AuthenticationKind.ApiKey

    override suspend fun listModels(credential: Credential): List<ModelInfo> =
        adapter.listModels(credential)

    override fun chat(request: ChatRequest, credential: Credential): Flow<ChatEvent> =
        adapter.chat(request, credential)

    override suspend fun cancel(requestId: RequestId) = adapter.cancel(requestId)

    companion object {
        const val DEFAULT_ORIGIN = "https://api.x.ai"
    }
}
