package dev.clankyard.ai.provider.http

import dev.clankyard.core.model.RequestId
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Shared OkHttp setup for LLM adapters (CLANK-021).
 *
 * Connect/write 15s. `listModels` read 30s. Streaming read timeout 0
 * (infinite) plus a 10-minute coroutine wall clock. No BODY logs — do not
 * attach [okhttp3.logging.HttpLoggingInterceptor].
 */
object ProviderHttp {
    const val CONNECT_TIMEOUT_MS = 15_000L
    const val WRITE_TIMEOUT_MS = 15_000L
    const val LIST_MODELS_READ_TIMEOUT_MS = 30_000L
    val STREAM_WALL_CLOCK: Duration = 10.minutes

    fun client(): OkHttpClient = applyTimeouts(OkHttpClient.Builder()).build()

    fun listClient(base: OkHttpClient): OkHttpClient =
        applyTimeouts(base.newBuilder()).build()

    fun streamingClient(base: OkHttpClient): OkHttpClient =
        applyTimeouts(base.newBuilder())
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

    private fun applyTimeouts(builder: OkHttpClient.Builder): OkHttpClient.Builder =
        builder
            .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(WRITE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(LIST_MODELS_READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
}

class InFlightCalls {
    private val calls = ConcurrentHashMap<RequestId, Call>()

    fun register(id: RequestId, call: Call) {
        calls.put(id, call)?.cancel()
    }

    fun cancel(id: RequestId) {
        calls[id]?.cancel()
    }

    fun remove(id: RequestId) {
        calls.remove(id)
    }
}

suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (cont.isActive) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            if (cont.isActive) cont.resume(response) else response.close()
        }
    })
    cont.invokeOnCancellation { cancel() }
}
