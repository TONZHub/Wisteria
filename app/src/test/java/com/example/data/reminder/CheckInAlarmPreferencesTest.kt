package com.example.data.reminder

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CheckInAlarmPreferencesTest {
    private lateinit var context: Context
    private lateinit var preferences: CheckInAlarmPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("check_in_alarm", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        preferences = CheckInAlarmPreferences(context)
    }

    @Test
    fun `alarm choice survives a new preferences instance`() {
        preferences.enable(hour = 18, minute = 45)

        val restored = CheckInAlarmPreferences(context).read()

        assertTrue(restored.enabled)
        assertEquals(18, restored.hour)
        assertEquals(45, restored.minute)
    }

    @Test
    fun `disable keeps the last chosen time without leaving the alarm enabled`() {
        preferences.enable(hour = 12, minute = 15)
        preferences.disable()

        val restored = preferences.read()

        assertFalse(restored.enabled)
        assertEquals(12, restored.hour)
        assertEquals(15, restored.minute)
    }
}
