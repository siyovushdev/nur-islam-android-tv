package dev.nurislam.tv.data

import kotlinx.coroutines.flow.StateFlow

data class ScheduleState(
    val current: ValidatedSchedule? = null,
    val previous: ValidatedSchedule? = null,
    val loaded: Boolean = false,
    val problem: String? = null,
)

/** The board observes this contract; import and storage never depend on the selected theme. */
interface ScheduleRepository {
    val state: StateFlow<ScheduleState>
    suspend fun load()
    suspend fun apply(candidate: ValidatedSchedule)
    suspend fun rollback()
}
