package dev.clankyard.core.security

import dev.clankyard.core.model.Credential
import dev.clankyard.core.model.CredentialSlotId

data class CredentialSlot(
    val id: CredentialSlotId,
    val providerHint: String,
    val label: String,
)

interface SecureCredentialStore {
    suspend fun get(slot: CredentialSlotId): Credential?
    suspend fun put(slot: CredentialSlotId, credential: Credential)
    suspend fun delete(slot: CredentialSlotId)
    suspend fun listSlots(): List<CredentialSlot>
}

fun CredentialSlotId.toCredentialSlot(): CredentialSlot = when {
    value.startsWith(COMPATIBLE_PREFIX) -> CredentialSlot(
        id = this,
        providerHint = "openai-compatible",
        label = value.removePrefix(COMPATIBLE_PREFIX),
    )
    value.startsWith(GIT_PREFIX) -> CredentialSlot(
        id = this,
        providerHint = "git",
        label = value.removePrefix(GIT_PREFIX),
    )
    value.startsWith(SSH_PREFIX) -> CredentialSlot(
        id = this,
        providerHint = "ssh",
        label = value.removePrefix(SSH_PREFIX),
    )
    else -> {
        val provider = value.removePrefix("llm.").removeSuffix(".default")
        CredentialSlot(id = this, providerHint = provider, label = provider)
    }
}

private const val COMPATIBLE_PREFIX = "llm.openai-compatible."
private const val GIT_PREFIX = "git.https."
private const val SSH_PREFIX = "ssh."
