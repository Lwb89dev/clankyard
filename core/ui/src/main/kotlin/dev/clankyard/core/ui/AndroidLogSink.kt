package dev.clankyard.core.ui

import android.util.Log
import dev.clankyard.core.common.LogSink
import dev.clankyard.core.common.LogSink.Level

object AndroidLogSink : LogSink {
    override fun log(level: Level, tag: String, message: String, throwable: Throwable?) {
        when (level) {
            Level.DEBUG -> Log.d(tag, message, throwable)
            Level.INFO -> Log.i(tag, message, throwable)
            Level.WARN -> Log.w(tag, message, throwable)
            Level.ERROR -> Log.e(tag, message, throwable)
        }
    }
}
