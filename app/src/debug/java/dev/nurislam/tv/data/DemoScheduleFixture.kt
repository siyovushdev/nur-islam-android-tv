package dev.nurislam.tv.data

import dev.nurislam.tv.model.DaySchedule
import dev.nurislam.tv.model.ScheduleEvent
import dev.nurislam.tv.model.ScheduleMetadata
import java.time.LocalDate
import java.time.LocalTime

/** Visual mock from the approved design, NOT the unapproved source calendar. Debug only. */
class DemoScheduleFixture {
    val metadata = ScheduleMetadata(2026, "DESIGN DEMO / NOT APPROVED", "visual-mock-1", false)
    private val sample = DaySchedule(
        LocalDate.of(2026, 10, 23),
        ScheduleEvent.entries.zip(listOf("05:08", "05:30", "07:12", "11:40", "12:05", "15:15", "17:48", "19:02")
            .map(LocalTime::parse)).toMap(),
    )
    fun day(date: LocalDate) = sample.takeIf { it.date == date }
}
