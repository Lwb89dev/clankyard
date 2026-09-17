package dev.clankyard.ai.secret

import dev.clankyard.core.model.WorkspacePath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretFilterTest {
    private val filter = DefaultSecretFilter()

    @Test
    fun envDenied() {
        val decision = filter.decide(path(".env"), null, 12)
        assertFalse(decision.allowed)
        assertEquals("secret filename", decision.reason)
        assertFalse(filter.decide(path("app/.env.local"), null, 4).allowed)
        assertFalse(filter.decide(path(".ENV"), null, 4).allowed)
    }

    @Test
    fun pemFilenameDenied() {
        assertFalse(filter.decide(path("certs/site.pem"), null, 100).allowed)
        assertFalse(filter.decide(path("id_rsa"), null, 100).allowed)
        assertFalse(filter.decide(path(".ssh/id_ed25519"), null, 100).allowed)
        assertFalse(filter.decide(path("app/google-services.json"), "application/json", 80).allowed)
        assertFalse(filter.decide(path("local.properties"), null, 40).allowed)
        assertFalse(filter.decide(path("service-account-foo.json"), "application/json", 40).allowed)
        assertFalse(filter.decide(path("credentials.json"), "application/json", 40).allowed)
    }

    @Test
    fun secretDirectoriesDenied() {
        assertFalse(filter.decide(path(".ssh/config"), "text/plain", 20).allowed)
        assertFalse(filter.decide(path(".gnupg/pubring.kbx"), null, 20).allowed)
        assertFalse(filter.decide(path(".git/hooks/pre-commit"), "text/plain", 20).allowed)
        assertFalse(filter.decide(path("App.xcuserdata/user.plist"), null, 20).allowed)
    }

    @Test
    fun mainActivityAllowed() {
        val path = path("app/src/main/java/dev/clankyard/app/MainActivity.kt")
        val decision = filter.decide(path, null, 120)
        assertTrue(decision.allowed)
        val filtered = filter.filterText(path, "class MainActivity")
        assertEquals("class MainActivity", filtered.text)
        assertFalse(filtered.redacted)
        assertTrue(filtered.omissions.isEmpty())
    }

    @Test
    fun pemBodyRedacted() {
        val path = path("src/Keys.kt")
        val pem = """
            val key = '''
            -----BEGIN PRIVATE KEY-----
            MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC7
            -----END PRIVATE KEY-----
            '''
        """.trimIndent()
        val filtered = filter.filterText(path, pem)
        assertTrue(filtered.redacted)
        assertFalse(filtered.text.contains("MIIEvQIBADANBgkqhkiG9w0BAQE"))
        assertTrue(filtered.text.contains("[REDACTED_PEM]"))
        assertTrue(filtered.omissions.contains("PEM"))
    }

    @Test
    fun knownKeyPrefixesRedacted() {
        val path = path("src/Config.kt")
        val text = "ant=sk-ant-secretvalue proj=sk-proj-secretvalue x=xai-secretvalue aws=AKIAIOSFODNN7EXAMPLE"
        val filtered = filter.filterText(path, text)
        assertTrue(filtered.redacted)
        assertFalse(filtered.text.contains("sk-ant-secretvalue"))
        assertFalse(filtered.text.contains("sk-proj-secretvalue"))
        assertFalse(filtered.text.contains("xai-secretvalue"))
        assertFalse(filtered.text.contains("AKIAIOSFODNN7EXAMPLE"))
    }

    @Test
    fun gitignoreIsExtraDenialNotAllow() {
        val rules = IgnoreRules.parse(
            gitignore = "*.tok\n!keep.tok\n",
            clankyardIgnore = "",
        )
        val withIgnore = filter.withIgnoreRules(rules)
        assertFalse(withIgnore.decide(path("notes.tok"), "text/plain", 8).allowed)
        assertEquals("ignored", withIgnore.decide(path("keep.tok"), "text/plain", 8).reason)
        val star = IgnoreRules.parse("*\n!MainActivity.kt\n", "")
        val denyAll = filter.withIgnoreRules(star)
        assertFalse(denyAll.decide(path("MainActivity.kt"), null, 20).allowed)
        assertTrue(filter.decide(path("MainActivity.kt"), null, 20).allowed)
    }

    @Test
    fun clankyardIgnoreDenies() {
        val rules = IgnoreRules.parse("", "private/\nscratch.md\n")
        val withIgnore = filter.withIgnoreRules(rules)
        assertFalse(withIgnore.decide(path("private/notes.kt"), null, 10).allowed)
        assertFalse(withIgnore.decide(path("scratch.md"), null, 10).allowed)
        assertTrue(withIgnore.decide(path("MainActivity.kt"), null, 10).allowed)
    }

    @Test
    fun binaryAndUnknownFailClosed() {
        val bin = path("blob.dat")
        assertFalse(filter.decide(bin, "application/octet-stream", 16).allowed)
        assertFalse(filter.decide(path("photo.png"), "image/png", 16).allowed)
        assertFalse(filter.decide(path("unknown.xyz"), null, 16).allowed)
        val txt = path("notes.txt")
        val nul = filter.filterText(txt, "hello\u0000world")
        assertFalse(nul.text.contains("hello"))
        assertTrue(nul.omissions.contains("binary"))
    }

    @Test
    fun oversizeDenied() {
        val path = path("big.kt")
        val over = SecretLimits.MAX_FILE_BYTES + 1
        val decision = filter.decide(path, null, over)
        assertFalse(decision.allowed)
        assertEquals("oversize", decision.reason)
        assertTrue(filter.decide(path, null, SecretLimits.MAX_FILE_BYTES).allowed)
    }

    @Test
    fun extraDenyGlobsAreConfigurable() {
        val custom = filter.withExtraDenyGlobs(listOf("*.tok"))
        assertFalse(custom.decide(path("a.tok"), "text/plain", 4).allowed)
        assertTrue(filter.decide(path("a.tok"), "text/plain", 4).allowed)
    }

    @Test
    fun toolResultRedactsAndFailsClosedOnNul() {
        val filtered = filter.filterToolResult("token sk-ant-leaked-value")
        assertTrue(filtered.redacted)
        assertFalse(filtered.text.contains("sk-ant-leaked-value"))
        val binary = filter.filterToolResult("ok\u0000nope")
        assertEquals("", binary.text)
        assertTrue(binary.omissions.contains("binary"))
    }

    @Test
    fun highEntropyPropertiesAssignmentRedacted() {
        val path = path("secrets.properties")
        val text = """
            sdk.dir=/home/user/Android/Sdk
            api.key=wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY
        """.trimIndent()
        val filtered = filter.filterText(path, text)
        assertTrue(filtered.text.contains("sdk.dir=/home/user/Android/Sdk"))
        assertFalse(filtered.text.contains("wJalrXUtnFEMI"))
        assertTrue(filtered.redacted)
    }

    @Test
    fun gitignoreDirPatternDeniesChildren() {
        val rules = IgnoreRules.parse("build/\n", "")
        val withIgnore = filter.withIgnoreRules(rules)
        assertFalse(withIgnore.decide(path("build/output.txt"), "text/plain", 4).allowed)
        assertTrue(withIgnore.decide(path("src/output.txt"), "text/plain", 4).allowed)
    }

    private fun path(raw: String) = WorkspacePath.parse(raw)
}
