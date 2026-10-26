package dev.nurislam.tv

import dev.nurislam.tv.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ScheduleTest {
    private val date = LocalDate.of(2026, 10, 23)
    @Test(expected = IllegalArgumentException::class) fun missingFieldIsRejected() { DaySchedule(date, emptyMap()) }
    @Test fun regionalTimezoneIsPreserved() { assertEquals(ZoneId.of("Asia/Yekaterinburg"), ScheduleMetadata(2026, "source", "1", true, ZoneId.of("Asia/Yekaterinburg")).timezone) }
    @Test(expected = IllegalArgumentException::class) fun unsupportedSchemaIsRejected() { ScheduleMetadata(2026, "source", "1", true, schemaVersion = 3) }
    @Test(expected = IllegalArgumentException::class) fun emptySourceIsRejected() { ScheduleMetadata(2026, "", "1", true) }
    @Test fun allEightSourceFieldsAndFivePrayerRowsArePreserved() {
        assertEquals(8, ScheduleEvent.entries.size)
        assertEquals(5, ScheduleEvent.entries.count { it.isPrayer })
    }
}
