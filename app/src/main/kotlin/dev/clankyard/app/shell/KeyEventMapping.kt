package dev.clankyard.app.shell

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import dev.clankyard.core.ui.KeyChord
import dev.clankyard.core.ui.WorkshopKey
import dev.clankyard.core.ui.WorkshopKeyMap
import dev.clankyard.core.ui.WorkshopShortcut

fun KeyEvent.toWorkshopShortcut(): WorkshopShortcut? {
    if (type != KeyEventType.KeyDown) return null
    val workshopKey = when (key) {
        Key.P -> WorkshopKey.P
        Key.F -> WorkshopKey.F
        Key.S -> WorkshopKey.S
        Key.Grave -> WorkshopKey.Grave
        Key.Z -> WorkshopKey.Z
        Key.Y -> WorkshopKey.Y
        Key.Escape -> WorkshopKey.Escape
        Key.Backspace, Key.Delete -> WorkshopKey.Delete
        else -> return null
    }
    val chord = KeyChord(
        key = workshopKey,
        ctrl = isCtrlPressed,
        shift = isShiftPressed,
        alt = isAltPressed,
        meta = isMetaPressed,
    )
    return WorkshopKeyMap.shortcut(chord)
}
