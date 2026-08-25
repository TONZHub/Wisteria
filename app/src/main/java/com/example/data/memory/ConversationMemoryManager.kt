package com.example.data.memory

import android.content.Context
import com.example.data.local.entity.AgentMemoryEntity
import com.example.domain.agent.model.AgentTurnIntent
import java.security.MessageDigest

class ConversationMemoryManager(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun isEnabled(): Boolean = preferences.getBoolean(KEY_ENABLED, false)

    fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun extract(userText: String, intent: AgentTurnIntent?): AgentMemoryEntity? {
        if (!isEnabled()) return null
        return ConversationMemoryExtractor.extract(userText, intent)
    }

    private companion object {
        const val PREFERENCES_NAME = "conversation_memory"
        const val KEY_ENABLED = "enabled"
    }
}

object ConversationMemoryExtractor {
    private const val MAX_MEMORY_LENGTH = 240
    private val eligibleIntents = setOf(AgentTurnIntent.FOLLOW_UP, AgentTurnIntent.GENERAL)
    private val secretTerms = listOf(
        "password", "passcode", "pin number", "social security", "credit card",
        "api key", "access token", "private key", "recovery code"
    )
    private val instructionTerms = listOf(
        "ignore previous", "ignore all", "system prompt", "developer message",
        "jailbreak", "do anything now", "reveal your prompt"
    )
    private val lowInformationReplies = setOf(
        "yes", "no", "yeah", "yep", "nope", "okay", "ok", "sure", "thanks",
        "thank you", "maybe", "idk", "i don't know", "done", "stop"
    )

    fun extract(userText: String, intent: AgentTurnIntent?): AgentMemoryEntity? {
        if (intent !in eligibleIntents) return null

        val normalized = userText.trim().replace(Regex("\\s+"), " ")
        val lower = normalized.lowercase()
        if (normalized.length < 12 || lower in lowInformationReplies) return null
        if (secretTerms.any(lower::contains) || instructionTerms.any(lower::contains)) return null
        if (EMAIL.containsMatchIn(normalized) || PHONE.containsMatchIn(normalized)) return null

        val category = when {
            listOf("helps", "helped", "calms", "soothes", "makes it easier", "works for me")
                .any(lower::contains) -> "CONVERSATION_SUPPORT"
            listOf("i like", "i love", "i prefer", "i dislike", "i hate", "i don't like")
                .any(lower::contains) -> "CONVERSATION_PREFERENCE"
            listOf("usually", "every morning", "every night", "most days", "routine")
                .any(lower::contains) -> "CONVERSATION_ROUTINE"
            listOf("lately", "recently", "this week", "work", "school", "home", "sleep", "slept",
                "stress", "overwhelmed", "busy", "argument", "change")
                .any(lower::contains) -> "CONVERSATION_CONTEXT"
            else -> return null
        }

        val value = normalized.take(MAX_MEMORY_LENGTH)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$category:$value".toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(16)

        return AgentMemoryEntity(
            memoryKey = "conversation_$digest",
            memoryValue = value,
            category = category,
            importance = 0.6f
        )
    }

    private val EMAIL = Regex("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", RegexOption.IGNORE_CASE)
    private val PHONE = Regex("(?<!\\d)(?:\\+?1[-. ]?)?\\(?\\d{3}\\)?[-. ]?\\d{3}[-. ]?\\d{4}(?!\\d)")
}
