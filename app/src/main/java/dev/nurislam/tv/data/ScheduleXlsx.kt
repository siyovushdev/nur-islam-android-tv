package dev.nurislam.tv.data

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.io.StringReader
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory

/** Bounded OOXML values reader, not an Excel calculation engine. No formulas are executed. */
object ScheduleXlsx {
    const val MAX_BYTES = 8 * 1024 * 1024
    private const val NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private val cellReference = Regex("([A-Z]{1,3})([1-9][0-9]{0,4})")
    private val decimal = Regex("[+-]?[0-9]{1,12}(\\.[0-9]{1,30})?([eE][+-]?[0-9]{1,2})?")
    private val keys = listOf("suhurEnd", "fajrInMosque", "sunrise", "zawal", "dhuhr", "asr", "maghrib", "isha")
    private fun fail(message: String): Nothing = throw ScheduleFormatException("Excel: $message")
    private data class Cell(val text: String, val type: String, val formula: Boolean)

    fun parse(bytes: ByteArray, phoneDemo: Boolean = false, checkActive: () -> Unit = {}): ValidatedSchedule {
        if (bytes.size > MAX_BYTES) fail("файл больше 8 МиБ")
        return try { checkActive(); convert(bytes, phoneDemo, checkActive) } catch (e: java.util.concurrent.CancellationException) { throw e }
        catch (e: ScheduleFormatException) { throw e }
        catch (_: Exception) { fail("повреждённый XLSX или неподдерживаемая структура") }
    }

