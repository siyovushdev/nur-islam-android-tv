package dev.nurislam.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nurislam.tv.R
import dev.nurislam.tv.domain.*
import dev.nurislam.tv.model.*
import dev.nurislam.tv.settings.BoardLanguage
import java.time.Instant
import java.time.LocalDate

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
) {
    // TextAutoSize keeps measurement state. Recreate only the visual subtree when
    // changing typeface/palette; keep the clock, calendar and settings outside it.
    key(LocalBoardAppearance.current, LocalBoardSettings.current.textScale, landscape) {
    BoxWithConstraints(Modifier.fillMaxSize().testTag("prayer-board")) {
        val scale = if (landscape) minOf(maxWidth.value / 1920f, maxHeight.value / 1080f)
            else minOf(maxWidth.value / 1080f, maxHeight.value / 1920f)
        val colors = LocalBoardAppearance.current
        if (colors.ornate) ArchitecturalBackground()
        else Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(colors.Emerald, colors.Background, colors.Deep))))
        val date = minute.atZone(MoscowZone).toLocalDate()
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
                    ClockAndDate(minute, date, scale, Modifier.weight(.32f))
                    NextPrayerCard(next, now, day, date, scale, Modifier.weight(.32f), status)
                    Footer(demo, scale, Modifier.weight(.10f), notice)
                }
                ScheduleTable(day, next, scale, Modifier.weight(1f).fillMaxHeight().padding(vertical = (26 * scale).dp))
            }
        } else {
            Column(safe, horizontalAlignment = Alignment.CenterHorizontally) {
                Header(scale, Modifier.weight(.15f))
                ClockAndDate(minute, date, scale, Modifier.weight(.215f))
                NextPrayerCard(next, now, day, date, scale, Modifier.weight(.19f), status)
                Spacer(Modifier.weight(.015f))
                ScheduleTable(day, next, scale, Modifier.weight(.38f))
                Footer(demo, scale, Modifier.weight(.05f), notice)
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
private fun ClockAndDate(minute: Instant, date: LocalDate, s: Float, modifier: Modifier) {
    val colors = LocalBoardAppearance.current
    val language = LocalBoardSettings.current.language
    @Composable fun Dates() {
        BoardText(if (language == BoardLanguage.RUSSIAN) formatRussianDate(date) else formatTatarDate(date), 42f, s, Modifier.fillMaxWidth().height((54 * s).dp), bold = true)
        if (language == BoardLanguage.BOTH) BoardText(formatRussianDate(date), 34f, s, Modifier.fillMaxWidth().height((46 * s).dp), color = colors.Secondary)
    }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (!colors.ornate) Dates()
        BoardText(formatClock(minute), 220f, s, Modifier.fillMaxWidth().weight(1f).testTag("clock"), digits = true)
        if (colors.ornate) Dates()
        if (colors.ornate) Ornament(Modifier.fillMaxWidth(.62f).height((46 * s).dp))
    }
}

@Composable
private fun NextPrayerCard(next: NextPrayer?, now: State<Instant>, day: DaySchedule?, date: LocalDate, s: Float, modifier: Modifier, status: PrayerStatus?) {
    val colors = LocalBoardAppearance.current
    val shape = RoundedCornerShape((BoardSpacing.PanelRadius * s).dp)
    Column(
        modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(colors.Emerald, if (colors.ornate) colors.Card else colors.Background)), shape)
            .border((if (colors.ornate) 2.5f * s else 0f).dp, if (colors.ornate) colors.Outline else Color.Transparent, shape).padding((18 * s).dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoardText(boardLanguage(stringResource(R.string.next_prayer)), 34f, s, Modifier.fillMaxWidth().weight(.20f), color = colors.Gold)
        if (next == null) {
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
            val labels = eventLabels(next.event)
            BoardText(prayerLabel(labels.first, labels.second), 76f, s,
                Modifier.fillMaxWidth().weight(.36f), bold = true)
            Row(Modifier.fillMaxWidth().weight(.44f), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    BoardText(boardLanguage(stringResource(R.string.prayer_time)), 27f, s, Modifier.fillMaxWidth().weight(.3f), color = colors.Secondary)
                    BoardText(formatTime(next.at.atZone(MoscowZone).toLocalTime()), 82f, s, Modifier.fillMaxWidth().weight(.7f).testTag("next-time"), color = colors.Gold, digits = true)
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

// This is the only board composable that reads the second-level clock state.
@Composable
private fun Countdown(now: State<Instant>, target: Instant, s: Float, modifier: Modifier) {
    val colors = LocalBoardAppearance.current
    BoardText(formatCountdown(now.value, target), 76f, s, modifier.testTag("countdown"), color = colors.Gold, digits = true)
}

@Composable
private fun ScheduleTable(day: DaySchedule?, next: NextPrayer?, s: Float, modifier: Modifier) {
    val colors = LocalBoardAppearance.current
    val shape = RoundedCornerShape((BoardSpacing.PanelRadius * s).dp)
    Column(modifier.fillMaxWidth().background(if (colors.ornate) colors.Card.copy(alpha = .92f) else Color.Transparent, shape)
        .border((1f * s).dp, if (colors.ornate) colors.Outline.copy(alpha = .55f) else Color.Transparent, shape).padding((10 * s).dp)) {
        ScheduleEvent.entries.forEachIndexed { index, event ->
            val highlight = next?.event == event && next.at.atZone(MoscowZone).toLocalDate() == day?.date
            val color = when { highlight -> if (colors.ornate) colors.Gold else colors.Deep; event.isPrayer -> colors.Text; else -> colors.Secondary }
            val rowShape = RoundedCornerShape((24 * s).dp)
            val rowModifier = if (highlight) Modifier.background(if (colors.ornate) Brush.horizontalGradient(listOf(colors.Emerald, colors.Highlight, colors.Emerald)) else Brush.linearGradient(listOf(colors.Gold, colors.Gold)), rowShape)
                .border((1.5f * s).dp, colors.Gold, rowShape) else if (!colors.ornate) Modifier.background(colors.Card.copy(alpha = .65f), rowShape).border((1f * s).dp, colors.Outline.copy(alpha = .42f), rowShape) else Modifier
            Row(Modifier.weight(1f).fillMaxWidth().padding(vertical = (if (colors.ornate) 0f else 4f * s).dp).then(rowModifier).padding(horizontal = (32 * s).dp).testTag("row-${event.name}"), verticalAlignment = Alignment.CenterVertically) {
                if (colors.ornate) {
                    EventIcon(event, color, Modifier.size((50 * s).dp))
                    Spacer(Modifier.width((32 * s).dp))
                }
                val labels = eventLabels(event)
                Column(Modifier.weight(1f).fillMaxHeight().padding(vertical = (5 * s).dp), verticalArrangement = Arrangement.Center) {
                    BoardText(stringResource(if (LocalBoardSettings.current.language == BoardLanguage.RUSSIAN) labels.second else labels.first), 36f, s, Modifier.fillMaxWidth().weight(.56f), color, bold = event.isPrayer, align = TextAlign.Start)
                    if (LocalBoardSettings.current.language == BoardLanguage.BOTH) BoardText(stringResource(labels.second), 26f, s, Modifier.fillMaxWidth().weight(.44f), color.copy(alpha = .9f), align = TextAlign.Start)
                }
                BoardText(day?.times?.get(event)?.let(::formatTime) ?: "—", 56f, s,
                    Modifier.width((202 * s).dp).fillMaxHeight(), color, digits = true, align = TextAlign.End)
            }
            if (colors.ornate && index < ScheduleEvent.entries.lastIndex) Box(Modifier.fillMaxWidth().height((1 * s).dp).background(colors.Secondary.copy(alpha = .16f)))
        }
    }
}

@Composable
private fun Footer(demo: Boolean, s: Float, modifier: Modifier, notice: String?) {
    val colors = LocalBoardAppearance.current
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (colors.ornate) Ornament(Modifier.fillMaxWidth(.4f).height((24 * s).dp))
        if (demo) BoardText(stringResource(R.string.demo_label), 23f, s,
            Modifier.fillMaxWidth().weight(1f).testTag("demo-label"), color = colors.Gold)
        if (notice != null && !demo) BoardText(notice, 22f, s, Modifier.fillMaxWidth().weight(1f), color = colors.Secondary)
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

fun eventLabels(event: ScheduleEvent): Pair<Int, Int> = when (event) {
    ScheduleEvent.SUHUR_END -> R.string.suhur_tt to R.string.suhur_ru
    ScheduleEvent.FAJR_IN_MOSQUE -> R.string.fajr_tt to R.string.fajr_ru
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
