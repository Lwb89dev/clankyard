package dev.clankyard.build.engine

import dev.clankyard.build.runtime.RuntimeManager
import java.io.File

object ToolchainResolver {
    fun resolve(
        runtimes: RuntimeManager,
        cacheDir: File,
        tmpDir: File,
        heapMb: Int,
    ): GradleDriver.GradleToolchain? {
        val jdk = runtimes.jdkHome() ?: return null
        val java = File(jdk, "bin/java")
        if (!java.isFile) return null
        val gradleHome = runtimes.gradleHome() ?: return null
        val launcher = findLauncher(gradleHome) ?: return null
        val sdk = runtimes.sdkHome() ?: return null
        cacheDir.mkdirs()
        tmpDir.mkdirs()
        val aapt2 = runtimes.aapt2()
        return GradleDriver.GradleToolchain(
            jdkHome = jdk,
            gradleLauncher = launcher,
            sdkHome = sdk,
            gradleUserHome = GradleUserHome.prepare(cacheDir, aapt2, heapMb),
            tmpDir = tmpDir,
            aapt2 = aapt2,
            initScript = GradleUserHome.initScript(cacheDir),
        )
    }

    internal fun findLauncher(gradleHome: File): File? {
        val lib = File(gradleHome, "lib")
        val search = if (lib.isDirectory) lib else gradleHome
        return search.walkTopDown().firstOrNull { file ->
            file.isFile && file.name.startsWith("gradle-launcher") && file.name.endsWith(".jar")
        }
    }
}
