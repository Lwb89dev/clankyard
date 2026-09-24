package dev.clankyard.build.engine

import dev.clankyard.core.model.WorkspaceId
import dev.clankyard.workspace.DiskFileBackedWorkspace
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProjectDetectorTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun detectsAndroidApplicationWithoutExecutingBuildScripts() = runBlocking {
        val workspace = workspace("android")
        File(workspace.root, "settings.gradle.kts").writeText("""rootProject.name = "demo"""")
        File(workspace.root, "build.gradle.kts").writeText(
            """plugins { id("com.android.application") version "8.7.3" apply false }""",
        )

        assertEquals(ProjectKind.GradleAndroidApp, ProjectDetector().detect(workspace))
    }

    @Test
    fun emptyWorkspaceIsNotGradle() = runBlocking {
        assertEquals(ProjectKind.NotGradle, ProjectDetector().detect(workspace("empty")))
    }

    private fun workspace(name: String): DiskFileBackedWorkspace = DiskFileBackedWorkspace(
        id = WorkspaceId(name),
        displayName = name,
        root = tmp.newFolder(name),
        journalDir = tmp.newFolder("journal-$name"),
    )
}
