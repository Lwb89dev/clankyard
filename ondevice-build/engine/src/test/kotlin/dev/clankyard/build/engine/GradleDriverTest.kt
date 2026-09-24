package dev.clankyard.build.engine

import dev.clankyard.build.api.BuildTask
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GradleDriverTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun commandUsesLinkerAndOnlyAllowlistedTask() {
        val root = tmp.newFolder("workspace")
        val jdk = tmp.newFolder("jdk")
        File(jdk, "bin").mkdirs()
        val launcher = File(tmp.newFolder("gradle/lib"), "gradle-launcher-8.11.1.jar").apply { writeBytes(byteArrayOf(1)) }
        val sdk = tmp.newFolder("sdk")
        val toolchain = GradleDriver.GradleToolchain(
            jdkHome = jdk,
            gradleLauncher = launcher,
            sdkHome = sdk,
            gradleUserHome = tmp.newFolder("gradle-user-home"),
            tmpDir = tmp.newFolder("tmp"),
        )

        val command = GradleDriver().command(BuildTask.AssembleDebug, root, toolchain)

        assertEquals("/system/bin/linker64", command[0])
        assertEquals(File(jdk, "bin/java").absolutePath, command[1])
        assertTrue(command.contains("org.gradle.launcher.GradleMain"))
        assertTrue(command.contains("assembleDebug"))
        assertTrue(command.none { it == "--offline" })
    }

    @Test
    fun rebuildIsFixedTwoTaskSequenceAndEnvironmentIsPrivate() {
        val root = tmp.newFolder("workspace")
        val jdk = tmp.newFolder("jdk")
        val sdk = tmp.newFolder("sdk")
        val toolchain = GradleDriver.GradleToolchain(
            jdkHome = jdk,
            gradleLauncher = tmp.newFile("gradle-launcher.jar"),
            sdkHome = sdk,
            gradleUserHome = tmp.newFolder("gradle-user-home"),
            tmpDir = tmp.newFolder("tmp"),
        )
        val driver = GradleDriver()

        val command = driver.command(BuildTask.RebuildDebug, root, toolchain)
        val env = driver.environment(toolchain)

        assertEquals(listOf("clean", "assembleDebug"), command.takeLast(2))
        assertEquals(toolchain.gradleUserHome.absolutePath, env["HOME"])
        assertEquals("/system/bin:/system/xbin", env["PATH"])
    }
}
