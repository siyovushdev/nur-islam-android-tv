package dev.nurislam.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.tooling.preview.Preview
import dev.nurislam.tv.data.DemoScheduleFixture
import dev.nurislam.tv.domain.*
import dev.nurislam.tv.ui.*
import java.time.Instant
import java.time.LocalDate

@Preview(name = "Portrait 9:16", widthDp = 540, heightDp = 960, showBackground = true)
@Composable
private fun PortraitPreview() = PreviewBoard(false)

@Preview(name = "Landscape 16:9", widthDp = 960, heightDp = 540, showBackground = true)
@Composable
private fun LandscapePreview() = PreviewBoard(true)

@Composable
private fun PreviewBoard(landscape: Boolean) {
    val now = remember { mutableStateOf(Instant.parse("2026-10-23T11:20:00Z")) }
    val day = remember { DemoScheduleFixture().day(LocalDate.of(2026, 10, 23))!! }
    NurIslamTheme { PrayerBoardScreen(now.value, now, day, nextPrayer(now.value, listOf(day)), landscape, true) }
}
