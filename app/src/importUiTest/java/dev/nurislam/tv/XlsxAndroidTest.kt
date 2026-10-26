package dev.nurislam.tv

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.nurislam.tv.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [26, 29, 30, 31, 34, 35])
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class XlsxAndroidTest {
    @Test fun nativeXmlParserAndAtomicStoragePreserveAllDatesAndValues() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<NurIslamApplication>()
        val storage = AtomicScheduleStorage(File(app.filesDir, "xlsx-native-test.json"))
        val repo = LocalScheduleRepository(storage); repo.load()
        val candidate = ScheduleXlsx.parse(ScheduleXlsxTest.source(), BuildConfig.PHONE_PREVIEW)
        repo.apply(ScheduleImport.confirmExcel(candidate))
        val restart = LocalScheduleRepository(storage); restart.load()
        assertEquals(candidate.days, restart.state.value.current!!.days)
        assertEquals(candidate.fajrMeaning, restart.state.value.current!!.fajrMeaning)
        assertNull(restart.state.value.problem)
    }
}
