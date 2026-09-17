package dev.clankyard.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkshopKeyMapTest {
    @Test
    fun ctrlPIsFilePalette() {
        assertEquals(
            WorkshopShortcut.FilePalette,
            WorkshopKeyMap.shortcut(KeyChord(WorkshopKey.P, ctrl = true)),
        )
    }

    @Test
    fun ctrlShiftFIsProjectSearch() {
        assertEquals(
            WorkshopShortcut.ProjectSearch,
            WorkshopKeyMap.shortcut(KeyChord(WorkshopKey.F, ctrl = true, shift = true)),
        )
    }

    @Test
    fun ctrlSIsSave() {
        assertEquals(
            WorkshopShortcut.Save,
            WorkshopKeyMap.shortcut(KeyChord(WorkshopKey.S, ctrl = true)),
        )
    }

    @Test
    fun ctrlShiftSIsSaveAll() {
        assertEquals(
            WorkshopShortcut.SaveAll,
            WorkshopKeyMap.shortcut(KeyChord(WorkshopKey.S, ctrl = true, shift = true)),
        )
    }

    @Test
    fun ctrlGraveIsToggleTerminal() {
        assertEquals(
            WorkshopShortcut.ToggleTerminal,
            WorkshopKeyMap.shortcut(KeyChord(WorkshopKey.Grave, ctrl = true)),
        )
    }

    @Test
    fun ctrlShiftPIsCommandPalette() {
        assertEquals(
            WorkshopShortcut.CommandPalette,
            WorkshopKeyMap.shortcut(KeyChord(WorkshopKey.P, ctrl = true, shift = true)),
        )
    }

    @Test
    fun escapeDismissesPalettes() {
        assertEquals(
            WorkshopShortcut.DismissPalettes,
            WorkshopKeyMap.shortcut(KeyChord(WorkshopKey.Escape)),
        )
    }

    @Test
    fun deleteIsNotBoundToFileDelete() {
        assertNull(WorkshopKeyMap.shortcut(KeyChord(WorkshopKey.Delete)))
        assertNull(WorkshopKeyMap.shortcut(KeyChord(WorkshopKey.Delete, ctrl = true)))
        assertNull(WorkshopKeyMap.shortcut(KeyChord(WorkshopKey.Delete, shift = true)))
    }

    @Test
    fun undoRedoChords() {
        assertEquals(
            WorkshopShortcut.Undo,
            WorkshopKeyMap.shortcut(KeyChord(WorkshopKey.Z, ctrl = true)),
        )
        assertEquals(
            WorkshopShortcut.Redo,
            WorkshopKeyMap.shortcut(KeyChord(WorkshopKey.Z, ctrl = true, shift = true)),
        )
        assertEquals(
            WorkshopShortcut.Redo,
            WorkshopKeyMap.shortcut(KeyChord(WorkshopKey.Y, ctrl = true)),
        )
    }

    @Test
    fun unmatchedChordsAreIgnored() {
        assertNull(WorkshopKeyMap.shortcut(KeyChord(WorkshopKey.F, ctrl = true)))
        assertNull(WorkshopKeyMap.shortcut(KeyChord(WorkshopKey.P)))
        assertNull(WorkshopKeyMap.shortcut(KeyChord(WorkshopKey.S, alt = true, ctrl = true)))
    }
}
