package dev.clankyard.build.runtime

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RuntimeManagerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun unpublishedPackIsBlocked() {
        val manager = RuntimeManager(OkHttpClient(), tmp.newFolder("runtimes"))
        val status = manager.status("jdk-17")
        assertEquals(RuntimeInstallState.Blocked, status.state)
        assertTrue(status.reason.orEmpty().contains("Blocked"))
    }

    @Test
    fun installRejectsMissingConsentAndHttpBeforeWriting() = runBlocking {
        val root = tmp.newFolder("runtimes")
        val pack = RuntimePack(
            id = "test-pack",
            version = "1",
            abi = "any",
            url = "http://example.invalid/test.zip",
            sha256 = "a".repeat(64),
            licenseSpdx = "Apache-2.0",
            licenseName = "Apache License 2.0",
            installRelative = "test-pack-1",
            expectedSizeBytes = 1,
        )
        val manager = RuntimeManager(OkHttpClient(), root, listOf(pack))

        val noConsent = manager.install("test-pack", acknowledged = false)
        assertFalse(noConsent.isSuccess)
        assertFalse(File(root, "test-pack-1").exists())

        val http = manager.install("test-pack", acknowledged = true)
        assertFalse(http.isSuccess)
        assertTrue(http.exceptionOrNull()!!.message.orEmpty().contains("HTTPS"))
        assertFalse(File(root, "test-pack-1").exists())
    }

    @Test
    fun verifiedArchiveIsPublishedAtomically() = runBlocking {
        val archive = zip("hello.txt" to "hello".toByteArray())
        val root = tmp.newFolder("runtimes")
        val pack = testPack(
            id = "verified-pack",
            url = "https://runtime.test/verified.zip",
            archive = archive,
        )
        val manager = RuntimeManager(
            client = serving(archive),
            runtimesDir = root,
            packs = listOf(pack),
            freeSpace = { Long.MAX_VALUE },
        )

        val result = manager.install(pack.id, acknowledged = true)

        assertTrue(result.isSuccess)
        assertEquals("hello", File(root, "verified-pack-1/hello.txt").readText())
        assertEquals(RuntimeInstallState.Ready, manager.status(pack.id).state)
        assertTrue(root.listFiles().orEmpty().none { it.name.startsWith(".tmp-") })
    }

    @Test
    fun hashMismatchLeavesNoPublishedDirectory() = runBlocking {
        val archive = zip("hello.txt" to "hello".toByteArray())
        val root = tmp.newFolder("runtimes")
        val pack = testPack(
            id = "mismatch-pack",
            url = "https://runtime.test/mismatch.zip",
            archive = archive,
            sha256 = "f".repeat(64),
        )
        val manager = RuntimeManager(
            client = serving(archive),
            runtimesDir = root,
            packs = listOf(pack),
            freeSpace = { Long.MAX_VALUE },
        )

        val result = manager.install(pack.id, acknowledged = true)

        assertFalse(result.isSuccess)
        assertFalse(File(root, "mismatch-pack-1").exists())
        assertTrue(root.listFiles().orEmpty().isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun packCannotEscapeRuntimeRoot() {
        RuntimePack(
            id = "evil",
            version = "1",
            abi = "any",
            url = "https://example.invalid/evil.zip",
            sha256 = "b".repeat(64),
            licenseSpdx = "Apache-2.0",
            licenseName = "Apache License 2.0",
            installRelative = "../credentials",
        )
    }

    @Test
    fun unpackRejectsArchiveThatExpandsPastBudget() {
        val archive = zip("large.bin" to ByteArray(128 * 1024))
        val source = tmp.newFile("expanding.zip").apply { writeBytes(archive) }
        val destination = tmp.newFolder("expanded")

        val error = runCatching {
            ZipUnpacker.unzip(source, destination, maxExtractedBytes = 4 * 1024)
        }.exceptionOrNull()

        assertTrue(error is IOException)
        assertTrue(error!!.message.orEmpty().contains("allowed size"))
    }

    private fun testPack(
        id: String,
        url: String,
        archive: ByteArray,
        sha256: String = ZipUnpacker.sha256(tmp.newFile("$id.zip").apply { writeBytes(archive) }),
    ): RuntimePack = RuntimePack(
        id = id,
        version = "1",
        abi = "any",
        url = url,
        sha256 = sha256,
        licenseSpdx = "Apache-2.0",
        licenseName = "Apache License 2.0",
        installRelative = "$id-1",
        expectedSizeBytes = archive.size.toLong(),
    )

    private fun serving(bytes: ByteArray): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(bytes.toResponseBody("application/zip".toMediaType()))
                .build()
        }
        .build()

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { out ->
            entries.forEach { (name, content) ->
                out.putNextEntry(ZipEntry(name))
                out.write(content)
                out.closeEntry()
            }
        }
        return bytes.toByteArray()
    }
}
