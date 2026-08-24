package com.example.domain.agent

import com.example.domain.agent.model.AgentTurnIntent
import com.example.domain.agent.model.DailyPulseData
import java.util.Locale

data class AgentSessionState(
    val currentPulse: DailyPulseData? = null,
    val recordedInputs: Set<String> = emptySet(),
    val careIdeasQueued: Boolean = false,
    val awaitingCareChoice: Boolean = false
)

data class AgentTurnDecision(
    val intent: AgentTurnIntent,
    val normalizedInput: String,
    val rationale: String
)

/**
 * A deliberately conservative, local router. It decides what kind of turn this is before any
 * model wording is requested or any write-capable tool is considered.
 */
class AgentTurnRouter {
    fun route(
        userPrompt: String,
        session: AgentSessionState,
        requestedIntent: AgentTurnIntent? = null
    ): AgentTurnDecision {
        val normalized = normalize(userPrompt)
        val inputKey = normalized.ifBlank { userPrompt.trim() }

        if (requestedIntent == AgentTurnIntent.CHECK_IN) {
            return checkInDecision(inputKey, session, "The check-in surface explicitly started this turn.")
        }

        if (isEndSession(normalized)) {
            return decision(AgentTurnIntent.END_SESSION, normalized, "The user ended the conversation.")
        }

        if (isReminderChange(normalized)) {
            return decision(
                AgentTurnIntent.REMINDER_CHANGE,
                normalized,
                "The turn asks to change a reminder, not record a feeling."
            )
        }

        if (isPatternQuestion(normalized)) {
            return decision(
                AgentTurnIntent.PATTERN_QUESTION,
                normalized,
                "The turn asks about saved history or a pattern."
            )
        }

        if (isCareRequest(normalized, session)) {
            return decision(
                AgentTurnIntent.CARE_REQUEST,
                normalized,
                "The turn asks for an optional idea within the current check-in."
            )
        }

        if (isExplicitCheckIn(userPrompt, normalized, session)) {
            return checkInDecision(inputKey, session, "The user explicitly described today's feeling.")
        }

        if (session.currentPulse != null) {
            return decision(
                AgentTurnIntent.FOLLOW_UP,
                normalized,
                "A check-in already exists, so this is treated as conversation unless the user clearly checks in again."
            )
        }

        return decision(
            AgentTurnIntent.GENERAL,
            normalized,
            "The turn is conversational but does not contain a clear check-in signal."
        )
    }

    fun normalize(value: String): String = value
        .lowercase(Locale.US)
        .trim()
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

    private fun checkInDecision(
        normalized: String,
        session: AgentSessionState,
        rationale: String
    ): AgentTurnDecision {
        return if (normalized in session.recordedInputs) {
            decision(
                AgentTurnIntent.DUPLICATE_CHECK_IN,
                normalized,
                "The same check-in was already recorded in this conversation."
            )
        } else {
            decision(AgentTurnIntent.CHECK_IN, normalized, rationale)
        }
    }

    private fun isExplicitCheckIn(
        original: String,
        normalized: String,
        session: AgentSessionState
    ): Boolean {
        if (normalized.isBlank()) {
            return original.isNotBlank()
        }

        val explicitRating = Regex("(?<!\\d)[1-5](?!\\d)").containsMatchIn(normalized)
        val checkInPhrases = listOf(
            "check in",
            "checking in",
            "i feel",
            "i am feeling",
            "im feeling",
            "feeling today",
            "today feels",
            "today is",
            "my day feels"
        )
        if (explicitRating || checkInPhrases.any(normalized::contains)) return true

        val directTextures = setOf(
            "off",
            "heavy",
            "steady",
            "bright",
            "clear",
            "awful",
            "tired",
            "foggy",
            "radiant",
            "rested"
        )
        if (normalized in directTextures) return true

        val softTextures = setOf("okay", "fine", "good", "great", "alive")
        return session.currentPulse == null && normalized in softTextures
    }

    private fun isCareRequest(normalized: String, session: AgentSessionState): Boolean {
        val careLanguage = listOf(
            "idea",
            "suggest",
            "what can i do",
            "what should i do",
            "something small",
            "something easy",
            "help me"
        )
        if (careLanguage.any(normalized::contains)) return true

        val affirmative = setOf("yes", "yeah", "yep", "sure", "please", "okay", "ok")
        return session.awaitingCareChoice && normalized in affirmative
    }

    private fun isPatternQuestion(normalized: String): Boolean {
        val patternLanguage = listOf(
            "pattern",
            "last week",
            "past week",
            "recent check ins",
            "recent checkin",
            "lately",
            "how have i been",
            "what have my days",
            "show my history"
        )
        return patternLanguage.any(normalized::contains)
    }

    private fun isReminderChange(normalized: String): Boolean {
        val reminderLanguage = listOf("reminder", "remind me", "notification", "notify me", "alarm")
        val changeLanguage = listOf("set", "change", "move", "disable", "cancel", "turn off", "at ")
        return reminderLanguage.any(normalized::contains) && changeLanguage.any(normalized::contains)
    }

    private fun isEndSession(normalized: String): Boolean {
        val exactEndings = setOf(
            "done",
            "im done",
            "i am done",
            "thats all",
            "that is all",
            "goodbye",
            "bye",
            "hang up",
            "end call",
            "never mind",
            "nevermind",
            "no thanks",
            "no thank you"
        )
        return normalized in exactEndings
    }

    private fun decision(
        intent: AgentTurnIntent,
        normalized: String,
        rationale: String
    ) = AgentTurnDecision(intent, normalized, rationale)
}
