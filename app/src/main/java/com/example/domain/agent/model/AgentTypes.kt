package com.example.domain.agent.model

enum class AgentExecutionState {
    IDLE,
    LEARNING,
    REASONING,
    EXECUTING_TOOL,
    SYNTHESIZING,
    COMPLETED,
    ERROR
}

enum class MessageSender {
    USER,
    AGENT,
    SYSTEM,
    TOOL_RESULT
}

enum class AgentTurnIntent {
    CHECK_IN,
    CARE_REQUEST,
    PATTERN_QUESTION,
    REMINDER_CHANGE,
    FOLLOW_UP,
    END_SESSION,
    GENERAL,
    DUPLICATE_CHECK_IN
}

enum class DailyTexture {
    BRIGHT,
    STEADY,
    HEAVY,
    OFF,
    UNKNOWN
}

data class ToolCallRecord(
    val toolName: String,
    val arguments: Map<String, String>,
    val resultSummary: String,
    val status: String = "SUCCESS",
    val timestamp: Long = System.currentTimeMillis()
)

data class AgentRuntimeTrace(
    val framework: String,
    val model: String,
    val sessionId: String,
    val eventCount: Int,
    val resolvedModelVersion: String? = null
)

data class CareActionData(
    val id: String,
    val title: String,
    val type: String, // "REST_SUPPORT", "LOW_EFFORT_MEAL", "SIMPLIFY", "COMFORT_QUEUE"
    val description: String,
    val isAutoTriggered: Boolean = true,
    val isCompleted: Boolean = false,
    val iconName: String = "Spa"
)

data class DailyPulseData(
    val ratingValue: Int = 3, // 1 to 5 scale
    val texture: DailyTexture = DailyTexture.UNKNOWN,
    val textureLabel: String = "Learning your pattern",
    val singleInputResponse: String = "",
    val agentAcknowledgment: String = "Check-in recorded.",
    val restOrHydrationLogged: Boolean = false,
    val lowEffortMealSuggested: String = "Simple meal or snack",
    val comfortContent: String = "Quiet audio or low light",
    val isOffDay: Boolean = false,
    val confidenceScore: Float = 0f,
    val careActions: List<CareActionData> = emptyList()
)

data class AgentMessage(
    val id: String,
    val sender: MessageSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val thoughtTrace: String? = null,
    val runtimeTrace: AgentRuntimeTrace? = null,
    val toolInvocations: List<ToolCallRecord> = emptyList(),
    val structuredPulse: DailyPulseData? = null,
    val turnIntent: AgentTurnIntent? = null
)
