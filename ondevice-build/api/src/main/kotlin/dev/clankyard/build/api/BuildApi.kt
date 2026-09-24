package dev.clankyard.build.api

import dev.clankyard.core.model.WorkspaceId
import dev.clankyard.core.model.WorkspacePath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class BuildTask { AssembleDebug, Clean, RebuildDebug }

enum class BuildTrust { Denied, Granted }

enum class BuildRequester { User, ClankerPropose }

enum class BuildStatus {
    Success,
    Failed,
    Cancelled,
    RuntimeMissing,
    Untrusted,
    NotAndroidGradle,
}

enum class DiagnosticSeverity { Error, Warning, Info }

@JvmInline
value class BuildId(val value: String)

data class BuildRequest(
    val buildId: BuildId,
    val workspaceId: WorkspaceId,
    val task: BuildTask,
    val requestedBy: BuildRequester,
)

data class ArtifactRef(
    val workspaceId: WorkspaceId,
    val relative: String,
    val variant: String,
)

data class BuildDiagnostic(
    val path: WorkspacePath?,
    val line: Int,
    val column: Int,
    val severity: DiagnosticSeverity,
    val message: String,
    val source: String,
)

data class BuildSnapshot(
    val running: Boolean = false,
    val last: BuildStatus? = null,
    val task: BuildTask? = null,
    val logChars: Int = 0,
    val diagnostics: List<BuildDiagnostic> = emptyList(),
    val lastApk: ArtifactRef? = null,
)

sealed interface BuildEvent {
    enum class Stream { Stdout, Stderr, System }

    data class Log(val text: String, val stream: Stream) : BuildEvent
    data class Diagnostic(val item: BuildDiagnostic) : BuildEvent
    data class Artifact(val apk: ArtifactRef) : BuildEvent
    data class Finished(val status: BuildStatus, val exitCode: Int) : BuildEvent
}

sealed interface BuildStartResult {
    data object Started : BuildStartResult
    data class Rejected(val status: BuildStatus, val reason: String) : BuildStartResult
}

data class ProposedBuild(val task: BuildTask)

interface BuildQuery {
    fun snapshot(): BuildSnapshot
    fun logTail(maxChars: Int): String
    fun diagnostics(): List<BuildDiagnostic>
}

interface BuildService {
    val snapshot: StateFlow<BuildSnapshot>
    fun events(): Flow<BuildEvent>
    fun query(): BuildQuery
    suspend fun start(request: BuildRequest): BuildStartResult
    suspend fun cancel()
}
