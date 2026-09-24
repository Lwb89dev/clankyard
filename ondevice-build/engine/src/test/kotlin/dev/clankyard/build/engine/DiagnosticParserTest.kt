package dev.clankyard.build.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DiagnosticParserTest {
    @Test
    fun parsesKotlincLine() {
        val item = DiagnosticParser.parse(
            "e: file:///tmp/ws/app/src/main/kotlin/Foo.kt:3:5 Type mismatch",
            workshopRoot = "/tmp/ws",
        )
        assertNotNull(item)
        assertEquals("app/src/main/kotlin/Foo.kt", item!!.path?.relative)
        assertEquals(2, item.line)
        assertEquals(4, item.column)
        assertEquals("Type mismatch", item.message)
        assertEquals("kotlinc", item.source)
    }
}
