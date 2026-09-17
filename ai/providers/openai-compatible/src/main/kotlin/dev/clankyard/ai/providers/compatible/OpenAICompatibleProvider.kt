package dev.clankyard.ai.providers.compatible

import dev.clankyard.ai.provider.ChatEvent
import dev.clankyard.ai.provider.ChatRequest
import dev.clankyard.ai.provider.LlmProvider
import dev.clankyard.ai.provider.ModelInfo
import dev.clankyard.ai.provider.http.OpenAICompletionsAdapter
import dev.clankyard.ai.provider.http.normalizeCompletionsRoot
import dev.clankyard.core.model.AuthenticationKind
import dev.clankyard.core.model.Credential
import dev.clankyard.core.model.ProviderId
import dev.clankyard.core.model.RequestId
import java.io.IOException
import java.security.MessageDigest
import java.security.cert.X509Certificate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

data class TofuCertSummary(
    val host: String,
    val sha256Fingerprint: String,
    val subjectDn: String,
    val issuerDn: String,
    val notAfterEpochMs: Long,
)

/**
 * OpenAI-compatible Completions host (KD-20). HTTPS + system CAs only.
 * `http://` is rejected with copy pointing at post-MVP LocalModelProvider.
 * TOFU probe is a TLS handshake **without** an Authorization header.
 */
class OpenAICompatibleProvider internal constructor(
    private val client: OkHttpClient,
    private val adapter: OpenAICompletionsAdapter,
    private val root: HttpUrl,
) : LlmProvider {
    constructor(client: OkHttpClient, baseUrl: String) : this(
        client,
        normalizeCompletionsRoot(baseUrl),
    )

    constructor(client: OkHttpClient, root: HttpUrl) : this(
        client,
        OpenAICompletionsAdapter(client, root),
        root,
    )

    override val id: ProviderId = ProviderId("openai-compatible")
    override val displayName: String = "OpenAI-compatible"
    override val authenticationKind: AuthenticationKind = AuthenticationKind.ApiKey

    override suspend fun listModels(credential: Credential): List<ModelInfo> =
        adapter.listModels(credential)

    override fun chat(request: ChatRequest, credential: Credential): Flow<ChatEvent> =
        adapter.chat(request, credential)

    override suspend fun cancel(requestId: RequestId) = adapter.cancel(requestId)

    /**
     * TLS probe with **no** Authorization header (T-7). Settings shows the
     * summary and allow-lists the host after the user confirms.
     */
    suspend fun probeTls(): TofuCertSummary {
        val request = Request.Builder()
            .url(root)
            .get()
            .build()
        check(request.header("Authorization") == null)
        val probeClient = client.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
        return withContext(Dispatchers.IO) {
            probeClient.newCall(request).execute().use { response ->
                val cert = response.handshake?.peerCertificates?.firstOrNull() as? X509Certificate
                    ?: throw IOException("TLS handshake missing; HTTPS is required")
                TofuCertSummary(
                    host = root.host,
                    sha256Fingerprint = sha256Hex(cert.encoded),
                    subjectDn = cert.subjectX500Principal.toString(),
                    issuerDn = cert.issuerX500Principal.toString(),
                    notAfterEpochMs = cert.notAfter.time,
                )
            }
        }
    }
}

private fun sha256Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    return digest.joinToString("") { b -> "%02x".format(b) }
}
