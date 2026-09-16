package dev.clankyard.core.security.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.clankyard.core.security.ApiKeyAuthentication
import dev.clankyard.core.security.AuthenticationStrategy
import dev.clankyard.core.security.KeystoreSecureCredentialStore
import dev.clankyard.core.security.SecureCredentialStore
import javax.inject.Singleton

/**
 * Production Hilt bindings. Tests replace this module with the in-memory fake:
 *
 * ```
 * @Module
 * @TestInstallIn(
 *     components = [SingletonComponent::class],
 *     replaces = [SecurityModule::class],
 * )
 * object FakeSecurityModule {
 *     @Provides
 *     @Singleton
 *     fun provideStore(): SecureCredentialStore = FakeSecureCredentialStore()
 * }
 * ```
 *
 * Never install [KeystoreSecureCredentialStore] in JVM unit tests — Android
 * Keystore is not on the JVM (CLANK-034 owns the device roundtrip).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {
    @Binds
    @Singleton
    abstract fun bindStore(impl: KeystoreSecureCredentialStore): SecureCredentialStore

    @Binds
    @Singleton
    abstract fun bindAuth(impl: ApiKeyAuthentication): AuthenticationStrategy
}
