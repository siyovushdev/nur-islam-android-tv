package dev.nurislam.tv.calendar

import com.google.gson.*
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import dev.nurislam.tv.data.*
import dev.nurislam.tv.iqamah.*
import dev.nurislam.tv.ramadan.*
import dev.nurislam.tv.settings.BoardLanguage
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.*

/** One replaceable document; no hidden per-section approvals or changes to prayer times. */
data class MosqueCalendar(
    val mosqueId: String, val name: String, val cityId: String, val city: String, val zone: ZoneId,
    val from: LocalDate, val until: LocalDate, val source: String, val revision: String,
    val demo: Boolean, val iqamah: IqamahConfig, val ramadan: RamadanConfig,
    val events: List<MosqueEvent>, val json: String,
) {
    fun compatibility(schedules: ScheduleState): String? {
        val schedule = schedules.current ?: return null
        if (schedule.locationId != cityId || (schedule.mosqueId != null && schedule.mosqueId != mosqueId))
            return "Календарь относится к мечети «$name», $city. Загрузите календарь для выбранного расписания."
        if (schedule.metadata.timezone != zone) return "Часовые пояса файлов различаются. Загрузите расписание и календарь для одной мечети."
        if (listOfNotNull(schedules.current, schedules.previous).flatMap { it.days.keys }.none { it >= from && it <= until && schedules.scheduleFor(it) != null }) return "Периоды файлов не совпадают. Загрузите календарь на год расписания."
        return null
    }
    fun active(now: Instant, schedules: ScheduleState): Boolean = compatibility(schedules) == null && now.atZone(zone).toLocalDate().let { it >= from && it <= until }
    fun eventText(now: Instant, language: BoardLanguage): String? = events.filter { it.contains(now, zone) }.joinToString(" · ") {
        when (language) { BoardLanguage.RUSSIAN -> it.title; BoardLanguage.TATAR -> it.tatar ?: it.title; BoardLanguage.BOTH -> it.tatar?.let { tt -> "$tt · ${it.title}" } ?: it.title }
    }.takeIf { it.isNotEmpty() }
    /** Validate each available day; absent years stay unavailable, never extrapolated. */
    fun validateAgainst(schedules: ScheduleState) {
        if (schedules.current == null) return
        require(compatibility(schedules) == null) { compatibility(schedules)!! }
        val compatible = schedules
        val conflict = iqamahConflicts(iqamah, compatible).firstOrNull { it.dates.any { d -> d >= from && d <= until } }
        require(conflict == null) { "Исправьте время в календаре: ${conflict?.detail}" }
        for (date in listOfNotNull(compatible.current, compatible.previous).flatMap { it.days.keys }.distinct().filter { it >= from && it <= until }) {
            val schedule = compatible.scheduleFor(date) ?: continue
            for (prayer in prayersOn(date)) {
                val rule = iqamah.rule(prayer, date) ?: continue
                if (rule.mode == IqamahMode.OFFSET && prayer == IqamahPrayer.FAJR)
                    require(schedule.fajrMeaning == FajrMeaning.PRAYER_START) { "Для отсчёта икамата Фаджра в расписании должно быть явно указано начало молитвы. Выберите точное время икамата или исправьте смысл поля в исходном файле." }
                if (rule.mode == IqamahMode.OFFSET && prayer == IqamahPrayer.JUMUAH)
                    require(rule.prayerStart != null) { "Для интервала Джума укажите начало Джума в календаре." }
                val row = iqamahDay(date, iqamah, compatible).first { it.prayer == prayer }
                require(rule.mode == IqamahMode.OFF || row.event != null) { "$date: ${prayer.title}. ${row.detail}" }
            }
        }
    }
}

data class MosqueEvent(val id: String, val title: String, val tatar: String?, val from: LocalDate, val until: LocalDate,
    val start: LocalTime?, val end: LocalTime?) {
    fun contains(now: Instant, zone: ZoneId): Boolean {
        val local = now.atZone(zone)
        return local.toLocalDate() in from..until && (start == null || local.toLocalTime() >= start) && (end == null || local.toLocalTime() < end)
    }
}

