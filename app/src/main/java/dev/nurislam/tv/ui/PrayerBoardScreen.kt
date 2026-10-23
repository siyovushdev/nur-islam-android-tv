package dev.nurislam.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
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
    frozen: Boolean = false,
    debug: Boolean = false,
) {
    BoxWithConstraints(Modifier.fillMaxSize().testTag("prayer-board")) {
        val scale = if (landscape) minOf(maxWidth.value / 1920f, maxHeight.value / 1080f)
            else minOf(maxWidth.value / 1080f, maxHeight.value / 1920f)
        ArchitecturalBackground()
        val date = minute.atZone(MoscowZone).toLocalDate()
        val safe = Modifier.fillMaxSize().padding(horizontal = maxWidth * BoardSpacing.SafeInset, vertical = maxHeight * .04f)
        if (landscape) {
            Row(safe, horizontalArrangement = Arrangement.spacedBy((48 * scale).dp)) {
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Header(scale, Modifier.weight(.26f))
                    ClockAndDate(minute, date, scale, Modifier.weight(.32f))
                    NextPrayerCard(next, now, day, date, scale, Modifier.weight(.32f))
                    Footer(demo, frozen, debug, scale, Modifier.weight(.10f))
                }
                ScheduleTable(day, next, scale, Modifier.weight(1f).fillMaxHeight().padding(vertical = (26 * scale).dp))
            }
        } else {
            Column(safe, horizontalAlignment = Alignment.CenterHorizontally) {
                Header(scale, Modifier.weight(.15f))
                ClockAndDate(minute, date, scale, Modifier.weight(.215f))
                NextPrayerCard(next, now, day, date, scale, Modifier.weight(.19f))
                Spacer(Modifier.weight(.015f))
                ScheduleTable(day, next, scale, Modifier.weight(.38f))
                Footer(demo, frozen, debug, scale, Modifier.weight(.05f))
            }
        }
    }
}

@Composable
private fun Header(s: Float, modifier: Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Crescent(Modifier.height((82 * s).dp).width((180 * s).dp))
        BoardText(stringResource(R.string.app_name), 92f, s, Modifier.fillMaxWidth().weight(1f), bold = true)
        BoardText(stringResource(R.string.city), 36f, s, Modifier.fillMaxWidth().height((48 * s).dp), color = BoardColors.Secondary)
    }
}

@Composable
private fun ClockAndDate(minute: Instant, date: LocalDate, s: Float, modifier: Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        BoardText(formatClock(minute), 220f, s, Modifier.fillMaxWidth().weight(1f).testTag("clock"), digits = true)
        BoardText(formatTatarDate(date), 42f, s, Modifier.fillMaxWidth().height((54 * s).dp), bold = true)
        BoardText(formatRussianDate(date), 34f, s, Modifier.fillMaxWidth().height((46 * s).dp), color = BoardColors.Secondary)
        Ornament(Modifier.fillMaxWidth(.62f).height((46 * s).dp))
    }
}

@Composable
private fun NextPrayerCard(next: NextPrayer?, now: State<Instant>, day: DaySchedule?, date: LocalDate, s: Float, modifier: Modifier) {
    val shape = RoundedCornerShape((BoardSpacing.PanelRadius * s).dp)
    Column(
        modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(BoardColors.Emerald, BoardColors.Card)), shape)
            .border((2.5f * s).dp, BoardColors.Outline, shape).padding((18 * s).dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BoardText(stringResource(R.string.next_prayer), 34f, s, Modifier.fillMaxWidth().weight(.20f), color = BoardColors.Gold)
        if (next == null) {
            BoardText(stringResource(if (day == null) R.string.unavailable else R.string.no_next), 43f, s,
                Modifier.fillMaxWidth().weight(.44f).testTag("schedule-status"), bold = true)
            BoardText(if (day == null) stringResource(R.string.unavailable_detail, date.toString()) else stringResource(R.string.no_next_detail),
                27f, s, Modifier.fillMaxWidth().weight(.36f), color = BoardColors.Secondary)
        } else {
            val labels = eventLabels(next.event)
            BoardText("${stringResource(labels.first)} · ${stringResource(labels.second)}", 76f, s,
                Modifier.fillMaxWidth().weight(.36f), bold = true)
            Row(Modifier.fillMaxWidth().weight(.44f), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    BoardText(stringResource(R.string.prayer_time), 27f, s, Modifier.fillMaxWidth().weight(.3f), color = BoardColors.Secondary)
                    BoardText(formatTime(next.at.atZone(MoscowZone).toLocalTime()), 82f, s, Modifier.fillMaxWidth().weight(.7f).testTag("next-time"), color = BoardColors.Gold, digits = true)
                }
                Box(Modifier.width((1.3f * s).dp).fillMaxHeight(.76f).background(BoardColors.Outline.copy(alpha = .65f)))
                Column(Modifier.weight(1.25f), horizontalAlignment = Alignment.CenterHorizontally) {
                    BoardText(stringResource(R.string.remaining), 27f, s, Modifier.fillMaxWidth().weight(.3f), color = BoardColors.Secondary)
                    Countdown(now, next.at, s, Modifier.fillMaxWidth().weight(.7f))
                }
            }
        }
    }
}

