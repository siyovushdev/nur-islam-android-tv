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
}
