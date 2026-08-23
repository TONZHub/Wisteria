package com.example.data.cloud

import com.example.domain.agent.model.DailyPulseData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
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

/**
 * Real Firestore-backed implementation. Writes/reads require a valid
 * app/google-services.json (see README) - without one, FirebaseAuth/Firestore
 * throw on first use, which callers surface as a failed tool execution rather
 * than a crash.
 */
class FirestoreSyncServiceImpl(
    firestoreProvider: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() },
    authProvider: () -> FirebaseAuth = { FirebaseAuth.getInstance() }
) : FirestoreSyncService {
    // Deferred until first actual use: constructing this class must not require
    // a configured Firebase app, only calling sync/fetch does.
    private val firestore: FirebaseFirestore by lazy(firestoreProvider)
    private val auth: FirebaseAuth by lazy(authProvider)

    private val defaultPhaseBlueprint = listOf(
        mapOf("phase" to "Spotting Window", "duration" to "learned", "texture" to "Irregular baseline spotting", "patternSignal" to "Spotting is tracked separately from an actual period"),
        mapOf("phase" to "Period Window", "duration" to "learned", "texture" to "Standard bleeding", "patternSignal" to "Follows the spotting window"),
        mapOf("phase" to "Alive Window", "duration" to "learned", "texture" to "High energy, clarity", "patternSignal" to "Baseline optimal wellness"),
        mapOf("phase" to "Drop Window (PMDD)", "duration" to "learned", "texture" to "Brain fog / harder window", "patternSignal" to "Harder window; rest and low cognitive load recommended")
    )

    private suspend fun ensureSignedIn() {
        if (auth.currentUser == null) {
            auth.signInAnonymously().await()
        }
    }

    override suspend fun syncDailyCheckIn(userId: String, pulse: DailyPulseData): FirestoreSyncRecord {
        ensureSignedIn()

        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val docRef = firestore.collection("users").document(userId)
            .collection("cycle_timeline").document(dateStr)

        val payload = mapOf(
            "ratingValue" to pulse.ratingValue,
            "texture" to pulse.texture.name,
            "textureLabel" to pulse.textureLabel,
            "singleInputResponse" to pulse.singleInputResponse,
            "agentAcknowledgment" to pulse.agentAcknowledgment,
            "isPmddWindowActive" to pulse.isPmddWindowActive,
            "nerveTonicTaken" to pulse.nerveTonicTaken,
            "lowEffortMealSuggested" to pulse.lowEffortMealSuggested,
            "comfortContent" to pulse.comfortContent,
            "confidenceScore" to pulse.confidenceScore,
            "syncedAt" to FieldValue.serverTimestamp()
        )
        docRef.set(payload).await()

        val payloadPreview = "Rating: ${pulse.ratingValue}/5, Texture: ${pulse.texture.name}, PMDD Active: ${pulse.isPmddWindowActive}, Rest Taken: ${pulse.nerveTonicTaken}"

        return FirestoreSyncRecord(
            documentPath = docRef.path,
            collection = "cycle_timeline",
            status = "PERSISTED_FIRESTORE",
            payloadPreview = payloadPreview
        )
    }

    override suspend fun fetchCycleMemorySnapshot(userId: String): List<Map<String, Any>> {
        ensureSignedIn()

        val snapshot = firestore.collection("users").document(userId)
            .collection("cycle_phases").get().await()

        if (snapshot.isEmpty) return defaultPhaseBlueprint
        return snapshot.documents.mapNotNull { it.data }
    }
}
