package dev.clankyard.workspace

/** Directory names skipped by watch/search/GoToFile. Explorer hides them by default. */
object GeneratedDirNames {
    val NAMES = setOf("build", ".gradle", "captures")

    fun hides(name: String): Boolean = name in NAMES
}
