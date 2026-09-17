package dev.clankyard.ai.provider

import dev.clankyard.core.model.AuthenticationKind
import dev.clankyard.core.model.Credential
import dev.clankyard.core.model.ProviderId
import dev.clankyard.core.model.RequestId
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

data class ModelInfo(
    val id: String,
    val displayName: String,
    val contextWindowTokens: Int?,
    val supportsTools: Boolean,
    val supportsStreaming: Boolean,
)

enum class ChatRole { System, User, Assistant, Tool }

@Serializable
sealed interface ContentPart {
    @Serializable
    data class Text(val text: String) : ContentPart

    @Serializable
    data class ToolUse(
        val id: String,
        val name: String,
        val argumentsJson: String,
    ) : ContentPart

    @Serializable
    data class ToolResult(
        val toolCallId: String,
        val content: String,
        val isError: Boolean,
    ) : ContentPart
}

data class ChatMessage(
    val role: ChatRole,
    val parts: List<ContentPart>,
)

data class ToolSpec(
    val name: String,
    val description: String,
    val parametersJsonSchema: String,
)

data class ChatRequest(
    val requestId: RequestId,
    val model: String,
    val messages: List<ChatMessage>,
    val tools: List<ToolSpec> = emptyList(),
    val stream: Boolean = true,
    val maxTokens: Int? = null,
    val temperature: Float? = null,
)

sealed interface ChatEvent {
    data class Delta(val text: String) : ChatEvent
    data class ToolCall(val id: String, val name: String, val argumentsJson: String) : ChatEvent
    data class Usage(val inputTokens: Int?, val outputTokens: Int?) : ChatEvent

    /** [message] is already redacted. Never show [cause].message in UI. */
    data class Error(
        val message: String,
        val retryable: Boolean,
        val cause: Throwable? = null,
    ) : ChatEvent

    data object Completed : ChatEvent
}

/**
 * Pure HTTP adapter (KD-7). Caller resolves [Credential] and passes it in.
 * Implementations must not retain the credential, must not depend on
 * `SecureCredentialStore`, and must not log the secret.
 */
interface LlmProvider {
    val id: ProviderId
    val displayName: String
    val authenticationKind: AuthenticationKind
    suspend fun listModels(credential: Credential): List<ModelInfo>
    fun chat(request: ChatRequest, credential: Credential): Flow<ChatEvent>
    suspend fun cancel(requestId: RequestId)
}
