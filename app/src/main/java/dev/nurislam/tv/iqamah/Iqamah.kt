package dev.nurislam.tv.iqamah

import dev.nurislam.tv.data.FajrMeaning
import dev.nurislam.tv.data.isTestSource
import dev.nurislam.tv.data.ScheduleState
import dev.nurislam.tv.model.MoscowZone
import dev.nurislam.tv.model.ScheduleEvent
import java.security.MessageDigest
import java.time.*

enum class IqamahPrayer(val title: String, val event: ScheduleEvent) {
    FAJR("Фаджр", ScheduleEvent.FAJR_IN_MOSQUE), DHUHR("Зухр", ScheduleEvent.DHUHR),
    ASR("Аср", ScheduleEvent.ASR), MAGHRIB("Магриб", ScheduleEvent.MAGHRIB),
    ISHA("Иша", ScheduleEvent.ISHA), JUMUAH("Джума (пятница)", ScheduleEvent.DHUHR),
}
enum class IqamahMode { OFF, FIXED, OFFSET }

data class IqamahRule(
    val prayer: IqamahPrayer,
    val from: LocalDate,
    val exception: Boolean = false,
    val mode: IqamahMode = IqamahMode.OFF,
    val value: Int = 0, // FIXED: minute of day; OFFSET: explicitly entered minutes.
    val baseCalendar: String? = null, // Full JSON hash, only after explicit confirmation of this base field.
) {
    init {
        require(from.year in 2000..2200) { "Дата вне диапазона 2000–2200" }
        require(when (mode) { IqamahMode.OFF -> value == 0 && baseCalendar == null; IqamahMode.FIXED -> value in 0..1439; IqamahMode.OFFSET -> value in 0..720 && prayer != IqamahPrayer.JUMUAH && baseCalendar != null }) { "Неверный режим, время или неподтверждённая база" }
        require(baseCalendar == null || baseCalendar.matches(Regex("[a-f0-9]{64}"))) { "Неверная привязка календаря" }
    }
}

