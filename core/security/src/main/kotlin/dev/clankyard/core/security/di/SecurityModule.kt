package dev.clankyard.core.security.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.clankyard.core.security.ApiKeyAuthentication
import dev.clankyard.core.security.AuthenticationStrategy
import dev.clankyard.core.security.SecureCredentialStore
import dev.clankyard.core.security.androidKeystoreCredentialStore
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
    @Provides
    @Singleton
    fun provideStore(@ApplicationContext context: Context): SecureCredentialStore =
        androidKeystoreCredentialStore(context)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthenticationModule {
    @Binds
    @Singleton
    abstract fun bindAuth(impl: ApiKeyAuthentication): AuthenticationStrategy
}
