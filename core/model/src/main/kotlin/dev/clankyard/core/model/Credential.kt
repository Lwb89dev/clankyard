package dev.clankyard.core.model

/** Secret value. `toString()` never includes the secret. */
sealed interface Credential {
    data class ApiKey(val secret: String) : Credential {
        override fun toString() = "ApiKey(****)"
    }

    data class OAuthToken(
        val accessToken: String,
        val refreshToken: String?,
        val expiresAtEpochMs: Long?,
    ) : Credential {
        override fun toString() = "OAuthToken(****)"
    }

    /** Ciphertext from Amber NIP-44. The nsec never enters Clankyard. */
    data class Nip44Wrap(val ciphertext: String) : Credential {
        override fun toString() = "Nip44Wrap(****)"
    }
}

/**
 * Slot id. Enforced format:
 *   llm.<providerId>.default
 *   llm.openai-compatible.<host>
 *   git.https.<host>
 *   ssh.<host>
 * providerId/host: lowercase [a-z0-9.-]+, no scheme, no path.
 */
@JvmInline
value class CredentialSlotId(val value: String) {
    init {
        require(PATTERN.matches(value)) { "illegal CredentialSlotId: $value" }
    }

    val isLlm: Boolean get() = value.startsWith("llm.")
    val isGit: Boolean get() = value.startsWith("git.")
    val isSsh: Boolean get() = value.startsWith("ssh.")

    companion object {
        private val PATTERN = Regex(
            """^(llm\.[a-z0-9-]+\.default|llm\.openai-compatible\.[a-z0-9.-]+|git\.https\.[a-z0-9.-]+|ssh\.[a-z0-9.-]+)$""",
        )
    }
}
