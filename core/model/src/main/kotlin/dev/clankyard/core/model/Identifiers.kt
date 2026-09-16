package dev.clankyard.core.model

@JvmInline
value class ProviderId(val value: String)

@JvmInline
value class RequestId(val value: String)

@JvmInline
value class SessionId(val value: String)

@JvmInline
value class PatchSetId(val value: String)

@JvmInline
value class ContentHash(val sha256Hex: String)

@JvmInline
value class WorkspaceId(val value: String)

enum class AuthenticationKind { ApiKey, OAuth, ExternalCli, Local }
