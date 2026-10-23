package dev.nurislam.tv

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
    @Test fun allEightSourceFieldsAndFivePrayerRowsArePreserved() {
        assertEquals(8, ScheduleEvent.entries.size)
        assertEquals(5, ScheduleEvent.entries.count { it.isPrayer })
    }
}