data class IqamahConfig(
    val enabled: Boolean = false,
    val approved: Boolean = false,
    val source: String = "",
    val dimMinutes: Int = 0,
    val rules: List<IqamahRule> = emptyList(),
) {
    init {
        require(source.length <= 200 && source.none { it.isISOControl() }) { "Источник: до 200 символов, без управляющих знаков" }
        require(!approved || (source.isNotBlank() && !isTestSource(source))) { "Укажите источник утверждённых правил" }
        require(dimMinutes in 0..60) { "Затемнение: 0–60 минут; 0 — выключено" }
        require(rules.size <= 2048) { "Слишком много правил" }
        require(rules.distinctBy { Triple(it.prayer, it.from, it.exception) }.size == rules.size) { "Повтор правила на дату" }
    }
    fun rule(prayer: IqamahPrayer, date: LocalDate): IqamahRule? =
        rules.firstOrNull { it.prayer == prayer && it.exception && it.from == date }
            ?: rules.filter { it.prayer == prayer && !it.exception && it.from <= date }.maxByOrNull { it.from }
}
fun calendarFingerprint(json: String): String = MessageDigest.getInstance("SHA-256").digest(json.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
fun prayersOn(date: LocalDate): List<IqamahPrayer> = IqamahPrayer.entries.filter {
    if (date.dayOfWeek == DayOfWeek.FRIDAY) it != IqamahPrayer.DHUHR else it != IqamahPrayer.JUMUAH
}

data class IqamahEvent(val prayer: IqamahPrayer, val at: Instant, val start: Instant?, val rule: IqamahRule, val demo: Boolean)
data class IqamahDayRow(val prayer: IqamahPrayer, val start: Instant?, val event: IqamahEvent?, val detail: String)
sealed interface IqamahStatus {
    data class Waiting(val event: IqamahEvent) : IqamahStatus
    data class Now(val event: IqamahEvent, val until: Instant, val dim: Boolean) : IqamahStatus
    data class Idle(val upcoming: IqamahEvent?, val detail: String) : IqamahStatus
    data class Conflict(val detail: String) : IqamahStatus
}

/** Never interprets a raw column as a start without confirmation bound to this exact calendar. */
private fun rawIqamahDay(date: LocalDate, config: IqamahConfig, calendars: ScheduleState, fingerprint: String?): List<IqamahDayRow> {
    val calendar = calendars.scheduleFor(date)
    val day = calendar?.day(date)
    return prayersOn(date).map { prayer ->
        val rule = config.rule(prayer, date)
        val provenance = rule?.let { if (it.exception) "Исключение $date" else "С ${it.from}" } ?: "Нет правила"
        if (rule == null || rule.mode == IqamahMode.OFF) return@map IqamahDayRow(prayer, null, null, "$provenance · отключено")
        if (day == null) return@map IqamahDayRow(prayer, null, null, "$provenance · нет календаря на дату")
        val fajrKnown = prayer != IqamahPrayer.FAJR || calendar.fajrMeaning == FajrMeaning.PRAYER_START
        val bound = rule.baseCalendar == fingerprint
        val start = if (prayer != IqamahPrayer.JUMUAH && bound && fajrKnown)
            date.atTime(day.times.getValue(prayer.event)).atZone(MoscowZone).toInstant() else null
        if (rule.mode == IqamahMode.OFFSET && start == null)
            return@map IqamahDayRow(prayer, null, null, "$provenance · начало не подтверждено для этого календаря; подтвердите базу заново")
        val at = if (rule.mode == IqamahMode.FIXED) date.atTime(LocalTime.of(rule.value / 60, rule.value % 60)).atZone(MoscowZone).toInstant()
            else start!!.plusSeconds(rule.value * 60L)
        if (at.atZone(MoscowZone).toLocalDate() != date || (start != null && at < start))
            return@map IqamahDayRow(prayer, start, null, "$provenance · икамат раньше начала или за пределами суток")
        val event = IqamahEvent(prayer, at, start, rule, !config.approved || isTestSource(calendar.metadata.source))
        IqamahDayRow(prayer, start, event, provenance + if (start == null) " · начало не подтверждено" else " · база подтверждена")
    }
}

/** Canonical prayer order is independent of the entered iqamah times. */
data class IqamahConflict(val earlier: IqamahEvent, val later: IqamahEvent, val reason: String) {
    val dates: Set<LocalDate> get() = setOf(earlier.at.atZone(MoscowZone).toLocalDate(), later.at.atZone(MoscowZone).toLocalDate())
    val detail: String get() = "$reason: ${earlier.prayer.title} ${earlier.at.atZone(MoscowZone).toLocalDate()} ${earlier.at.atZone(MoscowZone).toLocalTime()} / ${later.prayer.title} ${later.at.atZone(MoscowZone).toLocalDate()} ${later.at.atZone(MoscowZone).toLocalTime()}"
}

private fun evaluatedDays(dates: List<LocalDate>, config: IqamahConfig, calendars: ScheduleState): Pair<Map<LocalDate, List<IqamahDayRow>>, List<IqamahConflict>> {
    // A year preview hashes each calendar once, rather than once per prayer/date.
    val fingerprints = mutableMapOf<Int, String>()
    val rows = dates.distinct().sorted().associateWith { date ->
        val calendar = calendars.scheduleFor(date)
        val fingerprint = calendar?.let { fingerprints.getOrPut(it.metadata.year) { calendarFingerprint(it.json) } }
        rawIqamahDay(date, config, calendars, fingerprint)
    }
    val events = rows.values.flatten().mapNotNull { it.event }.sortedWith(compareBy(
        { it.at.atZone(MoscowZone).toLocalDate() }, { it.prayer.event.ordinal }))
    val duration = if (config.dimMinutes == 0) 60L else config.dimMinutes * 60L
    val conflicts = mutableListOf<IqamahConflict>()
    events.forEachIndexed { index, earlier ->
        val nextDate = earlier.at.atZone(MoscowZone).toLocalDate().plusDays(1)
        for (later in events.subList(index + 1, events.size)) {
            if (later.at.atZone(MoscowZone).toLocalDate() > nextDate) break
            val reason = when {
                earlier.at >= later.at -> "Нарушен порядок икаматов"
                earlier.at.plusSeconds(duration) > (later.start ?: later.at) -> "Пересекаются ожидание или индикация икаматов"
                else -> null
            }
            if (reason != null) conflicts += IqamahConflict(earlier, later, reason)
        }
    }
    return rows to conflicts
}

private fun dateWindow(date: LocalDate) = (-1L..1L).map(date::plusDays)

fun iqamahDay(date: LocalDate, config: IqamahConfig, calendars: ScheduleState): List<IqamahDayRow> {
    val (rows, conflicts) = evaluatedDays(dateWindow(date), config, calendars)
    val problem = conflicts.firstOrNull { date in it.dates } ?: return rows.getValue(date)
    return rows.getValue(date).map { row ->
        if (row.event == null) row else row.copy(event = null, detail = "Икамат на дату приостановлен. ${problem.detail}. Исправьте правила.")
    }
}

/** Save-time validation covers every date available in the current/preceding calendar. */
fun iqamahConflicts(config: IqamahConfig, calendars: ScheduleState): List<IqamahConflict> {
    val dates = listOfNotNull(calendars.current, calendars.previous).flatMap { it.days.keys }
        .distinct().filter { calendars.scheduleFor(it) != null }
    return evaluatedDays(dates, config, calendars).second
}

fun iqamahCompatibilityWarning(date: LocalDate, config: IqamahConfig, calendars: ScheduleState): String? {
    val calendar = calendars.scheduleFor(date) ?: return null
    val fingerprint = calendarFingerprint(calendar.json)
    return if (config.enabled && prayersOn(date).mapNotNull { config.rule(it, date) }.any {
        it.baseCalendar != null && it.baseCalendar != fingerprint
    }) "Икамат: календарь изменён. Подтвердите базовые поля заново; прежний отсчёт от начала приостановлен." else null
}

fun iqamahStatus(now: Instant, config: IqamahConfig, calendars: ScheduleState, allowDemo: Boolean, clockProblem: Boolean = false): IqamahStatus {
    if (!config.enabled) return IqamahStatus.Idle(null, "Икамат отключён")
    val date = now.atZone(MoscowZone).toLocalDate()
    if (clockProblem || date.year !in 2000..2200) return IqamahStatus.Idle(null, "Проверьте часы устройства; подтвердите их в настройках икамата")
    if (!config.approved && !allowDemo) return IqamahStatus.Idle(null, "Правила икамата не утверждены мечетью")
    if (calendars.scheduleFor(date)?.day(date) == null) return IqamahStatus.Idle(null, "Икамат: нет календаря на дату")
    if (!allowDemo && isTestSource(calendars.scheduleFor(date)!!.metadata.source)) return IqamahStatus.Idle(null, "Тестовый календарь: икамат недоступен в Release")
    val (days, conflicts) = evaluatedDays(dateWindow(date), config, calendars)
    conflicts.firstOrNull { date in it.dates }?.let {
        return IqamahStatus.Conflict("Конфликт икаматов. ${it.detail}. Икамат на дату приостановлен; исправьте правила в настройках.")
    }
    // Do not reintroduce an adjacent day's blocked event through the 3-day window.
    val blockedDates = conflicts.flatMap { it.dates }.toSet()
    val rows = days.filterKeys { it !in blockedDates }.values.flatten()
    val events = rows.mapNotNull { it.event }.filter { allowDemo || !it.demo }.sortedBy { it.at }
    val duration = if (config.dimMinutes == 0) 60L else config.dimMinutes * 60L
    events.lastOrNull { now >= it.at && now < it.at.plusSeconds(duration) }?.let {
        return IqamahStatus.Now(it, it.at.plusSeconds(duration), config.dimMinutes > 0)
    }
    events.filter { it.start != null && now >= it.start && now < it.at }.minByOrNull { it.at }?.let { return IqamahStatus.Waiting(it) }
    val next = events.firstOrNull { it.at > now }
    val errors = iqamahDay(date, config, calendars).filter { it.event == null && config.rule(it.prayer, date)?.mode?.let { mode -> mode != IqamahMode.OFF } == true }
    return IqamahStatus.Idle(next, errors.firstOrNull()?.detail ?: if (next == null) "Нет следующего икамата; проверьте правила" else if (next.start == null) "Начало не подтверждено; указан отдельный икамат" else "До начала — обычный отсчёт намаза")
}

/** A wall-clock jump cannot safely trigger an arrival screen until the administrator checks it. */
class IqamahClockGuard {
    private var previous: Instant? = null
    private var elapsed: Long? = null
    var problem: Boolean = false; private set
    fun sample(now: Instant, monotonicMillis: Long) {
        if (previous != null && elapsed != null && kotlin.math.abs(Duration.between(previous, now).toMillis() - (monotonicMillis - elapsed!!)) > 120_000) problem = true
        previous = now; elapsed = monotonicMillis
    }
    fun confirm(now: Instant, monotonicMillis: Long) { previous = now; elapsed = monotonicMillis; problem = false }
}
