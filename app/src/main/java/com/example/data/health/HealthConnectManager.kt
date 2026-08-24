package com.example.data.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.MenstruationPeriodRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.time.temporal.ChronoUnit

class HealthConnectManager(private val context: Context) {
    private val healthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    val permissions = setOf(
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(MenstruationPeriodRecord::class)
    )

    private val _isAvailable = MutableStateFlow(false)
    val isAvailable: StateFlow<Boolean> = _isAvailable

    init {
        checkAvailability()
    }

    fun checkAvailability() {
        val status = HealthConnectClient.getSdkStatus(context)
        _isAvailable.value = status == HealthConnectClient.SDK_AVAILABLE
    }

    suspend fun hasAllPermissions(): Boolean {
        if (!_isAvailable.value) return false
        val granted = healthConnectClient.permissionController.getGrantedPermissions()
        return granted.containsAll(permissions)
    }

    fun getHealthConnectInstallIntent(): Intent {
        val uriString = "market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding"
        return Intent(Intent.ACTION_VIEW).apply {
            setPackage("com.android.vending")
            data = Uri.parse(uriString)
            putExtra("overlay", true)
            putExtra("callerId", context.packageName)
        }
    }

    suspend fun fetchLastCycleContext(): String? {
        if (!hasAllPermissions()) return null
        
        try {
            // Health Connect often uses Instant for time range filters
            val now = Instant.now()
            val thirtyFiveDaysAgo = now.minus(35, ChronoUnit.DAYS)
            
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = MenstruationPeriodRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(thirtyFiveDaysAgo, now)
                )
            )
            
            val lastPeriod = response.records.maxByOrNull { it.startTime } ?: return null
            val daysSinceStart = ChronoUnit.DAYS.between(lastPeriod.startTime, now)
            
            return when {
                daysSinceStart < 7 -> "Phase: Menstrual (Day ${daysSinceStart + 1})"
                daysSinceStart < 14 -> "Phase: Follicular"
                daysSinceStart < 17 -> "Phase: Ovulatory"
                else -> "Phase: Luteal"
            }
        } catch (e: Exception) {
            return null
        }
    }
}
