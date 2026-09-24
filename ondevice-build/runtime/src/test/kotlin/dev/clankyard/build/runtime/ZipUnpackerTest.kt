package dev.clankyard.build.runtime

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ZipUnpackerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun unzipWritesRegularFile() {
        val zip = tmp.newFile("ok.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("hello.txt"))
            out.write("hi".toByteArray())
            out.closeEntry()
        }
        val dest = tmp.newFolder("out")
        ZipUnpacker.unzip(zip, dest)
        assertTrue(File(dest, "hello.txt").readText() == "hi")
    }

    @Test
    fun zipSlipIsRejected() {
        val zip = tmp.newFile("evil.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("../credentials/stolen.bin"))
            out.write("nope".toByteArray())
            out.closeEntry()
        }
        val dest = tmp.newFolder("out")
        val credentials = File(dest.parentFile, "credentials").apply { mkdirs() }
        var threw = false
        try {
            ZipUnpacker.unzip(zip, dest)
        } catch (_: SecurityException) {
            threw = true
        }
        assertTrue(threw)
        assertFalse(File(credentials, "stolen.bin").exists())
    }
}
