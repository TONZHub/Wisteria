package com.example.testutil

import com.example.data.cloud.FirestoreSyncRecord
import com.example.data.cloud.FirestoreSyncService
import com.example.domain.agent.model.DailyPulseData

/** Records every sync call so tests can assert on the real pulse data sent, without touching Firebase. */
class FakeFirestoreSyncService(
    private val shouldThrow: Boolean = false,
    private val phaseSnapshot: List<Map<String, Any>> = emptyList()
) : FirestoreSyncService {
    val syncedPulses = mutableListOf<DailyPulseData>()
    var syncCallCount = 0
        private set

    override suspend fun syncDailyCheckIn(userId: String, pulse: DailyPulseData): FirestoreSyncRecord {
        syncCallCount++
        if (shouldThrow) throw IllegalStateException("Firestore not configured (no google-services.json)")
        syncedPulses.add(pulse)
        return FirestoreSyncRecord(
            documentPath = "users/$userId/cycle_timeline/test-date",
            collection = "cycle_timeline",
            status = "PERSISTED_FIRESTORE",
            payloadPreview = "Rating: ${pulse.ratingValue}/5"
        )
    }

    override suspend fun fetchCycleMemorySnapshot(userId: String): List<Map<String, Any>> = phaseSnapshot
}
