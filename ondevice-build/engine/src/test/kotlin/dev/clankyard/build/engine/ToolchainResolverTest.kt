package dev.clankyard.build.engine

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ToolchainResolverTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun findsGradleLauncherJar() {
        val home = tmp.newFolder("gradle-8.11.1")
        val lib = File(home, "lib")
        lib.mkdirs()
        val jar = File(lib, "gradle-launcher-8.11.1.jar")
        jar.writeBytes(byteArrayOf(1))
        val found = ToolchainResolver.findLauncher(home)
        assertNotNull(found)
        assertEquals(jar.canonicalFile, found!!.canonicalFile)
    }
}
