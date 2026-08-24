package com.example.testutil

import com.example.data.cloud.FirestoreSyncRecord
import com.example.data.cloud.FirestoreSyncService
import com.example.domain.agent.model.DailyPulseData

/** Records every sync call so tests can assert on the real pulse data sent, without touching Firebase. */
class FakeFirestoreSyncService(
    private val shouldThrow: Boolean = false,
    private val textureSnapshot: List<Map<String, Any>> = emptyList()
) : FirestoreSyncService {
    val syncedPulses = mutableListOf<DailyPulseData>()
    var syncCallCount = 0
        private set
    private var signedIn = false

    override suspend fun signInWithGoogle(idToken: String) {
        signedIn = true
    }

    override fun signOut() {
        signedIn = false
    }

    override fun isUserLoggedIn(): Boolean = signedIn

    override fun getUserEmail(): String? = if (signedIn) "test@wisteria.local" else null

    override suspend fun syncDailyCheckIn(pulse: DailyPulseData): FirestoreSyncRecord {
        syncCallCount++
        if (shouldThrow) throw IllegalStateException("Firestore not configured (no google-services.json)")
        syncedPulses.add(pulse)
        return FirestoreSyncRecord(
            documentPath = "users/test-user/daily_timeline/test-date",
            collection = "daily_timeline",
            status = "PERSISTED_FIRESTORE",
            payloadPreview = "Rating: ${pulse.ratingValue}/5"
        )
    }

    override suspend fun fetchTextureSummary(): List<Map<String, Any>> = textureSnapshot
}
