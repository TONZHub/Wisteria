package com.example.domain.agent.tools

import com.example.domain.agent.model.CareActionData
import com.example.domain.agent.model.CyclePatternState
import com.example.domain.agent.model.CycleTexture
import com.example.domain.agent.model.DailyPulseData

data class ToolExecutionResult(
    val success: Boolean,
    val summary: String,
    val outputData: Map<String, Any> = emptyMap()
)

interface AdkAgentTool {
    val name: String
    val description: String
    val parametersSchema: String

    suspend fun execute(parameters: Map<String, String>): ToolExecutionResult
}

class RecordSingleInputCheckInTool(
    private val onCheckInRecorded: suspend (DailyPulseData) -> Unit
) : AdkAgentTool {
    override val name: String = "RecordSingleInputCheckInTool"
    override val description: String = "Logs a 3-second daily single-input check-in (1-5 rating, emoji, or quick word) and determines cycle texture without clinical burden."
    override val parametersSchema: String = "{ inputVal: String, rating: Int, detectedTexture: String }"

    override suspend fun execute(parameters: Map<String, String>): ToolExecutionResult {
        val inputVal = parameters["inputVal"] ?: "3"
        val rating = parameters["rating"]?.toIntOrNull() ?: 3
        val textureStr = parameters["detectedTexture"] ?: "FEELING_GOOD"
        val texture = try {
            CycleTexture.valueOf(textureStr)
        } catch (e: Exception) {
            CycleTexture.FEELING_GOOD
        }

        val isDrop = texture == CycleTexture.MEDS_DROP_WINDOW || rating <= 2
        val pulse = DailyPulseData(
            ratingValue = rating,
            texture = texture,
            textureLabel = when (texture) {
                CycleTexture.FEELING_GOOD -> "Alive & Baseline Okay"
                CycleTexture.SPOTTING_PHASE -> "Long Spotting Window"
                CycleTexture.ACTUAL_PERIOD -> "Actual Period"
                CycleTexture.MEDS_DROP_WINDOW -> "Drop Window (PMDD)"
                CycleTexture.UNKNOWN_CALIBRATING -> "Calibrating"
            },
            singleInputResponse = inputVal,
            agentAcknowledgment = if (isDrop)
                "Noted. Harder window detected. Cognitive load reduced, gentle care queued."
            else
                "Logged in 2 seconds. Holding your rhythm.",
            isPmddWindowActive = isDrop,
            confidenceScore = 0.92f
        )

        onCheckInRecorded(pulse)
        return ToolExecutionResult(
            success = true,
            summary = "Recorded single-input '$inputVal' (Rating $rating/5, Texture: ${pulse.textureLabel})",
            outputData = mapOf("pulse" to pulse)
        )
    }
}

class DetectPMDDWindowTool(
    private val onPatternUpdated: suspend (CyclePatternState) -> Unit
) : AdkAgentTool {
    override val name: String = "DetectPMDDWindowTool"
    override val description: String = "Analyzes irregular cycle textures (spotting vs period vs a learned meds-efficacy drop window) from behavioral & symptom history instead of standard 28-day calendar math."
    override val parametersSchema: String = "{ daysInSpotting: Int, medsEfficacyDrop: Boolean, confidence: Float }"

    override suspend fun execute(parameters: Map<String, String>): ToolExecutionResult {
        val daysInSpotting = parameters["daysInSpotting"]?.toIntOrNull() ?: 6
        val medsDrop = parameters["medsEfficacyDrop"]?.toBoolean() ?: true
        val confidence = parameters["confidence"]?.toFloatOrNull() ?: 0.91f

        val pattern = CyclePatternState(
            currentDayInTexture = daysInSpotting,
            detectedTexture = if (medsDrop) CycleTexture.MEDS_DROP_WINDOW else CycleTexture.SPOTTING_PHASE,
            isPmddWindowImminent = !medsDrop && daysInSpotting >= 7,
            pmddConfidence = confidence,
            daysUntilDropWindow = if (medsDrop) 0 else (10 - daysInSpotting).coerceAtLeast(0),
            nerveTonicRecommended = true,
            cognitiveLoadReduced = medsDrop,
            summaryInsight = if (medsDrop)
                "Meds drop window active. Lowering demands, avoiding decision fatigue, prioritising rest and low-effort care."
            else
                "Spotting phase tracked (Day $daysInSpotting/10). Approaching medication efficacy drop window in ~${(10 - daysInSpotting).coerceAtLeast(1)} days."
        )

        onPatternUpdated(pattern)
        return ToolExecutionResult(
            success = true,
            summary = "Pattern analyzed: ${pattern.summaryInsight}",
            outputData = mapOf("pattern" to pattern)
        )
    }
}

