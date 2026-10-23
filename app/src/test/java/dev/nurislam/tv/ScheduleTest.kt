package dev.nurislam.tv

import dev.nurislam.tv.data.*
import dev.nurislam.tv.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ScheduleTest {
    private val date = LocalDate.of(2026, 10, 23)
    @Test(expected = IllegalArgumentException::class) fun missingFieldIsRejected() { DaySchedule(date, emptyMap()) }
    @Test(expected = IllegalArgumentException::class) fun unsupportedTimezoneIsRejected() { ScheduleMetadata(2026, "source", "1", true, ZoneId.of("UTC")) }
    @Test(expected = IllegalArgumentException::class) fun unsupportedSchemaIsRejected() { ScheduleMetadata(2026, "source", "1", true, schemaVersion = 2) }
    @Test(expected = IllegalArgumentException::class) fun emptySourceIsRejected() { ScheduleMetadata(2026, "", "1", true) }
    @Test fun demoNeverLeaksThroughProductionPolicy() {
        val repository = object : ScheduleRepository {
            override val metadata = ScheduleMetadata(2026, "DEMO", "1", false)
            override fun day(request: LocalDate) = if (request == date) DaySchedule(date, ScheduleEvent.entries.associateWith { LocalTime.NOON }) else null
        }
        assertNotNull(repository.visibleDay(date, true))
        assertNull(repository.visibleDay(date, false))
        assertNull(repository.visibleDay(date.plusYears(1), true))
        assertNull(repository.visibleDay(date.plusDays(1), true))
    }
    @Test fun unavailableRepositoryDoesNotInventTimes() {
        assertNull(UnavailableScheduleRepository().visibleDay(date, false))
    }
    @Test fun mismatchedReturnedDateIsRejected() {
        val repository = object : ScheduleRepository {
            override val metadata = ScheduleMetadata(2026, "approved source", "1", true)
            override fun day(date: LocalDate) = DaySchedule(date.minusDays(1), ScheduleEvent.entries.associateWith { LocalTime.NOON })
        }
        assertNull(repository.visibleDay(date, false))
    }
    @Test fun allEightSourceFieldsAndFivePrayerRowsArePreserved() {
        assertEquals(8, ScheduleEvent.entries.size)
        assertEquals(5, ScheduleEvent.entries.count { it.isPrayer })
        assertFalse(ScheduleEvent.FAJR_IN_MOSQUE.canBeNextPrayer)
    }
}
