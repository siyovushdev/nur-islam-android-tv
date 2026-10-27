package dev.nurislam.tv.domain

import dev.nurislam.tv.data.FajrMeaning
import dev.nurislam.tv.data.ValidatedSchedule
import dev.nurislam.tv.data.ScheduleState
import dev.nurislam.tv.model.*
import java.time.Instant
import java.time.LocalDate

sealed interface PrayerStatus {
    data class Upcoming(val prayer: NextPrayer) : PrayerStatus
    data class FajrUnconfirmed(val date: LocalDate) : PrayerStatus
    data object Unavailable : PrayerStatus
    data object NextDayUnavailable : PrayerStatus
}

fun prayerStatus(now: Instant, schedule: ValidatedSchedule?): PrayerStatus {
    if (schedule == null) return PrayerStatus.Unavailable
    val date = now.atZone(schedule.metadata.timezone).toLocalDate()
    val today = schedule.day(date) ?: return PrayerStatus.Unavailable
    return prayerStatus(now, listOfNotNull(today, schedule.day(date.plusDays(1))), schedule.fajrMeaning, schedule.metadata.timezone)
}

fun prayerStatus(now: Instant, state: ScheduleState): PrayerStatus {
    val date = now.atZone(state.zone).toLocalDate()
    val today = state.scheduleFor(date) ?: return PrayerStatus.Unavailable
    val tomorrow = state.scheduleFor(date.plusDays(1))
    return statusForDays(now, listOfNotNull(today.day(date)?.let { it to today.fajrMeaning }, tomorrow?.day(date.plusDays(1))?.let { it to tomorrow.fajrMeaning }), state.zone)
}

fun prayerStatus(now: Instant, days: List<DaySchedule>, meaning: FajrMeaning, zone: java.time.ZoneId = MoscowZone): PrayerStatus = statusForDays(now, days.map { it to meaning }, zone)

private fun statusForDays(now: Instant, calendarDays: List<Pair<DaySchedule, FajrMeaning>>, zone: java.time.ZoneId): PrayerStatus {
    val days = calendarDays.map { it.first }
    val date = now.atZone(zone).toLocalDate()
    if (days.none { it.date == date }) return PrayerStatus.Unavailable
    val candidates = days.filter { it.date == date || it.date == date.plusDays(1) }.flatMap { day ->
        day.times.filterKeys { it.isPrayer }.map { (event, time) ->
            NextPrayer(event, day.date.atTime(time).atZone(zone).toInstant())
        }
    }.filter { it.at > now }.sortedBy { it.at }
    // Ambiguous mosque-time cannot establish the start of Fajr, even if that column's time has passed.
    val today = days.first { it.date == date }
    val beforeSunrise = now < date.atTime(today.times.getValue(ScheduleEvent.SUNRISE)).atZone(zone).toInstant()
    if (calendarDays.first { it.first.date == date }.second == FajrMeaning.UNCONFIRMED && beforeSunrise) return PrayerStatus.FajrUnconfirmed(date)
    val next = candidates.firstOrNull() ?: return PrayerStatus.NextDayUnavailable
    if (next.event == ScheduleEvent.FAJR_IN_MOSQUE && calendarDays.first { it.first.date == next.at.atZone(zone).toLocalDate() }.second == FajrMeaning.UNCONFIRMED)
        return PrayerStatus.FajrUnconfirmed(next.at.atZone(zone).toLocalDate())
    return PrayerStatus.Upcoming(next)
}
