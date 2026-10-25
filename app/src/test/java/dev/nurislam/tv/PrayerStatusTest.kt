package dev.nurislam.tv

import dev.nurislam.tv.domain.*
import dev.nurislam.tv.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class PrayerStatusTest {
    @Test fun ambiguousFajrNeverSkipsToDhuhrInTheMorning() {
        val data = ScheduleFixtures.schedule()
        for (time in listOf("2026-10-23T01:00:00Z", "2026-10-23T03:00:00Z", "2026-10-23T20:59:00Z"))
            assertTrue(prayerStatus(Instant.parse(time), data) is PrayerStatus.FajrUnconfirmed)
    }
    @Test fun confirmedFajrAndYearBoundaryAreHandled() {
        val data = ScheduleFixtures.schedule(meaning = "PRAYER_START")
        val next = prayerStatus(Instant.parse("2026-10-23T01:00:00Z"), data) as PrayerStatus.Upcoming
        assertEquals(ScheduleEvent.FAJR_IN_MOSQUE, next.prayer.event)
        assertEquals(PrayerStatus.NextDayUnavailable, prayerStatus(Instant.parse("2026-12-31T20:59:00Z"), data))
        assertEquals(PrayerStatus.Unavailable, prayerStatus(Instant.parse("2026-12-31T21:00:00Z"), data))
    }
    @Test fun eventBoundaryAdvancesWithoutInventingIqama() {
        val data = ScheduleFixtures.schedule()
        val next = prayerStatus(Instant.parse("2026-10-23T12:15:00Z"), data) as PrayerStatus.Upcoming
        assertEquals(ScheduleEvent.MAGHRIB, next.prayer.event)
    }
    @Test fun remoteEntryNeedsFiveCompletedClicksAndExpires() {
        val gesture = RemoteEntryGesture()
        repeat(4) { assertFalse(gesture.click(it * 100L)) }; assertTrue(gesture.click(500))
        assertFalse(gesture.click(600)); assertFalse(gesture.click(6000))
        repeat(3) { assertFalse(gesture.click(6100 + it * 100L)) }; assertTrue(gesture.click(6500))
    }
    @Test fun october25UnconfirmedFajrStillWaitsUntilSunrise() {
        val json = ScheduleFixtures.json().apply {
            getAsJsonArray("days").forEach { element -> element.asJsonObject.apply {
                addProperty("suhurEnd", "05:00"); addProperty("fajrInMosque", "05:25")
                addProperty("sunrise", "06:35"); addProperty("zawal", "11:40")
                addProperty("dhuhr", "12:00"); addProperty("asr", "15:10")
            } }
        }
        val calendar = dev.nurislam.tv.data.ScheduleJson.parse(json.toString().toByteArray())
        fun at(time: String) = java.time.LocalDate.of(2026, 10, 25).atTime(java.time.LocalTime.parse(time)).atZone(MoscowZone).toInstant()
        assertTrue(prayerStatus(at("06:29"), calendar) is PrayerStatus.FajrUnconfirmed)
        for (time in listOf("06:35", "06:36", "11:59")) assertEquals(ScheduleEvent.DHUHR, (prayerStatus(at(time), calendar) as PrayerStatus.Upcoming).prayer.event)
        assertEquals(ScheduleEvent.ASR, (prayerStatus(at("12:00"), calendar) as PrayerStatus.Upcoming).prayer.event)
    }

}
