package com.example.data.reminder

import android.content.Context

data class CheckInAlarmSettings(
    val enabled: Boolean = false,
    val hour: Int = DEFAULT_HOUR,
    val minute: Int = DEFAULT_MINUTE
) {
    companion object {
        const val DEFAULT_HOUR = 9
        const val DEFAULT_MINUTE = 0
    }
}

class CheckInAlarmPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun read(): CheckInAlarmSettings = CheckInAlarmSettings(
        enabled = preferences.getBoolean(KEY_ENABLED, false),
        hour = preferences.getInt(KEY_HOUR, CheckInAlarmSettings.DEFAULT_HOUR),
        minute = preferences.getInt(KEY_MINUTE, CheckInAlarmSettings.DEFAULT_MINUTE)
    )

    fun enable(hour: Int, minute: Int) {
        require(hour in 0..23) { "Hour must be between 0 and 23" }
        require(minute in 0..59) { "Minute must be between 0 and 59" }
        preferences.edit()
            .putBoolean(KEY_ENABLED, true)
            .putInt(KEY_HOUR, hour)
            .putInt(KEY_MINUTE, minute)
            .apply()
    }

    fun disable() {
        preferences.edit().putBoolean(KEY_ENABLED, false).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "check_in_alarm"
        const val KEY_ENABLED = "enabled"
        const val KEY_HOUR = "hour"
        const val KEY_MINUTE = "minute"
    }
}
