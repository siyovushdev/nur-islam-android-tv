package dev.nurislam.tv

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.nurislam.tv.data.*
import dev.nurislam.tv.domain.DisplayMode
import dev.nurislam.tv.model.ScheduleEvent
import dev.nurislam.tv.ramadan.*
import dev.nurislam.tv.settings.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.LocalDate

/** Real emulator instrumentation. No HOME/policy/provisioning operations in this test class. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class NativeIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as NurIslamApplication
    private val vm get() = androidx.lifecycle.ViewModelProvider(compose.activity)[BoardViewModel::class.java]
    private fun key(k: Key, n: Int = 1) { compose.onRoot().performKeyInput { repeat(n) { pressKey(k) } } }
    private fun waitIo() { compose.waitUntil(20_000) { !vm.busy.value }; compose.waitForIdle() }
    private fun board() {
        compose.waitUntil(10_000) { app.schedules.state.value.loaded && app.ramadan.state.value.loaded }
        if (compose.onAllNodesWithText("Пропустить и открыть табло").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithText("Пропустить и открыть табло").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("prayer-board").fetchSemanticsNodes().isNotEmpty() }
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val roles = app.getSystemService(android.app.role.RoleManager::class.java)
            File(app.getExternalFilesDir(null), "qa-home-capability.json").writeText(com.google.gson.JsonObject().apply {
                addProperty("available", roles?.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME) == true)
                addProperty("held", roles?.isRoleHeld(android.app.role.RoleManager.ROLE_HOME) == true)
            }.toString())
        }
        runBlocking { app.settings.update { it.copy(mosqueName = "ПРОВЕРКА НА ЭМУЛЯТОРЕ") } }
    }
    private fun settings() { key(Key.DirectionCenter, 5); key(Key.DirectionDown); key(Key.Enter); compose.onNodeWithText("Тема:", substring = true).assertExists() }
    private fun screenshot(name: String) {
        val file = File(app.getExternalFilesDir(null), "qa-screenshots/$name.png").apply { parentFile!!.mkdirs() }
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun xlsxNativeParserPreviewConfirmationExact2920ValuesAndRollback() {
        board(); runBlocking { app.settings.update { it.copy(displayMode = DisplayMode.LANDSCAPE) } }
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val inbox = File(app.getExternalFilesDir(null), "incoming-schedule.xlsx")
        val json = File(app.getExternalFilesDir(null), "incoming-schedule.json")
        val bytes = assets.open("xlsx/kazan-2026.xlsx").use { it.readBytes() }
        var checkpoints = 0
        assertThrows(java.util.concurrent.CancellationException::class.java) {
            ScheduleImport.parse(bytes) { if (++checkpoints > 1500) throw java.util.concurrent.CancellationException("native user cancellation") }
        }
        json.delete(); inbox.writeBytes(bytes)
        try {
            settings(); key(Key.DirectionDown, 7); key(Key.Enter)
            key(Key.DirectionDown, 2); key(Key.Enter); waitIo()
            compose.onNodeWithTag("import-preview").assertExists()
            compose.onNodeWithTag("import-apply").assertIsNotEnabled()
            screenshot("xlsx-unconfirmed")
            key(Key.DirectionDown); key(Key.Enter)
            compose.onNodeWithTag("import-confirmation").assertIsOn()
            key(Key.DirectionDown); key(Key.Enter); waitIo()
            val first = app.schedules.state.value.current!!
            val lines = assets.open("xlsx/kazan-2026.tsv").bufferedReader().use { it.readLines() }
            var compared = 0
            for (line in lines) {
                val cells = line.split('\t')
                val date = LocalDate.parse(cells[0])
                ScheduleEvent.entries.forEachIndexed { i, e -> assertEquals("$date/$e", cells[i + 1], first.day(date)!!.times.getValue(e).toString()); compared++ }
            }
            assertEquals(2920, compared)
            assertEquals(FajrMeaning.UNCONFIRMED, first.fajrMeaning)
            screenshot("xlsx-applied")
            compose.onNodeWithText("К расписанию").performClick()
            inbox.delete()
            val second = com.google.gson.JsonParser.parseString(first.json).asJsonObject.apply { addProperty("revision", "native-generation-2") }
            json.writeText(second.toString())
            key(Key.DirectionDown, 2); key(Key.Enter); waitIo()
            key(Key.DirectionDown); key(Key.Enter); key(Key.DirectionDown); key(Key.Enter); waitIo()
            assertEquals("native-generation-2", app.schedules.state.value.current!!.metadata.revision)
            compose.onNodeWithText("К расписанию").performClick()
            key(Key.DirectionDown, 3); key(Key.Enter); key(Key.DirectionDown); key(Key.Enter); waitIo()
            assertEquals(first.json, app.schedules.state.value.current!!.json)
            compose.onNodeWithText("Понятно").performClick()
            key(Key.Back); key(Key.Back)
            screenshot("xlsx-rollback-board")
        } finally { inbox.delete(); json.delete() }
    }
    @Test fun dpadSettingsThemesAllRotationsScalesAndRecreateKeepRowsInsideScreen() {
        board(); settings()
        val original = app.settings.state.value.theme
        key(Key.DirectionDown); key(Key.Enter); waitIo()
        assertNotEquals(original, app.settings.state.value.theme)
        key(Key.DirectionDown, 2); key(Key.Enter); waitIo()
        val scale = app.settings.state.value.textScale
        key(Key.Back); compose.activityRule.scenario.recreate()
        assertEquals(scale, app.settings.state.value.textScale)
        for (theme in BoardThemeId.entries) for (mode in listOf(DisplayMode.AUTO, DisplayMode.LANDSCAPE, DisplayMode.PORTRAIT_ROTATE_CW, DisplayMode.PORTRAIT_ROTATE_CCW)) for (size in listOf(.85f, 1f, 1.15f)) {
            runBlocking { app.settings.update { it.copy(theme = theme, displayMode = mode, textScale = size) } }
            compose.waitForIdle()
            ScheduleEvent.entries.forEach { compose.onNodeWithTag("row-${it.name}").assertIsDisplayed() }
            compose.onNodeWithTag("clock").assertIsDisplayed()
            if (size == 1f) screenshot("board-$theme-$mode")
        }
        runBlocking { app.settings.update { it.copy(displayMode = DisplayMode.AUTO, textScale = 1f) } }
    }
    @Test fun ramadanDpadConfirmationSaveAndRestartStayIndependentOfSchedule() {
        board()
        val first = Instant.now().atZone(dev.nurislam.tv.model.MoscowZone).toLocalDate().plusDays(5)
        val dates = RamadanDates(first.year, first, first.plusDays(29), "Контрольный пример эмулятора", false)
        runBlocking { app.ramadan.save(RamadanConfig(false, listOf(dates))) }
        val calendar = app.schedules.state.value.current?.json
        settings()
        compose.onNodeWithTag("admin-list").performScrollToNode(hasText("Рамадан"))
        compose.onNodeWithText("Рамадан").performClick()
        key(Key.Enter); key(Key.DirectionDown, 3); key(Key.Enter)
        key(Key.DirectionDown, 3); key(Key.Enter)
        compose.onNodeWithText("Даты утверждены мечетью").assertIsOn()
        key(Key.DirectionDown, 2); key(Key.Enter)
        key(Key.DirectionUp); key(Key.Enter); key(Key.DirectionDown); key(Key.Enter); waitIo()
        assertTrue(app.ramadan.state.value.config.enabled)
        compose.onNodeWithText("Вернуться к табло").performClick()
        compose.onNodeWithTag("ramadan-status").assertExists()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("ramadan-status").assertExists()
        assertEquals(calendar, app.schedules.state.value.current?.json)
        screenshot("ramadan-restored")
    }
}
