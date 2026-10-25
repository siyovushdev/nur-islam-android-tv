package dev.nurislam.tv

import com.google.gson.JsonParser
import dev.nurislam.tv.data.*
import dev.nurislam.tv.ramadan.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

class RamadanRepositoryTest {
    private class Storage : ScheduleStorage {
        var bytes: ByteArray? = null
        var failBefore = false
        var failAfter = false
        override fun read() = bytes
        override fun write(bytes: ByteArray) {
            if (failBefore) throw IOException("disk full")
            this.bytes = bytes
            if (failAfter) throw ScheduleWriteUncertainException(IOException("readback"))
        }
    }
    private val dates = RamadanDates(2026, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 2), "Контрольный документ unit-проверки", true)
    private val config = RamadanConfig(true, listOf(dates))
    @Test fun defaultsSaveRestartAndRollbackRemainIndependentOfPrayerCalendar() = runTest {
        val s = Storage(); val repo = RamadanRepository(s); repo.load()
        assertEquals(RamadanConfig(), repo.state.value.config)
        repo.save(config); repo.save(config.copy(enabled = false))
        val restart = RamadanRepository(s); restart.load(); restart.rollback()
        assertEquals(config, restart.state.value.config)
        assertEquals(config, RamadanRepository(s).also { it.load() }.state.value.config)
    }
    @Test fun preCommitFailurePreservesStateAndPostCommitFailureRereadsActualGeneration() = runTest {
        val s = Storage(); val repo = RamadanRepository(s); repo.load(); repo.save(config)
        s.failBefore = true
        assertThrows(IOException::class.java) { kotlinx.coroutines.runBlocking { repo.save(config.copy(enabled = false)) } }
        assertEquals(config, repo.state.value.config)
        s.failBefore = false; s.failAfter = true
        assertThrows(ScheduleWriteUncertainException::class.java) { kotlinx.coroutines.runBlocking { repo.save(config.copy(enabled = false)) } }
        assertFalse(repo.state.value.config.enabled)
        assertNotNull(repo.state.value.problem)
        assertEquals(config, repo.state.value.previous)
    }
    @Test fun corruptionRecoversPreviousOrDisablesWithDiagnostic() = runTest {
        val s = Storage(); val repo = RamadanRepository(s); repo.load(); repo.save(config); repo.save(config.copy(enabled = false))
        val envelope = JsonParser.parseString(s.bytes!!.toString(Charsets.UTF_8)).asJsonObject
        envelope.getAsJsonObject("current").addProperty("sha256", "bad")
        s.bytes = envelope.toString().toByteArray()
        val recovered = RamadanRepository(s); recovered.load()
        assertEquals(config, recovered.state.value.config); assertNotNull(recovered.state.value.problem)
        s.bytes = "broken".toByteArray(); recovered.load()
        assertFalse(recovered.state.value.config.enabled); assertNotNull(recovered.state.value.problem)
    }
    @Test fun largestValidTwoGenerationEnvelopeRemainsReadableAfterRestart() = runTest {
        val records = (2000..2200).flatMap { year -> listOf(1, 7).map { month ->
            val first = LocalDate.of(year, month, 1)
            RamadanDates(year, first, first.plusDays(29), "\\".repeat(200))
        } }
        val big = RamadanConfig(true, records)
        val s = Storage(); val repo = RamadanRepository(s); repo.load(); repo.save(big)
        repo.save(big.copy(enabled = false))
        assertTrue(s.bytes!!.size <= 1_048_576)
        val restart = RamadanRepository(s); restart.load()
        assertEquals(big.copy(enabled = false), restart.state.value.config)
        assertEquals(big, restart.state.value.previous)
        assertNull(restart.state.value.problem)
    }
    @Test fun strictStoredTypesAndDatesValidatedAtWriteBoundary() {
        val valid = RamadanJson.encode(config)
        assertEquals(config, RamadanJson.decode(valid))
        val wrong = valid.deepCopy().apply { addProperty("enabled", "true") }
        assertThrows(IllegalArgumentException::class.java) { RamadanJson.decode(wrong) }
        val date = valid.deepCopy().apply { getAsJsonArray("dates")[0].asJsonObject.addProperty("firstFast", "2026-02-29") }
        assertThrows(java.time.DateTimeException::class.java) { RamadanJson.decode(date) }
        val external = mutableListOf(dates)
        val changedAfterConstruction = RamadanConfig(true, external)
        external += dates
        assertThrows(IllegalArgumentException::class.java) { RamadanJson.decode(RamadanJson.encode(changedAfterConstruction)) }
    }
}