object MosqueCalendarJson {
    const val MAX_BYTES = 1_048_576
    fun parse(bytes: ByteArray): MosqueCalendar = try {
        require(bytes.size <= MAX_BYTES) { "Календарь больше 1 МиБ" }
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
        val root = JsonReader(StringReader(text)).apply { strictness = Strictness.STRICT }.use {
            val value = ScheduleJson.readJson(it, 0)
            require(it.peek() == JsonToken.END_DOCUMENT) { "Лишние данные после календаря" }; value.asJsonObject
        }
        root.keys("type", "schemaVersion", "mosque", "validFrom", "validUntil", "source", "revision", "demo", "iqamah", "ramadan", "events")
        require(root.text("type") == "mosqueCalendar" && root.number("schemaVersion") == 1) { "Неподдерживаемый формат календаря мечети" }
        val mosque = root.getAsJsonObject("mosque") ?: error("Укажите мечеть")
        mosque.keys("id", "name", "cityId", "city", "timezone")
        fun id(key: String) = mosque.text(key, 80).also { require(it.matches(Regex("[a-z0-9][a-z0-9_-]{0,79}"))) { "Неверный идентификатор $key" } }
        val zone = ZoneId.of(mosque.text("timezone"))
        require(zone.id in ZoneId.getAvailableZoneIds()) { "Укажите именованный часовой пояс" }
        val from = root.date("validFrom"); val until = root.date("validUntil")
        require(until >= from && until <= from.plusYears(5)) { "Период календаря: от одного дня до пяти лет" }
        val source = root.text("source"); val demo = root.bool("demo") || isTestSource(source)
        fun within(date: LocalDate) { require(date in from..until) { "Дата $date вне периода календаря" } }
        val iq = root.get("iqamah")?.asJsonObject
        iq?.keys("rules", "dimMinutes")
        val rules = iq?.array("rules")?.map { element ->
            val r = element.asJsonObject
            r.keys("prayer", "from", "until", "exception", "mode", "time", "minutes", "start")
            val prayer = runCatching { IqamahPrayer.valueOf(r.text("prayer")) }.getOrElse { error("Неверная молитва в поле prayer календаря") }
            val mode = runCatching { IqamahMode.valueOf(r.text("mode")) }.getOrElse { error("Неверный способ задания икамата в поле mode календаря") }
            val date = r.date("from").also(::within)
            val last = if (r.has("until")) r.date("until").also(::within) else until
            require(last >= date) { "Конец правила раньше начала" }
            val exception = r.bool("exception")
            require(!exception || !r.has("until")) { "Разовое исключение задаётся на один день, без until" }
            require(prayer == IqamahPrayer.JUMUAH || !r.has("start")) { "Начало обычной молитвы берётся только из расписания" }
            val start = if (r.has("start")) r.time("start") else null
            require(!exception || prayer != IqamahPrayer.JUMUAH || date.dayOfWeek == DayOfWeek.FRIDAY) { "Разовая Джума должна приходиться на пятницу" }
            val value = when (mode) {
                IqamahMode.OFF -> { require(!r.has("time") && !r.has("minutes") && start == null) { "У отключённого правила не должно быть времени" }; 0 }
                IqamahMode.FIXED -> { require(!r.has("minutes")) { "Для точного икамата укажите только time" }; r.time("time").let { it.hour * 60 + it.minute } }
                IqamahMode.OFFSET -> { require(!r.has("time")) { "Для интервала укажите только minutes" }; require(prayer != IqamahPrayer.JUMUAH || start != null) { "Для интервала Джума нужно отдельное начало start" }; r.number("minutes") }
            }
            if (mode == IqamahMode.FIXED && start != null) require(value >= start.hour * 60 + start.minute) { "Икамат Джума раньше указанного начала" }
            IqamahRule(prayer, date, exception, mode, value, fromFile = true, until = last, prayerStart = start)
        } ?: emptyList()
        val ramadan = root.array("ramadan").map { element ->
            val r = element.asJsonObject; r.keys("firstFast", "eid")
            val first = r.date("firstFast").also(::within)
            val eid = if (r.has("eid")) r.date("eid").also(::within) else null
            RamadanDates(first.year, first, eid, source)
        }
        val events = root.array("events").map { element ->
            val e = element.asJsonObject; e.keys("id", "title", "titleTatar", "from", "until", "start", "end")
            val first = e.date("from").also(::within); val last = if (e.has("until")) e.date("until").also(::within) else first
            val start = if (e.has("start")) e.time("start") else null; val end = if (e.has("end")) e.time("end") else null
            require(last >= first && (start == null || end == null || end > start)) { "Неверный период события; для показа через полночь задайте два события" }
            MosqueEvent(e.text("id", 80), e.text("title", 120), if (e.has("titleTatar")) e.text("titleTatar", 120) else null, first, last, start, end)
        }
        require(events.size <= 366 && events.map { it.id }.distinct().size == events.size) { "Повтор события или больше 366 событий" }
        MosqueCalendar(id("id"), mosque.text("name", 60), id("cityId"), mosque.text("city", 80), zone, from, until, source, root.text("revision", 80), demo,
            IqamahConfig(rules.isNotEmpty(), source = source, dimMinutes = iq?.let { if (it.has("dimMinutes")) it.number("dimMinutes") else 0 } ?: 0, rules = rules, fromFile = true, demo = demo),
            RamadanConfig(ramadan.isNotEmpty(), ramadan), events, root.toString())
    } catch (e: Exception) {
        throw ScheduleFormatException(if (e is IllegalArgumentException && e.message != null && e !is DateTimeException && e !is JsonParseException) e.message!!.take(350)
        else "Не удалось прочитать календарь: проверьте обязательные поля, даты YYYY-MM-DD, времена HH:mm и часовой пояс.")
    }
    private fun JsonObject.keys(vararg allowed: String) { require(keySet().all { it in allowed }) { "Неизвестное поле календаря: ${keySet().firstOrNull { it !in allowed }}" } }
    private fun JsonObject.text(key: String, max: Int = 200): String {
        val v = get(key); require(v?.isJsonPrimitive == true && v.asJsonPrimitive.isString) { "Укажите текстовое поле $key" }
        return v.asString.also { require(it.isNotBlank() && it.length <= max && it.none(Char::isISOControl)) { "Поле $key пустое или слишком длинное" } }
    }
    private fun JsonObject.number(key: String): Int { val v = get(key); require(v?.isJsonPrimitive == true && v.asJsonPrimitive.isNumber && v.asString.matches(Regex("0|[1-9][0-9]{0,3}"))) { "В поле $key нужно целое число" }; return v.asInt }
    private fun JsonObject.bool(key: String): Boolean { val v = get(key) ?: return false; require(v.isJsonPrimitive && v.asJsonPrimitive.isBoolean) { "В поле $key нужно true/false" }; return v.asBoolean }
    private fun JsonObject.date(key: String): LocalDate { val s = text(key); require(s.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))) { "Дата $key должна быть YYYY-MM-DD" }; return LocalDate.parse(s).also { require(it.year in 2000..2200) { "Год вне 2000–2200" } } }
    private fun JsonObject.time(key: String): LocalTime { val s = text(key); require(s.matches(Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]"))) { "Время $key должно быть HH:mm" }; return LocalTime.parse(s) }
    private fun JsonObject.array(key: String): JsonArray { val v = get(key) ?: return JsonArray(); require(v.isJsonArray) { "В поле $key нужен список" }; return v.asJsonArray }
}

/** Computed off the UI thread. Pending/replaced documents never execute stale rules. */
data class CalendarCheck(val problem: String?, val boundaryNotice: String?)
fun checkCalendar(calendar: MosqueCalendar, schedules: ScheduleState, date: LocalDate): CalendarCheck {
    val problem = calendar.compatibility(schedules) ?: runCatching { calendar.validateAgainst(schedules) }.exceptionOrNull()?.message
        ?: if(date < calendar.from || date > calendar.until) "Календарь мечети действует ${calendar.from} — ${calendar.until}. Загрузите файл на текущую дату." else null
    val boundary = if(problem == null) iqamahBoundaryWarnings(calendar.iqamah, schedules, date).firstOrNull()
        ?.replace("(fajrInMosque / UNCONFIRMED)", "— колонка «в мечети» не задаёт начало молитвы") else null
    return CalendarCheck(problem, boundary)
}
