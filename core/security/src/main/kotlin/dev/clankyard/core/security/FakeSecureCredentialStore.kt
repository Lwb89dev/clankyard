package dev.clankyard.core.security

import dev.clankyard.core.model.Credential
import dev.clankyard.core.model.CredentialSlotId

/** In-memory store for JVM tests. Never backed by Android Keystore. */
class FakeSecureCredentialStore : SecureCredentialStore {
    private val lock = Any()
    private val slots = LinkedHashMap<CredentialSlotId, Credential>()

    override suspend fun get(slot: CredentialSlotId): Credential? = synchronized(lock) {
        slots[slot]
    }

    override suspend fun put(slot: CredentialSlotId, credential: Credential) {
        synchronized(lock) { slots[slot] = credential }
    }

    override suspend fun delete(slot: CredentialSlotId) {
        synchronized(lock) { slots.remove(slot) }
    }

    override suspend fun listSlots(): List<CredentialSlot> = synchronized(lock) {
        slots.keys.map { it.toCredentialSlot() }
    }
}
