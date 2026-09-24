package dev.clankyard.build.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LinkerExecTest {
    @Test
    fun linkerArgvPutsLinkerFirst() {
        val elf = File("/data/data/dev.clankyard.app/files/environment/runtimes/java")
        val argv = LinkerExec.linkerArgv(elf, listOf("-version"))
        assertEquals(LinkerExec.LINKER64, argv[0])
        assertEquals(elf.absolutePath, argv[1])
        assertEquals("-version", argv[2])
    }

    @Test
    fun preloadEnvPreservesExisting() {
        val so = File("/data/app/libclankyard_exec.so")
        val env = LinkerExec.preloadEnv(so, mapOf("LD_PRELOAD" to "/system/lib64/other.so"))
        assertTrue(env.getValue("LD_PRELOAD").startsWith(so.absolutePath))
        assertTrue(env.getValue("LD_PRELOAD").contains("/system/lib64/other.so"))
    }
}
