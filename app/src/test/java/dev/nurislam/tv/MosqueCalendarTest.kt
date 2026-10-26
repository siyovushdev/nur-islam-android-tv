package dev.nurislam.tv

import com.google.gson.JsonParser
import dev.nurislam.tv.calendar.*
import dev.nurislam.tv.data.*
import dev.nurislam.tv.domain.*
import dev.nurislam.tv.iqamah.*
import dev.nurislam.tv.ramadan.*
import dev.nurislam.tv.settings.BoardLanguage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class MosqueCalendarTest {
    private fun json(zone: String = "Europe/Moscow", rules: String = "", extra: String = "") = """{
      "type":"mosqueCalendar","schemaVersion":1,
      "mosque":{"id":"mosque-a","name":"Мечеть А","cityId":"test-mosque","city":"Город","timezone":"$zone"},
      "validFrom":"2026-01-01","validUntil":"2026-12-31","source":"ТЕСТ ДЛЯ ТЕЛЕФОНА","revision":"1",
      "iqamah":{"rules":[$rules]}$extra }"""
    private fun rule(prayer: String = "ASR", mode: String = "OFFSET", value: String = "\"minutes\":10", from: String = "2026-01-01", extra: String = "") =
        """{"prayer":"$prayer","mode":"$mode","from":"$from",$value$extra}"""
    private fun calendar(text: String = json()) = MosqueCalendarJson.parse(text.toByteArray())
    private fun schedules(zone: String = "Europe/Moscow", fajr: String = "PRAYER_START") = ScheduleState(ScheduleJson.parse(ScheduleFixtures.json(meaning = fajr).apply {
        addProperty("timezone", zone); addProperty("approved", false)
    }.toString().toByteArray()), loaded = true)
    private fun at(local: String, zone: String = "Europe/Moscow") = LocalDateTime.parse(local).atZone(ZoneId.of(zone)).toInstant()
    private fun rejected(text: String) { try { calendar(text); fail("Must reject") } catch (_: ScheduleFormatException) {} }

    @Test fun importedRulesRunIdenticallyWithoutApprovalAndKeepDemoMarker() {
        val c = calendar(json(rules = rule())); val s = schedules()
        c.validateAgainst(s)
        val status = iqamahStatus(at("2026-10-26T15:20"), c.iqamah, s, false) as IqamahStatus.Waiting
        assertTrue(status.event.demo); assertEquals(IqamahPrayer.ASR, status.event.prayer)
        assertEquals("00:05:00", formatCountdown(at("2026-10-26T15:20"), status.event.at))
        assertTrue(iqamahStatus(at("2026-10-26T15:25"), c.iqamah, s, false) is IqamahStatus.Now)
        assertTrue(iqamahStatus(at("2026-10-26T15:26"), c.iqamah, s, false) is IqamahStatus.Idle)
        assertEquals(prayerStatus(at("2026-10-26T15:20"), s), prayerStatus(at("2026-10-26T15:20"), schedules()))
    }
    @Test fun allFiveAndSeparateFridayStartWorkAcrossRegions() {
        val rules = listOf("FAJR","DHUHR","ASR","MAGHRIB","ISHA").joinToString(",") { rule(it) } + "," + rule("JUMUAH", value="\"minutes\":15,\"start\":\"12:30\"")
        for (zone in listOf("Europe/Kaliningrad", "Europe/Moscow", "Asia/Yekaterinburg", "Asia/Vladivostok", "Asia/Kamchatka")) {
            val c = calendar(json(zone, rules)); val s = schedules(zone); c.validateAgainst(s)
            for (p in listOf("FAJR" to "05:35", "DHUHR" to "12:10", "ASR" to "15:20", "MAGHRIB" to "17:53", "ISHA" to "19:07")) {
                val state = iqamahStatus(at("2026-10-26T${p.second}", zone), c.iqamah, s, false)
                assertEquals(IqamahPrayer.valueOf(p.first), (state as IqamahStatus.Waiting).event.prayer)
            }
            assertEquals(IqamahPrayer.JUMUAH, (iqamahStatus(at("2026-10-23T12:35", zone), c.iqamah, s, false) as IqamahStatus.Waiting).event.prayer)
            assertEquals("23:59", formatClock(at("2026-12-31T23:59", zone), s.zone))
            assertTrue(prayerStatus(at("2027-01-01T00:00", zone), s) is PrayerStatus.Unavailable)
        }
    }
    @Test fun fajrUnknownAllowsExactButNeverInventsStart() {
        val c = calendar(json(rules=rule("FAJR","FIXED","\"time\":\"05:45\""))); val s = schedules(fajr="UNCONFIRMED")
        c.validateAgainst(s)
        assertNull((iqamahStatus(at("2026-10-26T05:40"), c.iqamah, s, false) as IqamahStatus.Idle).upcoming!!.start)
        assertTrue(iqamahStatus(at("2026-10-26T05:45"), c.iqamah, s, false) is IqamahStatus.Now)
        try { calendar(json(rules=rule("FAJR"))).validateAgainst(s); fail() } catch(e: IllegalArgumentException) { assertTrue(e.message!!.contains("Фаджра")) }
        assertTrue(prayerStatus(at("2026-10-26T06:29"), s) is PrayerStatus.FajrUnconfirmed)
    }
    @Test fun laterPrayerBoundaryRejectsJumuahEvenWithoutAsrRuleAndDoesNotAdjust() {
        val c = calendar(json(rules=rule("JUMUAH","FIXED","\"time\":\"15:27\"")))
        try { c.validateAgainst(schedules()); fail() } catch(e: IllegalArgumentException) { assertTrue(e.message!!.contains("Аср")) }
        assertEquals(15*60+27, c.iqamah.rules.single().value)
    }
    @Test fun ordinaryConflictAndEarlierThanStartAreRejected() {
        for (time in listOf("11:00","15:27")) {
            try { calendar(json(rules=rule("DHUHR","FIXED","\"time\":\"$time\""))).validateAgainst(schedules()); fail(time) } catch (_: IllegalArgumentException) {}
        }
    }
    @Test fun seasonsExceptionsAndOffHaveClearBoundaries() {
        val c = calendar(json(rules=listOf(rule(),rule(from="2026-06-01",extra=",\"until\":\"2026-08-31\""),rule(mode="FIXED",value="\"time\":\"15:40\"",from="2026-06-15",extra=",\"exception\":true")).joinToString(",")))
        assertEquals(IqamahMode.FIXED,c.iqamah.rule(IqamahPrayer.ASR,LocalDate.parse("2026-06-15"))!!.mode)
        assertEquals(IqamahMode.OFFSET,c.iqamah.rule(IqamahPrayer.ASR,LocalDate.parse("2026-06-16"))!!.mode)
        assertNull(c.iqamah.rule(IqamahPrayer.ASR,LocalDate.parse("2026-09-01")))
    }
    @Test fun independentRamadanAndLocalEventTimeNoApprovalNeeded() {
        val c = calendar(json("Asia/Vladivostok",extra=""", "ramadan":[{"firstFast":"2026-02-18","eid":"2026-03-20"}], "events":[{"id":"a","title":"Встреча","titleTatar":"Очрашу","from":"2026-03-01","start":"18:00","end":"20:00"}]"""))
        assertEquals(1,(ramadanStatus(at("2026-02-18T00:00",c.zone.id),c.ramadan,zone=c.zone,fromFile=true) as RamadanStatus.Fasting).day)
        assertTrue(ramadanStatus(at("2026-03-20T00:00",c.zone.id),c.ramadan,zone=c.zone,fromFile=true) is RamadanStatus.Eid)
        assertNull(c.eventText(at("2026-03-01T17:59",c.zone.id),BoardLanguage.RUSSIAN))
        assertEquals("Встреча",c.eventText(at("2026-03-01T18:00",c.zone.id),BoardLanguage.RUSSIAN))
        assertEquals("Очрашу",c.eventText(at("2026-03-01T19:59",c.zone.id),BoardLanguage.TATAR))
        assertNull(c.eventText(at("2026-03-01T20:00",c.zone.id),BoardLanguage.RUSSIAN))
    }
    @Test fun incompatibleMosqueZonePeriodAndMissingScheduleDoNotExecute() {
        val c = calendar()
        assertTrue(c.active(at("2026-10-26T12:00"),ScheduleState()))
        assertTrue(iqamahStatus(at("2026-10-26T12:00"), c.iqamah, ScheduleState(), false) is IqamahStatus.Idle)
        assertNotNull(c.compatibility(schedules("Asia/Yekaterinburg")))
        val other = ScheduleJson.parse(ScheduleFixtures.json().apply { addProperty("schemaVersion",2);addProperty("mosqueId","another-mosque");addProperty("mosqueName","Другая мечеть") }.toString().toByteArray())
        assertNotNull(c.compatibility(ScheduleState(other)))
        assertFalse(c.active(at("2027-01-01T00:00"),schedules()))
    }
    @Test fun malformedDuplicateHugeAndUnknownFieldsAreRejected() {
        rejected(json().replace("\"revision\":\"1\"", "\"revision\":\"1\",\"revision\":\"2\""))
        rejected(json().replace("Europe/Moscow","Imaginary/City"))
        rejected(json(rules=rule("ASR","FIXED","\"time\":\"25:01\"")))
        rejected(json(rules=rule()+","+rule()))
        rejected(json(extra=",\"approved\":true"))
        rejected(json().replace("2026-12-31","2026-02-30"))
        rejected(" ".repeat(MosqueCalendarJson.MAX_BYTES+1))
    }
    @Test fun atomicReplacementRollbackRecoveryAndFailurePreserveWholeCalendar() = runBlocking {
        class Memory: ScheduleStorage { var bytes: ByteArray? = null; var fail = false; override fun read()=bytes; override fun write(bytes: ByteArray) { if(fail) error("disk full"); this.bytes=bytes } }
        val storage=Memory(); val repo=MosqueCalendarRepository(storage); repo.load()
        val full=calendar(json(rules=rule(),extra=",\"events\":[{\"id\":\"a\",\"title\":\"Встреча\",\"from\":\"2026-10-26\"}]")); repo.apply(full)
        storage.fail=true; try { repo.apply(calendar()); fail() } catch (_: Exception) {}
        assertEquals(1,repo.state.value.current!!.events.size)
        storage.fail=false; repo.apply(calendar()); assertTrue(repo.state.value.current!!.events.isEmpty()); assertFalse(repo.state.value.current!!.iqamah.enabled)
        repo.rollback(); assertEquals(full.json,repo.state.value.current!!.json)
        val restored=MosqueCalendarRepository(storage);restored.load();assertEquals(full.json,restored.state.value.current!!.json)
        val envelope=JsonParser.parseString(storage.bytes!!.toString(Charsets.UTF_8)).asJsonObject
        envelope.getAsJsonObject("current").addProperty("sha256","damaged");storage.bytes=envelope.toString().toByteArray()
        restored.load();assertNotNull(restored.state.value.current);assertNotNull(restored.state.value.problem)
    }
    @Test fun currentYearMeaningWinsOverReplacedCalendarAndFutureYearRetainsCurrentPeriod() {
        val c=calendar(json(rules=rule("FAJR")))
        val known=schedules().current!!;val unknown=schedules(fajr="UNCONFIRMED").current!!
        c.validateAgainst(ScheduleState(known,unknown,true))
        val next=ScheduleJson.parse(ScheduleFixtures.json(2027,meaning="PRAYER_START").toString().toByteArray())
        val years=ScheduleState(next,known,true)
        assertNull(c.compatibility(years));assertTrue(c.active(at("2026-10-26T06:00"),years));c.validateAgainst(years)
    }
    @Test fun publishedDemoFilesMatchAndAreNeverOfficial() {
        val root=java.io.File("../data/examples").takeIf { it.isDirectory } ?: java.io.File("data/examples")
        val c=MosqueCalendarJson.parse(java.io.File(root,"mosque-calendar-DEMO.json").readBytes())
        val s=ScheduleJson.parse(java.io.File(root,"prayer-schedule-DEMO-2026.json").readBytes())
        assertTrue(c.demo);assertTrue(isTestSource(s.metadata.source));assertFalse(s.metadata.approved)
        c.validateAgainst(ScheduleState(s,loaded=true))
    }

}
