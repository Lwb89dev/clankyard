package dev.clankyard.build.engine

import dev.clankyard.core.model.WorkspaceId
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ArtifactCollectorTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun copiesDebugApkIntoArtifacts() {
        val root = tmp.newFolder("ws")
        val apkDir = File(root, "app/build/outputs/apk/debug")
        apkDir.mkdirs()
        val apk = File(apkDir, "app-debug.apk")
        apk.writeBytes(byteArrayOf(1, 2, 3))
        val found = ArtifactCollector.findDebugApk(root)!!
        val artifacts = tmp.newFolder("artifacts")
        val ref = ArtifactCollector.copyTo(artifacts, found, WorkspaceId("ws-1"))
        assertEquals("ws-1/app-debug.apk", ref.relative)
        assertEquals("debug", ref.variant)
        assertTrue(File(artifacts, ref.relative).isFile)
    }
}
