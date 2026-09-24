package dev.clankyard.ai.provider.http

import dev.clankyard.ai.provider.ChatEvent
import dev.clankyard.core.model.RequestId
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Authenticator
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Response

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
    const val MAX_REQUESTS = 16
    const val MAX_REQUESTS_PER_HOST = 4
    val STREAM_WALL_CLOCK: Duration = 10.minutes

    fun client(): OkHttpClient = applyTimeouts(
        OkHttpClient.Builder().dispatcher(
            Dispatcher().apply {
                maxRequests = MAX_REQUESTS
                maxRequestsPerHost = MAX_REQUESTS_PER_HOST
            },
        ),
    ).build()

    fun listClient(base: OkHttpClient): OkHttpClient =
        applyTimeouts(base.newBuilder()).build()

    fun streamingClient(base: OkHttpClient): OkHttpClient =
        applyTimeouts(base.newBuilder())
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

    /** Clean client for TOFU: system CAs, no authenticator, cookies, or app interceptors. */
    fun tofuProbeClient(): OkHttpClient =
        applyTimeouts(OkHttpClient.Builder())
            .followRedirects(false)
            .followSslRedirects(false)
            .authenticator(Authenticator.NONE)
            .proxyAuthenticator(Authenticator.NONE)
            .cookieJar(CookieJar.NO_COOKIES)
            .build()

    private fun applyTimeouts(builder: OkHttpClient.Builder): OkHttpClient.Builder =
        builder
            .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(WRITE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(LIST_MODELS_READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .authenticator(Authenticator.NONE)
            .proxyAuthenticator(Authenticator.NONE)
            .cookieJar(CookieJar.NO_COOKIES)
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

/**
 * Runs [block] with a wall-clock watchdog that [Call.cancel]s the socket so a
 * stalled SSE `readUtf8Line` unblocks. Distinguishes timeout from user cancel.
 */
suspend fun FlowCollector<ChatEvent>.collectHttpCall(
    call: Call,
    wallClock: Duration,
    block: suspend () -> Unit,
) {
    val timedOut = AtomicBoolean(false)
    try {
        coroutineScope {
            val watchdog = startWatchdog(call, wallClock, timedOut)
            try {
                block()
            } finally {
                watchdog.cancel()
            }
        }
    } catch (e: CancellationException) {
        emitTimeoutOrRethrow(timedOut.get(), call, e)
    } catch (e: IOException) {
        emitTimeoutOrIo(timedOut.get(), call, e)
    }
}

private fun CoroutineScope.startWatchdog(
    call: Call,
    wallClock: Duration,
    timedOut: AtomicBoolean,
) = launch {
    delay(wallClock)
    timedOut.set(true)
    call.cancel()
}

private suspend fun FlowCollector<ChatEvent>.emitTimeoutOrRethrow(
    timedOut: Boolean,
    call: Call,
    error: CancellationException,
) {
    if (timedOut) {
        emit(ChatEvent.Error("stream timed out", retryable = true, cause = error))
        return
    }
    call.cancel()
    throw error
}

private suspend fun FlowCollector<ChatEvent>.emitTimeoutOrIo(
    timedOut: Boolean,
    call: Call,
    error: IOException,
) {
    when {
        timedOut -> emit(ChatEvent.Error("stream timed out", retryable = true, cause = error))
        call.isCanceled() -> throw CancellationException("cancelled", error)
        else -> emit(redactedIoError(error))
    }
}
