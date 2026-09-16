package dev.clankyard.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspacePathTest {
    @Test
    fun emptyStringIsRoot() {
        val path = WorkspacePath.parse("")
        assertEquals(WorkspacePath.ROOT, path)
        assertTrue(path.isRoot)
        assertEquals("", path.relative)
    }

    @Test
    fun slashIsRoot() {
        val path = WorkspacePath.parse("/")
        assertEquals(WorkspacePath.ROOT, path)
        assertTrue(path.isRoot)
    }

    @Test
    fun relativeFileIsAccepted() {
        val path = WorkspacePath.parse("src/Main.kt")
        assertFalse(path.isRoot)
        assertEquals("src/Main.kt", path.relative)
    }

    @Test
    fun rejectDotDot() {
        assertIllegal("a/../b")
        assertIllegal("..")
        assertIllegal("../secret")
    }

    @Test
    fun rejectDotSegment() {
        assertIllegal(".")
        assertIllegal("src/./Main.kt")
    }

    @Test
    fun rejectEmptyIntermediate() {
        assertIllegal("src//Main.kt")
        assertIllegal("a//b/c")
    }

    @Test
    fun rejectBackslash() {
        assertIllegal("src\\Main.kt")
    }

    @Test
    fun rejectNul() {
        assertIllegal("src/\u0000file")
    }

    @Test
    fun rejectColon() {
        assertIllegal("C:Windows")
        assertIllegal("foo:bar")
    }

    @Test
    fun rejectTrailingSlash() {
        assertIllegal("src/")
        assertIllegal("src/main/")
    }

    @Test
    fun rejectLeadingSlashOtherThanRoot() {
        assertIllegal("/src")
        assertIllegal("/src/Main.kt")
    }

    @Test
    fun rejectUnicodeDots() {
        assertIllegal("src/\u2024/foo")
        assertIllegal("src/\uFF0E/foo")
        assertIllegal("\u2024")
        assertIllegal("\uFF0E")
    }

    @Test
    fun rejectIsoControl() {
        assertIllegal("foo\u0001bar")
        assertIllegal("foo\tbar")
        assertIllegal("foo\nbar")
    }

    private fun assertIllegal(raw: String) {
        assertThrows(IllegalArgumentException::class.java) {
            WorkspacePath.parse(raw)
        }
    }
}
