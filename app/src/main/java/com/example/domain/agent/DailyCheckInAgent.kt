package com.example.domain.agent

import com.example.core.network.CompanionModelService
import com.example.core.network.FirebaseCompanionModelService
import com.example.domain.agent.model.AgentExecutionState
import com.example.domain.agent.model.AgentMessage
import com.example.domain.agent.model.AgentTurnIntent
import com.example.domain.agent.model.CareActionData
import com.example.domain.agent.model.DailyTexture
import com.example.domain.agent.model.DailyPulseData
import com.example.domain.agent.model.MessageSender
import com.example.domain.agent.model.ToolCallRecord
import com.example.domain.agent.tools.AgentTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

class DailyCheckInAgent(
    private val modelService: CompanionModelService = FirebaseCompanionModelService(),
    private val tools: List<AgentTool>,
    private val turnRouter: AgentTurnRouter = AgentTurnRouter(),
    private val toolPolicy: AgentToolPolicy = AgentToolPolicy()
) {
    private val turnMutex = Mutex()
    private val sessionLock = Any()

    private var sessionState = AgentSessionState()
    private var sessionEpoch = 0L

    fun startNewSession() {
        synchronized(sessionLock) {
            sessionEpoch += 1
            sessionState = AgentSessionState()
        }
    }

    fun endSession() {
        synchronized(sessionLock) {
            sessionEpoch += 1
            sessionState = AgentSessionState()
        }
    }

    suspend fun processUserTurn(
        userPrompt: String,
        conversationHistory: List<AgentMessage>,
        healthContext: String? = null,
        requestedIntent: AgentTurnIntent? = null,
        onStateChange: (AgentExecutionState, String) -> Unit,
        onToolExecuted: (ToolCallRecord) -> Unit
    ): AgentMessage = turnMutex.withLock {
        withContext(Dispatchers.IO) {
            val (sessionAtStart, epochAtStart) = synchronized(sessionLock) {
                sessionState to sessionEpoch
            }
            val decision = turnRouter.route(userPrompt, sessionAtStart, requestedIntent)
            onStateChange(AgentExecutionState.REASONING, reasoningStatus(decision.intent))

            val pulse = if (decision.intent == AgentTurnIntent.CHECK_IN) {
                extractPulseFromInput(userPrompt)
            } else {
                null
            }
            val localResponse = generateLocalResponse(
                intent = decision.intent,
                pulse = pulse,
                currentPulse = sessionAtStart.currentPulse,
                userPrompt = userPrompt
            )

            val modelResponse = if (shouldAskModel(decision.intent)) {
                val modelPrompt = buildModelPrompt(
                    userPrompt = userPrompt,
                    history = conversationHistory,
                    healthContext = healthContext,
                    intent = decision.intent,
                    currentPulse = sessionAtStart.currentPulse
                )
                runCatching { modelService.generateReply(modelPrompt) }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?.takeIf { usesAllowedWording(it, decision.intent) }
            } else {
                null
            }

            val generatedText = modelResponse ?: localResponse
            val executedTools = mutableListOf<ToolCallRecord>()

            if (pulse != null) {
                tools.forEach { tool ->
                    if (!isSessionCurrent(epochAtStart)) return@forEach
                    val authorization = toolPolicy.authorize(
                        tool = tool,
                        intent = decision.intent,
                        pulse = pulse,
                        session = sessionAtStart
                    )
                    if (!authorization.allowed) return@forEach

                    when (tool.name) {
                        "RecordSingleInputCheckInTool" -> {
                            onStateChange(AgentExecutionState.EXECUTING_TOOL, "Saving this check-in once…")
                            val args = mapOf(
                                "inputVal" to userPrompt,
                                "rating" to pulse.ratingValue.toString(),
                                "detectedTexture" to pulse.texture.name,
                                "confidence" to pulse.confidenceScore.toString(),
                                "agentAcknowledgment" to generatedText
                            )
                            executeAndRecord(tool, args, executedTools, onToolExecuted)
                        }

                        "TriggerProactiveCareActionTool" -> {
                            onStateChange(AgentExecutionState.EXECUTING_TOOL, "Saving optional ideas locally…")
                            optionalCareActions().forEach { (type, title, description) ->
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

            val checkInSaved = executedTools.any {
                it.toolName == "RecordSingleInputCheckInTool" && it.status == "SUCCESS"
            }
            val careIdeasSaved = executedTools.any {
                it.toolName == "TriggerProactiveCareActionTool" && it.status == "SUCCESS"
            }

            val nextSessionState = when (decision.intent) {
                AgentTurnIntent.CHECK_IN -> sessionAtStart.copy(
                    currentPulse = if (checkInSaved) pulse else sessionAtStart.currentPulse,
                    recordedInputs = if (checkInSaved) {
                        sessionAtStart.recordedInputs + decision.normalizedInput
                    } else {
                        sessionAtStart.recordedInputs
                    },
                    careIdeasQueued = sessionAtStart.careIdeasQueued || careIdeasSaved,
                    awaitingCareChoice = checkInSaved && pulse?.let(::needsSupport) == true
                )

                AgentTurnIntent.CARE_REQUEST -> sessionAtStart.copy(awaitingCareChoice = false)
                AgentTurnIntent.END_SESSION -> AgentSessionState()
                else -> sessionAtStart
            }
            synchronized(sessionLock) {
                if (sessionEpoch == epochAtStart) {
                    sessionState = nextSessionState
                }
            }

            onStateChange(
                AgentExecutionState.COMPLETED,
                completionStatus(decision.intent, checkInSaved)
            )

            AgentMessage(
                id = UUID.randomUUID().toString(),
                sender = MessageSender.AGENT,
                text = generatedText,
                thoughtTrace = buildReasoningTrace(decision, modelResponse != null, checkInSaved),
                toolInvocations = executedTools,
                structuredPulse = if (decision.intent == AgentTurnIntent.CHECK_IN) pulse else null,
                turnIntent = decision.intent
            )
        }
    }

    private suspend fun executeAndRecord(
        tool: AgentTool,
        args: Map<String, String>,
        records: MutableList<ToolCallRecord>,
        onToolExecuted: (ToolCallRecord) -> Unit
    ): ToolCallRecord {
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
        return record
    }

    private fun buildModelPrompt(
        userPrompt: String,
        history: List<AgentMessage>,
        healthContext: String?,
        intent: AgentTurnIntent,
        currentPulse: DailyPulseData?
    ): String {
        val priorHistory = if (
            history.lastOrNull()?.sender == MessageSender.USER &&
            history.lastOrNull()?.text == userPrompt
        ) {
            history.dropLast(1)
        } else {
            history
        }

        val transcript = priorHistory.takeLast(6).joinToString("\n") { message ->
            val role = if (message.sender == MessageSender.USER) "User" else "Wisteria"
            "$role: ${message.text}"
        }
        val sessionSummary = currentPulse?.let {
            "A check-in already exists in this conversation: ${it.textureLabel.lowercase()} (${it.ratingValue}/5)."
        } ?: "No check-in has been saved in this conversation yet."

        return """
            You are Wisteria, a warm, concise everyday check-in companion.

            Safety and truth rules:
            - Reply in one or two sentences and ask at most one question.
            - Use only everyday feeling words: bright, steady, heavy, or off.
            - Never turn a feeling into a body phase, condition, cause, or certainty.
            - Never mention "luteal", "follicular", "menstrual", "period", or "cycle" in your response.
            - Offer gentle options, never instructions.
            - Never claim that anything was saved, logged, recorded, changed, or updated. The app reports tool results separately.
            - Do not mention implementation details.

            Local turn classification (FINAL): $intent
            $sessionSummary

            Background Context (SILENT - DO NOT MENTION):
            ${healthContext ?: "No additional health signals."}

            Recent conversation:
            ${transcript.ifBlank { "No earlier messages." }}

            Current turn: $userPrompt
        """.trimIndent()
    }

    private fun shouldAskModel(intent: AgentTurnIntent): Boolean = intent in setOf(
        AgentTurnIntent.CHECK_IN,
        AgentTurnIntent.FOLLOW_UP,
        AgentTurnIntent.GENERAL
    )

    private fun generateLocalResponse(
        intent: AgentTurnIntent,
        pulse: DailyPulseData?,
        currentPulse: DailyPulseData?,
        userPrompt: String
    ): String = when (intent) {
        AgentTurnIntent.CHECK_IN -> generateLocalCheckInResponse(requireNotNull(pulse))
        AgentTurnIntent.CARE_REQUEST -> generateCareIdea(currentPulse)
        AgentTurnIntent.PATTERN_QUESTION ->
            "I won't invent a pattern from this conversation. Rhythm Memory has the saved local view."
        AgentTurnIntent.REMINDER_CHANGE ->
            "I heard the reminder request. Nothing changed—use Reminder settings to choose or confirm the time."
        AgentTurnIntent.FOLLOW_UP -> if (userPrompt.contains("why", ignoreCase = true)) {
            "I can reflect what you told me, but I won't guess at why today feels this way."
        } else {
            "I'm with you. Say a little more, or tell me you're done."
        }
        AgentTurnIntent.END_SESSION -> "All right. I'm here when you want me."
        AgentTurnIntent.GENERAL ->
            "I'm here. You can check in with bright, steady, heavy, off, or a number from 1 to 5."
        AgentTurnIntent.DUPLICATE_CHECK_IN ->
            "I already have that check-in for this conversation, so I didn't add it twice."
    }

    private fun generateLocalCheckInResponse(pulse: DailyPulseData): String = when {
        pulse.isOffDay ->
            "I hear you—today feels off. Would one low-effort idea help?"
        pulse.texture == DailyTexture.HEAVY ->
            "I hear heavy. Would one small idea help?"
        pulse.texture == DailyTexture.STEADY ->
            "Steady makes sense. That's enough for today."
        pulse.texture == DailyTexture.BRIGHT ->
            "Bright—there's a little more room in today."
        else ->
            "I have your check-in without forcing a label. That's enough for today."
    }

    private fun generateCareIdea(currentPulse: DailyPulseData?): String = when (currentPulse?.texture) {
        DailyTexture.OFF,
        DailyTexture.HEAVY ->
            "One easy option: water and a quieter pace for a few minutes. Take it or leave it."
        DailyTexture.STEADY ->
            "One gentle option: keep the next step familiar and small."
        DailyTexture.BRIGHT ->
            "One gentle option: leave a little room for whatever is helping today feel bright."
        DailyTexture.UNKNOWN,
        null ->
            "One easy option: pause for water or a quieter minute, only if that sounds useful."
    }

    private fun usesAllowedWording(text: String, intent: AgentTurnIntent): Boolean {
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
        val falseWriteClaims = listOf("saved", "logged", "recorded", "updated", "changed", "set your")
        return blockedTerms.none(lower::contains) &&
            falseWriteClaims.none(lower::contains) &&
            intent != AgentTurnIntent.DUPLICATE_CHECK_IN
    }

    private fun buildReasoningTrace(
        decision: AgentTurnDecision,
        usedModel: Boolean,
        checkInSaved: Boolean
    ): String {
        val wordingSource = if (usedModel) {
            "Firebase AI Logic shaped the wording after the local decision."
        } else {
            "Local companion wording was used."
        }
        val writeResult = when {
            decision.intent == AgentTurnIntent.CHECK_IN && checkInSaved ->
                "Local policy authorized one check-in record."
            decision.intent == AgentTurnIntent.CHECK_IN ->
                "The check-in write did not complete."
            decision.intent == AgentTurnIntent.DUPLICATE_CHECK_IN ->
                "Duplicate writes were blocked."
            else ->
                "No new check-in or setting change was authorized."
        }
        return "${decision.rationale} $wordingSource $writeResult"
    }

    private fun reasoningStatus(intent: AgentTurnIntent): String = when (intent) {
        AgentTurnIntent.CHECK_IN -> "Reading this check-in…"
        AgentTurnIntent.CARE_REQUEST -> "Finding one gentle option…"
        AgentTurnIntent.PATTERN_QUESTION -> "Checking what can be answered honestly…"
        AgentTurnIntent.REMINDER_CHANGE -> "Checking permission before any setting change…"
        AgentTurnIntent.FOLLOW_UP -> "Following the conversation…"
        AgentTurnIntent.END_SESSION -> "Closing this conversation…"
        AgentTurnIntent.GENERAL -> "Understanding what you need…"
        AgentTurnIntent.DUPLICATE_CHECK_IN -> "Checking for a duplicate turn…"
    }

    private fun completionStatus(intent: AgentTurnIntent, checkInSaved: Boolean): String = when {
        intent == AgentTurnIntent.CHECK_IN && checkInSaved -> "Check-in saved once."
        intent == AgentTurnIntent.CHECK_IN -> "Check-in not saved. Your message is still here."
        intent == AgentTurnIntent.DUPLICATE_CHECK_IN -> "Already saved; duplicate write blocked."
        intent == AgentTurnIntent.REMINDER_CHANGE -> "Reminder unchanged."
        intent == AgentTurnIntent.END_SESSION -> "Conversation closed."
        else -> "Conversation continued. No new check-in saved."
    }

    private fun optionalCareActions(): List<Triple<String, String, String>> = listOf(
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

    private fun needsSupport(pulse: DailyPulseData): Boolean =
        pulse.texture == DailyTexture.OFF || pulse.texture == DailyTexture.HEAVY

    private fun isSessionCurrent(epoch: Long): Boolean = synchronized(sessionLock) {
        sessionEpoch == epoch
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
            agentAcknowledgment = generateLocalCheckInResponse(
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
