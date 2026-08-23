package com.example.domain.agent

import com.example.core.network.CompanionModelService
import com.example.core.network.FirebaseCompanionModelService
import com.example.domain.agent.model.AgentExecutionState
import com.example.domain.agent.model.AgentMessage
import com.example.domain.agent.model.CareActionData
import com.example.domain.agent.model.DailyTexture
import com.example.domain.agent.model.DailyPulseData
import com.example.domain.agent.model.MessageSender
import com.example.domain.agent.model.ToolCallRecord
import com.example.domain.agent.tools.AgentTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class DailyCheckInAgent(
    private val modelService: CompanionModelService = FirebaseCompanionModelService(),
    private val tools: List<AgentTool>
) {

    suspend fun processUserTurn(
        userPrompt: String,
        conversationHistory: List<AgentMessage>,
        onStateChange: (AgentExecutionState, String) -> Unit,
        onToolExecuted: (ToolCallRecord) -> Unit
    ): AgentMessage = withContext(Dispatchers.IO) {
        onStateChange(AgentExecutionState.REASONING, "Reading your self-reported signal...")

        val pulse = extractPulseFromInput(userPrompt)
        val localResponse = generateLocalCompanionResponse(pulse)
        val modelPrompt = buildModelPrompt(userPrompt, conversationHistory)

        val modelResponse = runCatching { modelService.generateReply(modelPrompt) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.takeIf(::usesEverydayLanguage)

        val generatedText = modelResponse ?: localResponse
        val reasoningTrace = if (modelResponse != null) {
            "Firebase AI Logic shaped the companion wording. Local rules saved the everyday texture exactly as reported."
        } else {
            "Local companion wording used. The everyday texture was saved locally; Firebase AI Logic was unavailable or not configured."
        }

        onStateChange(AgentExecutionState.EXECUTING_TOOL, "Saving your check-in and optional care ideas...")
        val executedTools = mutableListOf<ToolCallRecord>()

        tools.forEach { tool ->
            when (tool.name) {
                "RecordSingleInputCheckInTool" -> {
                    val args = mapOf(
                        "inputVal" to userPrompt,
                        "rating" to pulse.ratingValue.toString(),
                        "detectedTexture" to pulse.texture.name,
                        "confidence" to pulse.confidenceScore.toString()
                    )
                    executeAndRecord(tool, args, executedTools, onToolExecuted)
                }

                "TriggerProactiveCareActionTool" -> {
                    if (pulse.ratingValue <= 2 || pulse.isOffDay) {
                        val actionsToQueue = listOf(
                            Triple(
                                "REST_SUPPORT",
                                "Rest or hydrate",
                                "Optional idea: take a moment for water or a quieter pace."
                            ),
                            Triple(
                                "SIMPLIFY",
                                "Lower the cognitive load",
                                "Consider postponing one non-urgent decision if that would help."
                            ),
                            Triple(
                                "LOW_EFFORT_MEAL",
                                "Choose an easy meal",
                                "Optional idea: pick a familiar snack or meal with almost no prep."
                            )
                        )
                        actionsToQueue.forEach { (type, title, description) ->
                            val args = mapOf(
                                "type" to type,
                                "title" to title,
                                "description" to description,
                                "iconName" to "Spa"
                            )
                            executeAndRecord(tool, args, executedTools, onToolExecuted)
                        }
                    }
                }
            }
        }

        onStateChange(AgentExecutionState.COMPLETED, "Check-in saved. Pattern learning continues.")

        AgentMessage(
            id = UUID.randomUUID().toString(),
            sender = MessageSender.AGENT,
            text = generatedText,
            thoughtTrace = reasoningTrace,
            toolInvocations = executedTools,
            structuredPulse = pulse
        )
    }

    private suspend fun executeAndRecord(
        tool: AgentTool,
        args: Map<String, String>,
        records: MutableList<ToolCallRecord>,
        onToolExecuted: (ToolCallRecord) -> Unit
    ) {
        val record = try {
            val result = tool.execute(args)
            ToolCallRecord(
                toolName = tool.name,
                arguments = args,
                resultSummary = result.summary,
                status = if (result.success) "SUCCESS" else "FAILED"
            )
        } catch (error: Exception) {
            ToolCallRecord(
                toolName = tool.name,
                arguments = args,
                resultSummary = "${tool.name} failed: ${error.message ?: "unknown error"}",
                status = "FAILED"
            )
        }
        records += record
        onToolExecuted(record)
    }

    private fun buildModelPrompt(userPrompt: String, history: List<AgentMessage>): String {
        val priorHistory = if (
            history.lastOrNull()?.sender == MessageSender.USER &&
            history.lastOrNull()?.text == userPrompt
        ) {
            history.dropLast(1)
        } else {
            history
        }

        val transcript = priorHistory.takeLast(4).joinToString("\n") { message ->
            val role = if (message.sender == MessageSender.USER) "User" else "Wisteria"
            "$role: ${message.text}"
        }

        return """
            You are Wisteria, a warm, concise daily check-in companion.

            Safety and truth rules:
            - Reply in one or two sentences and ask at most one question.
            - Use only everyday feeling words: bright, steady, heavy, or off.
            - Never turn a feeling into a body phase, condition, cause, or certainty.
            - Never claim Wisteria changed alerts, tasks, contacts, or device settings.
            - Offer gentle options, never instructions.
            - Do not mention implementation details.

            Recent conversation:
            ${transcript.ifBlank { "No earlier messages." }}

            Current check-in: $userPrompt
        """.trimIndent()
    }

    private fun generateLocalCompanionResponse(pulse: DailyPulseData): String = when {
        pulse.isOffDay ->
            "I hear you—today feels off. Want one low-effort idea, or are you done for today?"
        pulse.texture == DailyTexture.HEAVY ->
            "Heavy is logged. You’re done for today unless one small idea would help."
        pulse.texture == DailyTexture.STEADY ->
            "Steady is logged. That’s enough for today."
        pulse.texture == DailyTexture.BRIGHT ->
            "Bright is logged. I’m glad there’s a little more room in today."
        else ->
            "Logged without adding a label. That’s enough for today."
    }

    private fun usesEverydayLanguage(text: String): Boolean {
        val lower = text.lowercase()
        val blockedTerms = listOf(
            "pmdd",
            "pms",
            "follicular",
            "luteal",
            "ovulation",
            "menstrual",
            "medication",
            "diagnosis",
            "spotting",
            "period"
        )
        return blockedTerms.none(lower::contains)
    }

    private fun extractPulseFromInput(userPrompt: String): DailyPulseData {
        val lower = userPrompt.lowercase().trim()
        val explicitRating = Regex("(?<!\\d)([1-5])(?!\\d)")
            .find(lower)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()

        val rating = explicitRating ?: when {
            listOf("awful", "crying", "crash").any(lower::contains) -> 1
            listOf("tired", "foggy", "heavy", "hard").any(lower::contains) -> 2
            listOf("okay", "fine", "managing", "baseline").any(lower::contains) -> 3
            listOf("steady", "clear").any(lower::contains) -> 4
            listOf("alive", "great", "radiant", "good").any(lower::contains) -> 5
            else -> 3
        }

        val texture = when {
            listOf("off", "awful", "crying", "crash").any(lower::contains) || explicitRating == 1 -> DailyTexture.OFF
            listOf("tired", "foggy", "heavy", "hard").any(lower::contains) || explicitRating == 2 -> DailyTexture.HEAVY
            listOf("okay", "fine", "managing", "baseline", "steady").any(lower::contains) || explicitRating == 3 -> DailyTexture.STEADY
            listOf("alive", "clear", "great", "radiant", "good", "bright").any(lower::contains) || explicitRating == 4 || explicitRating == 5 -> DailyTexture.BRIGHT
            else -> DailyTexture.UNKNOWN
        }

        val isOffDay = texture == DailyTexture.OFF
        val needsSupport = texture == DailyTexture.OFF || texture == DailyTexture.HEAVY
        val careActions = if (needsSupport) {
            listOf(
                CareActionData(
                    id = "care_rest_${System.currentTimeMillis()}",
                    title = "Rest or hydrate",
                    type = "REST_SUPPORT",
                    description = "Optional idea: take a moment for water or a quieter pace.",
                    iconName = "Spa"
                ),
                CareActionData(
                    id = "care_meal_${System.currentTimeMillis()}",
                    title = "Choose an easy meal",
                    type = "LOW_EFFORT_MEAL",
                    description = "Optional idea: pick a familiar snack or meal with almost no prep.",
                    iconName = "Restaurant"
                ),
                CareActionData(
                    id = "care_cognitive_${System.currentTimeMillis()}",
                    title = "Lower the cognitive load",
                    type = "SIMPLIFY",
                    description = "Consider postponing one non-urgent decision if that would help.",
                    iconName = "Shield"
                )
            )
        } else {
            emptyList()
        }

        val textureLabel = when (texture) {
            DailyTexture.BRIGHT -> "Bright"
            DailyTexture.STEADY -> "Steady"
            DailyTexture.HEAVY -> "Heavy"
            DailyTexture.OFF -> "Off"
            DailyTexture.UNKNOWN -> "Unlabeled"
        }

        return DailyPulseData(
            ratingValue = rating,
            texture = texture,
            textureLabel = textureLabel,
            singleInputResponse = userPrompt,
            agentAcknowledgment = generateLocalCompanionResponse(
                DailyPulseData(ratingValue = rating, texture = texture, isOffDay = isOffDay)
            ),
            restOrHydrationLogged = listOf("rest", "water", "hydrate", "hydrated").any(lower::contains),
            lowEffortMealSuggested = "Simple meal or snack",
            comfortContent = "Quiet audio or low light",
            isOffDay = isOffDay,
            confidenceScore = if (texture == DailyTexture.UNKNOWN) 0f else 1f,
            careActions = careActions
        )
    }
}
