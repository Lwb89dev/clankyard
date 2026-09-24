package dev.clankyard.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** CLANK-036 — fail if GPL Termux artifacts are declared in Gradle files. */
class LicenseGateTest {
    @Test
    fun gradleFilesDoNotPullTermuxGpl() {
        val hits = ArrayList<String>()
        for (file in gradleFiles()) {
            val text = file.readText()
            for (needle in FORBIDDEN) {
                if (text.contains(needle)) hits += "${file.relativeTo(repoRoot())}: $needle"
            }
        }
        assertTrue("forbidden coordinates:\n${hits.joinToString("\n")}", hits.isEmpty())
    }

    @Test
    fun noticeListsSoraEditorLgplAndJgit() {
        val notice = repoFile("NOTICE").readText()
        assertTrue(notice.contains("sora-editor"))
        assertTrue(notice.contains("LGPL-2.1"))
        assertTrue(notice.contains("Eclipse JGit") || notice.contains("JGit"))
        assertFalse(notice.contains("termux-shared"))
        assertFalse(notice.contains("termux-app"))
    }

    private fun gradleFiles(): List<File> {
        val root = repoRoot()
        return root.walkTopDown()
            .onEnter { dir -> dir.name != "build" && dir.name != ".gradle" && dir.name != ".git" }
            .filter { file ->
                file.isFile && (
                    file.name.endsWith(".gradle.kts") ||
                        file.name.endsWith(".gradle") ||
                        file.name == "libs.versions.toml"
                    )
            }
            .toList()
    }

    private fun repoFile(relative: String): File {
        val here = File(relative)
        if (here.isFile) return here
        val fromRoot = File(repoRoot(), relative)
        check(fromRoot.isFile) { "missing $relative" }
        return fromRoot
    }

    private fun repoRoot(): File {
        val cwd = File(".").canonicalFile
        if (File(cwd, "settings.gradle.kts").isFile) return cwd
        val parent = cwd.parentFile ?: error("repo root not found from $cwd")
        check(File(parent, "settings.gradle.kts").isFile) { "repo root not found from $cwd" }
        return parent
    }

    companion object {
        private val FORBIDDEN = listOf(
            "termux-shared",
            "termux-app",
            "com.termux:termux",
        )
    }
}
