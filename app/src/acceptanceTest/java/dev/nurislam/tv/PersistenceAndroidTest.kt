package dev.nurislam.tv

import android.util.AtomicFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.nurislam.tv.data.*
import dev.nurislam.tv.domain.DisplayMode
import dev.nurislam.tv.settings.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PersistenceAndroidTest {
    private val app get() = ApplicationProvider.getApplicationContext<NurIslamApplication>()
    @Test fun settingsReopenPreservesEveryFieldAndLabelsDoNotChangeCalendarZone() = runBlocking {
        val preferences = app.getSharedPreferences("settings-test", 0)
        val expected = AppSettings(BoardThemeId.CLASSIC, DisplayMode.PORTRAIT_ROTATE_CCW, 1.15f, BoardLanguage.TATAR, "Нур Әль-Ислам", "Казан")
        SettingsRepository(preferences).update { expected }
        assertEquals(expected, SettingsRepository(preferences).state.value)
        assertEquals("Europe/Moscow", dev.nurislam.tv.model.MoscowZone.id)
    }
    @Test fun invalidPersistedSettingsRecoverToSafeDefaults() {
        val preferences = app.getSharedPreferences("settings-invalid-test", 0)
        preferences.edit().putString("theme", "UNKNOWN").putFloat("scale", Float.NaN).commit()
        assertEquals(AppSettings(), SettingsRepository(preferences).state.value)
    }
    @Test fun interruptedAtomicWriteKeepsLastCommittedGeneration() {
        val file = File(app.filesDir, "atomic-interruption-test.json")
        val storage = AtomicScheduleStorage(file)
        val before = "last committed file".toByteArray()
        storage.write(before)
        // Simulate process death after writing part of .new but before finishWrite.
        AtomicFile(file).startWrite().use { it.write("partial".toByteArray()); it.fd.sync() }
        assertArrayEquals(before, AtomicScheduleStorage(file).read())
    }
    @Test fun firstInterruptedWriteDoesNotExposePartialData() {
        val file = File(app.filesDir, "atomic-first-write-test.json")
        AtomicFile(file).startWrite().use { it.write("partial".toByteArray()) }
        assertNull(AtomicScheduleStorage(file).read())
    }
    @Test fun noScheduleOrSettingsRequireNetworkPermission() {
        val info = app.packageManager.getPackageInfo(app.packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
        assertTrue(info.requestedPermissions.orEmpty().none { it == "android.permission.INTERNET" || it == "android.permission.ACCESS_NETWORK_STATE" })
    }
}
