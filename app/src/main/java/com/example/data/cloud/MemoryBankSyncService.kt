package com.example.data.cloud

import com.example.BuildConfig
import com.example.data.local.entity.AgentMemoryEntity
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI

interface MemoryBankSyncService {
    suspend fun remember(memory: AgentMemoryEntity)
    suspend fun recall(query: String): List<AgentMemoryEntity>
    suspend fun forget(memory: AgentMemoryEntity)
    suspend fun forgetAll()
}

object DisabledMemoryBankSyncService : MemoryBankSyncService {
    override suspend fun remember(memory: AgentMemoryEntity) = Unit
    override suspend fun recall(query: String): List<AgentMemoryEntity> = emptyList()
    override suspend fun forget(memory: AgentMemoryEntity) = Unit
    override suspend fun forgetAll() = Unit
}

/**
 * Sends bounded notes to Wisteria's authenticated bridge. Google Cloud credentials
 * never live in the APK. Firebase Auth identifies the person and App Check attests
 * the app; the bridge derives the actual Memory Bank scope server-side.
 */
class FirebaseMemoryBankSyncService(
    private val baseUrl: String = BuildConfig.MEMORY_SERVICE_URL,
    private val tokenProvider: suspend () -> BridgeTokens = ::firebaseBridgeTokens,
    private val transport: MemoryBridgeTransport = UrlConnectionMemoryBridgeTransport()
) : MemoryBankSyncService {
    override suspend fun remember(memory: AgentMemoryEntity) {
        if (!isAvailable()) return
        transport.post(
            url = "$baseUrl/v1/memories",
            tokens = tokenProvider(),
            body = memoryBody(memory)
        )
    }

    override suspend fun recall(query: String): List<AgentMemoryEntity> {
        if (!isAvailable() || query.isBlank()) return emptyList()
        val response = transport.post(
            url = "$baseUrl/v1/memories:search",
            tokens = tokenProvider(),
            // The current chat turn stays on-device. The bridge uses a fixed,
            // non-sensitive semantic query to fetch the bounded user scope.
            body = JSONObject()
        )
        val rows = response.optJSONArray("memories") ?: JSONArray()
        return buildList {
            for (index in 0 until rows.length()) {
                val row = rows.optJSONObject(index) ?: continue
                val fact = row.optString("fact").trim().take(240)
                if (fact.length < 12) continue
                val key = row.optString("id").ifBlank { "remote_$index" }
                add(
                    AgentMemoryEntity(
                        memoryKey = "remote_${key.filter(Char::isLetterOrDigit).take(48)}",
                        memoryValue = fact,
                        category = "CONVERSATION_CONTEXT",
                        importance = 0.6f
                    )
                )
            }
        }
    }

    override suspend fun forget(memory: AgentMemoryEntity) {
        if (!isAvailable()) return
        transport.post(
            url = "$baseUrl/v1/memories:forget",
            tokens = tokenProvider(),
            body = memoryBody(memory)
        )
    }

    override suspend fun forgetAll() {
        if (!isAvailable()) return
        transport.post(
            url = "$baseUrl/v1/memories:forgetAll",
            tokens = tokenProvider(),
            body = JSONObject()
        )
    }

    private fun isAvailable(): Boolean = baseUrl.startsWith("https://")

    private fun memoryBody(memory: AgentMemoryEntity) = JSONObject()
        .put("fact", memory.memoryValue.take(240))
        .put("category", memory.category)
}

data class BridgeTokens(val idToken: String, val appCheckToken: String)

private suspend fun firebaseBridgeTokens(): BridgeTokens {
    val user = Firebase.auth.currentUser
        ?: error("Sign in before syncing conversation memory")
    val idToken = user.getIdToken(false).await().token
        ?: error("Firebase did not return an ID token")
    val appCheckToken = Firebase.appCheck.getAppCheckToken(false).await().token
    return BridgeTokens(idToken, appCheckToken)
}

interface MemoryBridgeTransport {
    suspend fun post(url: String, tokens: BridgeTokens, body: JSONObject): JSONObject
}

class UrlConnectionMemoryBridgeTransport : MemoryBridgeTransport {
    override suspend fun post(url: String, tokens: BridgeTokens, body: JSONObject): JSONObject =
        withContext(Dispatchers.IO) {
            val connection = URI(url).toURL().openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 8_000
                connection.readTimeout = 12_000
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "Bearer ${tokens.idToken}")
                connection.setRequestProperty("X-Firebase-AppCheck", tokens.appCheckToken)
                connection.outputStream.use { it.write(body.toString().toByteArray()) }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val payload = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (status !in 200..299) error("Memory bridge returned HTTP $status")
                if (payload.isBlank()) JSONObject() else JSONObject(payload)
            } finally {
                connection.disconnect()
            }
        }
}