    private fun convert(bytes: ByteArray, phoneDemo: Boolean, checkActive: () -> Unit): ValidatedSchedule {
        fun readXml(data: ByteArray, handler: DefaultHandler) = xml(data, handler, checkActive)
        val parts = linkedMapOf<String, ByteArray>()
        var total = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                checkActive()
                val entry = zip.nextEntry ?: break
                val name = entry.name
                if (parts.size >= 200 || name.startsWith('/') || name.contains('\\') || name.split('/').any { it == ".." || it == "." } || parts.containsKey(name)) fail("небезопасный ZIP")
                val content = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    checkActive()
                    val count = zip.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > 24 * 1024 * 1024 || content.size() + count > 12 * 1024 * 1024) fail("слишком большой распакованный файл")
                    content.write(buffer, 0, count)
                }
                parts[name] = content.toByteArray()
            }
        }
        if (parts.isEmpty()) fail("нужен .xlsx; старый .xls не поддерживается")
        if (parts.keys.any { it.contains("vba", true) || it.contains("externalLinks", true) || it.contains("embeddings", true) }) fail("макросы и внешние ссылки не поддерживаются")
        val types = parts["[Content_Types].xml"] ?: fail("нет описания XLSX")
        readXml(types, object : DefaultHandler() {
            override fun startElement(uri: String, local: String, q: String, a: Attributes) {
                if (a.getValue("ContentType")?.let { it.contains("macroEnabled", true) || it.contains("vbaProject", true) || it.contains("oleObject", true) } == true) fail("макросы не поддерживаются")
            }
        })
        // Reject external relationships everywhere, including unused worksheets. Never resolve them.
        parts.filterKeys { it.endsWith(".rels") }.values.forEach { data -> readXml(data, object : DefaultHandler() {
            override fun startElement(uri: String, local: String, q: String, a: Attributes) {
                if (local == "Relationship" && (a.getValue("TargetMode") == "External" || a.getValue("Target")?.contains(Regex("^[a-zA-Z]+:")) == true)) fail("внешние ссылки не поддерживаются")
            }
        }) }
        val sheets = mutableListOf<Pair<Int, String>>()
        var date1904 = false
        readXml(parts["xl/workbook.xml"] ?: fail("нет workbook.xml"), object : DefaultHandler() {
            override fun startElement(uri: String, local: String, q: String, a: Attributes) {
                if (uri != NS) return
                if (local == "workbookPr") date1904 = a.getValue("date1904") in listOf("true", "1")
                if (local == "sheet") {
                    val year = a.getValue("name")?.toIntOrNull()
                    if (year != null && year in 2000..2200) sheets += year to (a.getValue(REL, "id") ?: fail("нет связи листа"))
                }
            }
        })
        if (sheets.size != 1) fail("нужен ровно один лист с годом 2000–2200 в названии")
        val (year, sheetId) = sheets.single()
        var target: String? = null
        readXml(parts["xl/_rels/workbook.xml.rels"] ?: fail("нет связей листов"), object : DefaultHandler() {
            override fun startElement(uri: String, local: String, q: String, a: Attributes) {
                if (local == "Relationship" && a.getValue("Id") == sheetId) {
                    if (target != null || a.getValue("Type") != "$REL/worksheet") fail("неверная связь листа")
                    target = a.getValue("Target")
                }
            }
        })
        val path = target?.let { if (it.startsWith('/')) it.removePrefix("/") else "xl/$it" } ?: fail("лист не найден")
        if (path.split('/').any { it == ".." }) fail("небезопасный путь листа")
        val strings = mutableListOf<String>()
        parts["xl/sharedStrings.xml"]?.let { data ->
            var text: StringBuilder? = null
            var inText = false
            readXml(data, object : DefaultHandler() {
                override fun startElement(uri: String, local: String, q: String, a: Attributes) {
                    if (uri != NS) return
                    if (local == "si") text = StringBuilder()
                    if (local == "t") inText = true
                }
                override fun characters(ch: CharArray, start: Int, length: Int) { if (inText) { text?.append(ch, start, length); if ((text?.length ?: 0) > 1000) fail("слишком длинная строка") } }
                override fun endElement(uri: String, local: String, q: String) {
                    if (uri != NS) return
                    if (local == "t") inText = false
                    if (local == "si") { if (strings.size >= 20_000) fail("слишком много строк"); strings += text.toString(); text = null }
                }
            })
        }
        val rows = linkedMapOf<Int, Map<String, Cell>>()
        var row = 0
        var previousRow = 0
        var cells = 0
        var rowCells = linkedMapOf<String, Cell>()
        var seen = mutableSetOf<String>()
        var column = ""
        var type = ""
        var value = StringBuilder()
        var reading = false
        var formula = false
        readXml(parts[path] ?: fail("нет данных листа"), object : DefaultHandler() {
            override fun startElement(uri: String, local: String, q: String, a: Attributes) {
                if (uri != NS) return
                when (local) {
                    "row" -> {
                        row = a.getValue("r")?.toIntOrNull() ?: fail("строка без номера")
                        if (row <= previousRow || row > 20_000) fail("повтор/неверный номер строки")
                        previousRow = row; rowCells = linkedMapOf(); seen = mutableSetOf()
                    }
                    "c" -> {
                        if (++cells > 200_000) fail("слишком много ячеек")
                        val ref = a.getValue("r") ?: fail("ячейка без адреса")
                        val address = cellReference.matchEntire(ref) ?: fail("неверный адрес ячейки")
                        if (address.groupValues[2].toInt() != row) fail("неверный адрес ячейки")
                        column = address.groupValues[1]
                        if (!seen.add(column)) fail("повтор ячейки $ref")
                        type = a.getValue("t") ?: "n"; value = StringBuilder(); formula = false
                    }
                    "v", "t" -> reading = true
                    "f" -> formula = true
                }
            }
            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (reading) { value.append(ch, start, length); if (value.length > 1000) fail("слишком длинная ячейка") }
            }
            override fun endElement(uri: String, local: String, q: String) {
                if (uri != NS) return
                when (local) {
                    "v", "t" -> reading = false
                    "c" -> if (value.isNotEmpty() || formula || type == "e") {
                        val raw = value.toString()
                        val text = if (type == "s") strings.getOrNull(raw.toIntOrNull() ?: -1) ?: fail("неверная shared string") else raw
                        rowCells[column] = Cell(text, type, formula)
                    }
                    "row" -> if (rowCells.isNotEmpty()) { if (rows.size >= 368) fail("слишком много заполненных строк"); rows[row] = rowCells }
                }
            }
        })
        val header = rows[2] ?: fail("нет второй строки заголовков")
        val expectedHeaders = listOf("Дата", "Завершение сухура", "Совершается в мечети", "Восход солнца", "Зенит", "Зухр", "Аср", "Магриб", "Иша")
        ('B'..'J').zip(expectedHeaders).forEach { (col, expected) ->
            if (header[col.toString()]?.text?.trim() != expected) fail("$col: ожидается заголовок «$expected»")
        }
        val days = JsonArray()
        var formulaCount = 0
        rows.filterKeys { it > 2 }.forEach { (r, columns) ->
            val city = columns["A"]?.text?.trim()
            if (city != "Казань") fail("A$r: поддерживается расписание Казани")
            fun cell(col: String): Cell {
                val c = columns[col] ?: fail("$col$r: пустая ячейка")
                if (c.type == "e") fail("$col$r: ошибка Excel")
                if (c.formula) { if (c.text.isBlank()) fail("$col$r: формула без сохранённого результата"); formulaCount++ }
                return c
            }
            val c = cell("B")
            val date = if (c.type == "n") {
                val serial = number(c.text, "B$r")
                val day = try { serial.intValueExact() } catch (_: Exception) { fail("B$r: дата содержит время") }
                if (day !in 1..110_000 || (!date1904 && day == 60)) fail("B$r: неверная Excel-дата")
                (if (date1904) LocalDate.of(1904, 1, 1) else LocalDate.of(1899, 12, 31)).plusDays((if (!date1904 && day > 60) day - 1 else day).toLong())
            } else {
                val formats = listOf(DateTimeFormatter.ISO_LOCAL_DATE, DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT))
                formats.firstNotNullOfOrNull { runCatching { LocalDate.parse(c.text.trim(), it) }.getOrNull() } ?: fail("B$r: неверная дата")
            }
            if (date.year != year) fail("B$r: дата не соответствует году листа")
            val obj = JsonObject().apply { addProperty("date", date.toString()) }
            ('C'..'J').zip(keys).forEach { (col, key) ->
                val time = cell(col.toString())
                val text = if (time.type == "n") {
                    val fraction = number(time.text, "$col$r")
                    if (fraction < BigDecimal.ZERO || fraction >= BigDecimal.ONE) fail("$col$r: время вне суток")
                    val minutes = fraction.multiply(BigDecimal(1440))
                    val integer = minutes.setScale(0, RoundingMode.HALF_UP)
                    // Excel binary floating-point noise only (less than 0.00006 seconds).
                    if (minutes.subtract(integer).abs() > BigDecimal("0.000001")) fail("$col$r: время содержит секунды; нужны точные минуты")
                    val m = integer.toInt()
                    if (m !in 0..1439) fail("$col$r: время вне суток")
                    "%02d:%02d".format(java.util.Locale.ROOT, m / 60, m % 60)
                } else if (time.type in listOf("s", "inlineStr", "str")) time.text.trim() else fail("$col$r: неподдерживаемый тип времени")
                obj.addProperty(key, text)
            }
            days.add(obj)
        }
        val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        val json = JsonObject().apply {
            addProperty("schemaVersion", 1); addProperty("locationId", "kazan"); addProperty("locationName", "Казань")
            addProperty("timezone", "Europe/Moscow"); addProperty("year", year)
            addProperty("source", if (phoneDemo) "ТЕСТ: Excel Казань — не утверждён мечетью" else "Excel Казань — подтверждение администратора")
            addProperty("revision", "xlsx-sha256:$sha"); addProperty("approved", false)
            addProperty("fajrMeaning", "UNCONFIRMED"); add("days", days)
        }
        val parsed = ScheduleJson.parse(json.toString().toByteArray())
        return ValidatedSchedule(parsed.metadata, parsed.locationId, parsed.locationName, parsed.fajrMeaning, parsed.days, parsed.json,
            "XLSX: ${days.size()} дней, $formulaCount сохранённых результатов формул. Формулы не вычислялись. Сверьте значения с исходным Excel; его формулы могли не пересчитываться. Фаджр не подтверждён.")
    }

    private fun number(text: String, address: String): BigDecimal {
        if (!text.matches(decimal)) fail("$address: неверное число")
        return text.toBigDecimal()
    }

    private fun xml(data: ByteArray, handler: DefaultHandler, checkActive: () -> Unit) {
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data)).toString().removePrefix("\uFEFF")
        if (text.contains('\u0000') || text.contains(Regex("<!\\s*(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE))) fail("DTD и XML-сущности запрещены")
        val factory = SAXParserFactory.newInstance().apply { isNamespaceAware = true }
        val reader = factory.newSAXParser().xmlReader
        // Some Android parsers do not implement these switches: lexical rejection above is mandatory too.
        listOf("http://xml.org/sax/features/external-general-entities", "http://xml.org/sax/features/external-parameter-entities", "http://apache.org/xml/features/nonvalidating/load-external-dtd").forEach {
            runCatching { reader.setFeature(it, false) }
        }
        reader.entityResolver = org.xml.sax.EntityResolver { _, _ -> fail("внешние XML-сущности запрещены") }
        var depth = 0
        reader.contentHandler = object : DefaultHandler() {
            override fun startElement(uri: String, local: String, q: String, a: Attributes) { checkActive(); if (++depth > 32) fail("слишком глубокий XML"); handler.startElement(uri, local, q, a) }
            override fun endElement(uri: String, local: String, q: String) { handler.endElement(uri, local, q); depth-- }
            override fun characters(ch: CharArray, start: Int, length: Int) = handler.characters(ch, start, length)
        }
        reader.errorHandler = object : DefaultHandler() { override fun fatalError(e: org.xml.sax.SAXParseException) { throw e }; override fun error(e: org.xml.sax.SAXParseException) { throw e } }
        reader.parse(InputSource(StringReader(text)))
    }
}

object ScheduleImport {
    fun parse(bytes: ByteArray, phoneDemo: Boolean = false, sourceName: String? = null, checkActive: () -> Unit = {}): ValidatedSchedule {
        checkActive()
        if (bytes.size >= 2 && bytes[0] == 0xd0.toByte() && bytes[1] == 0xcf.toByte()) throw ScheduleFormatException("Старый .xls не поддерживается. Сохраните файл в формате .xlsx.")
        return if (bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4b.toByte()) ScheduleXlsx.parse(bytes, phoneDemo || (sourceName?.let(::isTestSource) == true), checkActive) else ScheduleJson.parse(bytes).also { checkActive() }
    }

    /** Only XLSX drafts can acquire approval through this explicit UI confirmation. JSON rules stay unchanged. */
    fun confirmExcel(candidate: ValidatedSchedule): ValidatedSchedule {
        require(candidate.excelNotice != null) { "Это не предпросмотр XLSX" }
        val json = com.google.gson.JsonParser.parseString(candidate.json).asJsonObject
        json.addProperty("approved", true)
        return ScheduleJson.parse(json.toString().toByteArray())
    }
}
