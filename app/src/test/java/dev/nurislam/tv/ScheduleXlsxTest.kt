package dev.nurislam.tv

import dev.nurislam.tv.data.*
import dev.nurislam.tv.model.ScheduleEvent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry

class ScheduleXlsxTest {
    companion object {
        fun source(): ByteArray = ScheduleXlsxTest::class.java.getResourceAsStream("/xlsx/kazan-2026.xlsx")!!.use { it.readBytes() }
        fun mutate(transform: (String, String) -> String, extra: Pair<String, String>? = null): ByteArray {
            val result = ByteArrayOutputStream()
            ZipOutputStream(result).use { output ->
                ZipInputStream(ByteArrayInputStream(source())).use { input ->
                    while (true) {
                        val entry = input.nextEntry ?: break
                        val content = input.readBytes().toString(Charsets.UTF_8)
                        output.putNextEntry(ZipEntry(entry.name)); output.write(transform(entry.name, content).toByteArray()); output.closeEntry()
                    }
                }
                extra?.let { output.putNextEntry(ZipEntry(it.first)); output.write(it.second.toByteArray()); output.closeEntry() }
            }
            return result.toByteArray()
        }
    }
    @Test fun everyOriginalValueMatchesIndependentOpenpyxlOracle() {
        val parsed = ScheduleXlsx.parse(source())
        val oracle = javaClass.getResourceAsStream("/xlsx/kazan-2026.tsv")!!.bufferedReader().readLines()
        assertEquals(365, oracle.size)
        var compared = 0
        oracle.forEach { line ->
            val cells = line.split('\t')
            val date = LocalDate.parse(cells.first())
            ScheduleEvent.entries.forEachIndexed { i, event ->
                assertEquals("$date / $event", cells[i + 1], parsed.day(date)!!.times.getValue(event).toString())
                compared++
            }
        }
        assertEquals(2920, compared)
        assertEquals(FajrMeaning.UNCONFIRMED, parsed.fajrMeaning)
        assertFalse(parsed.metadata.approved)
        assertTrue(parsed.excelNotice!!.contains("730 сохранённых"))
        assertEquals("18:50", parsed.day(LocalDate.of(2026, 1, 1))!!.times.getValue(ScheduleEvent.ISHA).toString())
    }
    @Test fun approvalIsExplicitAndPhoneSourceRemainsTestAfterPersistence() = runTest {
        val phone = ScheduleImport.parse(source(), phoneDemo = true)
        assertTrue(isTestSource(phone.metadata.source)); assertFalse(phone.metadata.approved)
        val storage = MemoryStorage()
        val repo = LocalScheduleRepository(storage)
        repo.load()
        assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { repo.apply(phone) } }
        repo.apply(ScheduleImport.confirmExcel(phone))
        assertTrue(isTestSource(repo.state.value.current!!.metadata.source))
        val tv = ScheduleImport.confirmExcel(ScheduleXlsx.parse(source()))
        assertFalse(isTestSource(tv.metadata.source))
        repo.apply(tv)
        repo.rollback()
        val restored = LocalScheduleRepository(storage); restored.load()
        assertTrue(isTestSource(restored.state.value.current!!.metadata.source))
        assertEquals(phone.days, restored.state.value.current!!.days)
    }
    @Test fun testFilenameMarksExcelWithoutAutomaticallyApprovingIt() {
        val file = ScheduleImport.parse(source(), sourceName = "ТЕСТ ДЛЯ ТЕЛЕФОНА.xlsx")
        assertTrue(isTestSource(file.metadata.source)); assertFalse(file.metadata.approved)
        assertNotNull(file.excelNotice)
        assertFalse(isTestSource(ScheduleImport.parse(source(), sourceName = "vremena_namazov_RT_2026_0.xlsx").metadata.source))
    }
    @Test fun cancellationDuringUnzipOrXmlIsNotSwallowedAndAnotherImportRemainsValid() {
        var checkpoints = 0
        assertThrows(java.util.concurrent.CancellationException::class.java) {
            ScheduleImport.parse(source()) { if (++checkpoints > 1500) throw java.util.concurrent.CancellationException("user cancelled") }
        }
        assertTrue(checkpoints > 1500)
        assertEquals(365, ScheduleXlsx.parse(source()).days.size)
    }
    @Test fun jsonApprovalCannotUseExcelConfirmation() {
        val json = ScheduleJson.parse(ScheduleFixtures.json(approved = false).toString().toByteArray())
        assertThrows(IllegalArgumentException::class.java) { ScheduleImport.confirmExcel(json) }
        assertFalse(ScheduleImport.parse(json.json.toByteArray()).metadata.approved)
    }
    private fun reject(bytes: ByteArray, detail: String? = null) {
        val e = assertThrows(ScheduleFormatException::class.java) { ScheduleXlsx.parse(bytes) }
        if (detail != null) assertTrue("Expected $detail: ${e.message}", e.message!!.contains(detail))
    }
    private fun sheet(change: (String) -> String) = mutate({ name, text -> if (name == "xl/worksheets/sheet1.xml") change(text) else text })
    @Test fun rejectsMissingDateDuplicateAndWrongYear() {
        reject(sheet { it.replace("46024", "46023") }, "Повтор даты")
        reject(sheet { it.replace("46023", "45658") }, "году листа")
        reject(sheet { it.replace(Regex("<s:row r=\"3\".*?</s:row>"), "") }, "полный год")
    }
    @Test fun rejectsErrorsMissingFormulaCachesAndSeconds() {
        reject(sheet { it.replace("r=\"D3\" s=\"7\"", "r=\"D3\" s=\"7\" t=\"e\"") }, "ошибка Excel")
        reject(sheet { it.replace("<s:v>0.2944444444444444</s:v>", "") }, "формула без сохранённого")
        reject(sheet { it.replace("0.2944444444444444", "0.2945") }, "секунды")
        reject(sheet { it.replace("0.2944444444444444", "1") }, "вне суток")
    }
    @Test fun supportsInlineDateAnd1904DateSystemWithoutMinuteAdjustments() {
        val inline = sheet { it.replace("r=\"B3\" s=\"3\"><s:v>46023</s:v>", "r=\"B3\" s=\"3\" t=\"inlineStr\"><s:is><s:t>01.01.2026</s:t></s:is>") }
        assertEquals(365, ScheduleXlsx.parse(inline).days.size)
        val shifted = mutate({ name, text ->
            when (name) {
                "xl/workbook.xml" -> text.replace("<sheets>", "<workbookPr date1904=\"1\"/><sheets>")
                "xl/worksheets/sheet1.xml" -> text.replace(Regex("(<s:c r=\"B[0-9]+\"[^>]*><s:v>)([0-9]{5})(</s:v>)")) { match -> match.groupValues[1] + (match.groupValues[2].toInt() - 1462) + match.groupValues[3] }
                else -> text
            }
        })
        assertEquals(ScheduleXlsx.parse(source()).days, ScheduleXlsx.parse(shifted).days)
    }
    @Test fun rejectsHostileArchivesExternalLinksAndXmlEntities() {
        reject(ByteArray(ScheduleXlsx.MAX_BYTES + 1), "8 МиБ")
        reject(mutate({ _, t -> t }, "../escape.xml" to "x"), "небезопасный")
        reject(mutate({ _, t -> t }, "xl/vbaProject.bin" to "x"), "макросы")
        reject(mutate({ n, t -> if (n.endsWith("workbook.xml.rels")) t.replace("Target=\"styles.xml\"", "Target=\"https://example.invalid/evil\" TargetMode=\"External\"") else t }), "внешние ссылки")
        reject(mutate({ n, t -> if (n == "xl/workbook.xml") "<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///never-read'>]>$t" else t }), "DTD")
        reject(mutate({ _, t -> t }, "zip-bomb.txt" to "x".repeat(13 * 1024 * 1024)), "распакованный")
        reject(sheet { it.replace("0.2944444444444444", "1e99999999") })
    }
    private class MemoryStorage : ScheduleStorage {
        var bytes: ByteArray? = null
        override fun read() = bytes
        override fun write(bytes: ByteArray) { this.bytes = bytes }
    }
}
