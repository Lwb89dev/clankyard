package dev.clankyard.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageSupportRoutingTest {
    private val registry = LanguageSupportRegistry.Default

    @Test
    fun kotlinByFilename() {
        assertId("Main.kt", LanguageFileNames.KOTLIN)
        assertId("src/App.kt", LanguageFileNames.KOTLIN)
        assertId("build.gradle.kts", LanguageFileNames.KOTLIN)
        assertId("scripts/run.kts", LanguageFileNames.KOTLIN)
    }

    @Test
    fun javaByFilename() {
        assertId("Foo.java", LanguageFileNames.JAVA)
        assertId("src/Foo.JAVA", LanguageFileNames.JAVA)
    }

    @Test
    fun pythonByFilename() {
        assertId("app.py", LanguageFileNames.PYTHON)
        assertId("types.pyi", LanguageFileNames.PYTHON)
        assertId("gui.pyw", LanguageFileNames.PYTHON)
    }

    @Test
    fun javascriptByFilename() {
        assertId("index.js", LanguageFileNames.JAVASCRIPT)
        assertId("mod.mjs", LanguageFileNames.JAVASCRIPT)
        assertId("lib.cjs", LanguageFileNames.JAVASCRIPT)
        assertId("View.jsx", LanguageFileNames.JAVASCRIPT)
    }

    @Test
    fun typescriptByFilename() {
        assertId("main.ts", LanguageFileNames.TYPESCRIPT)
        assertId("main.tsx", LanguageFileNames.TYPESCRIPT)
        assertId("util.mts", LanguageFileNames.TYPESCRIPT)
        assertId("util.cts", LanguageFileNames.TYPESCRIPT)
    }

    @Test
    fun jsonYamlMarkdownBash() {
        assertId("package.json", LanguageFileNames.JSON)
        assertId("config.yaml", LanguageFileNames.YAML)
        assertId("config.yml", LanguageFileNames.YAML)
        assertId("README.md", LanguageFileNames.MARKDOWN)
        assertId("notes.markdown", LanguageFileNames.MARKDOWN)
        assertId("setup.sh", LanguageFileNames.BASH)
        assertId("init.bash", LanguageFileNames.BASH)
        assertId("rc.zsh", LanguageFileNames.BASH)
    }

    @Test
    fun cAndCppByFilename() {
        assertId("main.c", LanguageFileNames.C)
        assertId("api.h", LanguageFileNames.C)
        assertId("main.cpp", LanguageFileNames.CPP)
        assertId("main.cc", LanguageFileNames.CPP)
        assertId("main.cxx", LanguageFileNames.CPP)
        assertId("api.hpp", LanguageFileNames.CPP)
        assertId("api.hh", LanguageFileNames.CPP)
    }

    @Test
    fun unknownIsPlaintext() {
        assertId("notes.txt", LanguageFileNames.PLAINTEXT)
        assertId("Makefile", LanguageFileNames.PLAINTEXT)
        assertId("AndroidManifest.xml", LanguageFileNames.PLAINTEXT)
        assertId("noext", LanguageFileNames.PLAINTEXT)
    }

    @Test
    fun languageSupportHandlesMatchesId() {
        val kotlin = registry.forFileName("Main.kt")
        assertEquals("kotlin", kotlin.id)
        assertTrue(kotlin.handles("Main.kt"))
        assertFalse(kotlin.handles("Main.java"))
        assertEquals("Java", registry.forFileName("A.java").displayName)
        assertEquals("Plain text", registry.forFileName("foo.txt").displayName)
    }

    @Test
    fun shippedLanguageIds() {
        val ids = registry.all().map { it.id }.toSet()
        assertTrue(
            ids.containsAll(
                listOf(
                    LanguageFileNames.KOTLIN,
                    LanguageFileNames.JAVA,
                    LanguageFileNames.PYTHON,
                    LanguageFileNames.JAVASCRIPT,
                    LanguageFileNames.TYPESCRIPT,
                    LanguageFileNames.JSON,
                    LanguageFileNames.YAML,
                    LanguageFileNames.MARKDOWN,
                    LanguageFileNames.BASH,
                    LanguageFileNames.C,
                    LanguageFileNames.CPP,
                ),
            ),
        )
    }

    private fun assertId(fileName: String, expected: String) {
        assertEquals(expected, LanguageFileNames.idFor(fileName))
        assertEquals(expected, registry.forFileName(fileName).id)
        assertTrue(registry.forFileName(fileName).handles(fileName) || expected == LanguageFileNames.PLAINTEXT)
    }
}
