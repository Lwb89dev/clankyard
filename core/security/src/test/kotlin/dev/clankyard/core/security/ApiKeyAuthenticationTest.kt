package dev.clankyard.core.security

import dev.clankyard.core.model.AuthenticationKind
import dev.clankyard.core.model.Credential
import dev.clankyard.core.model.CredentialSlotId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiKeyAuthenticationTest {
    private val openai = CredentialSlotId("llm.openai.default")
    private val auth = ApiKeyAuthentication()

    @Test
    fun resolveReturnsStoredKey() = runBlocking {
        val store = FakeSecureCredentialStore()
        store.put(openai, Credential.ApiKey("sk-live-super-secret"))
        val got = auth.resolve(store, openai) as Credential.ApiKey
        assertEquals("sk-live-super-secret", got.secret)
        assertEquals("ApiKey(****)", got.toString())
    }

    @Test
    fun resolveMissingSlotFailsWithoutSecret() = runBlocking {
        val store = FakeSecureCredentialStore()
        val error = runCatching { auth.resolve(store, openai) }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertTrue(error!!.message!!.contains(openai.value))
        assertEquals(AuthenticationKind.ApiKey, auth.kind)
    }

    @Test
    fun validateRejectsShortOrWrongKind() = runBlocking {
        assertEquals(AuthValidation.Ok, auth.validate(Credential.ApiKey("12345678")))
        assertTrue(auth.validate(Credential.ApiKey("short")) is AuthValidation.Invalid)
        assertTrue(auth.validate(Credential.ApiKey("   ")) is AuthValidation.Invalid)
        val oauth = Credential.OAuthToken("access-secret", null, null)
        assertTrue(auth.validate(oauth) is AuthValidation.Invalid)
        assertEquals("OAuthToken(****)", oauth.toString())
    }

    @Test
    fun oauthInterfaceIsKindOnly() {
        val stub = object : OAuthAuthentication {
            override suspend fun resolve(
                store: SecureCredentialStore,
                slot: CredentialSlotId,
            ): Credential = error("OAuth is not implemented")

            override suspend fun validate(credential: Credential): AuthValidation =
                AuthValidation.Invalid("OAuth is not implemented")
        }
        assertEquals(AuthenticationKind.OAuth, stub.kind)
    }
}
