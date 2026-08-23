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
        val model = Firebase
            .ai(backend = GenerativeBackend.googleAI())
            .generativeModel(modelName = "gemini-3.5-flash")

        return model.generateContent(prompt).text?.trim()?.takeIf { it.isNotEmpty() }
    }
}
