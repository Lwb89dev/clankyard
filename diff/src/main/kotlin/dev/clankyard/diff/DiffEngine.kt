package dev.clankyard.diff

/**
 * Unified-diff **preview**. Not a GNU-patch apply path, not an AI module.
 */
interface DiffEngine {
    fun unified(beforeUtf8: String, afterUtf8: String, pathLabel: String): String
}
