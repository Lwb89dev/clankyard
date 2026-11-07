package dev.clankyard.app

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.clankyard.build.runtime.LinkerExec
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ARM64 W^X / nativeLibraryDir protocol. x86_64 emulators skip these gates.
 * Device: `adb shell getprop ro.product.cpu.abi` must be arm64-v8a.
 */
@RunWith(AndroidJUnit4::class)
class LibExecInstrumentedTest {
    @Test
    fun helloElfFromNativeLibraryDir() {
        assumeArm64()
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val hello = File(ctx.applicationInfo.nativeLibraryDir, LinkerExec.HELLO_SO)
        assumeTrue("hello .so extracted", hello.isFile)
        val proc = ProcessBuilder(LinkerExec.LINKER64, hello.absolutePath)
            .redirectErrorStream(true)
            .start()
        val out = proc.inputStream.bufferedReader().readText()
        val code = proc.waitFor()
        assertTrue("exit=$code out=$out", code == 0 && out.contains("clankyard-exec-ok"))
    }

    @Test
    fun helperPreloadInheritsOnGrandchild() {
        assumeArm64()
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val native = File(ctx.applicationInfo.nativeLibraryDir)
        val helper = File(native, LinkerExec.HELPER_SO)
        val interceptor = File(native, LinkerExec.INTERCEPTOR_SO)
        val hello = File(native, LinkerExec.HELLO_SO)
        assumeTrue(helper.isFile && interceptor.isFile && hello.isFile)
        val proc = ProcessBuilder(helper.absolutePath, hello.absolutePath)
            .redirectErrorStream(true)
            .start()
        val env = LinkerExec.preloadEnv(interceptor)
        proc.destroy()
        val nested = ProcessBuilder(helper.absolutePath, hello.absolutePath)
        nested.environment().putAll(env)
        nested.redirectErrorStream(true)
        val hooked = nested.start()
        val out = hooked.inputStream.bufferedReader().readText()
        val code = hooked.waitFor()
        assertTrue("preload grandchild exit=$code out=$out", code == 0 && out.contains("clankyard-exec-ok"))
    }

    private fun assumeArm64() {
        assumeTrue(
            "arm64-v8a only; see docs/build-on-device.md device protocol",
            Build.SUPPORTED_ABIS.contains("arm64-v8a") && !Build.SUPPORTED_ABIS.contains("x86_64"),
        )
    }
}
