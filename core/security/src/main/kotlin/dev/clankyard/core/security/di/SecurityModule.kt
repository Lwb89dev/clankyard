package dev.clankyard.core.security.di

import android.content.Context
import android.content.pm.PackageManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.clankyard.core.security.AesGcmCredentialStore
import dev.clankyard.core.security.ApiKeyAuthentication
import dev.clankyard.core.security.AuthenticationStrategy
import dev.clankyard.core.security.SecureCredentialStore
import java.io.File
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Singleton

/**
 * Production store: AES-256-GCM, Android Keystore alias `clankyard.credentials.v1`
 * (non-exportable; StrongBox if [PackageManager.FEATURE_STRONGBOX_KEYSTORE]
 * else TEE). Ciphertext + 12-byte IV + AAD (slot id) under
 * `filesDir/credentials/`. Not EncryptedSharedPreferences.
 *
 * Tests replace **this** module only; [AuthenticationModule] stays installed:
 *
 * ```
 * @Module
 * @TestInstallIn(
 *     components = [SingletonComponent::class],
 *     replaces = [SecurityModule::class],
 * )
 * object FakeCredentialStoreModule {
 *     @Provides
 *     @Singleton
 *     fun provideStore(): SecureCredentialStore = FakeSecureCredentialStore()
 * }
 * ```
 *
 * Never provide a Keystore-backed store in JVM unit tests (CLANK-034).
 * `setUserAuthenticationRequired(false)` for MVP; biometric-bound keys are
 * post-MVP.
 */
@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {
    private const val KEY_ALIAS = "clankyard.credentials.v1"
    private const val CREDENTIALS_DIR = "credentials"

    @Provides
    @Singleton
    fun provideStore(@ApplicationContext context: Context): SecureCredentialStore {
        return AesGcmCredentialStore(
            directory = File(context.filesDir, CREDENTIALS_DIR),
            key = getOrCreateKey(context),
        )
    }

    private fun getOrCreateKey(context: Context): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        return createKey(context, keyStore)
    }

    private fun createKey(context: Context, keyStore: KeyStore): SecretKey {
        val strongBox = context.packageManager.hasSystemFeature(
            PackageManager.FEATURE_STRONGBOX_KEYSTORE,
        )
        if (!strongBox) return generateKey(strongBox = false)
        try {
            return generateKey(strongBox = true)
        } catch (_: Exception) {
            runCatching { keyStore.deleteEntry(KEY_ALIAS) }
            return generateKey(strongBox = false)
        }
    }

    private fun generateKey(strongBox: Boolean): SecretKey {
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
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

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthenticationModule {
    @Binds
    @Singleton
    abstract fun bindAuth(impl: ApiKeyAuthentication): AuthenticationStrategy
}
