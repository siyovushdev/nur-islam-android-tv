package dev.nurislam.tv

import com.google.gson.JsonParser
import dev.nurislam.tv.data.*
import dev.nurislam.tv.iqamah.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

class IqamahRepositoryTest {
    private val first = IqamahConfig(true, false, "ТЕСТ ДЛЯ ТЕЛЕФОНА", rules = listOf(IqamahRule(IqamahPrayer.ASR, LocalDate.of(2026, 10, 24), mode = IqamahMode.FIXED, value = 900)))
    @Test fun saveRestartRollbackAndCalendarIndependentVersionedFile() = runBlocking {
        val disk = MemoryScheduleStorage(); val repo = IqamahRepository(disk); repo.load()
        assertEquals(IqamahConfig(), repo.state.value.config)
        assertTrue(repo.state.value.loaded)
        repo.save(first); repo.save(first.copy(dimMinutes = 5))
        val restarted = IqamahRepository(disk); restarted.load()
        assertEquals(5, restarted.state.value.config.dimMinutes)
        restarted.rollback(); assertEquals(first, restarted.state.value.config)
        val third = IqamahRepository(disk); third.load()
        assertEquals(first, third.state.value.config); assertEquals(5, third.state.value.previous!!.dimMinutes)
        assertEquals("1", JsonParser.parseString(disk.bytes!!.toString(Charsets.UTF_8)).asJsonObject.get("version").toString())
    }
    @Test fun precommitFailurePreservesCurrentAndRestartedRules() = runBlocking {
        val disk = MemoryScheduleStorage(); val repo = IqamahRepository(disk); repo.load(); repo.save(first)
        val before = disk.bytes!!.copyOf(); disk.fail = true
        try { repo.save(first.copy(dimMinutes = 5)); fail() } catch (_: IOException) {}
        assertEquals(first, repo.state.value.config); assertArrayEquals(before, disk.bytes)
        val restart = IqamahRepository(disk); restart.load(); assertEquals(first, restart.state.value.config)
    }
    @Test fun postcommitReadFailureReloadsActualStateAndDoesNotRollBackCommit() = runBlocking {
        var bytes: ByteArray? = null; var uncertain = false
        val disk = object : ScheduleStorage {
            override fun read() = bytes
            override fun write(value: ByteArray) { bytes = value.copyOf(); if (uncertain) throw ScheduleWriteUncertainException(IOException("verification")) }
        }
        val repo = IqamahRepository(disk); repo.load(); repo.save(first); uncertain = true
        val next = first.copy(dimMinutes = 2)
        try { repo.save(next); fail() } catch (_: ScheduleWriteUncertainException) {}
        assertEquals(next, repo.state.value.config); assertNotNull(repo.state.value.problem)
        val restart = IqamahRepository(disk); restart.load(); assertEquals(next, restart.state.value.config)
    }
    @Test fun corruptCurrentRecoversPreviousAndUnknownVersionDisables() = runBlocking {
        val disk = MemoryScheduleStorage(); val repo = IqamahRepository(disk); repo.load(); repo.save(first); repo.save(first.copy(dimMinutes = 3))
        val root = JsonParser.parseString(disk.bytes!!.toString(Charsets.UTF_8)).asJsonObject
        root.getAsJsonObject("current").addProperty("sha256", "corrupt"); disk.bytes = root.toString().toByteArray()
        repo.load(); assertEquals(first, repo.state.value.config); assertNotNull(repo.state.value.problem)
        root.addProperty("version", 2); disk.bytes = root.toString().toByteArray(); repo.load()
        assertFalse(repo.state.value.config.enabled); assertNotNull(repo.state.value.problem)
        disk.bytes = "not json".toByteArray(); repo.load(); assertFalse(repo.state.value.config.enabled)
    }
    @Test fun strictTypesDuplicateRulesAndRangesRejected() {
        listOf("dimMinutes", "enabled", "rules", "source").forEach { key ->
            val json = IqamahJson.encode(first); json.addProperty(key, if (key == "source") "\n" else "invalid")
            assertThrows(Exception::class.java) { IqamahJson.decode(json) }
        }
        val duplicate = IqamahJson.encode(first); duplicate.getAsJsonArray("rules").add(duplicate.getAsJsonArray("rules")[0].deepCopy())
        assertThrows(IllegalArgumentException::class.java) { IqamahJson.decode(duplicate) }
        val invalid = IqamahJson.encode(first); invalid.getAsJsonArray("rules")[0].asJsonObject.addProperty("value", 1440)
        assertThrows(IllegalArgumentException::class.java) { IqamahJson.decode(invalid) }
        val unknown = IqamahJson.encode(first); unknown.addProperty("future", 1)
        assertThrows(IllegalArgumentException::class.java) { IqamahJson.decode(unknown) }
    }
}
