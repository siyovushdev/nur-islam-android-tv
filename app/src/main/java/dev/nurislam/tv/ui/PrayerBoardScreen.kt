package dev.nurislam.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.CompositionLocalProvider
import dev.nurislam.tv.data.FajrMeaning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nurislam.tv.R
import dev.nurislam.tv.domain.*
import dev.nurislam.tv.iqamah.*
import dev.nurislam.tv.model.*
import dev.nurislam.tv.settings.BoardLanguage
import java.time.Instant
import java.time.LocalDate

val LocalBoardZone = staticCompositionLocalOf { MoscowZone }
private val LocalFajrMeaning = staticCompositionLocalOf { FajrMeaning.UNCONFIRMED }

// A visual marker only: Selected would expose a read-only row as a checkable control.
internal val BoardRowHighlighted = SemanticsPropertyKey<Boolean>("BoardRowHighlighted")

@Composable
fun PrayerBoardScreen(
    minute: Instant,
    now: State<Instant>,
    day: DaySchedule?,
    next: NextPrayer?,
    landscape: Boolean,
    demo: Boolean,
    status: PrayerStatus? = null,
    notice: String? = null,
    iqamah: IqamahStatus? = null,
    ramadan: String? = null,
    fajrMeaning: FajrMeaning = FajrMeaning.UNCONFIRMED,
) {
    // TextAutoSize keeps measurement state. Recreate only the visual subtree when
    // changing typeface/palette; keep the clock, calendar and settings outside it.
    CompositionLocalProvider(LocalFajrMeaning provides fajrMeaning) {
    key(LocalBoardAppearance.current, LocalBoardSettings.current.textScale, landscape) {
    Column(Modifier.fillMaxSize()) {
    val boundaryWarning = when (iqamah) {
        is IqamahStatus.Waiting -> iqamah.event.boundaryWarning
        is IqamahStatus.Now -> iqamah.event.boundaryWarning
        is IqamahStatus.Idle -> iqamah.upcoming?.boundaryWarning
        else -> null
    }
    val diagnostic = (iqamah as? IqamahStatus.Conflict)?.detail ?: boundaryWarning
    if (diagnostic != null) BoxWithConstraints(Modifier.fillMaxWidth().background(LocalBoardAppearance.current.Deep)) {
        Text("Проверьте правила икамата в настройках", color = LocalBoardAppearance.current.Gold,
            fontSize = if (maxWidth < 600.dp) 14.sp else 22.sp,
            modifier = Modifier.fillMaxWidth().padding(12.dp).testTag(if (iqamah is IqamahStatus.Conflict) "iqamah-conflict" else "iqamah-boundary-warning"))
    }
    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().testTag("prayer-board")) {
        val scale = if (landscape) minOf(maxWidth.value / 1920f, maxHeight.value / 1080f)
            else minOf(maxWidth.value / 1080f, maxHeight.value / 1920f)
        val colors = LocalBoardAppearance.current
        if (colors.ornate) ArchitecturalBackground()
        else Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(colors.Emerald, colors.Background, colors.Deep))))
        val date = minute.atZone(LocalBoardZone.current).toLocalDate()
        // The layout insets are symmetric; the painted foreground is not. Measured
        // at the design sizes after subtracting the unchanged architectural background:
        // portrait Elegant 82/112px, Classic 82/179px; landscape 48/69px and 48/83px.
        // Half the difference centers the normal board without changing any block size.
        val opticalOffset = if (landscape) { if (colors.ornate) 10.5f else 17.5f }
            else { if (colors.ornate) 15f else 48.5f }
        val safe = Modifier.fillMaxSize().padding(horizontal = maxWidth * BoardSpacing.SafeInset, vertical = maxHeight * .04f)
            .offset(y = (opticalOffset * scale).dp).testTag("board-content")
        if (landscape) {
            Row(safe, horizontalArrangement = Arrangement.spacedBy((48 * scale).dp)) {
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Header(scale, Modifier.weight(.26f))
                    ClockAndDate(minute, now, date, scale, Modifier.weight(.32f))
                    NextPrayerCard(next, now, day, date, scale, Modifier.weight(.32f), status, iqamah)
                    Footer(demo, scale, Modifier.weight(.10f), notice, ramadan)
                }
                ScheduleTable(day, next, iqamah, scale, Modifier.weight(1f).fillMaxHeight().padding(vertical = (26 * scale).dp))
            }
        } else {
            Column(safe, horizontalAlignment = Alignment.CenterHorizontally) {
                Header(scale, Modifier.weight(.15f))
                ClockAndDate(minute, now, date, scale, Modifier.weight(.215f))
                NextPrayerCard(next, now, day, date, scale, Modifier.weight(.19f), status, iqamah)
                Spacer(Modifier.weight(.015f))
                ScheduleTable(day, next, iqamah, scale, Modifier.weight(.38f))
                Footer(demo, scale, Modifier.weight(.05f), notice, ramadan)
            }
        }
        if (iqamah is IqamahStatus.Now && iqamah.dim) {
            Column(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .86f)).padding(24.dp).testTag("iqamah-dim"),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                BoardText(if (iqamah.event.demo) "ТЕСТОВЫЙ ИКАМАТ" else "ИКАМАТ", 120f, scale, Modifier.fillMaxWidth().height((180 * scale).dp), colors.Gold, bold = true)
                BoardText(iqamah.event.prayer.title, 76f, scale, Modifier.fillMaxWidth().height((120 * scale).dp), colors.Text, bold = true)
                BoardText("Настройки доступны с пульта / кнопки телефона", 26f, scale, Modifier.fillMaxWidth().height((60 * scale).dp), colors.Secondary)
            }
        }
    }
    }
    }
    }
}

