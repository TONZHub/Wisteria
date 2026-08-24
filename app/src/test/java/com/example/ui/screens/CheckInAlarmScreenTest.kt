package com.example.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CheckInAlarmScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `alarm offers start snooze and dismiss without trapping the user`() {
        var action = ""
        composeRule.setContent {
            MyApplicationTheme {
                CheckInAlarmScreen(
                    scheduledTime = "9:00 AM",
                    onStartCheckIn = { action = "start" },
                    onSnooze = { action = "snooze" },
                    onDismiss = { action = "dismiss" }
                )
            }
        }

        composeRule.onNodeWithTag("check_in_alarm_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("alarm_start_check_in").performClick()
        assertEquals("start", action)
        composeRule.onNodeWithTag("alarm_snooze").performClick()
        assertEquals("snooze", action)
        composeRule.onNodeWithTag("alarm_dismiss").performClick()
        assertEquals("dismiss", action)
    }
}
