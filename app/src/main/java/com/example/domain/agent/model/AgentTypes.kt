package com.example.domain.agent.model

enum class AgentExecutionState {
    IDLE,
    CALIBRATING,
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

enum class CycleTexture {
    FEELING_GOOD,      // Alive / Baseline okay
    SPOTTING_PHASE,    // Zoe's long spotting window
    ACTUAL_PERIOD,     // Full bleeding
    MEDS_DROP_WINDOW,  // ~5 days where psych meds stop working (PMDD window)
    UNKNOWN_CALIBRATING
}

data class ToolCallRecord(
    val toolName: String,
    val arguments: Map<String, String>,
    val resultSummary: String,
    val status: String = "SUCCESS",
    val timestamp: Long = System.currentTimeMillis()
)

data class CareActionData(
    val id: String,
    val title: String,
    val type: String, // "NERVE_TONIC", "LOW_EFFORT_MEAL", "COGNITIVE_REDUCTION", "COMFORT_QUEUE", "TRUSTED_CONTACT"
    val description: String,
    val isAutoTriggered: Boolean = true,
    val isCompleted: Boolean = false,
    val iconName: String = "Spa"
)

data class CyclePatternState(
    val currentDayInTexture: Int = 1,
    val detectedTexture: CycleTexture = CycleTexture.FEELING_GOOD,
    val isPmddWindowImminent: Boolean = false,
    val pmddConfidence: Float = 0.88f,
    val daysUntilDropWindow: Int = 2,
    val nerveTonicRecommended: Boolean = false,
    val cognitiveLoadReduced: Boolean = false,
    val summaryInsight: String = "Wisteria is calibrating to your body's specific rhythm without imposing standard 28-day calendar math."
)

data class DailyPulseData(
    val ratingValue: Int = 3, // 1 to 5 scale
    val texture: CycleTexture = CycleTexture.FEELING_GOOD,
    val textureLabel: String = "Feeling Alive / Okay",
    val singleInputResponse: String = "3",
    val agentAcknowledgment: String = "Recorded. Holding space for today.",
    val nerveTonicTaken: Boolean = false,
    val lowEffortMealSuggested: String = "Warm bone broth or easy comfort soup",
    val comfortContent: String = "Lofi rain & studio ghibli ambient sounds",
    val isPmddWindowActive: Boolean = false,
    val confidenceScore: Float = 0.85f,
    val careActions: List<CareActionData> = emptyList()
)

data class AgentMessage(
    val id: String,
    val sender: MessageSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val thoughtTrace: String? = null,
    val toolInvocations: List<ToolCallRecord> = emptyList(),
    val structuredPulse: DailyPulseData? = null
)
