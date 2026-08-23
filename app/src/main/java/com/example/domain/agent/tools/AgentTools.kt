package com.example.domain.agent.tools

import com.example.domain.agent.model.CareActionData
import com.example.domain.agent.model.DailyTexture
import com.example.domain.agent.model.DailyPulseData

data class ToolExecutionResult(
    val success: Boolean,
    val summary: String,
    val outputData: Map<String, Any> = emptyMap()
)

interface AgentTool {
    val name: String
    val description: String
    val parametersSchema: String

    suspend fun execute(parameters: Map<String, String>): ToolExecutionResult
}

class RecordSingleInputCheckInTool(
    private val onCheckInRecorded: suspend (DailyPulseData) -> Unit
) : AgentTool {
    override val name: String = "RecordSingleInputCheckInTool"
    override val description: String = "Logs a 3-second check-in and keeps the everyday texture: bright, steady, heavy, off, or unlabeled."
    override val parametersSchema: String = "{ inputVal: String, rating: Int, detectedTexture: String, confidence: Float }"

    override suspend fun execute(parameters: Map<String, String>): ToolExecutionResult {
        val inputVal = parameters["inputVal"] ?: "3"
        val rating = parameters["rating"]?.toIntOrNull() ?: 3
        val textureStr = parameters["detectedTexture"] ?: "UNKNOWN"
        val texture = try {
            DailyTexture.valueOf(textureStr)
        } catch (e: Exception) {
            DailyTexture.UNKNOWN
        }

        val isOff = texture == DailyTexture.OFF
        val needsSupport = texture == DailyTexture.OFF || texture == DailyTexture.HEAVY
        val pulse = DailyPulseData(
            ratingValue = rating,
            texture = texture,
            textureLabel = when (texture) {
                DailyTexture.BRIGHT -> "Bright"
                DailyTexture.STEADY -> "Steady"
                DailyTexture.HEAVY -> "Heavy"
                DailyTexture.OFF -> "Off"
                DailyTexture.UNKNOWN -> "Unlabeled"
            },
            singleInputResponse = inputVal,
            agentAcknowledgment = if (needsSupport)
                "Noted. Gentle, optional care ideas are ready."
            else
                "Logged in 2 seconds. Holding your rhythm.",
            isOffDay = isOff,
            confidenceScore = parameters["confidence"]?.toFloatOrNull() ?: 0f
        )

        onCheckInRecorded(pulse)
        return ToolExecutionResult(
            success = true,
            summary = "Recorded single-input '$inputVal' (Rating $rating/5, Texture: ${pulse.textureLabel})",
            outputData = mapOf("pulse" to pulse)
        )
    }
}

class TriggerProactiveCareActionTool(
    private val onCareActionTriggered: suspend (CareActionData) -> Unit
) : AgentTool {
    override val name: String = "TriggerProactiveCareActionTool"
    override val description: String = "Records optional, low-effort care ideas inside Wisteria; it does not change device settings or contact anyone."
    override val parametersSchema: String = "{ type: String, title: String, description: String, iconName: String }"

    override suspend fun execute(parameters: Map<String, String>): ToolExecutionResult {
        val type = parameters["type"] ?: "REST_SUPPORT"
        val title = parameters["title"] ?: "Prioritize Rest & Hydration"
        val desc = parameters["description"] ?: "Optional idea: pause or choose a quieter pace."
        val icon = parameters["iconName"] ?: "Spa"

        val action = CareActionData(
            id = "care_${System.currentTimeMillis()}_${(100..999).random()}",
            title = title,
            type = type,
            description = desc,
            isAutoTriggered = true,
            isCompleted = false,
            iconName = icon
        )

        onCareActionTriggered(action)
        return ToolExecutionResult(
            success = true,
            summary = "Saved optional care idea: $title",
            outputData = mapOf("careAction" to action)
        )
    }
}

class FirestoreSyncTool(
    private val onSyncRequested: suspend (DailyPulseData) -> String
) : AgentTool {
    override val name: String = "FirestoreSyncTool"
    override val description: String = "Persists a check-in to the signed-in user's optional Firestore daily timeline."
    override val parametersSchema: String = "{ rating: Int, texture: String, textureLabel: String, singleInputResponse: String, agentAcknowledgment: String, isOffDay: Boolean, restOrHydrationLogged: Boolean, lowEffortMealSuggested: String, comfortContent: String, confidenceScore: Float }"

    override suspend fun execute(parameters: Map<String, String>): ToolExecutionResult {
        val texture = try {
            DailyTexture.valueOf(parameters["texture"] ?: "UNKNOWN")
        } catch (e: Exception) {
            DailyTexture.UNKNOWN
        }
        val pulse = DailyPulseData(
            ratingValue = parameters["rating"]?.toIntOrNull() ?: 3,
            texture = texture,
            textureLabel = parameters["textureLabel"] ?: "",
            singleInputResponse = parameters["singleInputResponse"] ?: "",
            agentAcknowledgment = parameters["agentAcknowledgment"] ?: "",
            isOffDay = parameters["isOffDay"]?.toBoolean() ?: false,
            restOrHydrationLogged = parameters["restOrHydrationLogged"]?.toBoolean() ?: false,
            lowEffortMealSuggested = parameters["lowEffortMealSuggested"] ?: "",
            comfortContent = parameters["comfortContent"] ?: "",
            confidenceScore = parameters["confidenceScore"]?.toFloatOrNull() ?: 0f
        )
        val docPath = onSyncRequested(pulse)
        return ToolExecutionResult(
            success = true,
            summary = "Synced the daily check-in to Firestore at path: $docPath",
            outputData = mapOf("firestorePath" to docPath)
        )
    }
}
