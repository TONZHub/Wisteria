package com.example.core.network

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend

/** Testable boundary around the optional generative response layer. */
interface CompanionModelService {
    suspend fun generateReply(prompt: String): String?
}

/**
 * Uses Firebase AI Logic so no developer API key is embedded in the Android APK.
 * Firebase App Check protects configured builds; callers retain a local response
 * path when Firebase has not been configured or is temporarily unavailable.
 */
class FirebaseCompanionModelService : CompanionModelService {
    override suspend fun generateReply(prompt: String): String? {
        return try {
            val model = com.google.firebase.Firebase
                .ai(backend = com.google.firebase.ai.type.GenerativeBackend.agentPlatform())
                .generativeModel(modelName = "gemini-3.5-flash")

            val result = model.generateContent(prompt)
            val text = result.text?.trim()
            text?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            android.util.Log.e("WisteriaAI", "Gemini failure: ${e.message}", e)
            null
        }
    }
}
