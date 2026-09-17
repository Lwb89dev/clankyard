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
    languageSupports: LanguageSupportRegistry = LanguageSupportRegistry.Default,
    onTextChange: (String) -> Unit = {},
    onDirtyChange: (Boolean) -> Unit = {},
) {
    val handle = controller ?: remember { CodeEditorController() }
    val latestText = rememberUpdatedState(text)
    val latestFile = rememberUpdatedState(fileName)
    val latestReadOnly = rememberUpdatedState(readOnly)
    val latestWordwrap = rememberUpdatedState(wordwrap)
    val latestOnTextChange = rememberUpdatedState(onTextChange)
    val latestOnDirtyChange = rememberUpdatedState(onDirtyChange)
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
                wordwrap = latestWordwrap.value,
                readOnly = latestReadOnly.value,
                registry = latestLanguages.value,
                onTextChange = { latestOnTextChange.value(it) },
                onDirtyChange = { latestOnDirtyChange.value(it) },
            )
        },
        update = { editor ->
            editor.isWordwrap = latestWordwrap.value
            editor.isEditable = !latestReadOnly.value
            bindLanguage(editor, handle, latestFile.value, latestLanguages.value)
            val incoming = latestText.value
            if (editor.text.toString() != incoming) {
                editor.setText(incoming)
            }
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
    wordwrap: Boolean,
    readOnly: Boolean,
    registry: LanguageSupportRegistry,
    onTextChange: (String) -> Unit,
    onDirtyChange: (Boolean) -> Unit,
): CodeEditor {
    TextMateBootstrap.ensureLoaded(context)
    val editor = CodeEditor(context)
    configureEditor(editor, handle, wordwrap, readOnly)
    bindLanguage(editor, handle, fileName, registry)
    editor.setText(text)
    handle.markClean()
    listenForEdits(editor, handle, onTextChange, onDirtyChange)
    return editor
}

private fun listenForEdits(
    editor: CodeEditor,
    handle: CodeEditorController,
    onTextChange: (String) -> Unit,
    onDirtyChange: (Boolean) -> Unit,
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
