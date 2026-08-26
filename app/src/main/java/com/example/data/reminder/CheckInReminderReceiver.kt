package com.example.data.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.R

class CheckInReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val manager = CheckInReminderManager(context)
        when (intent.action) {
            ACTION_SNOOZE -> {
                manager.snooze()
                manager.dismissActiveAlarm()
                return
            }
            ACTION_DISMISS -> {
                manager.dismissActiveAlarm()
                return
            }
            ACTION_FIRE_DAILY -> {
                if (manager.scheduleNextDailyAlarm() == null) return
            }
            ACTION_FIRE_SNOOZE -> {
                if (!manager.snapshot().enabled) return
            }
            else -> return
        }

        Log.d("Wisteria", "Check-in alarm triggered: ${intent.action}")
        if (!manager.snapshot().hasNotificationAccess) {
            Log.w("Wisteria", "Check-in alarm could not notify because access is disabled")
            return
        }
        showAlarmNotification(context, manager)
    }

    private fun showAlarmNotification(
        context: Context,
        reminderManager: CheckInReminderManager
    ) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel(notificationManager)

        val alarmIntent = Intent(context, com.example.MainActivity::class.java).apply {
            action = ACTION_FIRE_DAILY
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(com.example.MainActivity.EXTRA_TRIGGER_TAKEOVER, true)
        }
        val alarmPendingIntent = PendingIntent.getActivity(
            context,
            ALARM_ACTIVITY_REQUEST_CODE,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snoozePendingIntent = actionPendingIntent(context, ACTION_SNOOZE, SNOOZE_ACTION_REQUEST_CODE)
        val dismissPendingIntent = actionPendingIntent(context, ACTION_DISMISS, DISMISS_ACTION_REQUEST_CODE)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_wisteria_notification)
            .setContentTitle("Wisteria check-in alarm")
            .setContentText("Your 3-second check-in is ready.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(alarmPendingIntent)
            .addAction(R.drawable.ic_wisteria_notification, "Snooze 10 min", snoozePendingIntent)
            .addAction(R.drawable.ic_wisteria_notification, "Dismiss", dismissPendingIntent)
            .setOngoing(true)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        if (reminderManager.canUseFullScreenIntent()) {
            builder.setFullScreenIntent(alarmPendingIntent, true)
        }

        notificationManager.notify(CheckInReminderManager.NOTIFICATION_ID, builder.build())
    }

    private fun createNotificationChannel(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val alarmAudio = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Check-In Alarms",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "User-scheduled daily Wisteria check-in alarms"
            enableVibration(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setSound(Settings.System.DEFAULT_ALARM_ALERT_URI, alarmAudio)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun actionPendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, CheckInReminderReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val ACTION_FIRE_DAILY = "com.zoeb.wisteria.action.FIRE_DAILY_CHECK_IN_ALARM"
        const val ACTION_FIRE_SNOOZE = "com.zoeb.wisteria.action.FIRE_SNOOZED_CHECK_IN_ALARM"
        const val ACTION_SNOOZE = "com.zoeb.wisteria.action.SNOOZE_CHECK_IN_ALARM"
        const val ACTION_DISMISS = "com.zoeb.wisteria.action.DISMISS_CHECK_IN_ALARM"

        private const val CHANNEL_ID = "check_in_alarm_v2"
        private const val ALARM_ACTIVITY_REQUEST_CODE = 1101
        private const val SNOOZE_ACTION_REQUEST_CODE = 1102
        private const val DISMISS_ACTION_REQUEST_CODE = 1103
    }
}
