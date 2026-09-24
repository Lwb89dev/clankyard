package dev.clankyard.app.onboarding

import dev.clankyard.feature.settings.SettingsProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnboardingValidationTest {
    @Test
    fun `ollama can be configured without a key`() {
        assertNull(
            OnboardingViewModel.validateAi(
                provider = SettingsProvider.Ollama,
                model = "llama3.2",
                baseUrl = "http://127.0.0.1:11434",
                key = "",
                acknowledged = false,
            ),
        )
    }

    @Test
    fun `compatible provider requires https`() {
        assertEquals(
            "Compatible providers require an HTTPS URL. Use Ollama for localhost.",
            OnboardingViewModel.validateAi(
                provider = SettingsProvider.Compatible,
                model = "local-model",
                baseUrl = "http://10.0.2.2:8080",
                key = "compatible-key",
                acknowledged = true,
            ),
        )
    }

    @Test
    fun `nsec and account passwords are rejected`() {
        assertEquals(
            "Never paste an nsec into Clankyard. Use Amber.",
            OnboardingViewModel.validateAi(
                provider = SettingsProvider.OpenAI,
                model = "gpt-4o-mini",
                baseUrl = "",
                key = "nsec1not-a-key",
                acknowledged = true,
            ),
        )
        assertEquals(
            "Use an API key from the provider dashboard, not an account password.",
            OnboardingViewModel.validateAi(
                provider = SettingsProvider.OpenAI,
                model = "gpt-4o-mini",
                baseUrl = "",
                key = "person@example.com",
                acknowledged = true,
            ),
        )
    }
}
