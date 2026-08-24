package com.example.core.network

data class CompanionModelReply(
    val text: String,
    val runtime: String,
    val model: String,
    val sessionId: String,
    val eventCount: Int,
    val resolvedModelVersion: String? = null
)

/** Testable boundary around the optional generative response layer. */
interface CompanionModelService {
    suspend fun generateReply(prompt: String): CompanionModelReply?

    /** Starts a fresh in-memory model conversation without affecting locally saved check-ins. */
    fun startNewSession() = Unit

    /** Makes the current model conversation inaccessible when an in-app call or chat ends. */
    fun endSession() = Unit
}
