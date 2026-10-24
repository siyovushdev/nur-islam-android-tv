package dev.nurislam.tv

import dev.nurislam.tv.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ScheduleJsonTest {
    private fun rejected(change: (com.google.gson.JsonObject) -> Unit) {
        val obj = ScheduleFixtures.json(); change(obj)
        assertThrows(ScheduleFormatException::class.java) { ScheduleJson.parse(obj.toString().toByteArray()) }
    }
    @Test fun validatesFullOrdinaryAndLeapYears() {
        assertEquals(365, ScheduleFixtures.schedule().days.size)
        val leap = ScheduleFixtures.schedule(2028)
        assertEquals(366, leap.days.size)
        assertNotNull(leap.day(LocalDate.of(2028, 2, 29)))
    }
    @Test fun missingDuplicateAndWrongYearDatesAreRejected() {
        rejected { it.getAsJsonArray("days").remove(0) }
        rejected { val days = it.getAsJsonArray("days"); days.set(1, days[0]) }
        rejected { it.getAsJsonArray("days")[0].asJsonObject.addProperty("date", "2027-01-01") }
        rejected { it.getAsJsonArray("days")[0].asJsonObject.addProperty("date", "2026-02-29") }
    }
    @Test fun everyRequiredFieldIsValidated() {
        for (key in listOf("schemaVersion", "year", "source", "revision", "locationId", "locationName", "timezone", "approved", "days")) rejected { it.remove(key) }
        rejected { it.addProperty("timezone", "UTC") }
        rejected { it.addProperty("schemaVersion", 2) }
        rejected { it.addProperty("approved", "true") }
        rejected { it.addProperty("year", 2026.1) }
        rejected { it.addProperty("source", " ") }
        rejected { it.addProperty("fajrMeaning", "IQAMA") }
        rejected { it.addProperty("typo", 1) }
    }
    @Test fun strictHoursAndEightFields() {
        for (time in listOf("24:00", "05:60", "5:08", "05:08:00", "", "ab:cd"))
            rejected { it.getAsJsonArray("days")[0].asJsonObject.addProperty("asr", time) }
        rejected { it.getAsJsonArray("days")[0].asJsonObject.remove("isha") }
        assertEquals("12:05", ScheduleFixtures.schedule().days.values.first().times[dev.nurislam.tv.model.ScheduleEvent.DHUHR].toString())
    }
    @Test fun strictJsonAndUtf8BoundsRejectMalformedInput() {
        for (text in listOf("{}{}", "{foo:1}", "{'year':2026}", "{\"year\":2026,}", "{\"year\":2026,\"year\":2026}", "null"))
            assertThrows(ScheduleFormatException::class.java) { ScheduleJson.parse(text.toByteArray()) }
        assertThrows(ScheduleFormatException::class.java) { ScheduleJson.parse(byteArrayOf(0xc3.toByte(), 0x28)) }
        assertThrows(ScheduleFormatException::class.java) { ScheduleJson.parse(ByteArray(ScheduleJson.MAX_BYTES + 1)) }
        assertThrows(ScheduleFormatException::class.java) { ScheduleJson.parse("[".repeat(20).toByteArray()) }
    }
    @Test fun sourceSeasonalTimesAreNotSortedOrCorrected() {
        val obj = ScheduleFixtures.json()
        obj.getAsJsonArray("days")[0].asJsonObject.addProperty("fajrInMosque", "01:00")
        assertEquals("01:00", ScheduleJson.parse(obj.toString().toByteArray()).days.values.first().times[dev.nurislam.tv.model.ScheduleEvent.FAJR_IN_MOSQUE].toString())
    }
}
