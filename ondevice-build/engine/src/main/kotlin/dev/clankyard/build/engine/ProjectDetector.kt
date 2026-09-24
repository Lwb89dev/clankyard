package dev.clankyard.build.engine

import dev.clankyard.core.model.WorkspacePath
import dev.clankyard.workspace.FileBackedWorkspace

class ProjectDetector {
    suspend fun detect(workspace: FileBackedWorkspace): ProjectKind {
        val settings = read(workspace, "settings.gradle.kts") ?: read(workspace, "settings.gradle")
        val rootBuild = read(workspace, "build.gradle.kts") ?: read(workspace, "build.gradle")
        if (settings == null && rootBuild == null) return ProjectKind.NotGradle
        val blob = listOfNotNull(settings, rootBuild, read(workspace, "app/build.gradle.kts"), read(workspace, "app/build.gradle"))
            .joinToString("\n")
        return when {
            blob.contains("com.android.application") -> ProjectKind.GradleAndroidApp
            blob.contains("com.android.library") -> ProjectKind.GradleAndroidLibrary
            blob.contains("org.jetbrains.kotlin.jvm") || blob.contains("java") -> ProjectKind.GradleJvm
            else -> ProjectKind.GradleUnknown
        }
    }

    private suspend fun read(workspace: FileBackedWorkspace, relative: String): String? {
        val path = runCatching { WorkspacePath.parse(relative) }.getOrNull() ?: return null
        return runCatching { workspace.readUtf8(path, 256 * 1024) }.getOrNull()
    }
}
