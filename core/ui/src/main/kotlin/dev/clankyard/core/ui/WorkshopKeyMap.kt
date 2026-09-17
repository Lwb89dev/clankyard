package dev.clankyard.core.ui

/**
 * Hardware chords handled at Activity/root, not only inside a focused composable.
 * [WorkshopKey.Delete] is never bound to file delete (confirm-dialog only).
 */
enum class WorkshopKey { P, F, S, Grave, Z, Y, Escape, Delete }

data class KeyChord(
    val key: WorkshopKey,
    val ctrl: Boolean = false,
    val shift: Boolean = false,
    val alt: Boolean = false,
    val meta: Boolean = false,
)

enum class WorkshopShortcut {
    FilePalette,
    ProjectSearch,
    Save,
    SaveAll,
    ToggleTerminal,
    CommandPalette,
    Undo,
    Redo,
    DismissPalettes,
}

object WorkshopKeyMap {
    fun shortcut(chord: KeyChord): WorkshopShortcut? {
        if (chord.alt || chord.meta) return null
        if (chord.key == WorkshopKey.Delete) return null
        return when {
            chord.ctrl && chord.shift && chord.key == WorkshopKey.P -> WorkshopShortcut.CommandPalette
            chord.ctrl && !chord.shift && chord.key == WorkshopKey.P -> WorkshopShortcut.FilePalette
            chord.ctrl && chord.shift && chord.key == WorkshopKey.F -> WorkshopShortcut.ProjectSearch
            chord.ctrl && chord.shift && chord.key == WorkshopKey.S -> WorkshopShortcut.SaveAll
            chord.ctrl && !chord.shift && chord.key == WorkshopKey.S -> WorkshopShortcut.Save
            chord.ctrl && !chord.shift && chord.key == WorkshopKey.Grave -> WorkshopShortcut.ToggleTerminal
            chord.ctrl && chord.shift && chord.key == WorkshopKey.Z -> WorkshopShortcut.Redo
            chord.ctrl && !chord.shift && chord.key == WorkshopKey.Y -> WorkshopShortcut.Redo
            chord.ctrl && !chord.shift && chord.key == WorkshopKey.Z -> WorkshopShortcut.Undo
            !chord.ctrl && !chord.shift && chord.key == WorkshopKey.Escape -> WorkshopShortcut.DismissPalettes
            else -> null
        }
    }
}
