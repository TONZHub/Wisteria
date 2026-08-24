package com.example.data.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class CheckInAlarmRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("Wisteria", "Restoring check-in alarm after ${intent.action}")
        CheckInReminderManager(context).scheduleNextDailyAlarm()
    }
}
