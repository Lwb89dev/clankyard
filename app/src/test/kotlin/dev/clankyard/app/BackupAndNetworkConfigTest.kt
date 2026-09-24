package dev.clankyard.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BackupAndNetworkConfigTest {
    @Test
    fun dataExtractionRulesExcludeSensitiveTrees() {
        val xml = readXml("data_extraction_rules.xml")
        val cloud = section(xml, "cloud-backup")
        val transfer = section(xml, "device-transfer")
        for (dir in SENSITIVE) {
            assertTrue("$dir missing from cloud-backup", cloud.contains("path=\"$dir\""))
            assertTrue("$dir missing from device-transfer", transfer.contains("path=\"$dir\""))
        }
    }

    @Test
    fun fullBackupContentExcludesSensitiveTrees() {
        val xml = readXml("full_backup_content.xml")
        for (dir in SENSITIVE) {
            assertTrue("$dir missing from full backup content", xml.contains("path=\"$dir\""))
        }
        assertFalse(xml.contains("<include"))
    }

    @Test
    fun networkSecurityDisablesCleartextExceptLoopback() {
        val xml = readXml("network_security_config.xml")
        val base = section(xml, "base-config")
        assertTrue(base.contains("cleartextTrafficPermitted=\"false\""))
        val local = section(xml, "domain-config")
        assertTrue(local.contains("cleartextTrafficPermitted=\"true\""))
        assertTrue(local.contains("127.0.0.1"))
        assertTrue(local.contains("localhost"))
    }

    @Test
    fun manifestKeepsBackupOffAndWiresRules() {
        val manifest = open("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:allowBackup=\"false\""))
        assertTrue(manifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
        assertTrue(manifest.contains("android:fullBackupContent=\"@xml/full_backup_content\""))
        assertTrue(manifest.contains("android:networkSecurityConfig=\"@xml/network_security_config\""))
        assertTrue(manifest.contains("android:usesCleartextTraffic=\"false\""))
        assertTrue(manifest.contains("android.permission.INTERNET"))
        assertFalse(manifest.contains("FOREGROUND_SERVICE"))
        assertFalse(manifest.contains("POST_NOTIFICATIONS"))
    }

    private fun section(xml: String, tag: String): String {
        val start = xml.indexOf("<$tag")
        val end = xml.indexOf("</$tag>")
        check(start >= 0 && end > start) { "missing <$tag>" }
        return xml.substring(start, end)
    }

    private fun readXml(name: String): String = open("src/main/res/xml/$name").readText()

    private fun open(relative: String): File {
        val here = File(relative)
        if (here.isFile) return here
        val fromRoot = File("app", relative)
        check(fromRoot.isFile) { "missing $relative (cwd=${File(".").canonicalPath})" }
        return fromRoot
    }

    companion object {
        private val SENSITIVE = listOf(
            "credentials/",
            "drafts/",
            "journal/",
            "workspaces/",
            "environment/",
        )
    }
}
