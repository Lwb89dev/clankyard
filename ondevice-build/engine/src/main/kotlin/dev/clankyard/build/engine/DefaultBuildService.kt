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
import dev.clankyard.core.model.SessionId
import dev.clankyard.core.model.WorkspaceId
import dev.clankyard.terminal.api.ExecutionEvent
import dev.clankyard.workspace.FileBackedWorkspace
import java.io.File
import kotlinx.coroutines.CancellationException
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
    private val backend: BuildProcessBackend? = null,
    private val driver: GradleDriver = GradleDriver(),
    private val artifactsDir: File? = null,
    private val cacheDir: File? = null,
    private val tmpDir: File? = null,
    private val heapMb: () -> Int = { 512 },
    private val freeSpace: (File) -> Long = { it.usableSpace },
) : BuildService, BuildQuery {
    private val _snapshot = MutableStateFlow(BuildSnapshot())
    override val snapshot: StateFlow<BuildSnapshot> = _snapshot.asStateFlow()
    private val _events = MutableSharedFlow<BuildEvent>(extraBufferCapacity = 64)
    private val log = StringBuilder()
    private var job: Job? = null
    private var sessionId: SessionId? = null

    override fun events() = _events
    override fun query(): BuildQuery = this
    override fun snapshot(): BuildSnapshot = _snapshot.value
    override fun logTail(maxChars: Int): String {
        val text = synchronized(log) { log.toString() }
        if (text.length <= maxChars) return text
        return text.takeLast(maxChars)
    }
    override fun diagnostics() = _snapshot.value.diagnostics

    override suspend fun start(request: BuildRequest): BuildStartResult {
        rejectStart(request)?.let { return it }
        val ws = workspace()!!
        val toolchain = ToolchainResolver.resolve(
            runtimes = runtimes,
            cacheDir = cacheDir ?: return missingRuntime(),
            tmpDir = tmpDir ?: return missingRuntime(),
            heapMb = heapMb(),
        ) ?: return missingRuntime()
        val process = backend ?: return missingRuntime()
        _snapshot.update {
            it.copy(running = true, task = request.task, last = null, diagnostics = emptyList())
        }
        synchronized(log) { log.clear() }
        val session = driver.session(request, ws, toolchain)
        sessionId = session.sessionId
        job = scope.launch { runSession(ws, process, session.sessionId, session) }
        return BuildStartResult.Started
    }

    override suspend fun cancel() {
        val id = sessionId
        if (id != null) backend?.destroy(id)
        job?.cancel()
        finish(BuildStatus.Cancelled, -1)
    }

    private suspend fun rejectStart(request: BuildRequest): BuildStartResult? {
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
        if (!runtimes.androidRuntimeReady()) return missingRuntime()
        val probe = cacheDir ?: artifactsDir ?: tmpDir ?: ws.root
        if (freeSpace(probe) < BuildResourceGates.MIN_BUILD_FREE_BYTES) {
            return BuildStartResult.Rejected(BuildStatus.Failed, "need at least 1 GiB free to build")
        }
        val kind = detector.detect(ws)
        if (kind != ProjectKind.GradleAndroidApp && request.task != BuildTask.Clean) {
            return BuildStartResult.Rejected(BuildStatus.NotAndroidGradle, "not an Android Gradle app")
        }
        return null
    }

    private suspend fun runSession(
        ws: FileBackedWorkspace,
        process: BuildProcessBackend,
        id: SessionId,
        session: dev.clankyard.terminal.api.ExecutionSessionRequest,
    ) {
        val lines = LineBuffer()
        try {
            process.start(session).collect { event ->
                when (event) {
                    is ExecutionEvent.Output -> {
                        append(event.text, BuildEvent.Stream.Stdout)
                        for (line in lines.push(event.text)) ingestDiagnostic(line, ws.root.path)
                    }
                    is ExecutionEvent.Exit -> {
                        lines.flush()?.let { ingestDiagnostic(it, ws.root.path) }
                        val status = statusFor(event.code)
                        if (status == BuildStatus.Success) copyApk(ws)
                        finish(status, event.code)
                    }
                    is ExecutionEvent.Error -> {
                        append(event.message + "\n", BuildEvent.Stream.System)
                        finish(BuildStatus.Failed, 1)
                    }
                }
            }
        } catch (e: CancellationException) {
            process.destroy(id)
            throw e
        } catch (e: Exception) {
            append((e.message ?: "build failed") + "\n", BuildEvent.Stream.System)
            finish(BuildStatus.Failed, 1)
        }
    }

    private suspend fun copyApk(ws: FileBackedWorkspace) {
        val dest = artifactsDir ?: return
        val apk = ArtifactCollector.findDebugApk(ws.root) ?: return
        val ref = ArtifactCollector.copyTo(dest, apk, ws.id)
        _snapshot.update { it.copy(lastApk = ref) }
        _events.emit(BuildEvent.Artifact(ref))
    }

    private suspend fun ingestDiagnostic(line: String, workshopRoot: String) {
        val item = DiagnosticParser.parse(line, workshopRoot) ?: return
        _snapshot.update { snap -> snap.copy(diagnostics = snap.diagnostics + item) }
        _events.emit(BuildEvent.Diagnostic(item))
    }

    private suspend fun append(text: String, stream: BuildEvent.Stream) {
        synchronized(log) {
            log.append(text)
            if (log.length > MAX_LOG_CHARS) log.delete(0, log.length - MAX_LOG_CHARS)
        }
        _snapshot.update { it.copy(logChars = synchronized(log) { log.length }) }
        _events.emit(BuildEvent.Log(text, stream))
    }

    private fun finish(status: BuildStatus, code: Int) {
        sessionId = null
        _snapshot.update { it.copy(running = false, last = status) }
        scope.launch { _events.emit(BuildEvent.Finished(status, code)) }
    }

    private fun missingRuntime() = BuildStartResult.Rejected(
        BuildStatus.RuntimeMissing,
        "Android/Kotlin runtime is not installed. Open Settings → Runtimes. " +
            "JDK/Gradle packs are unpublished until clankyard-runtimes ships a zip + SHA-256.",
    )

    private fun statusFor(code: Int): BuildStatus = when (code) {
        0 -> BuildStatus.Success
        else -> BuildStatus.Failed
    }

    private companion object {
        const val MAX_LOG_CHARS = 200_000
    }
}

internal class LineBuffer {
    private val pending = StringBuilder()

    fun push(chunk: String): List<String> {
        pending.append(chunk)
        val lines = ArrayList<String>()
        while (true) {
            val text = pending.toString()
            val nl = text.indexOf('\n')
            if (nl < 0) break
            lines += text.substring(0, nl).trimEnd('\r')
            pending.delete(0, nl + 1)
        }
        return lines
    }

    fun flush(): String? {
        if (pending.isEmpty()) return null
        val left = pending.toString()
        pending.setLength(0)
        return left
    }
}
