package dev.clankyard.ai.context

import dev.clankyard.ai.secret.DefaultSecretFilter
import dev.clankyard.core.model.WorkspaceId
import dev.clankyard.core.model.WorkspacePath
import dev.clankyard.workspace.DiskFileBackedWorkspace
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ContextEngineTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun selectionAndCurrentFile() = runBlocking {
        val ws = openWs()
        write(ws, "src/MainActivity.kt", "class MainActivity")
        val packet = engine(ws).assemble(
            request(
                prompt = "explain this",
                current = "src/MainActivity.kt",
                selection = "class MainActivity",
            ),
        )
        assertEquals("explain this", chunk(packet, "Prompt").text)
        assertEquals("class MainActivity", chunk(packet, "Selection").text)
        val current = chunk(packet, "MainActivity.kt")
        assertFalse(current.omitted)
        assertEquals("class MainActivity", current.text)
        assertTrue(packet.estimatedTokens > 0)
    }

    @Test
    fun atFileMention() = runBlocking {
        val ws = openWs()
        write(ws, "src/MainActivity.kt", "fun main() = Unit")
        write(ws, "README.md", "hello")
        val packet = engine(ws).assemble(
            request(prompt = "look at @MainActivity.kt", mentions = listOf("@MainActivity.kt")),
        )
        val mentioned = packet.chunks.single { it.label.contains("MainActivity") && !it.omitted }
        assertTrue(mentioned.text.contains("fun main()"))
        assertEquals("src/MainActivity.kt", mentioned.path?.relative)
    }

    @Test
    fun atDirMentionIncludesTreeAndSmallFiles() = runBlocking {
        val ws = openWs()
        write(ws, "src/network/Client.kt", "class Client")
        write(ws, "src/network/api/Api.kt", "interface Api")
        write(ws, "src/network/.env", "SECRET=1")
        val packet = engine(ws).assemble(
            request(prompt = "show network", mentions = listOf("@src/network/")),
        )
        val dir = packet.chunks.single { it.label.contains("src/network") }
        assertFalse(dir.omitted)
        assertTrue(dir.text.contains("Client.kt"))
        assertTrue(dir.text.contains("Api.kt"))
        assertTrue(dir.text.contains("class Client"))
        assertFalse(dir.text.contains("SECRET=1"))
        assertFalse(dir.text.contains(".env"))
    }

    @Test
    fun secretOmittedAndMainActivityAllowed() = runBlocking {
        val ws = openWs()
        write(ws, "src/MainActivity.kt", "class MainActivity")
        write(ws, ".env", "TOKEN=super-secret")
        val packet = engine(ws).assemble(
            request(
                prompt = "review @MainActivity.kt and @.env",
                mentions = listOf("MainActivity.kt", ".env"),
            ),
        )
        val activity = packet.chunks.single { it.path?.relative == "src/MainActivity.kt" }
        assertFalse(activity.omitted)
        assertEquals("class MainActivity", activity.text)
        val env = packet.chunks.single { it.label.contains(".env") }
        assertTrue(env.omitted)
        assertEquals("", env.text)
        assertTrue(packet.filterNotes.any { it.contains(".env") })
        assertFalse(packet.chunks.any { it.text.contains("super-secret") })
    }

    @Test
    fun budgetTruncatesCurrentFile() = runBlocking {
        val ws = openWs()
        val body = "A".repeat(5_000)
        write(ws, "src/MainActivity.kt", body)
        val packet = engine(ws).assemble(
            request(
                prompt = "p",
                current = "src/MainActivity.kt",
                maxChars = 80,
            ),
        )
        val sent = packet.chunks.filter { !it.omitted }.sumOf { it.text.length }
        assertTrue(sent <= 80)
        val current = chunk(packet, "MainActivity.kt")
        assertTrue(current.text.contains("…") || current.omitted)
        assertTrue(packet.filterNotes.any { it.contains("truncated") || it.contains("budget") })
    }

    @Test
    fun currentFileCapUsesHeadAndTail() = runBlocking {
        val ws = openWs()
        write(ws, "src/Big.kt", "HEAD-" + "x".repeat(400) + "-TAIL")
        val packet = engine(ws).assemble(
            request(prompt = "x", current = "src/Big.kt", maxChars = 40),
        )
        val current = chunk(packet, "Big.kt")
        assertTrue(current.text.startsWith("HEAD-") || current.text.startsWith("x"))
        assertTrue(current.text.contains("…"))
        assertTrue(current.text.contains("TAIL") || current.text.endsWith("x"))
    }

    @Test
    fun gitignoreDeniesButDoesNotAllowSecrets() = runBlocking {
        val ws = openWs()
        write(ws, ".gitignore", "*.log\n")
        write(ws, "notes.log", "should not send")
        write(ws, "src/MainActivity.kt", "class MainActivity")
        val packet = engine(ws).assemble(
            request(prompt = "go", mentions = listOf("notes.log", "MainActivity.kt")),
        )
        assertTrue(packet.chunks.single { it.label.contains("notes.log") }.omitted)
        assertFalse(packet.chunks.single { it.path?.relative == "src/MainActivity.kt" }.omitted)
    }

    @Test
    fun ambiguousBasenameGoesToPicker() = runBlocking {
        val ws = openWs()
        write(ws, "a/Util.kt", "fun a()")
        write(ws, "b/Util.kt", "fun b()")
        val packet = engine(ws).assemble(
            request(prompt = "use @Util.kt", mentions = listOf("Util.kt")),
        )
        assertEquals(1, packet.ambiguous.size)
        val names = packet.ambiguous.single().candidates.map { it.relative }.toSet()
        assertEquals(setOf("a/Util.kt", "b/Util.kt"), names)
        assertTrue(packet.chunks.single { it.label == "@Util.kt" }.omitted)
        assertFalse(packet.chunks.any { it.text.contains("fun a()") })
        assertFalse(packet.chunks.any { it.text.contains("fun b()") })
    }

    @Test
    fun neverSilentFirstMatchOfSecretPath() = runBlocking {
        val ws = openWs()
        File(ws.root, ".ssh").mkdirs()
        write(ws, ".ssh/config.txt", "Host secret")
        write(ws, "src/config.txt", "ok")
        val packet = engine(ws).assemble(
            request(prompt = "open @config.txt", mentions = listOf("config.txt")),
        )
        assertTrue(packet.ambiguous.isEmpty())
        val sent = packet.chunks.filter { !it.omitted && it.path != null }
        assertEquals(listOf("src/config.txt"), sent.map { it.path!!.relative })
        assertFalse(packet.chunks.any { it.text.contains("Host secret") })
    }

    @Test
    fun pinnedFilesAreIncludedUntilBudget() = runBlocking {
        val ws = openWs()
        write(ws, "a.kt", "AAA")
        write(ws, "b.kt", "BBB")
        val packet = engine(ws).assemble(
            request(
                prompt = "p",
                pinned = listOf("a.kt", "b.kt"),
            ),
        )
        assertEquals("AAA", packet.chunks.single { it.path?.relative == "a.kt" }.text)
        assertEquals("BBB", packet.chunks.single { it.path?.relative == "b.kt" }.text)
    }

    @Test
    fun packetIsSuitableForUiChips() = runBlocking {
        val ws = openWs()
        write(ws, "src/MainActivity.kt", "class MainActivity")
        write(ws, ".env", "NO=1")
        val packet = engine(ws).assemble(
            request(
                prompt = "hi",
                current = "src/MainActivity.kt",
                selection = "class",
                mentions = listOf(".env"),
            ),
        )
        assertTrue(packet.chunks.all { it.label.isNotBlank() })
        assertTrue(packet.chunks.any { it.label == "Prompt" })
        assertTrue(packet.chunks.any { it.label == "Selection" })
        assertTrue(packet.chunks.any { it.omitted && it.label.contains(".env") })
        assertTrue(packet.estimatedTokens >= 1)
    }

    private fun engine(ws: DiskFileBackedWorkspace) =
        WorkspaceContextEngine(ws, DefaultSecretFilter())

    private fun request(
        prompt: String,
        current: String? = null,
        selection: String? = null,
        pinned: List<String> = emptyList(),
        mentions: List<String> = emptyList(),
        maxChars: Int = DEFAULT_MAX_CHARS,
    ) = ContextRequest(
        mode = AgentMode.Ask,
        prompt = prompt,
        currentFile = current?.let { WorkspacePath.parse(it) },
        selection = selection,
        pinned = pinned.map { WorkspacePath.parse(it) },
        mentions = mentions,
        maxChars = maxChars,
    )

    private fun chunk(packet: ContextPacket, label: String): ContextChunk =
        packet.chunks.single { it.label == label }

    private fun write(ws: DiskFileBackedWorkspace, relative: String, content: String) {
        val dest = File(ws.root, relative)
        dest.parentFile?.mkdirs()
        dest.writeText(content)
    }

    private fun openWs(): DiskFileBackedWorkspace =
        DiskFileBackedWorkspace(
            WorkspaceId("ws"),
            "ws",
            tmp.newFolder("root"),
            tmp.newFolder("journal"),
        )
}
