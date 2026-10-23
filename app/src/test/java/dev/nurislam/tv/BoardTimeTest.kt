package dev.nurislam.tv

import dev.nurislam.tv.domain.*
import dev.nurislam.tv.data.FajrMeaning
import dev.nurislam.tv.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*
import java.util.Locale

class BoardTimeTest {
    private val date = LocalDate.of(2026, 10, 23)
    private fun day(date: LocalDate = this.date) = DaySchedule(date, ScheduleEvent.entries.zip(
        listOf("05:08", "05:30", "07:12", "11:40", "12:05", "15:15", "17:48", "19:02").map(LocalTime::parse)).toMap())
    private fun at(time: String) = date.atTime(LocalTime.parse(time)).atZone(MoscowZone).toInstant()

    @Test fun clockAlwaysUsesMoscowAnd24Hours() {
        assertEquals("14:20", formatClock(Instant.parse("2026-10-23T11:20:00Z")))
        assertEquals("00:00", formatClock(Instant.parse("2026-10-23T21:00:00Z")))
        assertEquals("05:08", formatTime(LocalTime.of(5, 8)))
    }
    @Test fun datesHaveCorrectTatarAndRussianCharacters() {
        assertEquals("Җомга, 23 октябрь 2026", formatTatarDate(date))
        assertEquals("Пятница, 23 октября 2026", formatRussianDate(date))
        assertEquals("Пәнҗешәмбе, 29 февраль 2024", formatTatarDate(LocalDate.of(2024, 2, 29)))
    }
    @Test fun countdownIsClampedAndDoesNotWrapAfter24Hours() {
        assertEquals("00:55:00", formatCountdown(at("14:20"), at("15:15")))
        assertEquals("00:00:00", formatCountdown(at("15:16"), at("15:15")))
        assertEquals("25:01:01", formatCountdown(at("14:20"), at("14:20").plusSeconds(90061)))
        assertEquals("00:00:01", formatCountdown(at("14:20"), at("14:20").plusMillis(1)))
    }
    @Test fun nextPrayerExcludesSuhurSunriseZawalAndAmbiguousFajr() {
        assertNull(nextPrayer(at("04:00"), listOf(day())))
        assertTrue(prayerStatus(at("06:00"), listOf(day()), FajrMeaning.UNCONFIRMED) is PrayerStatus.FajrUnconfirmed)
        assertEquals(ScheduleEvent.ASR, nextPrayer(at("14:20"), listOf(day()))?.event)
        assertEquals(ScheduleEvent.MAGHRIB, nextPrayer(at("15:15"), listOf(day()))?.event)
    }
    @Test fun afterIshaRequiresActualNextDateAndNeverReusesToday() {
        assertNull(nextPrayer(at("19:02"), listOf(day())))
        val next = nextPrayer(at("23:59"), listOf(day(), day(date.plusDays(1))), FajrMeaning.PRAYER_START)!!
        assertEquals(date.plusDays(1), next.at.atZone(MoscowZone).toLocalDate())
        assertEquals(ScheduleEvent.FAJR_IN_MOSQUE, next.event)
    }
    @Test fun systemTimeChangesAreRecomputedFromInstant() {
        assertEquals(ScheduleEvent.ASR, nextPrayer(at("14:20"), listOf(day()))?.event)
        assertEquals(ScheduleEvent.ISHA, nextPrayer(at("18:00"), listOf(day()))?.event)
        assertEquals(ScheduleEvent.DHUHR, nextPrayer(at("11:00"), listOf(day()))?.event)
    }
    @Test fun digitsDoNotDependOnDeviceLocale() {
        val previous = Locale.getDefault()
        try { Locale.setDefault(Locale.forLanguageTag("ar")); assertEquals("00:55:00", formatCountdown(at("14:20"), at("15:15"))) }
        finally { Locale.setDefault(previous) }
    }
}
