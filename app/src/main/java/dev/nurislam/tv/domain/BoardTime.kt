package dev.nurislam.tv.domain

import dev.nurislam.tv.data.FajrMeaning
import dev.nurislam.tv.model.DaySchedule
import dev.nurislam.tv.model.MoscowZone
import dev.nurislam.tv.model.ScheduleEvent
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
private val ruDate = DateTimeFormatter.ofPattern("EEEE, d MMMM uuuu", Locale.forLanguageTag("ru"))
private val ttDays = listOf("Дүшәмбе", "Сишәмбе", "Чәршәмбе", "Пәнҗешәмбе", "Җомга", "Шимбә", "Якшәмбе")
private val ttMonths = listOf("гыйнвар", "февраль", "март", "апрель", "май", "июнь", "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь")

data class NextPrayer(val event: ScheduleEvent, val at: Instant)

fun nextPrayer(now: Instant, days: List<DaySchedule>, meaning: FajrMeaning = FajrMeaning.UNCONFIRMED): NextPrayer? =
    (prayerStatus(now, days, meaning) as? PrayerStatus.Upcoming)?.prayer

fun formatTime(time: LocalTime): String = timeFormat.format(time)
fun formatClock(now: Instant, zone: java.time.ZoneId = MoscowZone): String = formatTime(now.atZone(zone).toLocalTime())
fun formatRussianDate(date: LocalDate): String = ruDate.format(date).replaceFirstChar { it.titlecase(Locale.forLanguageTag("ru")) }
fun formatTatarDate(date: LocalDate): String = "${ttDays[date.dayOfWeek.value - 1]}, ${date.dayOfMonth} ${ttMonths[date.monthValue - 1]} ${date.year}"
fun formatCountdown(now: Instant, target: Instant): String {
    // Ceiling keeps a positive fractional second from showing zero before the event.
    val millis = Duration.between(now, target).toMillis().coerceAtLeast(0)
    val seconds = (millis + 999) / 1000
    return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
}
