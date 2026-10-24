package dev.nurislam.tv.model

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

val MoscowZone: ZoneId = ZoneId.of("Europe/Moscow")

/** Order matches the source calendar. FAJR_IN_MOSQUE has unconfirmed semantics. */
enum class ScheduleEvent(val isPrayer: Boolean) {
    SUHUR_END(false), FAJR_IN_MOSQUE(true), SUNRISE(false), ZAWAL(false),
    DHUHR(true), ASR(true), MAGHRIB(true), ISHA(true),
}

data class DaySchedule(val date: LocalDate, val times: Map<ScheduleEvent, LocalTime>) {
    init { require(times.keys == ScheduleEvent.entries.toSet()) { "All eight calendar fields are required" } }
}

data class ScheduleMetadata(
    val year: Int,
    val source: String,
    val revision: String,
    val approved: Boolean,
    val timezone: ZoneId = MoscowZone,
    val schemaVersion: Int = 1,
) {
    init {
        require(schemaVersion == 1)
        require(timezone == MoscowZone)
        require(source.isNotBlank() && revision.isNotBlank())
        require(year in 1..9999)
    }
}
