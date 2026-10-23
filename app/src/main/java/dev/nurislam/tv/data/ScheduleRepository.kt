package dev.nurislam.tv.data

import dev.nurislam.tv.model.DaySchedule
import dev.nurislam.tv.model.ScheduleMetadata
import java.time.LocalDate

/** Stage 2 can back this contract with a validated private JSON file, without UI changes. */
interface ScheduleRepository {
    val metadata: ScheduleMetadata?
    fun day(date: LocalDate): DaySchedule?
}

class UnavailableScheduleRepository : ScheduleRepository {
    override val metadata: ScheduleMetadata? = null
    override fun day(date: LocalDate): DaySchedule? = null
}

/** Always gate the repository at the UI boundary, including future file-backed sources. */
fun ScheduleRepository.visibleDay(date: LocalDate, allowDemo: Boolean): DaySchedule? {
    val info = metadata ?: return null
    if ((!info.approved && !allowDemo) || info.year != date.year) return null
    return day(date)?.takeIf { it.date == date }
}
