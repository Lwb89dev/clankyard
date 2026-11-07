package dev.clankyard.build.engine

import dev.clankyard.build.api.BuildId
import dev.clankyard.build.api.BuildRequest
import dev.clankyard.build.api.BuildRequester
import dev.clankyard.build.api.BuildStartResult
import dev.clankyard.build.api.BuildStatus
import dev.clankyard.build.api.BuildTask
import dev.clankyard.build.api.BuildTrust
import dev.clankyard.build.runtime.RuntimeManager
import dev.clankyard.build.runtime.RuntimePack
import dev.clankyard.core.model.WorkspaceId
import dev.clankyard.workspace.DiskFileBackedWorkspace
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultBuildServiceTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun untrustedWorkshopIsRejected() = runTest {
        val ws = openWs()
        writeAndroidGradle(ws.root)
        val result = service(ws, trust = { BuildTrust.Denied }).start(request(ws.id))
        val rejected = result as BuildStartResult.Rejected
        assertEquals(BuildStatus.Untrusted, rejected.status)
    }

    @Test
    fun missingRuntimeIsRejected() = runTest {
        val ws = openWs()
        writeAndroidGradle(ws.root)
        val result = service(ws, trust = { BuildTrust.Granted }).start(request(ws.id))
        val rejected = result as BuildStartResult.Rejected
        assertEquals(BuildStatus.RuntimeMissing, rejected.status)
        assertTrue(rejected.reason.contains("Runtimes"))
    }

    @Test
    fun nonAndroidGradleIsRejected() = runTest {
        val ws = openWs()
        File(ws.root, "settings.gradle.kts").writeText("rootProject.name = \"plain\"\n")
        val result = service(ws, trust = { BuildTrust.Granted }, readyRuntimes = true)
            .start(request(ws.id))
        val rejected = result as BuildStartResult.Rejected
        assertEquals(BuildStatus.NotAndroidGradle, rejected.status)
    }

    @Test
    fun lowDiskIsRejected() = runTest {
        val ws = openWs()
        writeAndroidGradle(ws.root)
        val result = service(
            ws,
            trust = { BuildTrust.Granted },
            readyRuntimes = true,
            freeSpace = { 10L },
        ).start(request(ws.id))
        val rejected = result as BuildStartResult.Rejected
        assertEquals(BuildStatus.Failed, rejected.status)
        assertTrue(rejected.reason.contains("1 GiB"))
    }

    private fun TestScope.service(
        ws: DiskFileBackedWorkspace,
        trust: (WorkspaceId) -> BuildTrust,
        readyRuntimes: Boolean = false,
        freeSpace: (File) -> Long = { BuildResourceGates.MIN_BUILD_FREE_BYTES },
    ): DefaultBuildService {
        val runtimesDir = tmp.newFolder("runtimes")
        val packs = if (readyRuntimes) publishedPacks() else emptyList()
        val manager = RuntimeManager(OkHttpClient(), runtimesDir, packs)
        if (readyRuntimes) {
            markReady(runtimesDir, "jdk-17", "jdk-17", "17", "aa".repeat(32))
            markReady(runtimesDir, "gradle-8.11.1", "gradle-8.11", "8.11.1", "bb".repeat(32))
        }
        return DefaultBuildService(
            runtimes = manager,
            detector = ProjectDetector(),
            workspace = { ws },
            trust = trust,
            scope = this,
            cacheDir = tmp.newFolder("cache-${ws.id.value}"),
            tmpDir = tmp.newFolder("tmp-${ws.id.value}"),
            artifactsDir = tmp.newFolder("artifacts-${ws.id.value}"),
            freeSpace = freeSpace,
        )
    }

    private fun request(id: WorkspaceId) = BuildRequest(
        buildId = BuildId("b1"),
        workspaceId = id,
        task = BuildTask.AssembleDebug,
        requestedBy = BuildRequester.User,
    )

    private fun openWs(): DiskFileBackedWorkspace =
        DiskFileBackedWorkspace(
            WorkspaceId("ws"),
            "ws",
            tmp.newFolder("root"),
            tmp.newFolder("journal"),
        )

    private fun writeAndroidGradle(root: File) {
        File(root, "settings.gradle.kts").writeText("rootProject.name = \"demo\"\n")
        File(root, "build.gradle.kts").writeText(
            """
            plugins { id("com.android.application") }
            """.trimIndent(),
        )
    }

    private fun publishedPacks(): List<RuntimePack> = listOf(
        RuntimePack(
            id = "jdk-17",
            version = "17",
            abi = "arm64-v8a",
            url = "https://example.invalid/jdk.zip",
            sha256 = "aa".repeat(32),
            licenseSpdx = "GPL-2.0-with-classpath-exception",
            licenseName = "OpenJDK",
            installRelative = "jdk-17",
        ),
        RuntimePack(
            id = "gradle-8.11",
            version = "8.11.1",
            abi = "any",
            url = "https://example.invalid/gradle.zip",
            sha256 = "bb".repeat(32),
            licenseSpdx = "Apache-2.0",
            licenseName = "Apache License 2.0",
            installRelative = "gradle-8.11.1",
        ),
    )

    private fun markReady(root: File, relative: String, id: String, version: String, sha: String) {
        val dest = File(root, relative)
        dest.mkdirs()
        File(dest, ".clankyard-runtime").writeText("id=$id\nversion=$version\nsha256=$sha\n")
    }
}
