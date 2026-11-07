package dev.clankyard.build.engine

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GradleUserHomeTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun propertiesStayOutOfTheWorkshopAndPinAapt2() {
        val cache = tmp.newFolder("cache")
        val aapt2 = tmp.newFile("aapt2")
        val home = GradleUserHome.prepare(cache, aapt2, heapMb = 512)
        val text = File(home, "gradle.properties").readText()
        assertTrue(home.path.contains("gradle-user-home"))
        assertTrue(text.contains("org.gradle.daemon=false"))
        assertTrue(text.contains("android.aapt2FromMavenOverride=${aapt2.absolutePath}"))
        val init = GradleUserHome.initScript(cache)
        assertTrue(init.readText().contains("clankyard-init"))
    }
}
