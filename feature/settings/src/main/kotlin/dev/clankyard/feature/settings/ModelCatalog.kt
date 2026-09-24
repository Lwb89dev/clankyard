package dev.clankyard.feature.settings

object ModelCatalog {
    fun seeds(provider: SettingsProvider): List<String> = when (provider) {
        SettingsProvider.OpenAI -> listOf(
            "gpt-4o-mini",
            "gpt-4o",
            "gpt-4.1-nano",
            "gpt-4.1-mini",
            "gpt-4.1",
            "o4-mini",
            "o3-mini",
            "o3",
        )
        SettingsProvider.Anthropic -> listOf(
            "claude-3-5-haiku-latest",
            "claude-3-5-sonnet-latest",
            "claude-sonnet-4-0",
            "claude-opus-4-0",
        )
        SettingsProvider.Xai -> listOf(
            "grok-2",
            "grok-2-latest",
            "grok-3",
            "grok-4",
        )
        SettingsProvider.Ollama -> listOf(
            "llama3.2",
            "llama3.1",
            "mistral",
            "qwen2.5-coder",
            "codellama",
            "deepseek-r1",
        )
        SettingsProvider.Compatible -> emptyList()
    }

    fun pingModel(provider: SettingsProvider, selected: String, live: List<String>): String {
        val pool = live.ifEmpty { seeds(provider) }
        val cheap = listOf(
            "gpt-4o-mini",
            "gpt-4.1-nano",
            "gpt-4.1-mini",
            "claude-3-5-haiku-latest",
            "grok-2",
            "llama3.2",
        )
        cheap.firstOrNull { it in pool }?.let { return it }
        pool.firstOrNull { id ->
            val n = id.lowercase()
            !n.startsWith("o1") && !n.startsWith("o3") && !n.startsWith("o4")
        }?.let { return it }
        return selected.ifBlank { provider.defaultModel }
    }

    fun merge(provider: SettingsProvider, live: List<String>): List<String> {
        val chat = live.filter { isChatModel(provider, it) }
        val out = LinkedHashSet<String>()
        val seed = seeds(provider)
        if (chat.isEmpty()) {
            out += seed
            return out.toList()
        }
        seed.filter { it in chat }.forEach { out += it }
        chat.sorted().forEach { out += it }
        return out.toList()
    }

    fun isChatModel(provider: SettingsProvider, id: String): Boolean {
        val n = id.lowercase()
        if (n.contains("embed") || n.contains("whisper") || n.contains("tts") ||
            n.contains("dall-e") || n.contains("moderation") || n.contains("babbage") ||
            n.contains("davinci") || n.contains("audio") || n.contains("realtime") ||
            n.contains("transcribe") || n.contains("image")
        ) {
            return false
        }
        return when (provider) {
            SettingsProvider.OpenAI ->
                n.startsWith("gpt-") || n.startsWith("o1") || n.startsWith("o3") ||
                    n.startsWith("o4") || n.startsWith("chatgpt")
            SettingsProvider.Anthropic -> n.startsWith("claude-")
            SettingsProvider.Xai -> n.startsWith("grok-")
            SettingsProvider.Ollama, SettingsProvider.Compatible -> true
        }
    }
}
