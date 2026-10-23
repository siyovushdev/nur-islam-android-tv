package dev.nurislam.tv

import dev.nurislam.tv.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class MemoryScheduleStorage : ScheduleStorage {
    var bytes: ByteArray? = null
    var fail = false
    override fun read() = bytes
    override fun write(bytes: ByteArray) { if (fail) throw IOException("Injected write failure"); this.bytes = bytes.copyOf() }
}
class LocalScheduleRepositoryTest {
    @Test fun applyRollbackReloadAndImmediatePublication() = runBlocking {
        val disk = MemoryScheduleStorage()
        val repo = LocalScheduleRepository(disk)
        repo.load()
        repo.apply(ScheduleFixtures.schedule(revision = "one"))
        repo.apply(ScheduleFixtures.schedule(revision = "two", asr = "16:00"))
        assertEquals("two", repo.state.value.current!!.metadata.revision)
        assertEquals("one", repo.state.value.previous!!.metadata.revision)
        repo.rollback()
        assertEquals("one", repo.state.value.current!!.metadata.revision)
        val restarted = LocalScheduleRepository(disk); restarted.load()
        assertEquals("one", restarted.state.value.current!!.metadata.revision)
        assertEquals("two", restarted.state.value.previous!!.metadata.revision)
    }
    @Test fun failedWritePreservesDiskAndPublishedState() = runBlocking {
        val disk = MemoryScheduleStorage(); val repo = LocalScheduleRepository(disk); repo.load()
        repo.apply(ScheduleFixtures.schedule(revision = "one"))
        val before = disk.bytes!!.copyOf()
        disk.fail = true
        try { repo.apply(ScheduleFixtures.schedule(revision = "two")); fail("Expected failure") } catch (_: IOException) {}
        assertArrayEquals(before, disk.bytes)
        assertEquals("one", repo.state.value.current!!.metadata.revision)
        val restarted = LocalScheduleRepository(disk); restarted.load()
        assertEquals("one", restarted.state.value.current!!.metadata.revision)
    }
    @Test fun corruptCurrentRecoversVerifiedPrevious() = runBlocking {
        val disk = MemoryScheduleStorage(); val repo = LocalScheduleRepository(disk); repo.load()
        repo.apply(ScheduleFixtures.schedule(revision = "one")); repo.apply(ScheduleFixtures.schedule(revision = "two"))
        disk.bytes = disk.bytes!!.toString(Charsets.UTF_8).replace("two", "bad").toByteArray()
        val restarted = LocalScheduleRepository(disk); restarted.load()
        assertEquals("one", restarted.state.value.current!!.metadata.revision)
        assertNotNull(restarted.state.value.problem)
    }
    @Test fun corruptMissingAndUnapprovedNeverInventSchedule() = runBlocking {
        val disk = MemoryScheduleStorage(); val repo = LocalScheduleRepository(disk); repo.load()
        assertNull(repo.state.value.current)
        disk.bytes = "broken".toByteArray(); repo.load()
        assertNull(repo.state.value.current); assertNotNull(repo.state.value.problem)
        val demo = ScheduleJson.parse(ScheduleFixtures.json(approved = false).toString().toByteArray())
        try { repo.apply(demo); fail("Expected rejection") } catch (_: IllegalArgumentException) {}
        assertNull(repo.state.value.current)
    }
}
