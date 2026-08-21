package com.example.data.cloud

import com.example.domain.agent.model.DailyPulseData
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FirestoreSyncRecord(
    val documentPath: String,
    val collection: String,
    val status: String,
    val syncedAt: Long = System.currentTimeMillis(),
    val payloadPreview: String
)

interface FirestoreSyncService {
    suspend fun syncDailyCheckIn(userId: String, pulse: DailyPulseData): FirestoreSyncRecord
    suspend fun fetchCycleMemorySnapshot(userId: String): List<Map<String, Any>>
}

class FirestoreSyncServiceImpl : FirestoreSyncService {
    private val syncLogs = mutableListOf<FirestoreSyncRecord>()

    override suspend fun syncDailyCheckIn(userId: String, pulse: DailyPulseData): FirestoreSyncRecord {
        delay(300)
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val docPath = "users/$userId/cycle_timeline/$dateStr"
        val payloadPreview = "Rating: ${pulse.ratingValue}/5, Texture: ${pulse.texture.name}, PMDD Active: ${pulse.isPmddWindowActive}, Nerve Tonic: ${pulse.nerveTonicTaken}"

        val record = FirestoreSyncRecord(
            documentPath = docPath,
            collection = "cycle_timeline",
            status = "PERSISTED_FIRESTORE",
            payloadPreview = payloadPreview
        )
        syncLogs.add(record)
        return record
    }

    override suspend fun fetchCycleMemorySnapshot(userId: String): List<Map<String, Any>> {
        delay(200)
        return listOf(
            mapOf("phase" to "10-Day Spotting Window", "duration" to "10 days", "texture" to "Irregular baseline spotting", "patternSignal" to "Spotting ≠ Period (unique to Zoe)"),
            mapOf("phase" to "Actual Period Bleeding", "duration" to "4 days", "texture" to "Standard bleeding", "patternSignal" to "Follows long spotting phase"),
            mapOf("phase" to "Alive & Thriving Window", "duration" to "7-8 days", "texture" to "High energy, clarity", "patternSignal" to "Baseline optimal wellness"),
            mapOf("phase" to "5-Day Meds Efficacy Drop Window", "duration" to "5 days", "texture" to "PMDD window / Brain fog", "patternSignal" to "Psych meds efficacy dips; Nerve tonic required")
        )
    }
}
