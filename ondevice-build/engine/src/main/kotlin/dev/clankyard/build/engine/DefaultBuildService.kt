package dev.clankyard.build.engine

import dev.clankyard.build.api.BuildEvent
import dev.clankyard.build.api.BuildQuery
import dev.clankyard.build.api.BuildRequest
import dev.clankyard.build.api.BuildService
import dev.clankyard.build.api.BuildSnapshot
import dev.clankyard.build.api.BuildStartResult
import dev.clankyard.build.api.BuildStatus
import dev.clankyard.build.api.BuildTask
import dev.clankyard.build.api.BuildTrust
import dev.clankyard.build.runtime.RuntimeManager
import dev.clankyard.core.model.WorkspaceId
import dev.clankyard.workspace.FileBackedWorkspace
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DefaultBuildService(
    private val runtimes: RuntimeManager,
    private val detector: ProjectDetector,
    private val workspace: () -> FileBackedWorkspace?,
    private val trust: (WorkspaceId) -> BuildTrust,
    private val scope: CoroutineScope,
) : BuildService, BuildQuery {
    private val _snapshot = MutableStateFlow(BuildSnapshot())
    override val snapshot: StateFlow<BuildSnapshot> = _snapshot.asStateFlow()
    private val _events = MutableSharedFlow<BuildEvent>(extraBufferCapacity = 64)
    private val log = StringBuilder()
    private var job: Job? = null

    override fun events() = _events
    override fun query(): BuildQuery = this
    override fun snapshot(): BuildSnapshot = _snapshot.value
    override fun logTail(maxChars: Int): String {
        val text = synchronized(log) { log.toString() }
        return if (text.length <= maxChars) text else text.takeLast(maxChars)
    }
    override fun diagnostics() = _snapshot.value.diagnostics

    override suspend fun start(request: BuildRequest): BuildStartResult {
        if (_snapshot.value.running) {
            return BuildStartResult.Rejected(BuildStatus.Failed, "a build is already running")
        }
        val ws = workspace()
        if (ws == null || ws.id != request.workspaceId) {
            return BuildStartResult.Rejected(BuildStatus.Failed, "workshop not open")
        }
        if (trust(request.workspaceId) != BuildTrust.Granted) {
            return BuildStartResult.Rejected(BuildStatus.Untrusted, "workshop is not trusted for builds")
        }
        if (!runtimes.androidRuntimeReady()) {
            return BuildStartResult.Rejected(
                BuildStatus.RuntimeMissing,
                "Android/Kotlin runtime is not installed. Open Settings → Runtimes. " +
                    "JDK/Gradle packs are unpublished until clankyard-runtimes ships a zip + SHA-256.",
            )
        }
        val kind = detector.detect(ws)
        if (kind != ProjectKind.GradleAndroidApp && request.task != BuildTask.Clean) {
            return BuildStartResult.Rejected(BuildStatus.NotAndroidGradle, "not an Android Gradle app")
        }
        _snapshot.update {
            it.copy(running = true, task = request.task, last = null, diagnostics = emptyList())
        }
        synchronized(log) { log.clear() }
        job = scope.launch {
            append("No on-device JDK pack is published yet. Cannot run Gradle.\n")
            finish(BuildStatus.RuntimeMissing, 1)
        }
        return BuildStartResult.Started
    }

    override suspend fun cancel() {
        job?.cancel()
        finish(BuildStatus.Cancelled, -1)
    }

    private suspend fun append(text: String) {
        synchronized(log) { log.append(text) }
        _snapshot.update { it.copy(logChars = synchronized(log) { log.length }) }
        _events.emit(BuildEvent.Log(text, BuildEvent.Stream.System))
        DiagnosticParser.parse(text.trim())?.let { item ->
            _snapshot.update { snap -> snap.copy(diagnostics = snap.diagnostics + item) }
            _events.emit(BuildEvent.Diagnostic(item))
        }
    }

    private fun finish(status: BuildStatus, code: Int) {
        _snapshot.update { it.copy(running = false, last = status) }
        scope.launch { _events.emit(BuildEvent.Finished(status, code)) }
    }
}
