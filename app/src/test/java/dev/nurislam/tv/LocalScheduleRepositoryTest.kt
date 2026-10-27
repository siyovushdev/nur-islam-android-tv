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
    @Test fun nextYearCanBeLoadedAheadWithoutLosingCurrentYearAndRollbackRejectsFuture() = runBlocking {
        val repo = LocalScheduleRepository(MemoryScheduleStorage()); repo.load()
        repo.apply(ScheduleFixtures.schedule(2026, "2026", meaning = "PRAYER_START"))
        repo.apply(ScheduleFixtures.schedule(2027, "2027", meaning = "PRAYER_START"))
        assertEquals(2026, repo.state.value.scheduleFor(java.time.LocalDate.of(2026, 12, 31))!!.metadata.year)
        val before = dev.nurislam.tv.domain.prayerStatus(java.time.Instant.parse("2026-12-31T20:59:59Z"), repo.state.value) as dev.nurislam.tv.domain.PrayerStatus.Upcoming
        assertEquals(java.time.LocalDate.of(2027, 1, 1), before.prayer.at.atZone(dev.nurislam.tv.model.MoscowZone).toLocalDate())
        assertEquals(2027, repo.state.value.scheduleFor(java.time.LocalDate.of(2027, 1, 1))!!.metadata.year)
        assertNull(repo.state.value.scheduleFor(java.time.LocalDate.of(2028, 1, 1)))
        repo.rollback()
        assertNull(repo.state.value.scheduleFor(java.time.LocalDate.of(2027, 1, 1)))
    }
    @Test fun corruptMissingAndUnapprovedNeverInventSchedule() = runBlocking {
        val disk = MemoryScheduleStorage(); val repo = LocalScheduleRepository(disk); repo.load()
        assertNull(repo.state.value.current)
        disk.bytes = "broken".toByteArray(); repo.load()
        assertNull(repo.state.value.current); assertNotNull(repo.state.value.problem)
        val demo = ScheduleJson.parse(ScheduleFixtures.json(approved = false).toString().toByteArray())
        repo.apply(demo)
        assertFalse(repo.state.value.current!!.metadata.approved)
        val mismatched = ValidatedSchedule(demo.metadata.copy(approved = true), demo.locationId, demo.locationName, demo.fajrMeaning, demo.days, demo.json)
        repo.apply(mismatched)
        assertFalse(repo.state.value.current!!.metadata.approved)
        val restored = LocalScheduleRepository(disk); restored.load()
        assertEquals(demo.json, restored.state.value.current!!.json)
    }
}
