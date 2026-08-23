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
    suspend fun syncDailyCheckIn(pulse: DailyPulseData): FirestoreSyncRecord
    suspend fun fetchTextureSummary(): List<Map<String, Any>>
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

    private suspend fun signedInUserId(): String {
        auth.currentUser?.uid?.let { return it }
        return auth.signInAnonymously().await().user?.uid
            ?: error("Firebase anonymous sign-in did not return a user")
    }

    override suspend fun syncDailyCheckIn(pulse: DailyPulseData): FirestoreSyncRecord {
        val userId = signedInUserId()

        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val docRef = firestore.collection("users").document(userId)
            .collection("daily_timeline").document(dateStr)

        val payload = mapOf(
            "ratingValue" to pulse.ratingValue,
            "texture" to pulse.texture.name,
            "textureLabel" to pulse.textureLabel,
            "singleInputResponse" to pulse.singleInputResponse,
            "agentAcknowledgment" to pulse.agentAcknowledgment,
            "isOffDay" to pulse.isOffDay,
            "restOrHydrationLogged" to pulse.restOrHydrationLogged,
            "lowEffortMealSuggested" to pulse.lowEffortMealSuggested,
            "comfortContent" to pulse.comfortContent,
            "confidenceScore" to pulse.confidenceScore,
            "syncedAt" to FieldValue.serverTimestamp()
        )
        docRef.set(payload).await()

        val payloadPreview = "Rating: ${pulse.ratingValue}/5, Texture: ${pulse.texture.name}, Off: ${pulse.isOffDay}"

        return FirestoreSyncRecord(
            documentPath = docRef.path,
            collection = "daily_timeline",
            status = "PERSISTED_FIRESTORE",
            payloadPreview = payloadPreview
        )
    }

    override suspend fun fetchTextureSummary(): List<Map<String, Any>> {
        val userId = signedInUserId()
        val snapshot = firestore.collection("users").document(userId)
            .collection("daily_timeline").get().await()

        if (snapshot.isEmpty) return emptyList()

        val counts = snapshot.documents
            .mapNotNull { it.getString("texture") }
            .groupingBy { it }
            .eachCount()

        val labels = listOf(
            Triple("BRIGHT", "Bright days", "Days you described as clear, good, or bright"),
            Triple("STEADY", "Steady days", "Days you described as okay or steady"),
            Triple("HEAVY", "Heavy days", "Days you described as tired, foggy, hard, or heavy"),
            Triple("OFF", "Off days", "Days you described as off, awful, or crashed"),
            Triple("UNKNOWN", "Unlabeled days", "Check-ins kept without adding a feeling label")
        )

        return labels.mapNotNull { (texture, title, signal) ->
            val count = counts[texture] ?: return@mapNotNull null
            mapOf(
                "textureTitle" to title,
                "duration" to "$count recorded check-in${if (count == 1) "" else "s"}",
                "texture" to texture,
                "patternSignal" to signal
            )
        }
    }
}
