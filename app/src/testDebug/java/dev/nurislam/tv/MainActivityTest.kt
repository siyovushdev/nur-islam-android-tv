package dev.nurislam.tv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun dpadChangesModePersistsOnRecreateAndOpensFrozenDemo() {
        compose.onRoot().performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithText("Диагностика экрана · DEBUG").assertIsDisplayed()
        compose.onRoot().performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionDown)
            pressKey(Key.Enter)
        }
        compose.runOnIdle {
            assertEquals("PORTRAIT_ROTATE_CW", compose.activity.getSharedPreferences("display", 0).getString("mode", null))
        }
        compose.onRoot().performKeyInput { pressKey(Key.Back) }
        compose.onNodeWithText("Диагностика экрана · DEBUG").assertDoesNotExist()
        compose.activityRule.scenario.recreate()
        compose.onRoot().performKeyInput { pressKey(Key.Menu) }
        compose.onNodeWithText("✓ PORTRAIT_ROTATE_CW · +90°").assertIsDisplayed()
        // The menu is now rotated: physical LEFT moves down in logical portrait coordinates.
        compose.onRoot().performKeyInput {
            repeat(3) { pressKey(Key.DirectionLeft) }
            pressKey(Key.Enter)
        }
        compose.onNodeWithText("14:20").assertIsDisplayed()
        compose.onNodeWithText("00:55:00").assertIsDisplayed()
        compose.onNodeWithText("TEST / DEMO · 23.10.2026 14:20 · часы остановлены").assertIsDisplayed()
    }
}
