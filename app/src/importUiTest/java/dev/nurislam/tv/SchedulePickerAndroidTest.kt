package dev.nurislam.tv

import android.content.Intent
import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.nurislam.tv.data.ScheduleDocumentPicker
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [26, 29, 30, 31, 34, 35])
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class SchedulePickerAndroidTest {
    @Test fun realSafIntentKeepsUserConsentAndStartsOutsideRecents() {
        val app = ApplicationProvider.getApplicationContext<NurIslamApplication>()
        val mime = arrayOf("application/json", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        val intent = ScheduleDocumentPicker().createIntent(app, mime)
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.action)
        assertTrue(intent.hasCategory(Intent.CATEGORY_OPENABLE))
        assertArrayEquals(mime, intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES))
        assertEquals(ScheduleDocumentPicker.initialDirectory(), intent.getParcelableExtra<android.net.Uri>(DocumentsContract.EXTRA_INITIAL_URI))
        assertNull(intent.component) // Let Android choose a supported system provider.
    }
}
