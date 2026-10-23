package dev.nurislam.tv

import android.util.AtomicFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.nurislam.tv.data.LocalScheduleRepository
import kotlinx.coroutines.runBlocking
import dev.nurislam.tv.data.AtomicScheduleStorage
import dev.nurislam.tv.data.ScheduleWriteUncertainException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.SyncFailedException

/** Runs the real framework AtomicFile implementation for each supported storage protocol. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [26, 30, 35])
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class AtomicScheduleStorageTest {
    private val file get() = File(ApplicationProvider.getApplicationContext<NurIslamApplication>().filesDir, "atomic-regression.json")

    @Test fun successfulReplacementIsReadableAfterReopeningStorage() {
        val target = file
        val atomic = RecordingAtomicFile(target)
        val storage = AtomicScheduleStorage(atomic)
        storage.write("old".toByteArray())
        storage.write("new".toByteArray())
        assertBytes("new", target)
        assertEquals(2, atomic.finishes)
        assertEquals(0, atomic.failures)
    }

    @Test fun startFailureDoesNotCallEitherTerminalOperation() {
        val target = file
        AtomicScheduleStorage(target).write("old".toByteArray())
        val atomic = RecordingAtomicFile(target).apply { failStart = true }
        val error = assertThrows(IOException::class.java) { AtomicScheduleStorage(atomic).write("new".toByteArray()) }
        assertFalse(error is ScheduleWriteUncertainException)
        assertBytes("old", target)
        assertEquals(0, atomic.finishes)
        assertEquals(0, atomic.failures)
    }

    @Test fun partialWriteFailureRollsBackOnceWithoutFinishing() {
        val target = file
        AtomicScheduleStorage(target).write("old".toByteArray())
        val atomic = RecordingAtomicFile(target).apply { writeFault = WriteFault.PARTIAL }
        val error = assertThrows(IOException::class.java) { AtomicScheduleStorage(atomic).write("new".toByteArray()) }
        assertEquals("Injected partial write failure", error.message)
        assertBytes("old", target)
        assertEquals(0, atomic.finishes)
        assertEquals(1, atomic.failures)
    }

    @Test fun syncFailureRollsBackOnceWithoutFinishing() {
        val target = file
        AtomicScheduleStorage(target).write("old".toByteArray())
        val atomic = RecordingAtomicFile(target).apply { writeFault = WriteFault.CLOSE_BEFORE_SYNC }
        assertThrows(SyncFailedException::class.java) { AtomicScheduleStorage(atomic).write("new".toByteArray()) }
        assertBytes("old", target)
        assertEquals(0, atomic.finishes)
        assertEquals(1, atomic.failures)
    }

    @Test fun readFailureAfterCommitNeverCallsFailWriteOrDeletesCommittedData() {
        val target = file
        val atomic = RecordingAtomicFile(target)
        val storage = AtomicScheduleStorage(atomic)
        storage.write("old".toByteArray())
        atomic.readFault = ReadFault.OPEN
        val error = assertThrows(ScheduleWriteUncertainException::class.java) { storage.write("new".toByteArray()) }
        assertTrue(error.cause is FileNotFoundException)
        assertBytes("new", target)
        assertEquals(2, atomic.finishes)
        assertEquals(0, atomic.failures)
    }

    @Test fun firstWriteStreamReadFailurePreservesTheCommittedFile() {
        val target = file
        val atomic = RecordingAtomicFile(target).apply { readFault = ReadFault.BODY }
        val error = assertThrows(ScheduleWriteUncertainException::class.java) { AtomicScheduleStorage(atomic).write("new".toByteArray()) }
        assertEquals("Injected verification read failure", error.cause!!.message)
        assertBytes("new", target)
        assertEquals(1, atomic.finishes)
        assertEquals(0, atomic.failures)
    }

    @Test fun verificationMismatchDoesNotRollBackCommittedData() {
        val target = file
        AtomicScheduleStorage(target).write("old".toByteArray())
        val wrong = File(target.parentFile, "wrong-read.json").apply { writeText("wrong bytes") }
        val atomic = RecordingAtomicFile(target).apply { wrongRead = wrong }
        val error = assertThrows(ScheduleWriteUncertainException::class.java) { AtomicScheduleStorage(atomic).write("new".toByteArray()) }
        assertTrue(error.cause is IllegalStateException)
        assertBytes("new", target)
        assertEquals(1, atomic.finishes)
        assertEquals(0, atomic.failures)
    }

    @Test fun finishFailureAfterCommitDoesNotDeleteCommittedData() {
        val target = file
        AtomicScheduleStorage(target).write("old".toByteArray())
        val atomic = RecordingAtomicFile(target).apply { finishFault = FinishFault.AFTER_COMMIT }
        assertThrows(ScheduleWriteUncertainException::class.java) { AtomicScheduleStorage(atomic).write("new".toByteArray()) }
        assertBytes("new", target)
        assertFalse(atomic.lastStream!!.fd.valid())
        assertEquals(1, atomic.finishes)
        assertEquals(0, atomic.failures)
    }

    @Test fun finishFailureBeforeCommitClosesStreamAndLeavesFrameworkRecoveryIntact() {
        val target = file
        AtomicScheduleStorage(target).write("old".toByteArray())
        val atomic = RecordingAtomicFile(target).apply { finishFault = FinishFault.BEFORE_COMMIT }
        assertThrows(ScheduleWriteUncertainException::class.java) { AtomicScheduleStorage(atomic).write("new".toByteArray()) }
        assertFalse(atomic.lastStream!!.fd.valid())
        assertBytes("old", target)
        assertEquals(1, atomic.finishes)
        assertEquals(0, atomic.failures)
    }

    @Test fun repositoryRetainsVerifiedStateAndReloadRecoversCommittedCalendarAfterReadFailure() = runBlocking {
        val target = file
        val atomic = RecordingAtomicFile(target)
        val repo = LocalScheduleRepository(AtomicScheduleStorage(atomic))
        repo.load()
        repo.apply(ScheduleFixtures.schedule(revision = "verified-one"))
        val verifiedState = repo.state.value
        atomic.readFault = ReadFault.OPEN
        try {
            repo.apply(ScheduleFixtures.schedule(revision = "committed-two"))
            fail("Expected a post-commit verification error")
        } catch (_: ScheduleWriteUncertainException) {
            assertSame(verifiedState, repo.state.value)
        }
        assertEquals(0, atomic.failures)
        val restarted = LocalScheduleRepository(AtomicScheduleStorage(target))
        restarted.load()
        assertNull(restarted.state.value.problem)
        assertEquals("committed-two", restarted.state.value.current!!.metadata.revision)
        assertEquals("verified-one", restarted.state.value.previous!!.metadata.revision)
        restarted.rollback()
        assertEquals("verified-one", restarted.state.value.current!!.metadata.revision)
    }

    private fun assertBytes(expected: String, target: File) {
        // A fresh reader bypasses only fault injection, not Android's recovery protocol.
        assertArrayEquals(expected.toByteArray(), AtomicScheduleStorage(target).read())
    }

    private enum class WriteFault { NONE, PARTIAL, CLOSE_BEFORE_SYNC }
    private enum class ReadFault { NONE, OPEN, BODY }
    private enum class FinishFault { NONE, BEFORE_COMMIT, AFTER_COMMIT }
    private class RecordingAtomicFile(file: File) : AtomicFile(file) {
        var failStart = false
        var writeFault = WriteFault.NONE
        var readFault = ReadFault.NONE
        var finishFault = FinishFault.NONE
        var wrongRead: File? = null
        var finishes = 0
        var failures = 0
        var lastStream: FileOutputStream? = null
        override fun startWrite(): FileOutputStream {
            if (failStart) throw IOException("Injected start failure")
            val real = super.startWrite()
            return (if (writeFault == WriteFault.NONE) real else object : FileOutputStream(real.fd) {
                override fun write(bytes: ByteArray) {
                    when (writeFault) {
                        WriteFault.PARTIAL -> { real.write(bytes, 0, 1); throw IOException("Injected partial write failure") }
                        WriteFault.CLOSE_BEFORE_SYNC -> { real.write(bytes); real.close() }
                        WriteFault.NONE -> error("Unexpected test mode")
                    }
                }
            }).also { lastStream = it }
        }
        override fun finishWrite(stream: FileOutputStream?) {
            finishes++
            if (finishFault == FinishFault.BEFORE_COMMIT) throw IOException("Injected finish failure before commit")
            super.finishWrite(stream)
            if (finishFault == FinishFault.AFTER_COMMIT) throw IOException("Injected finish failure after commit")
        }
        override fun failWrite(stream: FileOutputStream?) { failures++; super.failWrite(stream) }
        override fun openRead(): FileInputStream {
            if (readFault == ReadFault.OPEN) throw FileNotFoundException("Injected post-commit open failure")
            if (readFault == ReadFault.BODY) return object : FileInputStream(baseFile) {
                override fun read(bytes: ByteArray): Int = throw IOException("Injected verification read failure")
            }
            return wrongRead?.let(::FileInputStream) ?: super.openRead()
        }
    }
}
