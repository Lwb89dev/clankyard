package dev.clankyard.ai.tools

import dev.clankyard.ai.context.AgentMode
import dev.clankyard.ai.patch.EditKind
import dev.clankyard.ai.provider.ToolSpec
import dev.clankyard.core.model.ContentHash
import dev.clankyard.core.model.RequestId
import dev.clankyard.core.model.WorkspaceId
import dev.clankyard.core.model.WorkspacePath
import dev.clankyard.git.GitIdentity
import dev.clankyard.git.GitHandle
import dev.clankyard.git.JGitRepository
import dev.clankyard.search.SearchHit
import dev.clankyard.search.SearchQuery
import dev.clankyard.search.TextSearch
import dev.clankyard.search.WorkspaceTextSearch
import dev.clankyard.workspace.DiskFileBackedWorkspace
import dev.clankyard.workspace.Workspace
import dev.clankyard.workspace.WriteRequest
import dev.clankyard.workspace.WriteResult
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ToolRegistryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val search = WorkspaceTextSearch()
    private val registry = DefaultToolRegistry.mvp(search)

    @Test
    fun readFile() = runBlocking {
        val ws = openWs()
        write(ws, "src/a.txt", "hello workshop")
        val result = invoke("read_file", json("path" to "src/a.txt"), ws)
        assertFalse(result.isError)
        assertEquals("hello workshop", result.content)
    }

    @Test
    fun listDirectoryRootIsLegal() = runBlocking {
        val ws = openWs()
        write(ws, "a.txt", "a")
        File(ws.root, "dir").mkdirs()
        val empty = invoke("list_directory", JsonObject(emptyMap()), ws)
        val slash = invoke("list_directory", json("path" to "/"), ws)
        val blank = invoke("list_directory", json("path" to ""), ws)
        assertFalse(empty.isError)
        assertTrue(empty.content.contains("a.txt"))
        assertTrue(empty.content.contains("dir/"))
        assertEquals(empty.content, slash.content)
        assertEquals(empty.content, blank.content)
    }

    @Test
    fun searchTextUsesSearchModule() = runBlocking {
        val recording = RecordingSearch()
        val local = DefaultToolRegistry.mvp(recording)
        val ws = openWs()
        write(ws, "src/a.kt", "fun alpha() = 1")
        val result = invoke(local, "search_text", json("query" to "alpha"), ws)
        assertFalse(result.isError)
        assertNotNull(recording.last)
        assertEquals("alpha", recording.last!!.pattern)
        assertTrue(result.content.contains("src/a.kt"))
        assertTrue(result.content.contains("alpha"))
    }

    @Test
    fun searchTextFindsMatchesViaWorkspaceSearch() = runBlocking {
        val ws = openWs()
        write(ws, "src/a.kt", "fun alpha() = 1\n")
        write(ws, "src/b.kt", "fun beta() = 2\n")
        val result = invoke("search_text", json("query" to "alpha"), ws)
        assertFalse(result.isError)
        assertTrue(result.content.contains("src/a.kt"))
        assertFalse(result.content.contains("beta"))
    }

    @Test
    fun gitStatusAndDiff() = runBlocking {
        val ws = openWs()
        val handle = JGitRepository().init(ws, identity)
        write(ws, "README.md", "hello\n")
        val status = invoke("git_status", JsonObject(emptyMap()), ws, git = handle)
        assertFalse(status.isError)
        assertTrue(status.content.contains("README.md"))
        handle.stage(listOf(WorkspacePath.parse("README.md")))
        handle.commit("add readme", identity)
        write(ws, "README.md", "hello\nworld\n")
        val diff = invoke("git_diff", JsonObject(emptyMap()), ws, git = handle)
        assertFalse(diff.isError)
        assertTrue(diff.content.contains("+world"))
        (handle as AutoCloseable).close()
    }

    @Test
    fun replaceTextUniqueOldStringProposesFullFile() = runBlocking {
        val disk = openWs()
        write(disk, "src/a.txt", "foo bar")
        val result = invoke("replace_text", json(
            "path" to "src/a.txt",
            "old_string" to "foo",
            "new_string" to "baz",
        ), noWrite(disk), mode = AgentMode.Edit)
        assertFalse(result.isError)
        val edit = result.proposedEdits.single()
        assertEquals(EditKind.Replace, edit.kind)
        assertEquals("src/a.txt", edit.path.relative)
        assertEquals("baz bar", edit.afterUtf8)
        assertNotNull(edit.expectedHash)
        assertEquals("foo bar", File(disk.root, "src/a.txt").readText())
    }

    @Test
    fun replaceTextNonUniqueOrMissingIsErrorWithoutEdit() = runBlocking {
        val disk = openWs()
        write(disk, "dup.txt", "foo foo")
        write(disk, "none.txt", "zzz")
        val ws = noWrite(disk)
        val dup = invoke("replace_text", json(
            "path" to "dup.txt",
            "old_string" to "foo",
            "new_string" to "bar",
        ), ws)
        val missing = invoke("replace_text", json(
            "path" to "none.txt",
            "old_string" to "foo",
            "new_string" to "bar",
        ), ws)
        assertTrue(dup.isError)
        assertTrue(dup.proposedEdits.isEmpty())
        assertTrue(missing.isError)
        assertTrue(missing.proposedEdits.isEmpty())
        assertEquals("foo foo", File(disk.root, "dup.txt").readText())
    }

    @Test
    fun createFileFailsIfDestinationExists() = runBlocking {
        val disk = openWs()
        write(disk, "a.txt", "old")
        val ws = noWrite(disk)
        val exists = invoke("create_file", json("path" to "a.txt", "content" to "new"), ws)
        assertTrue(exists.isError)
        assertTrue(exists.proposedEdits.isEmpty())
        assertEquals("old", File(disk.root, "a.txt").readText())
        val created = invoke("create_file", json("path" to "b.txt", "content" to "hello"), ws)
        assertFalse(created.isError)
        val edit = created.proposedEdits.single()
        assertEquals(EditKind.Create, edit.kind)
        assertNull(edit.expectedHash)
        assertEquals("hello", edit.afterUtf8)
        assertFalse(File(disk.root, "b.txt").exists())
    }

    @Test
    fun renameFileEmitsFromTo() = runBlocking {
        val disk = openWs()
        write(disk, "from.txt", "body")
        val result = invoke("rename_file", json("from" to "from.txt", "to" to "to.txt"), noWrite(disk))
        assertFalse(result.isError)
        val edit = result.proposedEdits.single()
        assertEquals(EditKind.Rename, edit.kind)
        assertEquals("from.txt", edit.path.relative)
        assertEquals("to.txt", edit.renameTo?.relative)
        assertNotNull(edit.expectedHash)
        assertTrue(File(disk.root, "from.txt").isFile)
        assertFalse(File(disk.root, "to.txt").exists())
    }

    @Test
    fun highRiskInvokeThrowsAndIsNotRegistered() {
        for (name in HIGH_RISK_NAMES) {
            assertNull(registry.get(name))
        }
        assertNull(registry.get("apply_patch"))
        assertThrows(IllegalArgumentException::class.java) {
            DefaultToolRegistry(listOf(TestHighRiskTool()))
        }
        val tool = TestHighRiskTool()
        val ws = openWs()
        val ctx = ToolContext(ws, AgentMode.Edit)
        assertThrows(SecurityException::class.java) {
            runBlocking { tool.invoke(JsonObject(emptyMap()), ctx) }
        }
        val mismatch = ctx.copy(grant = HighRiskGrant("delete_file", RequestId("r1")))
        assertThrows(SecurityException::class.java) {
            runBlocking { tool.invoke(JsonObject(emptyMap()), mismatch) }
        }
        val granted = ctx.copy(grant = HighRiskGrant("run_command", RequestId("r1")))
        val ok = runBlocking { tool.invoke(JsonObject(emptyMap()), granted) }
        assertFalse(ok.isError)
        assertEquals("would-run", ok.content)
    }

    @Test
    fun pathDotDotFailsParse() {
        assertThrows(IllegalArgumentException::class.java) {
            WorkspacePath.parse("../secret")
        }
        val ws = openWs()
        val read = runBlocking {
            invoke("read_file", json("path" to "../secret"), ws)
        }
        assertTrue(read.isError)
        assertTrue(read.proposedEdits.isEmpty())
        val create = runBlocking {
            invoke("create_file", json("path" to "../x", "content" to "nope"), ws)
        }
        assertTrue(create.isError)
        assertTrue(create.proposedEdits.isEmpty())
    }

    @Test
    fun askHidesWrites() {
        val ask = registry.specsFor(AgentMode.Ask).map { it.name }.toSet()
        val plan = registry.specsFor(AgentMode.Plan).map { it.name }.toSet()
        val edit = registry.specsFor(AgentMode.Edit).map { it.name }.toSet()
        for (name in WRITE_TOOLS) {
            assertFalse(ask.contains(name))
            assertFalse(plan.contains(name))
            assertTrue(edit.contains(name))
        }
        for (name in SAFE_READ_TOOLS) {
            assertTrue(ask.contains(name))
            assertTrue(plan.contains(name))
            assertTrue(edit.contains(name))
        }
        val disk = openWs()
        write(disk, "a.txt", "x")
        val hidden = runBlocking {
            invoke("replace_text", json(
                "path" to "a.txt",
                "old_string" to "x",
                "new_string" to "y",
            ), noWrite(disk), mode = AgentMode.Ask)
        }
        assertTrue(hidden.isError)
        assertTrue(hidden.proposedEdits.isEmpty())
    }

    @Test
    fun mainSourcesDoNotUseFileOrFileBackedWorkspace() {
        val src = moduleMain()
        assertTrue(src.isDirectory)
        val hits = ArrayList<String>()
        src.walkTopDown().filter { it.extension == "kt" }.forEach { file ->
            file.readLines().forEachIndexed { index, line ->
                if (forbiddenImport(line)) hits += "${file.name}:${index + 1}: $line"
            }
        }
        assertEquals(emptyList<String>(), hits)
    }

    private suspend fun invoke(
        name: String,
        args: JsonObject,
        ws: Workspace,
        mode: AgentMode = AgentMode.Edit,
        git: GitHandle? = null,
    ): ToolResult = invoke(registry, name, args, ws, mode, git)

    private suspend fun invoke(
        tools: ToolRegistry,
        name: String,
        args: JsonObject,
        ws: Workspace,
        mode: AgentMode = AgentMode.Edit,
        git: GitHandle? = null,
    ): ToolResult {
        val tool = tools.get(name) ?: error("missing tool $name")
        return tool.invoke(args, ToolContext(ws, mode, git = git))
    }

    private fun json(vararg pairs: Pair<String, String>): JsonObject = buildJsonObject {
        for ((key, value) in pairs) put(key, value)
    }

    private fun write(ws: DiskFileBackedWorkspace, relative: String, content: String) {
        val dest = File(ws.root, relative)
        dest.parentFile?.mkdirs()
        dest.writeText(content)
    }

    private fun noWrite(ws: DiskFileBackedWorkspace): Workspace = object : Workspace by ws {
        override suspend fun writeAtomic(request: WriteRequest): WriteResult =
            error("tools must not write")

        override suspend fun rename(
            from: WorkspacePath,
            to: WorkspacePath,
            expectedHash: ContentHash?,
        ): WriteResult = error("tools must not write")

        override suspend fun delete(path: WorkspacePath, expectedHash: ContentHash?): WriteResult =
            error("tools must not write")
    }

    private fun openWs(): DiskFileBackedWorkspace =
        DiskFileBackedWorkspace(
            WorkspaceId("ws"),
            "ws",
            tmp.newFolder("root"),
            tmp.newFolder("journal"),
        )

    private fun moduleMain(): File {
        val here = File("src/main/kotlin/dev/clankyard/ai/tools")
        if (here.isDirectory) return here
        return File("ai/tools/src/main/kotlin/dev/clankyard/ai/tools")
    }

    private val identity = GitIdentity("Workshop User", "user@clankyard.dev")
}

