package dev.clankyard.ai.agent

import dev.clankyard.ai.context.AgentMode
import dev.clankyard.ai.context.ContextRequest
import dev.clankyard.ai.context.WorkspaceContextEngine
import dev.clankyard.ai.patch.WorkspacePatchEngine
import dev.clankyard.ai.provider.ChatEvent
import dev.clankyard.ai.providers.fake.FakeLlmProvider
import dev.clankyard.ai.secret.DefaultSecretFilter
import dev.clankyard.ai.tools.DefaultToolRegistry
import dev.clankyard.core.model.Credential
import dev.clankyard.core.model.RequestId
import dev.clankyard.core.model.WorkspaceId
import dev.clankyard.core.model.WorkspacePath
import dev.clankyard.diff.MyersDiffEngine
import dev.clankyard.search.WorkspaceTextSearch
import dev.clankyard.workspace.DiskFileBackedWorkspace
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AgentOrchestratorTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun askStreamsTextAndDoesNotPatch() = runBlocking {
        val ws = openWs()
        write(ws, "a.txt", "hello")
        val events = orchestrator(ws).run(turn(ws, AgentMode.Ask, "what is a.txt")).toList()
        assertTrue(events.any { it is AgentEvent.Text })
        assertTrue(events.any { it is AgentEvent.Completed })
        assertFalse(events.any { it is AgentEvent.PatchProposed })
        assertEquals("hello", File(ws.root, "a.txt").readText())
    }

    @Test
    fun askHidesWriteTools() = runBlocking {
        val ws = openWs()
        write(ws, "a.txt", "a")
        val fake = FakeLlmProvider()
        fake.enqueue(
            ChatEvent.ToolCall(
                "t1",
                "replace_text",
                """{"path":"a.txt","old_string":"a","new_string":"b"}""",
            ),
            ChatEvent.Completed,
        )
        fake.enqueue(ChatEvent.Delta("ok"), ChatEvent.Completed)
        val events = orchestrator(ws, fake).run(turn(ws, AgentMode.Ask, "edit it")).toList()
        assertFalse(events.any { it is AgentEvent.PatchProposed })
        assertEquals("a", File(ws.root, "a.txt").readText())
    }

    @Test
    fun editProposesPatchAndDoesNotWrite() = runBlocking {
        val ws = openWs()
        write(ws, "a.txt", "foo")
        val fake = FakeLlmProvider()
        fake.enqueue(
            ChatEvent.ToolCall(
                "t1",
                "replace_text",
                """{"path":"a.txt","old_string":"foo","new_string":"bar"}""",
            ),
            ChatEvent.Completed,
        )
        fake.enqueue(ChatEvent.Delta("proposed"), ChatEvent.Completed)
        val engine = WorkspacePatchEngine(ws, MyersDiffEngine())
        val events = orchestrator(ws, fake).run(turn(ws, AgentMode.Edit, "fix a.txt", engine)).toList()
        assertTrue(events.any { it is AgentEvent.PatchProposed })
        assertEquals("foo", File(ws.root, "a.txt").readText())
        assertTrue(events.last() is AgentEvent.Completed)
    }

    private fun orchestrator(
        ws: DiskFileBackedWorkspace,
        fake: FakeLlmProvider = FakeLlmProvider(),
    ): DefaultAgentOrchestrator = DefaultAgentOrchestrator(
        provider = fake,
        contextEngine = WorkspaceContextEngine(ws, DefaultSecretFilter()),
        tools = DefaultToolRegistry.mvp(WorkspaceTextSearch()),
        secrets = DefaultSecretFilter(),
    )

    private fun turn(
        ws: DiskFileBackedWorkspace,
        mode: AgentMode,
        prompt: String,
        engine: WorkspacePatchEngine? = null,
    ) = AgentTurnRequest(
        requestId = RequestId("r1"),
        mode = mode,
        model = "fake-model",
        credential = Credential.ApiKey("test-key-1"),
        context = ContextRequest(
            mode = mode,
            prompt = prompt,
            currentFile = WorkspacePath.parse("a.txt"),
            selection = null,
            pinned = emptyList(),
            mentions = emptyList(),
        ),
        workspace = ws,
        patchEngine = engine,
    )

    private fun openWs(): DiskFileBackedWorkspace =
        DiskFileBackedWorkspace(WorkspaceId("ws"), "ws", tmp.newFolder("root"), tmp.newFolder("journal"))

    private fun write(ws: DiskFileBackedWorkspace, relative: String, content: String) {
        val dest = File(ws.root, relative)
        dest.parentFile?.mkdirs()
        dest.writeText(content)
    }
}