@Composable
private fun Header(s: Float, modifier: Modifier) {
    val colors = LocalBoardAppearance.current
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Crescent(Modifier.height((82 * s).dp).width((180 * s).dp))
        BoardText(LocalBoardSettings.current.mosqueName, 92f, s, Modifier.fillMaxWidth().weight(1f), color = if (colors.ornate) colors.Text else colors.Gold, bold = true)
        BoardText(LocalBoardSettings.current.location, 36f, s, Modifier.fillMaxWidth().height((48 * s).dp), color = colors.Secondary)
    }
}

@Composable
private fun ClockAndDate(minute: Instant, now: State<Instant>, date: LocalDate, s: Float, modifier: Modifier) {
    val colors = LocalBoardAppearance.current
    val language = LocalBoardSettings.current.language
    @Composable fun Dates() {
        BoardText(if (language == BoardLanguage.RUSSIAN) formatRussianDate(date) else formatTatarDate(date), 42f, s, Modifier.fillMaxWidth().height((54 * s).dp), bold = true)
        if (language == BoardLanguage.BOTH) BoardText(formatRussianDate(date), 34f, s, Modifier.fillMaxWidth().height((46 * s).dp), color = colors.Secondary)
    }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (!colors.ornate) Dates()
        CurrentClock(formatClock(minute, LocalBoardZone.current), now, s, Modifier.fillMaxWidth().weight(1f).testTag("clock"))
        if (colors.ornate) Dates()
        if (colors.ornate) Ornament(Modifier.fillMaxWidth(.62f).height((46 * s).dp))
    }
}

@Composable
private fun CurrentClock(time: String, now: State<Instant>, s: Float, modifier: Modifier) {
    val colors = LocalBoardAppearance.current
    val settings = LocalBoardSettings.current
    // Reuse the lifecycle-aware second ticker. Keep the colon's advance and the
    // complete HH:mm accessibility text in both phases; only its paint changes.
    val text = buildAnnotatedString {
        append(time)
        addStyle(SpanStyle(color = if (now.value.epochSecond % 2L == 0L) colors.Text else Color.Transparent), 2, 3)
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        BasicText(text, style = BoardDigitsStyle.copy(color = colors.Text, fontFamily = colors.fontFamily, textAlign = TextAlign.Center),
            maxLines = 1, softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 6.sp, maxFontSize = (220f * s * settings.textScale).coerceAtLeast(6f).sp, stepSize = .5.sp))
    }
}

