package dev.clankyard.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageSupportRoutingTest {
    private val registry = LanguageSupportRegistry.Default

    @Test
    fun kotlinByFilename() {
        assertId("Main.kt", "kotlin")
        assertId("src/App.kt", "kotlin")
        assertId("build.gradle.kts", "kotlin")
        assertId("scripts/run.kts", "kotlin")
    }

    @Test
    fun javaByFilename() {
        assertId("Foo.java", "java")
        assertId("src/Foo.JAVA", "java")
    }

    @Test
    fun pythonByFilename() {
        assertId("app.py", "python")
        assertId("types.pyi", "python")
        assertId("gui.pyw", "python")
    }

    @Test
    fun javascriptByFilename() {
        assertId("index.js", "javascript")
        assertId("mod.mjs", "javascript")
        assertId("lib.cjs", "javascript")
    }

    @Test
    fun typescriptByFilename() {
        assertId("main.ts", "typescript")
        assertId("util.mts", "typescript")
        assertId("util.cts", "typescript")
    }

    @Test
    fun tsxAndJsxByFilename() {
        assertId("main.tsx", "tsx")
        assertId("View.jsx", "tsx")
    }

    @Test
    fun jsonMarkdownBash() {
        assertId("package.json", "json")
        assertId("README.md", "markdown")
        assertId("notes.markdown", "markdown")
        assertId("setup.sh", "bash")
        assertId("init.bash", "bash")
        assertId("rc.zsh", "bash")
    }

    @Test
    fun cAndCppByFilename() {
        assertId("main.c", "c")
        assertId("api.h", "c")
        assertId("main.cpp", "cpp")
        assertId("main.cc", "cpp")
        assertId("main.cxx", "cpp")
        assertId("api.hpp", "cpp")
        assertId("api.hh", "cpp")
    }

    @Test
    fun unknownAndYamlArePlaintext() {
        assertId("notes.txt", "plaintext")
        assertId("Makefile", "plaintext")
        assertId("AndroidManifest.xml", "plaintext")
        assertId("noext", "plaintext")
        assertId("config.yaml", "plaintext")
        assertId("config.yml", "plaintext")
    }

    @Test
    fun languageSupportHandlesMatchesId() {
        val kotlin = registry.forFileName("Main.kt")
        assertEquals("kotlin", kotlin.id)
        assertTrue(kotlin.handles("Main.kt"))
        assertFalse(kotlin.handles("Main.java"))
        assertEquals("Java", registry.forFileName("A.java").displayName)
        assertEquals("Plain text", registry.forFileName("foo.txt").displayName)
        assertEquals("typescript", registry.forFileName("a.ts").id)
        assertEquals("tsx", registry.forFileName("a.tsx").id)
        assertEquals("javascript", registry.forFileName("a.js").id)
    }

    @Test
    fun shippedLanguageIds() {
        val ids = registry.all().map { it.id }.toSet()
        assertTrue(
            ids.containsAll(
                listOf(
                    "kotlin", "java", "python", "javascript", "typescript", "tsx",
                    "json", "markdown", "bash", "c", "cpp",
                ),
            ),
        )
        assertFalse(ids.contains("yaml"))
        assertFalse(ids.contains("plaintext"))
    }

    private fun assertId(fileName: String, expected: String) {
        val support = registry.forFileName(fileName)
        assertEquals(expected, support.id)
        if (expected == "plaintext") {
            assertFalse(registry.all().any { it.handles(fileName) })
            return
        }
        assertTrue(support.handles(fileName))
    }
}
