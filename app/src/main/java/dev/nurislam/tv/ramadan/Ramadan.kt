package dev.nurislam.tv.ramadan

import dev.nurislam.tv.data.isTestSource
import dev.nurislam.tv.model.MoscowZone
import dev.nurislam.tv.settings.BoardLanguage
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** First Gregorian fasting day, not the preceding religious evening. No default dates. */
data class RamadanDates(val year: Int, val firstFast: LocalDate, val eid: LocalDate? = null, val source: String, val approved: Boolean = false) {
    init {
        require(year in 2000..2200 && firstFast.year == year) { "Год должен совпадать с первым днём поста (2000–2200)" }
        require(eid == null || ChronoUnit.DAYS.between(firstFast, eid) in 29..30) { "До Ураза-байрама должно быть 29 или 30 дней поста" }
        require(source.isNotBlank() && source.length <= 200 && source.none(Char::isISOControl)) { "Укажите источник дат (до 200 символов)" }
        require(!approved || !isTestSource(source)) { "Тестовый источник нельзя отметить утверждённым" }
    }
}
data class RamadanConfig(val enabled: Boolean = false, val dates: List<RamadanDates> = emptyList()) {
    init {
        require(dates.size <= 402 && dates.map { it.firstFast }.distinct().size == dates.size) { "Повтор первого дня поста или слишком много записей" }
        dates.sortedBy { it.firstFast }.zipWithNext().forEach { (a, b) ->
            require(b.firstFast > (a.eid ?: a.firstFast.plusDays(29))) { "Периоды поста пересекаются" }
        }
    }
}
sealed interface RamadanStatus {
    data object Off : RamadanStatus
    data class Hidden(val reason: String) : RamadanStatus
    data class Before(val days: Int, val dates: RamadanDates) : RamadanStatus
    data class Fasting(val day: Int, val untilEid: Int?, val dates: RamadanDates) : RamadanStatus
    data class Eid(val dates: RamadanDates) : RamadanStatus
}
fun ramadanStatus(now: Instant, config: RamadanConfig, clockProblem: Boolean = false, zone: java.time.ZoneId = MoscowZone, fromFile: Boolean = false): RamadanStatus {
    if (!config.enabled) return RamadanStatus.Off
    if (clockProblem) return RamadanStatus.Hidden("Рамадан скрыт: проверьте системные часы и подтвердите их")
    val today = try { now.atZone(zone).toLocalDate() } catch (_: java.time.DateTimeException) { return RamadanStatus.Hidden("Рамадан скрыт: неверная системная дата") }
    if (today.year !in 2000..2200) return RamadanStatus.Hidden("Рамадан скрыт: неверный год системных часов")
    val dates = config.dates.sortedBy { it.firstFast }
    val active = dates.lastOrNull { today >= it.firstFast && today <= (it.eid ?: it.firstFast.plusDays(29)) }
    val next = dates.firstOrNull { it.firstFast > today && it.year <= today.year + 1 }
    val selected = active ?: next ?: return RamadanStatus.Hidden(if (dates.isEmpty()) "Даты Рамадана не настроены" else if (dates.any { it.firstFast > today }) "Нет дат Рамадана на этот или следующий год" else "Период завершён; нужны даты следующего года")
    if (!fromFile && !selected.approved) return RamadanStatus.Hidden("Рамадан скрыт: даты ${selected.year} не подтверждены мечетью")
    if (selected == next && active == null) return RamadanStatus.Before(ChronoUnit.DAYS.between(today, selected.firstFast).toInt(), selected)
    if (today == selected.eid) return RamadanStatus.Eid(selected)
    val day = ChronoUnit.DAYS.between(selected.firstFast, today).toInt() + 1
    // With no Eid, day 30 may already be Eid. Do not invent a 30-day fasting month.
    if (selected.eid == null && day >= 30) return RamadanStatus.Hidden("Дата Ураза-байрама не указана; окончание поста не подтверждено")
    return RamadanStatus.Fasting(day, selected.eid?.let { ChronoUnit.DAYS.between(today, it).toInt() }, selected)
}
fun ramadanText(status: RamadanStatus, language: BoardLanguage): String? {
    val ru: String
    val tt: String
    when (status) {
        is RamadanStatus.Before -> { ru = "До первого дня поста Рамадан: ${status.days} дн."; tt = "Беренче ураза көненә: ${status.days} көн" }
        is RamadanStatus.Fasting -> {
            ru = "Рамадан: день ${status.day}" + (status.untilEid?.let { " · до Ураза-байрама $it дн." } ?: "")
            tt = "Рамазан: ${status.day} нче көн" + (status.untilEid?.let { " · Ураза бәйрәменә $it көн" } ?: "")
        }
        is RamadanStatus.Eid -> { ru = "Ураза-байрам. С праздником!"; tt = "Ураза бәйрәме мөбарәк булсын!" }
        else -> return null
    }
    return when (language) { BoardLanguage.RUSSIAN -> ru; BoardLanguage.TATAR -> tt; BoardLanguage.BOTH -> "$tt\n$ru" }
}