@Composable
private fun NextPrayerCard(next: NextPrayer?, now: State<Instant>, day: DaySchedule?, date: LocalDate, s: Float, modifier: Modifier, status: PrayerStatus?, iqamah: IqamahStatus?) {
    val colors = LocalBoardAppearance.current
    val shape = RoundedCornerShape((BoardSpacing.PanelRadius * s).dp)
    Column(
        modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(colors.Emerald, if (colors.ornate) colors.Card else colors.Background)), shape)
            .border((if (colors.ornate) 2.5f * s else 0f).dp, if (colors.ornate) colors.Outline else Color.Transparent, shape).padding((18 * s).dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val event = when (iqamah) { is IqamahStatus.Waiting -> iqamah.event; is IqamahStatus.Now -> iqamah.event; is IqamahStatus.Idle -> iqamah.upcoming; else -> null }
        val activeIqamah = iqamah is IqamahStatus.Waiting || iqamah is IqamahStatus.Now
        val heading = when (iqamah) {
            is IqamahStatus.Waiting -> if (iqamah.event.demo) "До икамата · ТЕСТ" else "До икамата"
            is IqamahStatus.Now -> if (iqamah.event.demo) "ТЕСТОВЫЙ ИКАМАТ" else "ИКАМАТ"
            is IqamahStatus.Conflict -> boardLanguage(stringResource(R.string.next_prayer)) + "\nИкамат приостановлен"
            else -> boardLanguage(stringResource(R.string.next_prayer)) + if (event != null) "\n${if (event.demo) "ТЕСТ · " else ""}Икамат ${event.prayer.title}: ${formatTime(event.at.atZone(LocalBoardZone.current).toLocalTime())}${if (event.start == null) " · начало не подтверждено" else ""}" else if (iqamah != null) "\n${(iqamah as IqamahStatus.Idle).detail}" else ""
        }
        BoardText(heading, if (iqamah == null) 34f else if (activeIqamah) 36f else 30f, s, Modifier.fillMaxWidth().weight(.20f).testTag("iqamah-heading"), color = colors.Gold, lines = if (iqamah == null || activeIqamah) 1 else 2)
        if (activeIqamah && event != null) {
            BoardText(event.prayer.title, 76f, s, Modifier.fillMaxWidth().weight(.36f), bold = true)
            Row(Modifier.fillMaxWidth().weight(.44f), verticalAlignment = Alignment.CenterVertically) {
                BoardText(formatTime(event.at.atZone(LocalBoardZone.current).toLocalTime()), 76f, s, Modifier.weight(1f).fillMaxHeight().testTag("iqamah-time"), color = colors.Gold, digits = true)
                if (iqamah is IqamahStatus.Waiting) Countdown(now, event.at, s, Modifier.weight(1.25f).fillMaxHeight())
                else BoardText("Икамат", 72f, s, Modifier.weight(1.25f).fillMaxHeight().testTag("iqamah-arrival"), color = colors.Gold, bold = true)
            }
        } else if (next == null) {
            val title = when {
                status is PrayerStatus.FajrUnconfirmed -> statusLabel(R.string.fajr_uncertain_tt, R.string.fajr_uncertain)
                day == null -> statusLabel(R.string.unavailable_tt, R.string.unavailable)
                else -> statusLabel(R.string.no_next_tt, R.string.no_next)
            }
            val detail = when {
                status is PrayerStatus.FajrUnconfirmed -> statusLabel(R.string.fajr_uncertain_detail_tt, R.string.fajr_uncertain_detail) + " · ${status.date}"
                day == null -> statusLabel(R.string.unavailable_detail_tt, R.string.unavailable_detail, date.toString())
                else -> statusLabel(R.string.no_next_detail_tt, R.string.no_next_detail)
            }
            BoardText(title, 43f, s, Modifier.fillMaxWidth().weight(.44f).testTag("schedule-status"), bold = true, lines = 2)
            BoardText(detail, 27f, s, Modifier.fillMaxWidth().weight(.36f), color = colors.Secondary, lines = 2)
        } else {
            val labels = eventLabels(next.event, LocalFajrMeaning.current)
            BoardText(prayerLabel(labels.first, labels.second), 76f, s,
                Modifier.fillMaxWidth().weight(.36f), bold = true)
            Row(Modifier.fillMaxWidth().weight(.44f), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    BoardText(boardLanguage(stringResource(R.string.prayer_time)), 27f, s, Modifier.fillMaxWidth().weight(.3f), color = colors.Secondary)
                    BoardText(formatTime(next.at.atZone(LocalBoardZone.current).toLocalTime()), 82f, s, Modifier.fillMaxWidth().weight(.7f).testTag("next-time"), color = colors.Gold, digits = true)
                }
                Box(Modifier.width((1.3f * s).dp).fillMaxHeight(.76f).background(colors.Outline.copy(alpha = .65f)))
                Column(Modifier.weight(1.25f), horizontalAlignment = Alignment.CenterHorizontally) {
                    BoardText(boardLanguage(stringResource(R.string.remaining)), 27f, s, Modifier.fillMaxWidth().weight(.3f), color = colors.Secondary)
                    Countdown(now, next.at, s, Modifier.fillMaxWidth().weight(.7f))
                }
            }
        }
    }
}

// Only this countdown and CurrentClock read the second-level state in composition.
@Composable
private fun Countdown(now: State<Instant>, target: Instant, s: Float, modifier: Modifier) {
    val colors = LocalBoardAppearance.current
    BoardText(formatCountdown(now.value, target), 76f, s, modifier.testTag("countdown"), color = colors.Gold, digits = true)
}

