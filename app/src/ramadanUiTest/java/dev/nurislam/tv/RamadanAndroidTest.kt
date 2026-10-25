package dev.nurislam.tv

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.nurislam.tv.data.*
import dev.nurislam.tv.ramadan.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(sdk = [26, 29, 30, 31, 34, 35])
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class RamadanAndroidTest {
    @Test fun frameworkAtomicStorageRestartRollbackAndScheduleReplacementAreIndependent() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<NurIslamApplication>()
        val storage = AtomicScheduleStorage(File(app.filesDir, "ramadan-android-test.json"))
        val c = RamadanConfig(true, listOf(RamadanDates(2026, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 2), "Контрольный протокол unit", true)))
        val repo = RamadanRepository(storage); repo.load(); repo.save(c); repo.save(c.copy(enabled = false))
        app.schedules.load(); app.schedules.apply(ScheduleFixtures.schedule())
        val restart = RamadanRepository(storage); restart.load(); restart.rollback()
        assertEquals(c, restart.state.value.config)
        assertNull(restart.state.value.problem)
        assertEquals(365, app.schedules.state.value.current!!.days.size)
    }
}
