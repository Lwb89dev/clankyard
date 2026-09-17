package dev.clankyard.diff

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MyersDiffEngineTest {
    private val engine: DiffEngine = MyersDiffEngine()

    @Test
    fun identicalHasHeadersAndNoHunk() {
        val diff = engine.unified("a\nb\n", "a\nb\n", "f.txt")
        assertTrue(diff.startsWith("--- a/f.txt\n+++ b/f.txt\n"))
        assertFalse(diff.contains("@@"))
        assertFalse(diff.lineSequence().any { it.startsWith("+") && !it.startsWith("+++") })
        assertFalse(diff.lineSequence().any { it.startsWith("-") && !it.startsWith("---") })
    }

    @Test
    fun emptyToContentIsAllInserts() {
        val diff = engine.unified("", "hello\nworld\n", "new.txt")
        assertTrue(diff.contains("@@ -0,0 +1,2 @@"))
        assertTrue(diff.contains("+hello"))
        assertTrue(diff.contains("+world"))
        assertFalse(diff.contains("\n-"))
    }

    @Test
    fun contentToEmptyIsAllDeletes() {
        val diff = engine.unified("hello\nworld\n", "", "gone.txt")
        assertTrue(diff.contains("@@ -1,2 +0,0 @@"))
        assertTrue(diff.contains("-hello"))
        assertTrue(diff.contains("-world"))
    }

    @Test
    fun replaceMiddleLine() {
        val before = "keep\nold\nkeep\n"
        val after = "keep\nnew\nkeep\n"
        val diff = engine.unified(before, after, "m.txt")
        assertTrue(diff.contains("-old"))
        assertTrue(diff.contains("+new"))
        assertTrue(diff.contains(" keep"))
    }

    @Test
    fun insertLineBetween() {
        val diff = engine.unified("a\nc\n", "a\nb\nc\n", "i.txt")
        assertTrue(diff.contains("+b"))
        assertTrue(diff.contains(" a") || diff.contains("\na\n") || diff.contains(" a\n"))
    }

    @Test
    fun noNewlineAtEofMarker() {
        val diff = engine.unified("a", "a\nb", "nl.txt")
        assertTrue(diff.contains("\\ No newline at end of file") || diff.contains("+b"))
        val both = engine.unified("x", "y", "z.txt")
        assertTrue(both.contains("-x"))
        assertTrue(both.contains("+y"))
        assertTrue(both.contains("\\ No newline at end of file"))
    }

    @Test
    fun multipleHunksStaySeparate() {
        val before = (1..20).joinToString("\n", postfix = "\n") { "line$it" }
        val after = before.replace("line2\n", "LINE2\n").replace("line18\n", "LINE18\n")
        val diff = engine.unified(before, after, "h.txt")
        val hunks = Regex("@@ ").findAll(diff).count()
        assertEquals(2, hunks)
        assertTrue(diff.contains("-line2"))
        assertTrue(diff.contains("+LINE2"))
        assertTrue(diff.contains("-line18"))
        assertTrue(diff.contains("+LINE18"))
    }

    @Test
    fun pathLabelIsQuotedInHeaders() {
        val diff = engine.unified("a\n", "b\n", "src/Main.kt")
        assertTrue(diff.startsWith("--- a/src/Main.kt\n+++ b/src/Main.kt\n"))
    }
}
