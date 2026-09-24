package dev.clankyard.build.runtime

import java.io.File

/**
 * Constructs argv/env for W^X-safe exec of filesDir ELFs.
 * Native interceptors live in `:app` jniLibs, not this module.
 */
object LinkerExec {
    const val LINKER64 = "/system/bin/linker64"
    const val INTERCEPTOR_SO = "libclankyard_exec.so"
    const val HELPER_SO = "libclankyard_exec_helper.so"
    const val HELLO_SO = "libclankyard_hello.so"

    fun linkerArgv(elf: File, args: List<String> = emptyList()): List<String> {
        require(elf.path.isNotEmpty()) { "elf path empty" }
        return listOf(LINKER64, elf.absolutePath) + args
    }

    fun nativeLib(nativeLibraryDir: File, soName: String): File = File(nativeLibraryDir, soName)

    fun preloadEnv(
        interceptor: File,
        base: Map<String, String> = emptyMap(),
    ): Map<String, String> {
        val extra = interceptor.absolutePath
        val existing = base["LD_PRELOAD"]
        val preload = if (existing.isNullOrBlank()) extra else "$extra:$existing"
        return base + ("LD_PRELOAD" to preload)
    }
}
