package dev.clankyard.editor

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.rosemoe.sora.widget.CodeEditor
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CodeEditorTextTest {
    @Test
    fun setTextGetTextRoundtrip() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val editor = CodeEditor(context)
        try {
            editor.setText("hello clanker")
            assertEquals("hello clanker", editor.text.toString())
        } finally {
            editor.release()
        }
    }
}
