package dev.clankyard.app.di

import dev.clankyard.ai.provider.LlmProvider
import dev.clankyard.ai.providers.anthropic.AnthropicProvider
import dev.clankyard.ai.providers.compatible.OpenAICompatibleProvider
import dev.clankyard.ai.providers.openai.OpenAIProvider
import dev.clankyard.ai.providers.xai.XAIProvider
import dev.clankyard.feature.settings.SettingsProvider
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Singleton
class LlmProviderFactory @Inject constructor(
    private val client: OkHttpClient,
) {
    fun create(kind: SettingsProvider, compatibleBaseUrl: String): LlmProvider = when (kind) {
        SettingsProvider.OpenAI -> OpenAIProvider(client)
        SettingsProvider.Anthropic -> AnthropicProvider(client)
        SettingsProvider.Xai -> XAIProvider(client)
        SettingsProvider.Compatible -> OpenAICompatibleProvider(client, compatibleBaseUrl)
        SettingsProvider.Ollama -> OpenAICompatibleProvider(
            client,
            compatibleBaseUrl.ifBlank { "http://127.0.0.1:11434" },
            allowLoopbackHttp = true,
        )
    }
}
