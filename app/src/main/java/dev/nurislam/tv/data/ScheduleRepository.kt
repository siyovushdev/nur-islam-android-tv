package dev.nurislam.tv.data

import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

data class ScheduleState(
    val current: ValidatedSchedule? = null,
    val previous: ValidatedSchedule? = null,
    val loaded: Boolean = false,
    val problem: String? = null,
) {
    /** A newly imported future year can coexist with the preceding approved year until midnight.
     * Rolling back to an older year deliberately disables a rejected future calendar. */
    val zone get() = current?.metadata?.timezone ?: dev.nurislam.tv.model.MoscowZone
    fun samePlace(a: ValidatedSchedule, b: ValidatedSchedule): Boolean = a.locationId == b.locationId && a.mosqueId == b.mosqueId && a.metadata.timezone == b.metadata.timezone
    fun scheduleFor(date: LocalDate): ValidatedSchedule? = current?.takeIf { it.metadata.year == date.year }
        ?: previous?.takeIf { it.metadata.year == date.year && current != null && samePlace(current, it) && it.metadata.year < current.metadata.year }
}

/** The board observes this contract; import and storage never depend on the selected theme. */
interface ScheduleRepository {
    val state: StateFlow<ScheduleState>
    suspend fun load()
    suspend fun apply(candidate: ValidatedSchedule)
    suspend fun rollback()
}
