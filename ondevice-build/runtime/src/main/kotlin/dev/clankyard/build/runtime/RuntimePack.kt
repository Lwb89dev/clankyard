package dev.clankyard.build.runtime

data class RuntimePack(
    val id: String,
    val version: String,
    val abi: String,
    val url: String,
    val sha256: String,
    val licenseSpdx: String,
    val licenseName: String,
    val installRelative: String,
    val notes: String = "",
    val expectedSizeBytes: Long? = null,
) {
    init {
        require(id.matches(ID_PATTERN)) { "invalid runtime id: $id" }
        require(version.isNotBlank()) { "runtime version is blank" }
        require(abi.isNotBlank()) { "runtime ABI is blank" }
        require(isSafeRuntimePath(installRelative)) {
            "runtime install path must be relative and contained: $installRelative"
        }
        require(sha256.isEmpty() || sha256.matches(SHA256_PATTERN)) {
            "runtime SHA-256 must be 64 hexadecimal characters"
        }
        require(expectedSizeBytes == null || expectedSizeBytes > 0) {
            "runtime size must be positive when provided"
        }
    }

    val isPublished: Boolean
        get() = url.startsWith("https://") && sha256.isNotBlank()

    companion object {
        private val ID_PATTERN = Regex("[a-z0-9][a-z0-9.-]*")
        private val SHA256_PATTERN = Regex("[0-9a-fA-F]{64}")
    }
}

internal fun isSafeRuntimePath(path: String): Boolean {
    if (path.isBlank() || path.startsWith('/') || path.startsWith('\\')) return false
    val parts = path.split('/', '\\')
    return parts.none { it.isEmpty() || it == "." || it == ".." || it.contains(':') }
}

enum class RuntimeInstallState { Missing, Ready, Blocked }

data class RuntimeStatus(
    val pack: RuntimePack,
    val state: RuntimeInstallState,
    val reason: String? = null,
)

object RuntimeManifest {
    const val ABI = "arm64-v8a"

    val packs: List<RuntimePack> = listOf(
        RuntimePack(
            id = "jdk-17",
            version = "17",
            abi = ABI,
            url = "",
            sha256 = "",
            licenseSpdx = "GPL-2.0-with-classpath-exception",
            licenseName = "OpenJDK GPL-2.0-with-classpath-exception",
            installRelative = "jdk-17",
            notes = "Blocked until clankyard-runtimes publishes a relocatable zip + SHA-256.",
        ),
        RuntimePack(
            id = "gradle-8.11",
            version = "8.11.1",
            abi = "any",
            url = "",
            sha256 = "",
            licenseSpdx = "Apache-2.0",
            licenseName = "Apache License 2.0",
            installRelative = "gradle-8.11.1",
            notes = "Official Gradle -bin.zip after JDK pack exists.",
        ),
        RuntimePack(
            id = "aapt2",
            version = "pending",
            abi = ABI,
            url = "",
            sha256 = "",
            licenseSpdx = "Apache-2.0",
            licenseName = "Apache License 2.0",
            installRelative = "aapt2",
            notes = "Blocked until Bionic aapt2 zip is published.",
        ),
        RuntimePack(
            id = "sdk-34-stub",
            version = "34",
            abi = "any",
            url = "",
            sha256 = "",
            licenseSpdx = "Apache-2.0",
            licenseName = "AOSP android.jar stub (Apache-2.0)",
            installRelative = "sdk-34",
            notes = "platforms/android-34/{android.jar,source.properties}. Google platform zip is optional NonFreeAdd.",
        ),
    )
}
