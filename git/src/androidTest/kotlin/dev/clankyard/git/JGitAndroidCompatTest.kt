package dev.clankyard.git

import dev.clankyard.core.model.WorkspaceId
import dev.clankyard.core.model.WorkspacePath
import dev.clankyard.workspace.DiskFileBackedWorkspace
import java.io.File

/**
 * CLANK-014 — JGit Android compatibility suite (instrumented skeleton).
 *
 * `:git` is a JVM library so this source set is not compiled by `./gradlew :git:test`.
 * Run it as an `androidTest` on **API 29 and API 36** against app-specific dirs
 * (`context.filesDir` / `context.getExternalFilesDir(null)`). Public FUSE
 * (`/storage/emulated/0/Download`, …) is **not** the default root.
 *
 * Catalog pin is `org.eclipse.jgit:org.eclipse.jgit:7.7.1.202607240634-r` for
 * everyone. If either API level fails with `NoSuchMethodError`, change the
 * catalog to `6.10.1.202505221210-r` for all devices. No runtime fallback.
 *
 * When an emulator is available, wire this class with AndroidJUnit4:
 *
 * ```
 * @RunWith(AndroidJUnit4::class)
 * class JGitAndroidCompatTest {
 *   @Test fun initStatusCommitDiffOnAppFilesDir() { ... }
 * }
 * ```
 *
 * using `InstrumentationRegistry.getInstrumentation().targetContext.filesDir`.
 */
class JGitAndroidCompatTest {
    fun initStatusCommitDiffOnAppFilesDir(appFilesDir: File) {
        val root = File(appFilesDir, "clank-014").apply { mkdirs() }
        val journal = File(appFilesDir, "clank-014-journal").apply { mkdirs() }
        val ws = DiskFileBackedWorkspace(WorkspaceId("clank-014"), "compat", root, journal)
        val git = JGitRepository()
        val identity = GitIdentity("Workshop User", "user@clankyard.dev")
        val handle = kotlinx.coroutines.runBlocking { git.init(ws, identity) }
        File(ws.root, "a.txt").writeText("one\n")
        val path = WorkspacePath.parse("a.txt")
        kotlinx.coroutines.runBlocking {
            handle.stage(listOf(path))
            handle.commit("init", identity)
            File(ws.root, "a.txt").writeText("one\ntwo\n")
            val status = handle.status()
            check(status.unstaged.contains(path)) { "expected unstaged $path, got $status" }
            val diffs = handle.diff(path)
            check(diffs.any { it.unified.contains("+two") }) { "expected +two in $diffs" }
        }
        handle.close()
    }
}
