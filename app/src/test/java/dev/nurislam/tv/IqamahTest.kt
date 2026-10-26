package dev.nurislam.tv

import dev.nurislam.tv.data.*
import dev.nurislam.tv.iqamah.*
import dev.nurislam.tv.model.MoscowZone
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class IqamahTest {
    private val date = LocalDate.of(2026, 10, 24) // Saturday, not Friday.
    private fun calendar(year: Int = 2026, meaning: String? = "PRAYER_START", revision: String = "one") = ScheduleJson.parse(
        ScheduleFixtures.json(year, revision, meaning = meaning).apply { addProperty("source", "Fixture protocol 42") }.toString().toByteArray())
    private val calendar = calendar()
    private val schedules = ScheduleState(calendar, loaded = true)
    private fun at(time: String, day: LocalDate = date) = day.atTime(LocalTime.parse(time)).atZone(MoscowZone).toInstant()
    private fun rule(p: IqamahPrayer, mode: IqamahMode = IqamahMode.OFFSET, value: Int = 12, day: LocalDate = date, bound: Boolean = true, exception: Boolean = false) =
        IqamahRule(p, day, exception, mode, if (mode == IqamahMode.OFF) 0 else value, if (bound && mode != IqamahMode.OFF) calendarFingerprint(calendar.json) else null)
    private fun config(vararg rules: IqamahRule, dim: Int = 0) = IqamahConfig(true, true, "Fixture protocol 42", dim, rules.toList())
    @Test fun allFiveExactRelativeTransitionsAndNoMutation() {
        val original = calendar.json
        val starts = listOf("05:30", "12:05", "15:15", "17:48", "19:02")
        prayersOn(date).zip(starts).forEach { (p, t) ->
            val c = config(rule(p))
            val start = at(t); val target = start.plusSeconds(12 * 60)
            assertTrue(iqamahStatus(start.minusSeconds(1), c, schedules, false) is IqamahStatus.Idle)
            val waiting = iqamahStatus(start, c, schedules, false) as IqamahStatus.Waiting
            assertEquals(p, waiting.event.prayer); assertEquals(target, waiting.event.at)
            assertTrue(iqamahStatus(target.minusSeconds(1), c, schedules, false) is IqamahStatus.Waiting)
            assertFalse((iqamahStatus(target, c, schedules, false) as IqamahStatus.Now).dim)
            assertTrue(iqamahStatus(target.plusSeconds(59), c, schedules, false) is IqamahStatus.Now)
            assertTrue(iqamahStatus(target.plusSeconds(60), c, schedules, false) is IqamahStatus.Idle)
            val fixed = config(rule(p, IqamahMode.FIXED, (target.atZone(MoscowZone).hour * 60 + target.atZone(MoscowZone).minute)))
            assertEquals(target, (iqamahStatus(start, fixed, schedules, false) as IqamahStatus.Waiting).event.at)
        }
        assertEquals(original, calendar.json)
        assertEquals("05:30", calendar.day(date)!!.times.getValue(dev.nurislam.tv.model.ScheduleEvent.FAJR_IN_MOSQUE).toString())
    }
    @Test fun fajrUnknownCannotBecomeAzanEvenWhenBindingIsForged() {
        val unknown = calendar(meaning = null)
        val c = config(rule(IqamahPrayer.FAJR).copy(baseCalendar = calendarFingerprint(unknown.json)))
        val s = ScheduleState(unknown, loaded = true)
        assertNull(iqamahDay(date, c, s).first().event)
        assertTrue(iqamahStatus(at("05:35"), c, s, false) is IqamahStatus.Idle)
        val fixed = config(rule(IqamahPrayer.FAJR, IqamahMode.FIXED, 360, bound = false))
        val info = iqamahStatus(at("05:35"), fixed, s, false) as IqamahStatus.Idle
        assertNull(info.upcoming!!.start); assertEquals(at("06:00"), info.upcoming!!.at)
        assertTrue(iqamahStatus(at("06:00"), fixed, s, false) is IqamahStatus.Now)
    }
    @Test fun datesSeasonsExceptionOffAndFutureRules() {
        val early = rule(IqamahPrayer.ASR, day = date.minusMonths(1), value = 10)
        val seasonal = rule(IqamahPrayer.ASR, day = date.plusDays(2), value = 20)
        val oneDay = rule(IqamahPrayer.ASR, day = date.plusDays(3), value = 30, exception = true)
        val off = rule(IqamahPrayer.ASR, IqamahMode.OFF, day = date.plusDays(4))
        val c = config(early, seasonal, oneDay, off)
        assertNull(c.rule(IqamahPrayer.ASR, early.from.minusDays(1)))
        assertEquals(10, c.rule(IqamahPrayer.ASR, date)!!.value)
        assertEquals(20, c.rule(IqamahPrayer.ASR, date.plusDays(2))!!.value)
        assertEquals(30, c.rule(IqamahPrayer.ASR, date.plusDays(3))!!.value)
        assertEquals(IqamahMode.OFF, c.rule(IqamahPrayer.ASR, date.plusDays(4))!!.mode)
        assertEquals(20, config(early, seasonal, oneDay).rule(IqamahPrayer.ASR, date.plusDays(4))!!.value)
    }
    @Test fun fridayRequiresExplicitJumuahNeverDhuhrFallbackOrInterval() {
        val friday = date.minusDays(1)
        val dhuhr = rule(IqamahPrayer.DHUHR, day = friday)
        val c = config(dhuhr)
        assertFalse(iqamahDay(friday, c, schedules).any { it.prayer == IqamahPrayer.DHUHR })
        assertNull(iqamahDay(friday, c, schedules).first { it.prayer == IqamahPrayer.JUMUAH }.event)
        val j = rule(IqamahPrayer.JUMUAH, IqamahMode.FIXED, 13 * 60, friday, false)
        assertTrue(iqamahStatus(at("13:00", friday), config(dhuhr, j), schedules, false) is IqamahStatus.Now)
        assertTrue(iqamahDay(date, config(dhuhr, j), schedules).none { it.prayer == IqamahPrayer.JUMUAH })
        assertThrows(IllegalArgumentException::class.java) { rule(IqamahPrayer.JUMUAH) }
    }
    @Test fun newCalendarDisablesRelativeUntilReconfirmedButPreservesFixedAndStoredRules() {
        val c = config(rule(IqamahPrayer.ASR), rule(IqamahPrayer.ISHA, IqamahMode.FIXED, 1200, bound = false))
        val changed = ScheduleState(calendar(revision = "two"), calendar, true)
        val rows = iqamahDay(date, c, changed)
        assertNull(rows.first { it.prayer == IqamahPrayer.ASR }.event)
        assertTrue(rows.first { it.prayer == IqamahPrayer.ASR }.detail.contains("загрузите исправленные данные"))
        assertEquals(at("20:00"), rows.first { it.prayer == IqamahPrayer.ISHA }.event!!.at)
        assertEquals(12, c.rules.first().value)
        assertNotNull(iqamahCompatibilityWarning(date, c, changed))
        assertNull(iqamahCompatibilityWarning(date, c, schedules))
    }
    @Test fun yearBoundaryUsesBothCalendarsAndRequiresNewYearBinding() {
        val end = LocalDate.of(2026, 12, 31); val jan = end.plusDays(1)
        val next = calendar(2027)
        val state = ScheduleState(next, calendar, true)
        val r = rule(IqamahPrayer.FAJR, day = end)
        val c = config(r, rule(IqamahPrayer.ISHA, IqamahMode.FIXED, 23 * 60 + 59, end, false))
        assertNotNull(iqamahDay(end, c, state).first().event)
        assertNull(iqamahDay(jan, c, state).first().event)
        assertEquals(at("05:42", jan), iqamahDay(jan, config(r.copy(from = jan, baseCalendar = calendarFingerprint(next.json))), state).first().event!!.at)
        assertTrue(iqamahStatus(at("00:00", jan), c, state, false) is IqamahStatus.Idle)
        assertTrue(iqamahStatus(at("23:59:59", end), c, state, false) is IqamahStatus.Now)
    }
    @Test fun missingCalendarRulesInvalidDateAndReleaseDemoFailClosed() {
        assertTrue(iqamahStatus(at("12:00"), IqamahConfig(), schedules, true) is IqamahStatus.Idle)
        val c = config(rule(IqamahPrayer.ASR))
        assertNull((iqamahStatus(at("15:20"), c, ScheduleState(), true) as IqamahStatus.Idle).upcoming)
        assertNull((iqamahStatus(at("15:20"), c.copy(approved = false), schedules, false) as IqamahStatus.Idle).upcoming)
        assertTrue(iqamahStatus(at("15:20"), c.copy(approved = false), schedules, true) is IqamahStatus.Waiting)
        val test = ScheduleState(ScheduleFixtures.schedule(meaning = "PRAYER_START"), loaded = true)
        val fixed = config(rule(IqamahPrayer.ASR, IqamahMode.FIXED, 16 * 60, bound = false))
        assertTrue((iqamahStatus(at("16:00"), fixed, test, false) as IqamahStatus.Now).event.demo)
        assertTrue((iqamahStatus(at("16:00"), fixed, test, true) as IqamahStatus.Now).event.demo)
        assertNull((iqamahStatus(at("15:20"), c, schedules, true, clockProblem = true) as IqamahStatus.Idle).upcoming)
        assertTrue(iqamahStatus(Instant.EPOCH, c, schedules, true) is IqamahStatus.Idle)
        assertThrows(IllegalArgumentException::class.java) { c.copy(source = "ТЕСТ ДЛЯ ТЕЛЕФОНА") }
    }
    @Test fun invalidValuesAndCrossMidnightNeverInventTimes() {
        assertThrows(IllegalArgumentException::class.java) { rule(IqamahPrayer.ASR, value = -1) }
        assertThrows(IllegalArgumentException::class.java) { rule(IqamahPrayer.ASR, value = 721) }
        assertThrows(IllegalArgumentException::class.java) { rule(IqamahPrayer.ASR, IqamahMode.FIXED, 1440) }
        assertThrows(IllegalArgumentException::class.java) { rule(IqamahPrayer.ASR, bound = false) }
        val r = rule(IqamahPrayer.ASR)
        assertThrows(IllegalArgumentException::class.java) { config(r, r) }
        assertThrows(IllegalArgumentException::class.java) { config(r, dim = 61) }
        assertNull(iqamahDay(date, config(rule(IqamahPrayer.ISHA, value = 720)), schedules).last().event)
        assertNull(iqamahDay(date, config(rule(IqamahPrayer.ASR, IqamahMode.FIXED, 12 * 60)), schedules)[2].event)
        val zero = config(rule(IqamahPrayer.ASR, value = 0))
        assertTrue(iqamahStatus(at("15:15"), zero, schedules, false) is IqamahStatus.Now)
        val dim = config(rule(IqamahPrayer.ASR), dim = 5)
        assertTrue((iqamahStatus(at("15:31:59"), dim, schedules, false) as IqamahStatus.Now).dim)
        assertTrue(iqamahStatus(at("15:32"), dim, schedules, false) is IqamahStatus.Idle)
    }
    @Test fun clockJumpLatchesUntilConfirmedAndResumeWithNormalElapsedDoesNotLatch() {
        val guard = IqamahClockGuard(); val start = at("12:00")
        guard.sample(start, 1000); guard.sample(start.plusSeconds(3600), 3_601_000); assertFalse(guard.problem)
        guard.sample(start.plusSeconds(7200), 3_602_000); assertTrue(guard.problem)
        guard.sample(start.plusSeconds(7201), 3_603_000); assertTrue(guard.problem)
        guard.confirm(start.plusSeconds(7201), 3_603_000); assertFalse(guard.problem)
        guard.sample(start.minusSeconds(60), 3_604_000); assertTrue(guard.problem)
    }
    @Test fun reversedAndEqualIqamahNeverReturnToEarlierPrayer() {
        for (asr in listOf(15 * 60 + 20, 15 * 60 + 30)) {
            val c = config(rule(IqamahPrayer.DHUHR, IqamahMode.FIXED, 930), rule(IqamahPrayer.ASR, IqamahMode.FIXED, asr))
            val before = IqamahJson.encode(c).toString()
            for (time in listOf("12:00", "15:15", "15:20", "15:21", "15:30", "15:31")) {
                val status = iqamahStatus(at(time), c, schedules, false) as IqamahStatus.Conflict
                assertTrue(status.detail.contains("Зухр")); assertTrue(status.detail.contains("Аср"))
                assertTrue(status.detail.contains("15:30"))
            }
            assertTrue(iqamahDay(date, c, schedules).all { it.event == null })
            assertEquals(before, IqamahJson.encode(c).toString()) // No correction of entered times.
        }
    }
    @Test fun orderedTimesWithOverlappingWaitOrDimAreAlsoConflicts() {
        val waiting = config(rule(IqamahPrayer.DHUHR, IqamahMode.FIXED, 920), rule(IqamahPrayer.ASR, IqamahMode.FIXED, 930))
        assertTrue(iqamahStatus(at("15:21"), waiting, schedules, false) is IqamahStatus.Conflict)
        val dim = config(rule(IqamahPrayer.DHUHR, IqamahMode.FIXED, 910, bound = false), rule(IqamahPrayer.ASR, IqamahMode.FIXED, 912, bound = false), dim = 5)
        assertTrue(iqamahStatus(at("15:15"), dim, schedules, false) is IqamahStatus.Conflict)
        assertTrue(iqamahConflicts(dim, schedules).first().detail.contains("Пересекаются"))
    }
    @Test fun touchingHalfOpenWindowsAdvanceNormallyAndOffRulesDoNotConflict() {
        val c = config(rule(IqamahPrayer.DHUHR, IqamahMode.FIXED, 914), rule(IqamahPrayer.ASR))
        assertTrue(iqamahConflicts(c, schedules).isEmpty())
        assertEquals(IqamahPrayer.DHUHR, (iqamahStatus(at("15:14:59"), c, schedules, false) as IqamahStatus.Now).event.prayer)
        assertEquals(IqamahPrayer.ASR, (iqamahStatus(at("15:15"), c, schedules, false) as IqamahStatus.Waiting).event.prayer)
        val off = config(rule(IqamahPrayer.DHUHR, IqamahMode.OFF), rule(IqamahPrayer.ASR))
        assertTrue(iqamahConflicts(off, schedules).isEmpty())
    }
    @Test fun conflictChecksRespectExceptionsFridayAndMidnight() {
        val first = rule(IqamahPrayer.DHUHR, IqamahMode.FIXED, 735, day = date.minusDays(1))
        val asr = rule(IqamahPrayer.ASR, IqamahMode.FIXED, 930, day = date.minusDays(1))
        val exception = first.copy(from = date, exception = true, value = 950)
        val c = config(first, asr, exception)
        assertTrue(iqamahStatus(at("15:20"), c, schedules, false) is IqamahStatus.Conflict)
        assertFalse(iqamahStatus(at("15:20", date.plusDays(1)), c, schedules, false) is IqamahStatus.Conflict)
        val friday = date.minusDays(1)
        val j = rule(IqamahPrayer.JUMUAH, IqamahMode.FIXED, 780, friday, false)
        assertTrue(iqamahStatus(at("13:00", friday), config(first, asr, j), schedules, false) is IqamahStatus.Now)
        val badJ = j.copy(value = 960)
        assertTrue(iqamahStatus(at("15:31", friday), config(first, asr, badJ), schedules, false) is IqamahStatus.Conflict)
        val crossing = config(rule(IqamahPrayer.ISHA, IqamahMode.FIXED, 1439, bound = false), rule(IqamahPrayer.FAJR, IqamahMode.FIXED, 2, bound = false), dim = 5)
        assertTrue(iqamahStatus(at("23:59"), crossing, schedules, false) is IqamahStatus.Conflict)
        assertTrue(iqamahStatus(at("00:03", date.plusDays(1)), crossing, schedules, false) is IqamahStatus.Conflict)
        val nextYear = calendar(2027)
        val years = ScheduleState(nextYear, calendar, true)
        val end = LocalDate.of(2026, 12, 31)
        assertTrue(iqamahStatus(at("00:03", end.plusDays(1)), crossing, years, false) is IqamahStatus.Conflict)
    }
    @Test fun fullCalendarValidationFindsFutureExceptionAndLeavesFajrUnconfirmedAlone() {
        val future = date.plusDays(10)
        val c = config(rule(IqamahPrayer.DHUHR, IqamahMode.FIXED, 735), rule(IqamahPrayer.ASR), rule(IqamahPrayer.DHUHR, IqamahMode.FIXED, 950, future, exception = true))
        val conflicts = iqamahConflicts(c, schedules)
        assertTrue(conflicts.any { future in it.dates })
        assertFalse(iqamahStatus(at("12:10"), c, schedules, false) is IqamahStatus.Conflict)
        val unknown = calendar(meaning = null)
        val unknownState = ScheduleState(unknown, loaded = true)
        val blocked = config(rule(IqamahPrayer.FAJR).copy(baseCalendar = calendarFingerprint(unknown.json)))
        assertTrue(iqamahConflicts(blocked, unknownState).isEmpty())
        assertNull(iqamahDay(date, blocked, unknownState).first().event)
    }

    @Test fun effectiveDateRepairPreservesHistoryAndOnlyFutureConflictsBlockSaving() {
        val bad = config(rule(IqamahPrayer.DHUHR, IqamahMode.FIXED, 950), rule(IqamahPrayer.ASR, IqamahMode.FIXED, 930))
        val tomorrow = date.plusDays(1)
        val repaired = bad.copy(rules = bad.rules + rule(IqamahPrayer.DHUHR, IqamahMode.FIXED, 735, tomorrow))
        assertTrue(iqamahConflicts(repaired, schedules).any { date in it.dates })
        assertTrue(iqamahConflicts(repaired, schedules, tomorrow).isEmpty())
        assertEquals(950, repaired.rule(IqamahPrayer.DHUHR, date)!!.value)
        assertEquals(735, repaired.rule(IqamahPrayer.DHUHR, tomorrow)!!.value)
        assertTrue(iqamahStatus(at("15:21"), repaired, schedules, false) is IqamahStatus.Conflict)
        assertFalse(iqamahStatus(at("15:21", tomorrow), repaired, schedules, false) is IqamahStatus.Conflict)
    }

    @Test fun fridayJumuahChecksAsrStartWithoutAnyAsrIqamahRule() {
        val friday = date.minusDays(1)
        for (asrRule in listOf<IqamahRule?>(null, rule(IqamahPrayer.ASR, IqamahMode.OFF, day = friday))) {
            val j = rule(IqamahPrayer.JUMUAH, IqamahMode.FIXED, 927, friday, false)
            val c = config(*listOfNotNull(j, asrRule).toTypedArray())
            val unchanged = calendar.json
            val conflicts = iqamahConflicts(c, schedules)
            assertTrue(conflicts.any { it.earlier.prayer == IqamahPrayer.JUMUAH && it.prayerStart && it.laterPrayer == IqamahPrayer.ASR && it.laterAt == at("15:15", friday) })
            val status = iqamahStatus(at("15:27", friday), c, schedules, false) as IqamahStatus.Conflict
            assertTrue(status.detail.contains("начало молитвы Аср"))
            assertTrue(status.detail.contains("15:15")); assertTrue(status.detail.contains("15:27"))
            assertEquals(unchanged, calendar.json)
            assertEquals(927, c.rule(IqamahPrayer.JUMUAH, friday)!!.value)
        }
    }
    @Test fun ordinaryIqamahChecksAllLaterJsonPrayersWithoutTheirRules() {
        val cases = listOf(IqamahPrayer.FAJR to 726, IqamahPrayer.DHUHR to 916, IqamahPrayer.ASR to 1069, IqamahPrayer.MAGHRIB to 1143)
        for ((p, value) in cases) {
            val c = config(rule(p, IqamahMode.FIXED, value, bound = false))
            assertTrue(iqamahConflicts(c, schedules).any { it.earlier.prayer == p && it.prayerStart })
            assertTrue(iqamahStatus(at("12:00"), c, schedules, false) is IqamahStatus.Conflict)
        }
    }
    @Test fun correctRulesAndExactlyTouchingIndicationBoundaryStayAllowed() {
        val valid = listOf(IqamahPrayer.FAJR to 342, IqamahPrayer.DHUHR to 914, IqamahPrayer.ASR to 930, IqamahPrayer.MAGHRIB to 1080, IqamahPrayer.ISHA to 1200)
        for ((p, value) in valid) {
            val c = config(rule(p, IqamahMode.FIXED, value))
            assertTrue("$p", iqamahConflicts(c, schedules).isEmpty())
            assertFalse(iqamahStatus(at("12:00"), c, schedules, false) is IqamahStatus.Conflict)
        }
        val friday = date.minusDays(1)
        assertTrue(iqamahConflicts(config(rule(IqamahPrayer.JUMUAH, IqamahMode.FIXED, 780, friday, false)), schedules).isEmpty())
        assertTrue(iqamahStatus(at("15:15"), config(rule(IqamahPrayer.DHUHR, IqamahMode.FIXED, 915)), schedules, false) is IqamahStatus.Conflict)
        assertTrue(iqamahStatus(at("15:12"), config(rule(IqamahPrayer.DHUHR, IqamahMode.FIXED, 912), dim = 5), schedules, false) is IqamahStatus.Conflict)
    }
    @Test fun unknownNextFajrIsDiagnosticNotAnInventedStartOrFalseConflict() {
        val json = ScheduleFixtures.json(meaning = null).apply {
            addProperty("source", "Fixture protocol 42")
            getAsJsonArray("days").forEach { it.asJsonObject.addProperty("fajrInMosque", "00:02") }
        }
        val unknown = ScheduleJson.parse(json.toString().toByteArray())
        val state = ScheduleState(unknown, loaded = true)
        val c = config(rule(IqamahPrayer.ISHA, IqamahMode.FIXED, 1439, bound = false), dim = 5)
        assertTrue(iqamahConflicts(c, state).isEmpty())
        val status = iqamahStatus(at("23:59"), c, state, false) as IqamahStatus.Now
        assertTrue(status.event.boundaryWarning!!.contains("UNCONFIRMED"))
        assertFalse(status.event.boundaryWarning!!.contains("00:02"))
        assertTrue(iqamahDay(date, c, state).last().detail.contains("Согласованность"))
        json.addProperty("fajrMeaning", "PRAYER_START")
        val confirmed = ScheduleState(ScheduleJson.parse(json.toString().toByteArray()), loaded = true)
        assertTrue(iqamahStatus(at("23:59"), c, confirmed, false) is IqamahStatus.Conflict)
        assertTrue(iqamahConflicts(c, confirmed).any { it.prayerStart && it.laterPrayer == IqamahPrayer.FAJR })
        val lastDay = LocalDate.of(2026, 12, 31)
        val missingNextYear = iqamahStatus(at("23:59", lastDay), c, state, false) as IqamahStatus.Now
        assertTrue(missingNextYear.event.boundaryWarning!!.contains("Нет календаря на 2027-01-01"))
    }
    @Test fun fridayDoesNotSubstituteDhuhrForUnknownJumuahStart() {
        val friday = date.minusDays(1)
        val c = config(rule(IqamahPrayer.FAJR, IqamahMode.FIXED, 730, friday, false))
        assertFalse(iqamahStatus(at("12:10", friday), c, schedules, false) is IqamahStatus.Conflict)
        val warning = (iqamahStatus(at("12:10", friday), c, schedules, false) as IqamahStatus.Now).event.boundaryWarning!!
        assertTrue(warning.contains("Зухра из JSON не заменяет"))
        assertTrue(iqamahBoundaryWarnings(c, schedules, friday).isNotEmpty())
        val tooLate = config(rule(IqamahPrayer.FAJR, IqamahMode.FIXED, 927, friday, false))
        assertTrue(iqamahConflicts(tooLate, schedules).any { it.laterPrayer == IqamahPrayer.ASR && it.prayerStart })
    }

}
