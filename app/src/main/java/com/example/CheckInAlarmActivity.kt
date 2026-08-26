package com.example

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import com.example.data.reminder.CheckInReminderManager
import com.example.ui.screens.CheckInAlarmScreen
import com.example.ui.theme.MyApplicationTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** A bounded alarm surface: the person can start, snooze, or dismiss without unlocking Wisteria. */
class CheckInAlarmActivity : ComponentActivity() {
    private val reminderManager by lazy { CheckInReminderManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        enableEdgeToEdge()

        val alarm = reminderManager.snapshot()
        val scheduledTime = LocalTime.of(alarm.hour, alarm.minute)
            .format(DateTimeFormatter.ofPattern("h:mm a"))

        setContent {
            MyApplicationTheme {
                CheckInAlarmScreen(
                    scheduledTime = scheduledTime,
                    onStartCheckIn = ::startCheckIn,
                    onSnooze = ::snooze,
                    onDismiss = ::dismiss
                )
            }
        }
    }

    private fun showOverLockScreen() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    private fun startCheckIn() {
        val keyguardManager = getSystemService(KeyguardManager::class.java)
        if (!keyguardManager.isKeyguardLocked) {
            launchCheckIn()
            return
        }

        keyguardManager.requestDismissKeyguard(
            this,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    launchCheckIn()
                }
            }
        )
    }

    private fun launchCheckIn() {
        reminderManager.dismissActiveAlarm()
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_TRIGGER_TAKEOVER, true)
            }
        )
        finish()
    }

    private fun snooze() {
        reminderManager.snooze()
        reminderManager.dismissActiveAlarm()
        finish()
    }

    private fun dismiss() {
        reminderManager.dismissActiveAlarm()
        finish()
    }
}
