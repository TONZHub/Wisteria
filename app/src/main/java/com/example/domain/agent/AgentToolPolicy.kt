package com.example.domain.agent

import com.example.domain.agent.model.AgentTurnIntent
import com.example.domain.agent.model.DailyPulseData
import com.example.domain.agent.tools.AgentTool

data class ToolAuthorization(
    val allowed: Boolean,
    val reason: String
)

/** The model cannot bypass this gate. Write-capable tools run only for a matching local intent. */
class AgentToolPolicy {
    fun authorize(
        tool: AgentTool,
        intent: AgentTurnIntent,
        pulse: DailyPulseData?,
        session: AgentSessionState
    ): ToolAuthorization = when (tool.name) {
        "RecordSingleInputCheckInTool" -> if (intent == AgentTurnIntent.CHECK_IN && pulse != null) {
            ToolAuthorization(true, "An explicit check-in may create one local record.")
        } else {
            ToolAuthorization(false, "Only an explicit check-in may create a local record.")
        }

        "TriggerProactiveCareActionTool" -> if (
            intent == AgentTurnIntent.CHECK_IN &&
            pulse != null &&
            (pulse.ratingValue <= 2 || pulse.isOffDay) &&
            !session.careIdeasQueued
        ) {
            ToolAuthorization(true, "A low check-in may queue optional local ideas once per conversation.")
        } else {
            ToolAuthorization(false, "This turn is not authorized to add care ideas.")
        }

        else -> ToolAuthorization(false, "Unknown tools are denied unless the local policy explicitly allows them.")
    }
}
