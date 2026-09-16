package dev.clankyard.core.common

sealed interface Outcome<out T> {
    data class Ok<out T>(val value: T) : Outcome<T>
    data class Err(val error: Throwable) : Outcome<Nothing> {
        val message: String get() = error.message ?: error.toString()
    }

    val isOk: Boolean get() = this is Ok

    fun getOrNull(): T? = (this as? Ok)?.value

    fun errorOrNull(): Throwable? = (this as? Err)?.error
}

inline fun <T> runOutcome(block: () -> T): Outcome<T> =
    try {
        Outcome.Ok(block())
    } catch (t: Throwable) {
        Outcome.Err(t)
    }