@Composable
private fun ScheduleTable(day: DaySchedule?, next: NextPrayer?, iqamah: IqamahStatus?, s: Float, modifier: Modifier) {
    val colors = LocalBoardAppearance.current
    val shape = RoundedCornerShape((BoardSpacing.PanelRadius * s).dp)
    val active = when (iqamah) { is IqamahStatus.Waiting -> iqamah.event; is IqamahStatus.Now -> iqamah.event; else -> null }
    // Jumuah and a previous day's arrival have their own explicit iqamah row.
    // Never relabel a JSON Dhuhr time as Jumuah or highlight today's time for yesterday.
    val separate = active?.takeIf { it.prayer == IqamahPrayer.JUMUAH || it.at.atZone(LocalBoardZone.current).toLocalDate() != day?.date }
    val rows: List<ScheduleEvent?> = ScheduleEvent.entries.flatMap { event ->
        if (separate?.prayer?.event == event) listOf(event, null) else listOf(event)
    }
    val compact = rows.size > ScheduleEvent.entries.size
    Column(modifier.fillMaxWidth().background(if (colors.ornate) colors.Card.copy(alpha = .92f) else Color.Transparent, shape)
        .border((1f * s).dp, if (colors.ornate) colors.Outline.copy(alpha = .55f) else Color.Transparent, shape).padding((10 * s).dp)) {
        rows.forEachIndexed { index, calendarEvent ->
            val extra = if (calendarEvent == null) separate else null
            val event = calendarEvent ?: requireNotNull(extra).prayer.event
            val highlight = if (active != null) extra != null || (separate == null && active.prayer.event == event)
                else next?.event == event && next.at.atZone(LocalBoardZone.current).toLocalDate() == day?.date
            val labels = eventLabels(event, LocalFajrMeaning.current)
            val time = extra?.at?.atZone(LocalBoardZone.current)?.toLocalTime()?.let(::formatTime) ?: day?.times?.get(event)?.let(::formatTime) ?: "—"
            val description = (if (extra != null) "${extra.prayer.title} · Икамат · ${extra.at.atZone(LocalBoardZone.current).toLocalDate()}" else prayerLabel(labels.first, labels.second)) + "; $time" + if (highlight) "; выделен" else ""
            val color = when { highlight -> if (colors.ornate) colors.Gold else colors.Deep; event.isPrayer -> colors.Text; else -> colors.Secondary }
            val rowShape = RoundedCornerShape((24 * s).dp)
            val rowModifier = if (highlight) Modifier.background(if (colors.ornate) Brush.horizontalGradient(listOf(colors.Emerald, colors.Highlight, colors.Emerald)) else Brush.linearGradient(listOf(colors.Gold, colors.Gold)), rowShape)
                .border((1.5f * s).dp, colors.Gold, rowShape) else if (!colors.ornate) Modifier.background(colors.Card.copy(alpha = .65f), rowShape).border((1f * s).dp, colors.Outline.copy(alpha = .42f), rowShape) else Modifier
            Row(Modifier.weight(1f).fillMaxWidth().padding(vertical = (if (colors.ornate) 0f else (if (compact) 2f else 4f) * s).dp).then(rowModifier).padding(horizontal = (32 * s).dp).testTag(if (extra != null) "row-${extra.prayer.name}" + if (extra.prayer != IqamahPrayer.JUMUAH) "-iqamah" else "" else "row-${event.name}").semantics { this[BoardRowHighlighted] = highlight; contentDescription = description }, verticalAlignment = Alignment.CenterVertically) {
                if (colors.ornate) {
                    EventIcon(event, color, Modifier.size((50 * s).dp))
                    Spacer(Modifier.width((32 * s).dp))
                }
                Column(Modifier.weight(1f).fillMaxHeight().padding(vertical = (if (compact) 0f else 5 * s).dp), verticalArrangement = Arrangement.Center) {
                    BoardText(if (extra != null) "${extra.prayer.title} · Икамат" else stringResource(if (LocalBoardSettings.current.language == BoardLanguage.RUSSIAN) labels.second else labels.first), 36f, s, Modifier.fillMaxWidth().weight(.56f), color, bold = event.isPrayer, align = TextAlign.Start)
                    if (extra != null || LocalBoardSettings.current.language == BoardLanguage.BOTH) BoardText(if (extra != null) extra.at.atZone(LocalBoardZone.current).toLocalDate().toString() else stringResource(labels.second), 26f, s, Modifier.fillMaxWidth().weight(.44f), color.copy(alpha = .9f), align = TextAlign.Start)
                }
                BoardText(time, 56f, s,
                    Modifier.width((202 * s).dp).fillMaxHeight(), color, digits = true, align = TextAlign.End)
            }
            if (colors.ornate && index < rows.lastIndex) Box(Modifier.fillMaxWidth().height((1 * s).dp).background(colors.Secondary.copy(alpha = .16f)))
        }
    }
}

