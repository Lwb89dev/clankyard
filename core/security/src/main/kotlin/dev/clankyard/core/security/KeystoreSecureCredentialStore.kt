package dev.clankyard.core.security

import android.content.Context
import android.content.pm.PackageManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.clankyard.core.model.Credential
import dev.clankyard.core.model.CredentialSlotId
import java.io.File
import java.security.KeyStore
import java.security.ProviderException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [SecureCredentialStore]: AES-256-GCM, key in Android Keystore
 * alias [KEYSTORE_ALIAS] (non-exportable; StrongBox if
 * [PackageManager.FEATURE_STRONGBOX_KEYSTORE] else TEE). Ciphertext + 12-byte
 * IV + AAD (slot id) in `filesDir/credentials/`. Not EncryptedSharedPreferences.
 *
 * Device roundtrip is CLANK-034. JVM tests must use [FakeSecureCredentialStore]
 * or [AesGcmCredentialStore] with a software key — never this class, never a
 * real Keystore.
 *
 * `setUserAuthenticationRequired(false)` for MVP; biometric-bound keys are
 * post-MVP.
 */
@Singleton
class KeystoreSecureCredentialStore @Inject constructor(
    @ApplicationContext context: Context,
) : SecureCredentialStore {
    private val delegate by lazy {
        AesGcmCredentialStore(
            directory = File(context.filesDir, CREDENTIALS_DIR),
            key = AndroidKeystoreKeys.getOrCreate(context),
        )
    }

    override suspend fun get(slot: CredentialSlotId): Credential? = delegate.get(slot)

    override suspend fun put(slot: CredentialSlotId, credential: Credential) =
        delegate.put(slot, credential)

    override suspend fun delete(slot: CredentialSlotId) = delegate.delete(slot)

    override suspend fun listSlots(): List<CredentialSlot> = delegate.listSlots()
}

internal const val KEYSTORE_ALIAS = "clankyard.credentials.v1"

internal object AndroidKeystoreKeys {
    fun getOrCreate(context: Context): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        val existing = keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        return create(context, keyStore)
    }

    private fun create(context: Context, keyStore: KeyStore): SecretKey {
        val strongBox = context.packageManager.hasSystemFeature(
            PackageManager.FEATURE_STRONGBOX_KEYSTORE,
        )
        if (strongBox) {
            try {
                return generate(strongBox = true)
            } catch (_: StrongBoxUnavailableException) {
                keyStore.deleteEntry(KEYSTORE_ALIAS)
            } catch (_: ProviderException) {
                keyStore.deleteEntry(KEYSTORE_ALIAS)
            }
        }
        return generate(strongBox = false)
    }

    private fun generate(strongBox: Boolean): SecretKey {
        val spec = KeyGenParameterSpec.Builder(
            KEYSTORE_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(false)
            .setIsStrongBoxBacked(strongBox)
            .build()
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore",
        )
        generator.init(spec)
        return generator.generateKey()
    }
}
