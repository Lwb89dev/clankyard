package dev.clankyard.build.engine

import java.io.File

object GradleUserHome {
    fun prepare(cacheDir: File, aapt2: File?, heapMb: Int): File {
        val home = File(cacheDir, "gradle-user-home")
        home.mkdirs()
        File(home, "gradle.properties").writeText(properties(aapt2, heapMb))
        return home
    }

    fun initScript(cacheDir: File): File {
        cacheDir.mkdirs()
        val file = File(cacheDir, "clankyard-init.gradle")
        file.writeText(INIT_SCRIPT)
        return file
    }

    internal fun properties(aapt2: File?, heapMb: Int): String = buildString {
        appendLine("org.gradle.daemon=false")
        appendLine("org.gradle.parallel=false")
        appendLine("org.gradle.workers.max=1")
        appendLine("org.gradle.jvmargs=-Xmx${heapMb.coerceIn(128, 2048)}m -Dfile.encoding=UTF-8")
        aapt2?.takeIf { it.isFile }?.let { file ->
            appendLine("android.aapt2FromMavenOverride=${file.absolutePath}")
        }
    }

    private const val INIT_SCRIPT =
        "gradle.settingsEvaluated { println('clankyard-init: settings evaluated') }\n"
}