@Composable
private fun Footer(demo: Boolean, s: Float, modifier: Modifier, notice: String?, ramadan: String?) {
    val colors = LocalBoardAppearance.current
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (ramadan != null) BoardText(ramadan, 24f, s, Modifier.fillMaxWidth().weight(1.4f).testTag("ramadan-status"), color = colors.Gold, lines = if (LocalBoardSettings.current.language == BoardLanguage.BOTH) 2 else 1)
        else if (colors.ornate) Ornament(Modifier.fillMaxWidth(.4f).height((24 * s).dp))
        if (demo) BoardText(stringResource(R.string.demo_label), 23f, s,
            Modifier.fillMaxWidth().weight(if (ramadan != null) .6f else 1f).testTag("demo-label"), color = colors.Gold)
        if (notice != null && !demo) BoardText(notice, 22f, s, Modifier.fillMaxWidth().weight(if (ramadan != null) .6f else 1f).testTag("board-notice"), color = colors.Secondary)
    }
}

@Composable
private fun BoardText(text: String, size: Float, s: Float, modifier: Modifier, color: Color = Color.Unspecified,
    bold: Boolean = false, digits: Boolean = false, align: TextAlign = TextAlign.Center, lines: Int = 1) {
    val colors = LocalBoardAppearance.current
    val settings = LocalBoardSettings.current
    val resolvedColor = if (color == Color.Unspecified) colors.Text else color
    Box(modifier, contentAlignment = when (align) { TextAlign.Start -> Alignment.CenterStart; TextAlign.End -> Alignment.CenterEnd; else -> Alignment.Center }) {
        BasicText(text, style = (if (digits) BoardDigitsStyle else BoardTextStyle).copy(color = resolvedColor, fontFamily = colors.fontFamily,
            fontWeight = if (bold || digits) FontWeight.Bold else FontWeight.Normal, textAlign = align),
            maxLines = lines, softWrap = lines > 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 6.sp, maxFontSize = (size * s * settings.textScale).coerceAtLeast(6f).sp, stepSize = .5.sp))
    }
}

fun eventLabels(event: ScheduleEvent, fajrMeaning: FajrMeaning = FajrMeaning.UNCONFIRMED): Pair<Int, Int> = when (event) {
    ScheduleEvent.SUHUR_END -> R.string.suhur_tt to R.string.suhur_ru
    ScheduleEvent.FAJR_IN_MOSQUE -> R.string.fajr_tt to if (fajrMeaning == FajrMeaning.PRAYER_START) R.string.fajr_start_ru else R.string.fajr_ru
    ScheduleEvent.SUNRISE -> R.string.sunrise_tt to R.string.sunrise_ru
    ScheduleEvent.ZAWAL -> R.string.zawal_tt to R.string.zawal_ru
    ScheduleEvent.DHUHR -> R.string.dhuhr_tt to R.string.dhuhr_ru
    ScheduleEvent.ASR -> R.string.asr_tt to R.string.asr_ru
    ScheduleEvent.MAGHRIB -> R.string.maghrib_tt to R.string.maghrib_ru
    ScheduleEvent.ISHA -> R.string.isha_tt to R.string.isha_ru
}

@Composable
private fun prayerLabel(tt: Int, ru: Int): String = when (LocalBoardSettings.current.language) {
    BoardLanguage.BOTH -> "${stringResource(tt)} · ${stringResource(ru)}"
    BoardLanguage.TATAR -> stringResource(tt)
    BoardLanguage.RUSSIAN -> stringResource(ru)
}
@Composable
private fun boardLanguage(text: String): String {
    if (!text.contains(" · ") || text.startsWith("TEST")) return text
    return when (LocalBoardSettings.current.language) {
        BoardLanguage.BOTH -> text
        BoardLanguage.TATAR -> text.substringBefore(" · ")
        BoardLanguage.RUSSIAN -> text.substringAfter(" · ")
    }
}

@Composable
private fun statusLabel(tt: Int, ru: Int, vararg args: Any): String = when (LocalBoardSettings.current.language) {
    BoardLanguage.BOTH -> "${stringResource(tt, *args)}\n${stringResource(ru, *args)}"
    BoardLanguage.TATAR -> stringResource(tt, *args)
    BoardLanguage.RUSSIAN -> stringResource(ru, *args)
}
