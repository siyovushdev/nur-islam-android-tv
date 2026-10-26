package dev.nurislam.tv

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.nurislam.tv.data.AtomicScheduleStorage
import dev.nurislam.tv.iqamah.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** Framework AtomicFile + real local files, not a physical reboot/hardware acceptance. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [26, 29, 30, 31, 34, 35])
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class IqamahPersistenceAndroidTest {
    @Test fun savedRulesReloadAndRollbackUsingFrameworkStorage() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<NurIslamApplication>()
        val file = File(app.filesDir, "iqamah-storage-regression.json")
        val repo = IqamahRepository(AtomicScheduleStorage(file)); repo.load()
        val first = IqamahConfig(true, false, "TEST ONLY", rules = listOf(IqamahRule(IqamahPrayer.MAGHRIB, LocalDate.of(2026, 10, 24), mode = IqamahMode.FIXED, value = 1100)))
        repo.save(first); repo.save(first.copy(dimMinutes = 4))
        val restart = IqamahRepository(AtomicScheduleStorage(file)); restart.load()
        assertEquals(4, restart.state.value.config.dimMinutes)
        restart.rollback()
        val reloaded = IqamahRepository(AtomicScheduleStorage(file)); reloaded.load()
        assertEquals(first, reloaded.state.value.config)
        assertNull(reloaded.state.value.problem)
    }
}
