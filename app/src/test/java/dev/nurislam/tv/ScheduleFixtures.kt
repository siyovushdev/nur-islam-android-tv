package dev.nurislam.tv

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dev.nurislam.tv.data.ScheduleJson
import java.time.LocalDate

/** Entirely fabricated test data. Never shipped as a runtime calendar. */
object ScheduleFixtures {
    fun json(year: Int = 2026, revision: String = "test-1", approved: Boolean = true, asr: String = "15:15", meaning: String? = null): JsonObject = JsonObject().apply {
        addProperty("schemaVersion", 1); addProperty("locationId", "test-mosque"); addProperty("locationName", "TEST ONLY")
        addProperty("timezone", "Europe/Moscow"); addProperty("year", year)
        addProperty("source", "SYNTHETIC TEST FIXTURE / NOT AN OFFICIAL CALENDAR")
        addProperty("revision", revision); addProperty("approved", approved)
        if (meaning != null) addProperty("fajrMeaning", meaning)
        add("days", JsonArray().apply {
            var date = LocalDate.of(year, 1, 1)
            repeat(date.lengthOfYear()) {
                add(JsonObject().apply {
                    addProperty("date", date.toString())
                    listOf("suhurEnd", "fajrInMosque", "sunrise", "zawal", "dhuhr", "asr", "maghrib", "isha")
                        .zip(listOf("05:08", "05:30", "07:12", "11:40", "12:05", asr, "17:48", "19:02")).forEach { (key, time) -> addProperty(key, time) }
                })
                date = date.plusDays(1)
            }
        })
    }
    fun schedule(year: Int = 2026, revision: String = "test-1", asr: String = "15:15", meaning: String? = null) =
        ScheduleJson.parse(json(year, revision, asr = asr, meaning = meaning).toString().toByteArray())
}