class TriggerProactiveCareActionTool(
    private val onCareActionTriggered: suspend (CareActionData) -> Unit
) : AdkAgentTool {
    override val name: String = "TriggerProactiveCareActionTool"
    override val description: String = "Executes pre-configured proactive care actions before the PMDD crash (rest reminder, low-effort comfort meal, cognitive load reduction, comfort queue, trusted contact alert)."
    override val parametersSchema: String = "{ type: String, title: String, description: String, iconName: String }"

    override suspend fun execute(parameters: Map<String, String>): ToolExecutionResult {
        val type = parameters["type"] ?: "REST_SUPPORT"
        val title = parameters["title"] ?: "Prioritize Rest & Hydration"
        val desc = parameters["description"] ?: "Pre-emptive rest before a drop window hits."
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
            summary = "Triggered proactive care action: $title",
            outputData = mapOf("careAction" to action)
        )
    }
}

class FirestoreSyncTool(
    private val onSyncRequested: suspend (DailyPulseData) -> String
) : AdkAgentTool {
    override val name: String = "FirestoreSyncTool"
    override val description: String = "Persists single-input check-ins, irregular cycle texture timeline, and agent memory state to Google Cloud Firestore."
    override val parametersSchema: String = "{ rating: Int, texture: String, textureLabel: String, singleInputResponse: String, agentAcknowledgment: String, isPmddWindowActive: Boolean, nerveTonicTaken: Boolean, lowEffortMealSuggested: String, comfortContent: String, confidenceScore: Float }"

    override suspend fun execute(parameters: Map<String, String>): ToolExecutionResult {
        val texture = try {
            CycleTexture.valueOf(parameters["texture"] ?: "FEELING_GOOD")
        } catch (e: Exception) {
            CycleTexture.FEELING_GOOD
        }
        val pulse = DailyPulseData(
            ratingValue = parameters["rating"]?.toIntOrNull() ?: 3,
            texture = texture,
            textureLabel = parameters["textureLabel"] ?: "",
            singleInputResponse = parameters["singleInputResponse"] ?: "",
            agentAcknowledgment = parameters["agentAcknowledgment"] ?: "",
            isPmddWindowActive = parameters["isPmddWindowActive"]?.toBoolean() ?: false,
            nerveTonicTaken = parameters["nerveTonicTaken"]?.toBoolean() ?: false,
            lowEffortMealSuggested = parameters["lowEffortMealSuggested"] ?: "",
            comfortContent = parameters["comfortContent"] ?: "",
            confidenceScore = parameters["confidenceScore"]?.toFloatOrNull() ?: 0.85f
        )
        val docPath = onSyncRequested(pulse)
        return ToolExecutionResult(
            success = true,
            summary = "Synced cycle state & memory to Firestore at path: $docPath",
            outputData = mapOf("firestorePath" to docPath)
        )
    }
}

class CloudRunWorkflowTool(
    private val onCloudRunTriggered: suspend (String) -> String
) : AdkAgentTool {
    override val name: String = "CloudRunWorkflowTool"
    override val description: String = "Executes asynchronous background Cloud Run agent worker for multi-day pattern recognition and proactive care scheduling."
    override val parametersSchema: String = "{ workflowType: String, priority: String }"

    override suspend fun execute(parameters: Map<String, String>): ToolExecutionResult {
        val workflowType = parameters["workflowType"] ?: "pmdd_pattern_analysis"
        val jobId = onCloudRunTriggered(workflowType)
        return ToolExecutionResult(
            success = true,
            summary = "Dispatched Cloud Run async agent job '$jobId' ($workflowType)",
            outputData = mapOf("jobId" to jobId)
        )
    }
}
