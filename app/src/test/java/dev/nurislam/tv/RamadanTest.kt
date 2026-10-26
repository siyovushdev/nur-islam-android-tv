package dev.nurislam.tv

import dev.nurislam.tv.model.MoscowZone
import dev.nurislam.tv.ramadan.*
import dev.nurislam.tv.settings.BoardLanguage
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class RamadanTest {
    // Deliberately fabricated dates, never bundled into runtime settings.
    private fun dates(days: Long = 29, first: String = "2026-02-01", approved: Boolean = true): RamadanDates {
        val d = LocalDate.parse(first)
        return RamadanDates(d.year, d, d.plusDays(days), "Контрольный протокол для unit-проверки", approved)
    }
    private fun now(d: LocalDate) = d.atStartOfDay(MoscowZone).toInstant()
    private fun status(d: LocalDate, dates: RamadanDates) = ramadanStatus(now(d), RamadanConfig(true, listOf(dates)))
    @Test fun disabledMissingUnapprovedAndWrongClockNeverProduceCountdown() {
        assertEquals(RamadanStatus.Off, ramadanStatus(Instant.now(), RamadanConfig()))
        assertTrue(ramadanStatus(Instant.parse("2026-01-01T00:00:00Z"), RamadanConfig(true)) is RamadanStatus.Hidden)
        val d = dates(approved = false)
        assertTrue(status(d.firstFast.minusDays(1), d) is RamadanStatus.Hidden)
        assertTrue(ramadanStatus(now(d.firstFast), RamadanConfig(true, listOf(d)), true) is RamadanStatus.Hidden)
        assertTrue(ramadanStatus(Instant.parse("1970-01-01T00:00:00Z"), RamadanConfig(true, listOf(d))) is RamadanStatus.Hidden)
        assertNull(ramadanText(RamadanStatus.Hidden("нет дат"), BoardLanguage.BOTH))
    }
    @Test fun transitionsAcrossBoth29And30DayMonthsAndOneDayEid() {
        for (days in listOf(29L, 30L)) {
            val d = dates(days)
            assertEquals(1, (status(d.firstFast.minusDays(1), d) as RamadanStatus.Before).days)
            assertEquals(1, (status(d.firstFast, d) as RamadanStatus.Fasting).day)
            assertEquals(days.toInt(), (status(d.eid!!.minusDays(1), d) as RamadanStatus.Fasting).day)
            assertEquals(1, (status(d.eid.minusDays(1), d) as RamadanStatus.Fasting).untilEid)
            assertTrue(status(d.eid, d) is RamadanStatus.Eid)
            assertTrue(status(d.eid.plusDays(1), d) is RamadanStatus.Hidden)
        }
    }
    @Test fun missingEidNeverInvents30thFastingDayOrHoliday() {
        val d = dates().copy(eid = null)
        assertEquals(29, (status(d.firstFast.plusDays(28), d) as RamadanStatus.Fasting).day)
        val unknown = status(d.firstFast.plusDays(29), d) as RamadanStatus.Hidden
        assertTrue(unknown.reason.contains("не подтверждено"))
        assertNull(ramadanText(unknown, BoardLanguage.RUSSIAN))
    }
    @Test fun midnightUsesMoscowCalendarDateAndNoSecondsCountdown() {
        val d = dates()
        val c = RamadanConfig(true, listOf(d))
        assertEquals(1, (ramadanStatus(now(d.firstFast).minusSeconds(1), c) as RamadanStatus.Before).days)
        assertEquals(1, (ramadanStatus(now(d.firstFast), c) as RamadanStatus.Fasting).day)
    }
    @Test fun configuredNextYearAndActiveCrossYearPeriodWorkWithoutReusingStaleDates() {
        val d = dates(first = "2027-01-10")
        assertEquals(10, (status(LocalDate.of(2026, 12, 31), d) as RamadanStatus.Before).days)
        assertEquals(9, (status(LocalDate.of(2027, 1, 1), d) as RamadanStatus.Before).days)
        assertTrue(status(LocalDate.of(2028, 1, 1), d) is RamadanStatus.Hidden)
        val cross = dates(first = "2026-12-20")
        assertEquals(13, (status(LocalDate.of(2027, 1, 1), cross) as RamadanStatus.Fasting).day)
    }
    @Test fun invalidPeriodsSourceAndDuplicateDatesRejectedButTwoPeriodsInOneGregorianYearAllowed() {
        for (days in listOf(28L, 31L, -1L)) assertThrows(IllegalArgumentException::class.java) { dates(days) }
        assertThrows(IllegalArgumentException::class.java) { dates().copy(year = 2027) }
        assertThrows(IllegalArgumentException::class.java) { dates().copy(source = "") }
        assertThrows(IllegalArgumentException::class.java) { dates().copy(source = "ТЕСТ ДЛЯ ТЕЛЕФОНА") }
        assertThrows(IllegalArgumentException::class.java) { RamadanConfig(true, listOf(dates(), dates())) }
        assertThrows(IllegalArgumentException::class.java) { RamadanConfig(true, listOf(dates(), dates(first = "2026-02-20"))) }
        assertEquals(2, RamadanConfig(true, listOf(dates(first = "2026-01-01"), dates(first = "2026-12-01"))).dates.size)
        assertThrows(java.time.DateTimeException::class.java) { dates(first = "2026-02-29") }
    }
    @Test fun bothLanguagesHaveClearFirstFastAndIndependentEidMessages() {
        val d = dates()
        val before = status(d.firstFast.minusDays(2), d)
        assertTrue(ramadanText(before, BoardLanguage.RUSSIAN)!!.contains("первого дня поста"))
        assertTrue(ramadanText(before, BoardLanguage.TATAR)!!.contains("ураза көненә"))
        assertEquals(2, ramadanText(before, BoardLanguage.BOTH)!!.lines().size)
        assertTrue(ramadanText(status(d.eid!!, d), BoardLanguage.RUSSIAN)!!.contains("С праздником"))
        assertTrue(ramadanText(status(d.firstFast, d), BoardLanguage.TATAR)!!.contains("1 нче көн"))
    }
}
