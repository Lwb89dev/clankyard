package dev.clankyard.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BackupAndNetworkConfigTest {
    @Test
    fun dataExtractionRulesExcludeSensitiveTrees() {
        val xml = readXml("data_extraction_rules.xml")
        assertTrue(xml.contains("<cloud-backup"))
        assertTrue(xml.contains("<device-transfer"))
        for (dir in SENSITIVE) {
            assertTrue("$dir missing from data extraction rules", xml.contains("path=\"$dir\""))
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
    fun networkSecurityDisablesCleartext() {
        val xml = readXml("network_security_config.xml")
        assertTrue(xml.contains("cleartextTrafficPermitted=\"false\""))
        assertFalse(xml.contains("cleartextTrafficPermitted=\"true\""))
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

    private fun readXml(name: String): String = open("src/main/res/xml/$name").readText()

    private fun open(relative: String): File {
        val here = File(relative)
        if (here.isFile) return here
        val fromRoot = File("app", relative)
        check(fromRoot.isFile) { "missing $relative (cwd=${File(".").canonicalPath})" }
        return fromRoot
    }

    companion object {
        private val SENSITIVE = listOf("credentials/", "drafts/", "journal/", "workspaces/")
    }
}
