package com.example.voice

import com.example.BuildConfig
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI

/**
 * Fetches synthesized Wisteria speech from the authenticated backend bridge.
 * The Inworld credential and voice configuration never live in the APK.
 */
class InworldTtsService(
    private val baseUrl: String = BuildConfig.MEMORY_SERVICE_URL,
    private val tokenProvider: suspend () -> TtsBridgeTokens = ::firebaseTtsBridgeTokens
) {
    suspend fun synthesize(text: String): ByteArray? {
        val normalized = text.trim().take(2_000)
        if (!baseUrl.startsWith("https://") || normalized.isBlank()) return null

        val tokens = tokenProvider()
        return withContext(Dispatchers.IO) {
            val connection = URI("$baseUrl/v1/tts").toURL().openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 8_000
                connection.readTimeout = 30_000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Accept", "audio/wav")
                connection.setRequestProperty("Authorization", "Bearer ${tokens.idToken}")
                connection.setRequestProperty("X-Firebase-AppCheck", tokens.appCheckToken)

                val body = JSONObject().put("text", normalized).toString().toByteArray()
                connection.outputStream.use { it.write(body) }

                val status = connection.responseCode
                if (status !in 200..299) {
                    connection.errorStream?.use { it.readBytes() }
                    error("Voice bridge returned HTTP $status")
                }
                connection.inputStream.use { it.readBytes() }
            } finally {
                connection.disconnect()
            }
        }
    }
}

data class TtsBridgeTokens(val idToken: String, val appCheckToken: String)

private suspend fun firebaseTtsBridgeTokens(): TtsBridgeTokens {
    val user = Firebase.auth.currentUser
        ?: error("Sign in before using Wisteria voice")
    val idToken = user.getIdToken(false).await().token
        ?: error("Firebase did not return an ID token")
    val appCheckToken = Firebase.appCheck.getAppCheckToken(false).await().token
    return TtsBridgeTokens(idToken, appCheckToken)
}
