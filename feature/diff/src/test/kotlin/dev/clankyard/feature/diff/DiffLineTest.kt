package dev.clankyard.feature.diff

import org.junit.Assert.assertEquals
import org.junit.Test

class DiffLineTest {
    @Test
    fun parseKinds() {
        val unified = """
            --- a/f.txt
            +++ b/f.txt
            @@ -1,1 +1,2 @@
             keep
            -old
            +new
        """.trimIndent()
        val kinds = parseUnifiedLines(unified).map { it.kind }
        assertEquals(
            listOf(
                DiffLineKind.Header,
                DiffLineKind.Header,
                DiffLineKind.Hunk,
                DiffLineKind.Context,
                DiffLineKind.Removed,
                DiffLineKind.Added,
            ),
            kinds,
        )
    }
}
