package dev.clankyard.build.runtime

import java.io.File
import java.io.IOException
import java.util.UUID
import okhttp3.OkHttpClient
import okhttp3.Request

class RuntimeManager(
    private val client: OkHttpClient,
    private val runtimesDir: File,
    private val packs: List<RuntimePack> = RuntimeManifest.packs,
    private val freeSpace: (File) -> Long = { it.usableSpace },
) {
    private val root = runtimesDir.canonicalFile

    init {
        require(packs.map { it.id }.distinct().size == packs.size) { "duplicate runtime pack id" }
        packs.forEach { pack ->
            require(isSafeRuntimePath(pack.installRelative)) {
                "runtime path escapes manager root: ${pack.installRelative}"
            }
        }
        root.mkdirs()
    }

    fun status(id: String): RuntimeStatus {
        val pack = packs.firstOrNull { it.id == id } ?: return RuntimeStatus(
            RuntimePack("unknown", "unknown", "any", "", "", "", "", "unknown"),
            RuntimeInstallState.Missing,
            "unknown pack",
        )
        if (!pack.isPublished) {
            return RuntimeStatus(pack, RuntimeInstallState.Blocked, pack.notes.ifBlank { "pack URL not published" })
        }
        val dest = destination(pack)
        val ready = isReady(dest, pack)
        return if (ready) {
            RuntimeStatus(pack, RuntimeInstallState.Ready)
        } else {
            RuntimeStatus(pack, RuntimeInstallState.Missing)
        }
    }

    fun allStatus(): List<RuntimeStatus> = packs.map { status(it.id) }

    fun jdkHome(): File? = readyDir("jdk-17")
    fun gradleHome(): File? = readyDir("gradle-8.11")
    fun aapt2(): File? = readyDir("aapt2")?.walkTopDown()?.firstOrNull { it.name == "aapt2" }
    fun sdkHome(): File? = readyDir("sdk-34-stub") ?: readyDir("sdk-34")

    fun androidRuntimeReady(): Boolean =
        status("jdk-17").state == RuntimeInstallState.Ready &&
            status("gradle-8.11").state == RuntimeInstallState.Ready

    suspend fun install(id: String, acknowledged: Boolean): Result<File> {
        if (!acknowledged) return Result.failure(IllegalStateException("F-Droid runtime consent required"))
        val pack = packs.firstOrNull { it.id == id }
            ?: return Result.failure(IllegalArgumentException("unknown pack"))
        if (pack.url.isNotBlank() && !pack.url.startsWith("https://")) {
            return Result.failure(IllegalArgumentException("runtime downloads must use HTTPS"))
        }
        if (!pack.isPublished) {
            return Result.failure(IllegalStateException(pack.notes.ifBlank { "pack URL not published" }))
        }
        val dest = destination(pack)
        if (isReady(dest, pack)) return Result.success(dest)
        if (dest.exists()) {
            return Result.failure(IllegalStateException("runtime destination already exists: ${dest.path}"))
        }
        val token = UUID.randomUUID().toString()
        val tmpZip = File(root, ".tmp-$id-$token.zip")
        val staging = File(root, ".staging-$id-$token")
        return runCatching {
            download(pack, tmpZip)
            val hash = ZipUnpacker.sha256(tmpZip)
            if (!hash.equals(pack.sha256, ignoreCase = true)) {
                error("SHA-256 mismatch for $id")
            }
            val extractionBudget = (freeSpace(root) - FREE_SPACE_RESERVE_BYTES)
                .coerceIn(0L, ZipUnpacker.MAX_EXTRACTED_BYTES)
            ZipUnpacker.unzip(tmpZip, staging, maxExtractedBytes = extractionBudget)
            writeMarker(staging, pack, hash)
            publish(staging, dest)
            dest
        }.onSuccess {
            tmpZip.delete()
        }.onFailure {
            tmpZip.delete()
            staging.deleteRecursively()
        }
    }

    private fun readyDir(id: String): File? {
        val st = status(id)
        if (st.state != RuntimeInstallState.Ready) return null
        return destination(st.pack)
    }

    private fun destination(pack: RuntimePack): File {
        val dest = File(root, pack.installRelative).canonicalFile
        check(contains(root, dest)) { "runtime destination escapes manager root" }
        return dest
    }

    private fun isReady(dest: File, pack: RuntimePack): Boolean {
        if (!dest.isDirectory) return false
        val marker = File(dest, READY_MARKER)
        if (!marker.isFile) return false
        val values = marker.readLines().mapNotNull { line ->
            line.substringBefore('=', missingDelimiterValue = "").takeIf { it.isNotBlank() }
                ?.let { key -> key to line.substringAfter('=', "") }
        }.toMap()
        return values["id"] == pack.id &&
            values["version"] == pack.version &&
            values["sha256"]?.equals(pack.sha256, ignoreCase = true) == true
    }

    private fun writeMarker(staging: File, pack: RuntimePack, sha256: String) {
        File(staging, READY_MARKER).writeText(
            "id=${pack.id}\nversion=${pack.version}\nsha256=$sha256\n",
        )
    }

    private fun publish(staging: File, dest: File) {
        if (!staging.isDirectory) error("runtime staging directory is missing")
        dest.parentFile?.mkdirs()
        if (!staging.renameTo(dest)) error("failed to publish ${dest.name}")
    }

    private fun download(pack: RuntimePack, dest: File) {
        val request = Request.Builder().url(pack.url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            if (response.request.url.scheme != "https") {
                throw IOException("runtime download did not remain on HTTPS")
            }
            val body = response.body ?: throw IOException("empty body")
            val contentLength = body.contentLength()
            val expectedSize = pack.expectedSizeBytes ?: contentLength.takeIf { it > 0 }
                ?: throw IOException("runtime archive size is unavailable")
            if (expectedSize !in 1..MAX_ARCHIVE_BYTES) {
                throw IOException("runtime archive size is invalid")
            }
            if (pack.expectedSizeBytes != null && contentLength >= 0 && contentLength != pack.expectedSizeBytes) {
                throw IOException("unexpected runtime archive size")
            }
            if (freeSpace(root) < requiredFreeSpace(expectedSize)) {
                throw IOException("not enough free space for runtime install")
            }
            var downloaded = 0L
            dest.outputStream().use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        downloaded += count
                        if (downloaded > MAX_ARCHIVE_BYTES) {
                            throw IOException("runtime archive is too large")
                        }
                        output.write(buffer, 0, count)
                    }
                }
            }
            if (downloaded != expectedSize) throw IOException("unexpected runtime archive size")
        }
    }

    private fun requiredFreeSpace(downloadBytes: Long): Long =
        downloadBytes.coerceIn(0L, MAX_ARCHIVE_BYTES) * 2L + FREE_SPACE_RESERVE_BYTES

    private fun contains(parent: File, child: File): Boolean {
        val parentPath = parent.canonicalPath
        val childPath = child.canonicalPath
        return childPath == parentPath || childPath.startsWith(parentPath + File.separator)
    }

    companion object {
        private const val READY_MARKER = ".clankyard-runtime"
        private const val FREE_SPACE_RESERVE_BYTES = 500L * 1024L * 1024L
        private const val MAX_ARCHIVE_BYTES = 2L * 1024L * 1024L * 1024L
    }
}
