package com.example.data.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

enum class AlarmSchedulePrecision {
    EXACT,
    INEXACT_FALLBACK
}

data class CheckInAlarmSnapshot(
    val enabled: Boolean,
    val hour: Int,
    val minute: Int,
    val hasNotificationAccess: Boolean,
    val hasExactAlarmAccess: Boolean,
    val hasFullScreenAccess: Boolean
)

/**
 * Owns Wisteria's user-configured daily check-in alarm.
 *
 * The selected schedule is persisted separately from AlarmManager because Android clears alarms
 * across reboot, package replacement, and exact-alarm permission changes. If exact access is not
 * available, Wisteria keeps an inexact safety-net alarm until the person grants it.
 */
class CheckInReminderManager(
    context: Context,
    private val nowMillis: () -> Long = { System.currentTimeMillis() }
) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val preferences = CheckInAlarmPreferences(appContext)

    fun snapshot(): CheckInAlarmSnapshot {
        val settings = preferences.read()
        return CheckInAlarmSnapshot(
            enabled = settings.enabled,
            hour = settings.hour,
            minute = settings.minute,
            hasNotificationAccess = hasNotificationAccess(),
            hasExactAlarmAccess = canScheduleExactAlarms(),
            hasFullScreenAccess = canUseFullScreenIntent()
        )
    }

    fun enableDailyAlarm(hour: Int, minute: Int): AlarmSchedulePrecision {
        preferences.enable(hour, minute)
        return scheduleNextDailyAlarm()
            ?: error("A newly enabled alarm must have a schedule")
    }

    fun scheduleNextDailyAlarm(): AlarmSchedulePrecision? {
        val settings = preferences.read()
        if (!settings.enabled) return null

        val triggerAtMillis = CheckInAlarmSchedule.nextDailyTriggerMillis(
            hour = settings.hour,
            minute = settings.minute,
            nowMillis = nowMillis()
        )
        return schedule(
            triggerAtMillis = triggerAtMillis,
            pendingIntent = alarmPendingIntent(
                requestCode = DAILY_ALARM_REQUEST_CODE,
                action = CheckInReminderReceiver.ACTION_FIRE_DAILY
            )
        )
    }

    fun snooze(minutes: Int = DEFAULT_SNOOZE_MINUTES): AlarmSchedulePrecision {
        require(minutes > 0) { "Snooze duration must be positive" }
        val triggerAtMillis = nowMillis() + minutes * 60_000L
        return schedule(
            triggerAtMillis = triggerAtMillis,
            pendingIntent = alarmPendingIntent(
                requestCode = SNOOZE_ALARM_REQUEST_CODE,
                action = CheckInReminderReceiver.ACTION_FIRE_SNOOZE
            )
        )
    }

    fun disableAlarm() {
        preferences.disable()
        alarmManager.cancel(
            alarmPendingIntent(
                requestCode = DAILY_ALARM_REQUEST_CODE,
                action = CheckInReminderReceiver.ACTION_FIRE_DAILY
            )
        )
        alarmManager.cancel(
            alarmPendingIntent(
                requestCode = SNOOZE_ALARM_REQUEST_CODE,
                action = CheckInReminderReceiver.ACTION_FIRE_SNOOZE
            )
        )
        dismissActiveAlarm()
    }

    fun dismissActiveAlarm() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    fun exactAlarmSettingsIntent(): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || canScheduleExactAlarms()) return null
        return Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${appContext.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun fullScreenSettingsIntent(): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || canUseFullScreenIntent()) {
            return null
        }
        return Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
            data = Uri.parse("package:${appContext.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun canUseFullScreenIntent(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            notificationManager.canUseFullScreenIntent()
        } else {
            true
        }
    }

    private fun hasNotificationAccess(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    private fun schedule(
        triggerAtMillis: Long,
        pendingIntent: PendingIntent
    ): AlarmSchedulePrecision {
        if (canScheduleExactAlarms()) {
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
                return AlarmSchedulePrecision.EXACT
            } catch (_: SecurityException) {
                // Permission can be revoked between the capability check and this call.
            }
        }

        // Keep a best-effort reminder alive until the user grants precise timing access.
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent
        )
        return AlarmSchedulePrecision.INEXACT_FALLBACK
    }

    private fun alarmPendingIntent(requestCode: Int, action: String): PendingIntent {
        val intent = Intent(appContext, CheckInReminderReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            appContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val DEFAULT_SNOOZE_MINUTES = 10
        private const val DAILY_ALARM_REQUEST_CODE = 1001
        private const val SNOOZE_ALARM_REQUEST_CODE = 1002
    }
}