private val HIGH_RISK_NAMES = listOf("run_command", "delete_file", "git_commit", "git_push")
private val WRITE_TOOLS = listOf("create_file", "replace_text", "rename_file")
private val SAFE_READ_TOOLS = listOf(
    "read_file",
    "list_directory",
    "search_text",
    "git_status",
    "git_diff",
)

private fun forbiddenImport(line: String): Boolean {
    if (line.contains("java.io.File")) return true
    if (line.contains("FileBackedWorkspace")) return true
    if (line.contains("Runtime.exec") || line.contains("java.lang.Runtime")) return true
    return line.contains("writeAtomic")
}

private class TestHighRiskTool : GuardedTool() {
    override val spec = ToolSpec(
        name = "run_command",
        description = "not registered",
        parametersJsonSchema = """{"type":"object"}""",
    )
    override val risk = ToolRisk.HighRisk
    override suspend fun execute(args: JsonObject, ctx: ToolContext): ToolResult =
        toolOk("would-run")
}

private class RecordingSearch : TextSearch {
    var last: SearchQuery? = null
    override suspend fun search(workspace: Workspace, query: SearchQuery): List<SearchHit> {
        last = query
        val path = WorkspacePath.parse("src/a.kt")
        return listOf(SearchHit(path, 1, 5, "fun alpha() = 1"))
    }
}
