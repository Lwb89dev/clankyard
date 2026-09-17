package dev.clankyard.editor

import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher

/**
 * Undo/redo, wordwrap, in-file search, dirty flag, and text access for a bound [CodeEditor].
 */
class CodeEditorController {
    private var editor: CodeEditor? = null
    internal var appliedLanguageId: String? = null

    var isDirty: Boolean = false
        private set

    internal fun attach(editor: CodeEditor) {
        this.editor = editor
    }

    internal fun detach() {
        editor = null
        appliedLanguageId = null
    }

    internal fun markDirty() {
        isDirty = true
    }

    fun markClean() {
        isDirty = false
    }

    fun getText(): String = editor?.text?.toString().orEmpty()

    fun setText(value: String) {
        editor?.setText(value)
        isDirty = false
    }

    fun undo() {
        editor?.undo()
    }

    fun redo() {
        editor?.redo()
    }

    fun canUndo(): Boolean = editor?.canUndo() == true

    fun canRedo(): Boolean = editor?.canRedo() == true

    fun setWordwrap(enabled: Boolean) {
        editor?.isWordwrap = enabled
    }

    fun isWordwrap(): Boolean = editor?.isWordwrap == true

    fun searchInFile(query: String, caseInsensitive: Boolean = true, regex: Boolean = false) {
        val current = editor ?: return
        if (query.isEmpty()) {
            current.searcher.stopSearch()
            return
        }
        current.searcher.search(query, EditorSearcher.SearchOptions(caseInsensitive, regex))
    }

    fun findNext(): Boolean = runSearch { it.gotoNext() }

    fun findPrevious(): Boolean = runSearch { it.gotoPrevious() }

    fun stopSearch() {
        editor?.searcher?.stopSearch()
    }

    private fun runSearch(block: (EditorSearcher) -> Boolean): Boolean {
        val searcher = editor?.searcher ?: return false
        if (!searcher.hasQuery()) return false
        return block(searcher)
    }
}
