package dev.clankyard.core.security

import dev.clankyard.core.model.AuthenticationKind
import dev.clankyard.core.model.Credential
import dev.clankyard.core.model.CredentialSlotId
import javax.inject.Inject

interface AuthenticationStrategy {
    val kind: AuthenticationKind
    suspend fun resolve(store: SecureCredentialStore, slot: CredentialSlotId): Credential
    suspend fun validate(credential: Credential): AuthValidation
}

sealed interface AuthValidation {
    data object Ok : AuthValidation
    data class Invalid(val message: String) : AuthValidation
}

class ApiKeyAuthentication @Inject constructor() : AuthenticationStrategy {
    override val kind = AuthenticationKind.ApiKey

    override suspend fun resolve(store: SecureCredentialStore, slot: CredentialSlotId): Credential {
        return store.get(slot) ?: error("missing credential for $slot")
    }

    override suspend fun validate(credential: Credential): AuthValidation {
        val key = (credential as? Credential.ApiKey)?.secret.orEmpty().trim()
        return if (key.length >= 8) AuthValidation.Ok
        else AuthValidation.Invalid("API key missing or too short")
    }
}

/**
 * Interface only in MVP. Do not implement against Claude, OpenAI, or xAI —
 * those vendors have no documented third-party API OAuth for this app.
 * No WebView login, no custom URL schemes.
 */
interface OAuthAuthentication : AuthenticationStrategy {
    override val kind: AuthenticationKind
        get() = AuthenticationKind.OAuth
}
