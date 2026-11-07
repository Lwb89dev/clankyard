package dev.clankyard.build.engine

import java.io.File

object BuildResourceGates {
    const val MIN_BUILD_FREE_BYTES = 1L * 1024L * 1024L * 1024L

    fun hasFreeSpace(dir: File, minBytes: Long = MIN_BUILD_FREE_BYTES): Boolean {
        val usable = runCatching { dir.usableSpace }.getOrDefault(0L)
        return usable >= minBytes
    }
}
