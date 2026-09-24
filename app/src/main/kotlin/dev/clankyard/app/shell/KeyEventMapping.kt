package dev.clankyard.app.shell

import android.view.KeyEvent
import dev.clankyard.core.ui.KeyChord
import dev.clankyard.core.ui.WorkshopKey
import dev.clankyard.core.ui.WorkshopKeyMap
import dev.clankyard.core.ui.WorkshopShortcut

fun KeyEvent.toWorkshopShortcut(): WorkshopShortcut? {
    if (action != KeyEvent.ACTION_DOWN) return null
    val key = when (keyCode) {
        KeyEvent.KEYCODE_P -> WorkshopKey.P
        KeyEvent.KEYCODE_F -> WorkshopKey.F
        KeyEvent.KEYCODE_S -> WorkshopKey.S
        KeyEvent.KEYCODE_GRAVE -> WorkshopKey.Grave
        KeyEvent.KEYCODE_Z -> WorkshopKey.Z
        KeyEvent.KEYCODE_Y -> WorkshopKey.Y
        KeyEvent.KEYCODE_ESCAPE -> WorkshopKey.Escape
        KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL -> WorkshopKey.Delete
        else -> return null
    }
    val chord = KeyChord(
        key = key,
        ctrl = isCtrlPressed,
        shift = isShiftPressed,
        alt = isAltPressed,
        meta = isMetaPressed,
    )
    return WorkshopKeyMap.shortcut(chord)
}
