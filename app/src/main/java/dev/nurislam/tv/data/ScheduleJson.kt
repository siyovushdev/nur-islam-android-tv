package dev.nurislam.tv.data

import com.google.gson.*
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import dev.nurislam.tv.model.*
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.LocalDate
import java.time.LocalTime
import java.util.Collections

class ScheduleFormatException(message: String) : IllegalArgumentException(message)

enum class FajrMeaning { UNCONFIRMED, PRAYER_START }

/** Constructed only after full validation. Raw bytes are canonical UTF-8 JSON, never a live URI. */
class ValidatedSchedule internal constructor(
    val metadata: ScheduleMetadata,
    val locationId: String,
    val locationName: String,
    val fajrMeaning: FajrMeaning,
    val days: Map<LocalDate, DaySchedule>,
    val json: String,
    val excelNotice: String? = null,
) {
    fun day(date: LocalDate) = days[date]
}

object ScheduleJson {
    const val MAX_BYTES = 1_048_576
    private val keys = listOf("suhurEnd", "fajrInMosque", "sunrise", "zawal", "dhuhr", "asr", "maghrib", "isha")
    private fun fail(message: String): Nothing = throw ScheduleFormatException(message)

    fun parse(bytes: ByteArray): ValidatedSchedule {
        if (bytes.size > MAX_BYTES) fail("Файл больше 1 МиБ")
        val json = try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
        } catch (_: Exception) { fail("Файл должен быть в UTF-8") }
        return try { parseText(json) } catch (e: ScheduleFormatException) { throw e }
        catch (_: Exception) { fail("Повреждённый JSON или неверный формат даты/времени") }
    }

    private fun parseText(json: String): ValidatedSchedule {
        val reader = JsonReader(StringReader(json)).apply { strictness = Strictness.STRICT }
        val root = reader.use {
            val value = readJson(it, 0)
            if (it.peek() != JsonToken.END_DOCUMENT) fail("Данные после JSON")
            if (!value.isJsonObject) fail("Ожидается объект расписания")
            value.asJsonObject
        }
        val allowed = setOf("schemaVersion", "locationId", "locationName", "timezone", "year", "source", "revision", "approved", "days", "fajrMeaning")
        if (root.keySet().any { it !in allowed }) fail("Неизвестное поле расписания")
        fun text(key: String, max: Int = 200): String = string(root, key, max)
        if (integer(root, "schemaVersion") != 1) fail("Неизвестная schemaVersion")
        if (text("timezone") != MoscowZone.id) fail("Поддерживается только Europe/Moscow")
        val year = integer(root, "year")
        if (year !in 2000..2200) fail("Год вне поддерживаемого диапазона 2000–2200")
        val approvedValue = root.get("approved")
        if (approvedValue == null || !approvedValue.isJsonPrimitive || !approvedValue.asJsonPrimitive.isBoolean) fail("approved должен быть true/false")
        val approved = approvedValue.asBoolean
        val metadata = ScheduleMetadata(year, text("source"), text("revision", 80), approved)
        val locationId = text("locationId", 80)
        if (!locationId.matches(Regex("[a-z0-9][a-z0-9_-]{0,79}"))) fail("Неверный locationId")
        val locationName = text("locationName", 120)
        val meaning = if (root.has("fajrMeaning")) runCatching { FajrMeaning.valueOf(text("fajrMeaning")) }
            .getOrElse { fail("Неизвестный fajrMeaning") } else FajrMeaning.UNCONFIRMED
        val array = root.get("days")
        if (array == null || !array.isJsonArray) fail("days должен быть массивом")
        val expected = LocalDate.of(year, 1, 1).lengthOfYear()
        if (array.asJsonArray.size() != expected) fail("Требуется полный год: $expected дней")
        val days = linkedMapOf<LocalDate, DaySchedule>()
        array.asJsonArray.forEach { element ->
            if (!element.isJsonObject) fail("Неверная запись дня")
            val obj = element.asJsonObject
            if (obj.keySet() != (keys + "date").toSet()) fail("В каждом дне нужны date и восемь времён")
            val dateText = string(obj, "date", 10)
            if (!dateText.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))) fail("Дата должна быть YYYY-MM-DD")
            val date = LocalDate.parse(dateText)
            if (date.year != year) fail("Дата $date не соответствует году $year")
            if (days.containsKey(date)) fail("Повтор даты $date")
            val times = ScheduleEvent.entries.zip(keys).associate { (event, key) ->
                val time = string(obj, key, 5)
                if (!time.matches(Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]"))) fail("$date / $key: требуется HH:mm 00:00–23:59")
                event to LocalTime.parse(time)
            }
            // Seasonal ordering is intentionally not normalised: the approved source is authoritative.
            days[date] = DaySchedule(date, Collections.unmodifiableMap(times))
        }
        var cursor = LocalDate.of(year, 1, 1)
        repeat(expected) { if (!days.containsKey(cursor)) fail("Отсутствует $cursor"); cursor = cursor.plusDays(1) }
        return ValidatedSchedule(metadata, locationId, locationName, meaning, Collections.unmodifiableMap(days), root.toString())
    }

    private fun string(obj: JsonObject, key: String, max: Int): String {
        val value = obj.get(key)
        if (value == null || !value.isJsonPrimitive || !value.asJsonPrimitive.isString) fail("$key должен быть строкой")
        val text = value.asString
        if (text.isBlank() || text.length > max || text.any { it.isISOControl() }) fail("Пустое или слишком длинное поле $key")
        return text
    }
    private fun integer(obj: JsonObject, key: String): Int {
        val value = obj.get(key)
        if (value == null || !value.isJsonPrimitive || !value.asJsonPrimitive.isNumber || !value.asString.matches(Regex("[0-9]{1,4}"))) fail("$key должен быть целым числом")
        return value.asInt
    }
    private fun readJson(reader: JsonReader, depth: Int): JsonElement {
        if (depth > 6) fail("Слишком глубокая структура JSON")
        return when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> JsonObject().apply {
                reader.beginObject()
                while (reader.hasNext()) {
                    val name = reader.nextName()
                    if (has(name)) fail("Повтор JSON-поля $name")
                    add(name, readJson(reader, depth + 1))
                }
                reader.endObject()
            }
            JsonToken.BEGIN_ARRAY -> JsonArray().apply {
                reader.beginArray()
                while (reader.hasNext()) {
                    if (size() >= 366) fail("Слишком много записей")
                    add(readJson(reader, depth + 1))
                }
                reader.endArray()
            }
            JsonToken.STRING -> JsonPrimitive(reader.nextString())
            JsonToken.NUMBER -> JsonPrimitive(java.math.BigDecimal(reader.nextString()))
            JsonToken.BOOLEAN -> JsonPrimitive(reader.nextBoolean())
            else -> fail("Недопустимое значение JSON")
        }
    }
}
