package dev.clankyard.workspace

import java.io.File

/**
 * App-private sandbox root. Created on first launch.
 * Workshops live under [workspacesDir]; credentials/journal stay *outside* this tree.
 * This is not a kernel chroot: the app UID can still open absolute paths.
 * The local terminal only *starts* commands with cwd inside this tree.
 */
object WorkshopEnvironment {
    const val DIR = "environment"

    fun root(filesDir: File): File = File(filesDir, DIR)

    fun ensure(filesDir: File): File {
        val env = root(filesDir)
        env.mkdirs()
        val readme = File(env, "README.txt")
        if (!readme.exists()) {
            readme.writeText(README)
        }
        workspacesDir(filesDir).mkdirs()
        return env
    }

    fun workspacesDir(filesDir: File): File {
        val nested = File(root(filesDir), "workspaces")
        val legacy = File(filesDir, "workspaces")
        if (legacy.isDirectory && !nested.exists()) {
            root(filesDir).mkdirs()
            if (!legacy.renameTo(nested)) nested.mkdirs()
        } else {
            nested.mkdirs()
        }
        return nested
    }

    fun contains(jail: File, path: File): Boolean {
        val root = runCatching { jail.canonicalFile }.getOrNull() ?: return false
        val target = runCatching { path.canonicalFile }.getOrNull() ?: return false
        if (target == root) return true
        val prefix = root.path + File.separator
        return target.path.startsWith(prefix)
    }

    private const val README =
        "Clankyard environment sandbox.\n" +
            "The local terminal runs commands with cwd in this folder (or a workshop under it).\n" +
            "It is not a Linux chroot. Do not put secrets here; API keys live outside this tree.\n"
}