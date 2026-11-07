package dev.clankyard.build.engine

import dev.clankyard.build.api.ArtifactRef
import dev.clankyard.core.model.WorkspaceId
import java.io.File

object ArtifactCollector {
    fun findDebugApk(workspaceRoot: File): File? {
        val root = workspaceRoot.canonicalFile
        return root.walkTopDown()
            .firstOrNull { file ->
                file.isFile &&
                    file.name.endsWith(".apk", ignoreCase = true) &&
                    file.path.contains("${File.separator}outputs${File.separator}apk${File.separator}")
            }
    }

    fun copyTo(artifactsDir: File, apk: File, workspaceId: WorkspaceId): ArtifactRef {
        val destDir = File(artifactsDir, workspaceId.value).canonicalFile
        destDir.mkdirs()
        val dest = File(destDir, "app-debug.apk")
        apk.copyTo(dest, overwrite = true)
        return ArtifactRef(
            workspaceId = workspaceId,
            relative = "${workspaceId.value}/app-debug.apk",
            variant = "debug",
        )
    }
}
