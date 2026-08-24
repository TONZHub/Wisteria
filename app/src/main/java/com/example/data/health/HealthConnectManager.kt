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
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

data class HealthAccessStatus(
    val hasAnyAccess: Boolean,
    val summary: String
)

class HealthConnectManager(private val context: Context) {
    private val healthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    private val sleepPermission = HealthPermission.getReadPermission(SleepSessionRecord::class)
    private val stepsPermission = HealthPermission.getReadPermission(StepsRecord::class)
    private val timingPermission = HealthPermission.getReadPermission(MenstruationPeriodRecord::class)

    val permissions = setOf(sleepPermission, stepsPermission, timingPermission)

    private val _isAvailable = MutableStateFlow(false)
    val isAvailable: StateFlow<Boolean> = _isAvailable

    init {
        checkAvailability()
    }

    fun checkAvailability() {
        val status = HealthConnectClient.getSdkStatus(context)
        _isAvailable.value = status == HealthConnectClient.SDK_AVAILABLE
    }

    suspend fun getAccessStatus(): HealthAccessStatus {
        if (!_isAvailable.value) {
            return HealthAccessStatus(false, "Not connected · integration optional")
        }

        val granted = runCatching {
            healthConnectClient.permissionController.getGrantedPermissions()
        }.getOrDefault(emptySet())
        val labels = buildList {
            if (sleepPermission in granted) add("sleep")
            if (stepsPermission in granted) add("steps")
            if (timingPermission in granted) add("period timing")
        }

        return if (labels.isEmpty()) {
            HealthAccessStatus(false, "Not connected · integration optional")
        } else {
            HealthAccessStatus(true, "Connected · ${labels.joinToString()}")
        }
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

    /**
     * Reads only permissions the person granted, then reduces records to a tone-only instruction.
     * No raw Health Connect value, date, or inferred label is returned to the conversational layer.
     */
    suspend fun fetchPrivateResponseContext(): String? {
        if (!_isAvailable.value) return null

        val granted = runCatching {
            healthConnectClient.permissionController.getGrantedPermissions()
        }.getOrDefault(emptySet())
        if (granted.isEmpty()) return null

        val now = Instant.now()
        val signals = PrivateHealthSignals(
            recentSleepHours = if (sleepPermission in granted) readRecentSleepHours(now) else null,
            recentSteps = if (stepsPermission in granted) readRecentSteps(now) else null,
            daysSinceLoggedStart = if (timingPermission in granted) readDaysSinceLoggedStart(now) else null
        )
        return PrivateHealthContextPolicy.modelToneInstruction(signals)
    }

    private suspend fun readRecentSleepHours(now: Instant): Double? = runCatching {
        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(now.minus(36, ChronoUnit.HOURS), now)
            )
        )
        val latest = response.records.maxByOrNull { it.endTime } ?: return@runCatching null
        Duration.between(latest.startTime, latest.endTime).toMinutes() / 60.0
    }.getOrNull()

    private suspend fun readRecentSteps(now: Instant): Long? = runCatching {
        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(now.minus(24, ChronoUnit.HOURS), now)
            )
        )
        response.records.takeIf { it.isNotEmpty() }?.sumOf { it.count }
    }.getOrNull()

    private suspend fun readDaysSinceLoggedStart(now: Instant): Long? = runCatching {
        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = MenstruationPeriodRecord::class,
                timeRangeFilter = TimeRangeFilter.between(now.minus(35, ChronoUnit.DAYS), now)
            )
        )
        val latest = response.records.maxByOrNull { it.startTime } ?: return@runCatching null
        ChronoUnit.DAYS.between(latest.startTime, now).coerceAtLeast(0L)
    }.getOrNull()
}
