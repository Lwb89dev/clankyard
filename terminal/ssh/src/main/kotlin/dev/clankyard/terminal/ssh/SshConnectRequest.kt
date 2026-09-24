package dev.clankyard.terminal.ssh

data class SshConnectRequest(
    val host: String,
    val port: Int = 22,
    val username: String,
    val password: String?,
    val remoteCwd: String? = null,
    val fingerprint: String? = null,
)
