package dev.nurislam.tv

import android.content.Intent
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.nurislam.tv.domain.formatClock
import dev.nurislam.tv.ui.rememberBoardClock
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.*

@RunWith(AndroidJUnit4::class)
class ClockLifecycleTest {
    @get:Rule val compose = createComposeRule()
    @Test fun resumeAndTimeBroadcastReadFreshInstantInsteadOfAccumulatingTicks() {
        val clock = object : Clock() {
            var time = Instant.parse("2026-12-31T20:59:30Z")
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: ZoneId): Clock = this
            override fun instant() = time
        }
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle = registry
        }
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                val time = rememberBoardClock(clock)
                Text(formatClock(time.value))
            }
        }
        compose.onNodeWithText("23:59").assertIsDisplayed()
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED; clock.time = Instant.parse("2026-12-31T21:01:00Z") }
        compose.onNodeWithText("23:59").assertIsDisplayed()
        compose.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.onNodeWithText("00:01").assertIsDisplayed()
        compose.runOnIdle {
            clock.time = Instant.parse("2026-12-31T19:00:00Z")
            ApplicationProvider.getApplicationContext<NurIslamApplication>().sendBroadcast(Intent(Intent.ACTION_TIME_CHANGED))
        }
        compose.onNodeWithText("22:00").assertIsDisplayed()
    }
}
