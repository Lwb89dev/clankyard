package dev.clankyard.build.engine

import dev.clankyard.build.api.BuildRequest
import dev.clankyard.build.api.BuildTask
import dev.clankyard.build.runtime.LinkerExec
import dev.clankyard.core.model.SessionId
import dev.clankyard.terminal.api.ExecutionSessionRequest
import dev.clankyard.workspace.FileBackedWorkspace
import java.io.File

/**
 * Builds the fixed GradleMain argv and environment for a trusted on-device build.
 * User input supplies only the workspace and the small [BuildTask] enum; it never
 * supplies an executable, arbitrary Gradle arguments, properties, or environment.
 */
class GradleDriver(
    private val maxHeapMb: Int = DEFAULT_HEAP_MB,
) {
    init {
        require(maxHeapMb in MIN_HEAP_MB..MAX_HEAP_MB) { "invalid Gradle heap size" }
    }

    fun session(
        request: BuildRequest,
        workspace: FileBackedWorkspace,
        toolchain: GradleToolchain,
    ): ExecutionSessionRequest = ExecutionSessionRequest(
        sessionId = SessionId(request.buildId.value),
        cwd = workspace.root,
        command = command(request.task, workspace.root, toolchain),
        env = environment(toolchain),
        pty = false,
        emitLimitationBanner = false,
        mergeErrorStream = true,
    )

    fun command(
        task: BuildTask,
        workspaceRoot: File,
        toolchain: GradleToolchain,
    ): List<String> {
        require(workspaceRoot.isDirectory) { "workspace root does not exist" }
        require(toolchain.jdkHome.isDirectory) { "JDK home does not exist" }
        require(toolchain.gradleLauncher.isFile) { "Gradle launcher does not exist" }
        require(toolchain.sdkHome.isDirectory) { "Android SDK home does not exist" }

        val javaArgs = listOf(
            "-Djava.home=${toolchain.jdkHome.absolutePath}",
            "-Xmx${maxHeapMb}m",
            "-Dfile.encoding=UTF-8",
            "-cp",
            toolchain.gradleLauncher.absolutePath,
            "org.gradle.launcher.GradleMain",
        )
        val gradleArgs = buildList {
            add("--no-daemon")
            add("--max-workers=1")
            add("-p")
            add(workspaceRoot.absolutePath)
            add("-Dorg.gradle.java.home=${toolchain.jdkHome.absolutePath}")
            toolchain.initScript?.takeIf { it.isFile }?.let {
                add("--init-script")
                add(it.absolutePath)
            }
            toolchain.aapt2?.takeIf { it.isFile }?.let {
                add("-Pandroid.aapt2FromMavenOverride=${it.absolutePath}")
            }
            addAll(task.gradleTasks)
        }
        return LinkerExec.linkerArgv(File(toolchain.jdkHome, "bin/java"), javaArgs + gradleArgs)
    }

    fun environment(toolchain: GradleToolchain): Map<String, String> = buildMap {
        put("JAVA_HOME", toolchain.jdkHome.absolutePath)
        put("ANDROID_HOME", toolchain.sdkHome.absolutePath)
        put("ANDROID_SDK_ROOT", toolchain.sdkHome.absolutePath)
        put("GRADLE_USER_HOME", toolchain.gradleUserHome.absolutePath)
        put("TMPDIR", toolchain.tmpDir.absolutePath)
        put("HOME", toolchain.gradleUserHome.absolutePath)
        put("LANG", "C.UTF-8")
        // Executables are always passed by absolute path. Keep PATH to system
        // utilities only; it must not expose user-controlled workshop files.
        put("PATH", "/system/bin:/system/xbin")
    }

    data class GradleToolchain(
        val jdkHome: File,
        val gradleLauncher: File,
        val sdkHome: File,
        val gradleUserHome: File,
        val tmpDir: File,
        val aapt2: File? = null,
        val initScript: File? = null,
    )

    private companion object {
        const val DEFAULT_HEAP_MB = 512
        const val MIN_HEAP_MB = 128
        const val MAX_HEAP_MB = 2048

        val BuildTask.gradleTasks: List<String>
            get() = when (this) {
                BuildTask.AssembleDebug -> listOf("assembleDebug")
                BuildTask.Clean -> listOf("clean")
                BuildTask.RebuildDebug -> listOf("clean", "assembleDebug")
            }
    }
}
