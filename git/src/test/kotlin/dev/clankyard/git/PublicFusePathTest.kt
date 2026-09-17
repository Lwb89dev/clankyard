package dev.clankyard.git

import kotlinx.coroutines.CancellationException
import org.eclipse.jgit.errors.LockFailedException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PublicFusePathTest {
    @Test
    fun publicEmulatedStorageIsRefused() {
        assertTrue(isPublicFusePath("/storage/emulated/0/Download/repo"))
        assertTrue(isPublicFusePath("/sdcard/Documents/project"))
        try {
            refusePublicFuse(File("/storage/emulated/0/Download/repo"))
            throw AssertionError("expected FuseExclException")
        } catch (e: FuseExclException) {
            assertTrue(e.message!!.contains("POSIX-safe"))
        }
    }

    @Test
    fun appSpecificAndPrivateDirsAreAllowed() {
        assertFalse(
            isPublicFusePath(
                "/storage/emulated/0/Android/data/dev.clankyard.app/files/workspaces/1",
            ),
        )
        assertFalse(isPublicFusePath("/data/user/0/dev.clankyard.app/files/workspaces/1"))
        assertFalse(isPublicFusePath("/tmp/junit-workspace"))
    }

    @Test
    fun leftoverIndexLockIsNotFuseExcl() {
        val lock = LockFailedException(File("/tmp/repo/.git/index.lock"))
        try {
            wrapGitFailure(lock, File("/tmp/repo"))
            throw AssertionError("expected LockFailedException")
        } catch (e: FuseExclException) {
            throw AssertionError("generic lock must not look like FUSE", e)
        } catch (e: LockFailedException) {
            assertSame(lock, e)
        }
    }

    @Test
    fun wrapRethrowsCancellation() {
        val cancel = CancellationException("cancelled")
        try {
            wrapGitFailure(cancel, File("/tmp/repo"))
            throw AssertionError("expected CancellationException")
        } catch (e: CancellationException) {
            assertSame(cancel, e)
        }
    }
}
