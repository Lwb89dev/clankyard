package dev.clankyard.editor

import android.content.Context
import android.graphics.Typeface
import android.view.ViewGroup
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.EventReceiver
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.widget.CodeEditor

/** IME padding hook so the cursor is not under the software keyboard. */
fun Modifier.codeEditorImePadding(): Modifier = imePadding()

@Composable
fun CodeEditorPane(
    fileName: String,
    text: String,
    modifier: Modifier = Modifier,
    controller: CodeEditorController? = null,
    readOnly: Boolean = false,
    wordwrap: Boolean = true,
    applyImePadding: Boolean = true,
    /** Bump when loading a different buffer for the same [fileName] (reload/revert). */
    contentEpoch: Long = 0L,
    cursorLine: Int = 0,
    cursorCol: Int = 0,
    languageSupports: LanguageSupportRegistry = LanguageSupportRegistry.Default,
    onTextChange: (String) -> Unit = {},
    onDirtyChange: (Boolean) -> Unit = {},
    onCursorChange: (Int, Int) -> Unit = { _, _ -> },
) {
    val handle = controller ?: remember { CodeEditorController() }
    val latestText = rememberUpdatedState(text)
    val latestFile = rememberUpdatedState(fileName)
    val latestEpoch = rememberUpdatedState(contentEpoch)
    val latestReadOnly = rememberUpdatedState(readOnly)
    val latestWordwrap = rememberUpdatedState(wordwrap)
    val latestOnTextChange = rememberUpdatedState(onTextChange)
    val latestOnDirtyChange = rememberUpdatedState(onDirtyChange)
    val latestOnCursorChange = rememberUpdatedState(onCursorChange)
    val latestCursorLine = rememberUpdatedState(cursorLine)
    val latestCursorCol = rememberUpdatedState(cursorCol)
    val latestLanguages = rememberUpdatedState(languageSupports)
    val viewModifier = if (applyImePadding) modifier.codeEditorImePadding() else modifier
    AndroidView(
        modifier = viewModifier,
        factory = { context ->
            createBoundEditor(
                context = context,
                handle = handle,
                fileName = latestFile.value,
                text = latestText.value,
                contentEpoch = latestEpoch.value,
                wordwrap = latestWordwrap.value,
                readOnly = latestReadOnly.value,
                registry = latestLanguages.value,
                onTextChange = { latestOnTextChange.value(it) },
                onDirtyChange = { latestOnDirtyChange.value(it) },
                onCursorChange = { line, col -> latestOnCursorChange.value(line, col) },
                cursorLine = latestCursorLine.value,
                cursorCol = latestCursorCol.value,
            )
        },
        update = { editor ->
            editor.isWordwrap = latestWordwrap.value
            editor.isEditable = !latestReadOnly.value
            maybeApplyDocument(
                editor = editor,
                handle = handle,
                fileName = latestFile.value,
                text = latestText.value,
                contentEpoch = latestEpoch.value,
                registry = latestLanguages.value,
                onDirtyChange = latestOnDirtyChange.value,
                cursorLine = latestCursorLine.value,
                cursorCol = latestCursorCol.value,
            )
        },
        onRelease = { editor ->
            handle.detach()
            editor.release()
        },
    )
}

private fun createBoundEditor(
    context: Context,
    handle: CodeEditorController,
    fileName: String,
    text: String,
    contentEpoch: Long,
    wordwrap: Boolean,
    readOnly: Boolean,
    registry: LanguageSupportRegistry,
    onTextChange: (String) -> Unit,
    onDirtyChange: (Boolean) -> Unit,
    onCursorChange: (Int, Int) -> Unit,
    cursorLine: Int,
    cursorCol: Int,
): CodeEditor {
    TextMateBootstrap.ensureLoaded(context)
    val editor = CodeEditor(context)
    configureEditor(editor, handle, wordwrap, readOnly)
    applyDocument(editor, handle, fileName, text, contentEpoch, registry, onDirtyChange, cursorLine, cursorCol)
    listenForEdits(editor, handle, onTextChange, onDirtyChange, onCursorChange)
    return editor
}

private fun maybeApplyDocument(
    editor: CodeEditor,
    handle: CodeEditorController,
    fileName: String,
    text: String,
    contentEpoch: Long,
    registry: LanguageSupportRegistry,
    onDirtyChange: (Boolean) -> Unit,
    cursorLine: Int,
    cursorCol: Int,
) {
    if (handle.appliedFileName == fileName && handle.appliedEpoch == contentEpoch) return
    applyDocument(editor, handle, fileName, text, contentEpoch, registry, onDirtyChange, cursorLine, cursorCol)
}

private fun applyDocument(
    editor: CodeEditor,
    handle: CodeEditorController,
    fileName: String,
    text: String,
    contentEpoch: Long,
    registry: LanguageSupportRegistry,
    onDirtyChange: (Boolean) -> Unit,
    cursorLine: Int,
    cursorCol: Int,
) {
    bindLanguage(editor, handle, fileName, registry)
    editor.setText(text)
    handle.appliedFileName = fileName
    handle.appliedEpoch = contentEpoch
    handle.markClean()
    onDirtyChange(false)
    handle.moveCursor(cursorLine, cursorCol)
}

private fun listenForEdits(
    editor: CodeEditor,
    handle: CodeEditorController,
    onTextChange: (String) -> Unit,
    onDirtyChange: (Boolean) -> Unit,
    onCursorChange: (Int, Int) -> Unit,
) {
    editor.subscribeEvent(
        ContentChangeEvent::class.java,
        EventReceiver { event, _ ->
            if (event.action != ContentChangeEvent.ACTION_SET_NEW_TEXT) {
                handle.markDirty()
                onDirtyChange(true)
                onTextChange(editor.text.toString())
            }
        },
    )
    editor.subscribeEvent(
        SelectionChangeEvent::class.java,
        EventReceiver { _, _ ->
            val cursor = editor.cursor
            onCursorChange(cursor.leftLine, cursor.leftColumn)
        },
    )
}

private fun configureEditor(
    editor: CodeEditor,
    handle: CodeEditorController,
    wordwrap: Boolean,
    readOnly: Boolean,
) {
    editor.layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
    )
    editor.typefaceText = Typeface.MONOSPACE
    editor.isUndoEnabled = true
    editor.isWordwrap = wordwrap
    editor.isEditable = !readOnly
    editor.colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
    handle.attach(editor)
}

private fun bindLanguage(
    editor: CodeEditor,
    handle: CodeEditorController,
    fileName: String,
    registry: LanguageSupportRegistry,
) {
    val support = registry.forFileName(fileName)
    if (handle.appliedLanguageId == support.id) return
    editor.setEditorLanguage(support.createLanguage())
    handle.appliedLanguageId = support.id
}