// This is the only board composable that reads the second-level clock state.
@Composable
private fun Countdown(now: State<Instant>, target: Instant, s: Float, modifier: Modifier) {
    BoardText(formatCountdown(now.value, target), 76f, s, modifier.testTag("countdown"), color = BoardColors.Gold, digits = true)
}

@Composable
private fun ScheduleTable(day: DaySchedule?, next: NextPrayer?, s: Float, modifier: Modifier) {
    val shape = RoundedCornerShape((BoardSpacing.PanelRadius * s).dp)
    Column(modifier.fillMaxWidth().background(BoardColors.Card.copy(alpha = .92f), shape)
        .border((1f * s).dp, BoardColors.Outline.copy(alpha = .55f), shape).padding((10 * s).dp)) {
        ScheduleEvent.entries.forEachIndexed { index, event ->
            val highlight = next?.event == event && next.at.atZone(MoscowZone).toLocalDate() == day?.date
            val color = when { highlight -> BoardColors.Gold; event.isPrayer -> BoardColors.Text; else -> BoardColors.Secondary }
            val rowShape = RoundedCornerShape((24 * s).dp)
            val rowModifier = if (highlight) Modifier.background(Brush.horizontalGradient(listOf(BoardColors.Emerald, BoardColors.Highlight, BoardColors.Emerald)), rowShape)
                .border((1.5f * s).dp, BoardColors.Gold, rowShape) else Modifier
            Row(Modifier.weight(1f).fillMaxWidth().then(rowModifier).padding(horizontal = (32 * s).dp).testTag("row-${event.name}"), verticalAlignment = Alignment.CenterVertically) {
                EventIcon(event, color, Modifier.size((50 * s).dp))
                Spacer(Modifier.width((32 * s).dp))
                val labels = eventLabels(event)
                Column(Modifier.weight(1f).fillMaxHeight().padding(vertical = (5 * s).dp), verticalArrangement = Arrangement.Center) {
                    BoardText(stringResource(labels.first), 36f, s, Modifier.fillMaxWidth().weight(.56f), color, bold = event.isPrayer, align = TextAlign.Start)
                    BoardText(stringResource(labels.second), 26f, s, Modifier.fillMaxWidth().weight(.44f), color.copy(alpha = .9f), align = TextAlign.Start)
                }
                BoardText(day?.times?.get(event)?.let(::formatTime) ?: "—", 56f, s,
                    Modifier.width((202 * s).dp).fillMaxHeight(), color, digits = true, align = TextAlign.End)
            }
            if (index < ScheduleEvent.entries.lastIndex) Box(Modifier.fillMaxWidth().height((1 * s).dp).background(BoardColors.Secondary.copy(alpha = .16f)))
        }
    }
}

@Composable
private fun Footer(demo: Boolean, frozen: Boolean, debug: Boolean, s: Float, modifier: Modifier) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Ornament(Modifier.fillMaxWidth(.4f).height((24 * s).dp))
        if (demo) BoardText(stringResource(if (frozen) R.string.debug_frozen else R.string.demo_label), 23f, s,
            Modifier.fillMaxWidth().weight(1f).testTag("demo-label"), color = BoardColors.Gold)
        if (debug) BoardText(stringResource(R.string.debug_hint), 17f, s, Modifier.fillMaxWidth().weight(.6f), color = BoardColors.Secondary)
    }
}

@Composable
private fun BoardText(text: String, size: Float, s: Float, modifier: Modifier, color: Color = BoardColors.Text,
    bold: Boolean = false, digits: Boolean = false, align: TextAlign = TextAlign.Center) {
    Box(modifier, contentAlignment = when (align) { TextAlign.Start -> Alignment.CenterStart; TextAlign.End -> Alignment.CenterEnd; else -> Alignment.Center }) {
        BasicText(text, style = (if (digits) BoardDigitsStyle else BoardTextStyle).copy(color = color,
            fontWeight = if (bold || digits) FontWeight.Bold else FontWeight.Normal, textAlign = align),
            maxLines = 1, softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 6.sp, maxFontSize = (size * s).coerceAtLeast(6f).sp, stepSize = .5.sp))
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
