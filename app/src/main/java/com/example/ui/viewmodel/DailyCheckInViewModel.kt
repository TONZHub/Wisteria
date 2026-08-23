package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.cloud.CloudRunJobExecution
import com.example.data.cloud.FirestoreSyncRecord
import com.example.data.local.WisteriaDatabase
import com.example.data.local.entity.CareActionEntity
import com.example.data.local.entity.AgentMemoryEntity
import com.example.data.local.entity.DailyCheckInEntity
import com.example.data.repository.WisteriaRepository
import com.example.domain.agent.model.AgentExecutionState
import com.example.domain.agent.model.AgentMessage
import com.example.domain.agent.model.CareActionData
import com.example.domain.agent.model.CyclePatternState
import com.example.domain.agent.model.CycleTexture
import com.example.domain.agent.model.DailyPulseData
import com.example.domain.agent.model.MessageSender
import com.example.domain.agent.model.ToolCallRecord
import com.example.domain.agent.MorningBrief
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class CheckInUiState(
    val messages: List<AgentMessage> = emptyList(),
    val agentState: AgentExecutionState = AgentExecutionState.IDLE,
    val agentStatusMessage: String = "Wisteria is calibrated & ready",
    val latestPulse: DailyPulseData = DailyPulseData(),
    val patternState: CyclePatternState = CyclePatternState(),
    val executedTools: List<ToolCallRecord> = emptyList(),
    val cloudRunLogs: List<CloudRunJobExecution> = emptyList(),
    val firestoreSyncLogs: List<FirestoreSyncRecord> = emptyList(),
    val cyclePhases: List<Map<String, Any>> = emptyList(),
    val morningBrief: MorningBrief? = null,
    val isNightShiftRunning: Boolean = false,
    val isAgentActive: Boolean = false,
    val showStartupAnimation: Boolean = false,
    val isFullScreenTakeoverActive: Boolean = false,
    val startupBootLog: List<String> = emptyList()
)

class DailyCheckInViewModel(application: Application) : AndroidViewModel(application) {
    private val database = WisteriaDatabase.getInstance(application)
    private val repository = WisteriaRepository(database.checkInDao())

    private val _uiState = MutableStateFlow(CheckInUiState())
    val uiState: StateFlow<CheckInUiState> = _uiState.asStateFlow()

    val allCheckIns: StateFlow<List<DailyCheckInEntity>> = repository.getAllCheckInsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val latestSavedCheckIn: StateFlow<DailyCheckInEntity?> = repository.getLatestCheckInFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val careActions: StateFlow<List<CareActionEntity>> = repository.getAllCareActionsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val agentMemories: StateFlow<List<AgentMemoryEntity>> = repository.getAllMemoriesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Initialize the welcome / startup greeting
        val initialAgentMessage = AgentMessage(
            id = UUID.randomUUID().toString(),
            sender = MessageSender.AGENT,
            text = """
                Wisteria is ready. Let's start simple.

                How are you feeling today? (1–5, an emoji, or one word)
            """.trimIndent(),
            thoughtTrace = "Initialized system pattern recognition. Standing by for single-input 3-second daily pulse."
        )
        _uiState.value = _uiState.value.copy(
            messages = listOf(initialAgentMessage)
        )
        loadCycleMemory()
    }

    private fun loadCycleMemory() {
        viewModelScope.launch {
            try {
                val phases = repository.getCycleMemorySnapshot()
                _uiState.value = _uiState.value.copy(cyclePhases = phases)
            } catch (e: Exception) {
                // Firestore not reachable/configured yet - screens fall back to their own defaults.
            }
        }
    }

    fun runStartupBootSequence() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                showStartupAnimation = true,
                startupBootLog = emptyList()
            )
            val bootSteps = listOf(
                "initializing pattern recognition...",
                "connecting to Gemini 3.5 Flash & Google ADK...",
                "retrieving your irregular cycle timeline from Firestore...",
                "calibrating to you...",
                "Wisteria is ready."
            )
            for (step in bootSteps) {
                delay(380)
                _uiState.value = _uiState.value.copy(
                    startupBootLog = _uiState.value.startupBootLog + step
                )
            }
            delay(500)
            _uiState.value = _uiState.value.copy(showStartupAnimation = false)
        }
    }

    fun openFullScreenTakeover() {
        _uiState.value = _uiState.value.copy(isFullScreenTakeoverActive = true)
    }

    fun closeFullScreenTakeover() {
        _uiState.value = _uiState.value.copy(isFullScreenTakeoverActive = false)
    }

    fun submitSingleInputCheckIn(input: String) {
        if (input.isBlank()) return
        closeFullScreenTakeover()
        sendMessage(input)
    }

    fun sendMessage(userText: String) {
        if (userText.isBlank()) return

        val userMessage = AgentMessage(
            id = UUID.randomUUID().toString(),
            sender = MessageSender.USER,
            text = userText
        )

        val updatedMessages = _uiState.value.messages + userMessage
        _uiState.value = _uiState.value.copy(
            messages = updatedMessages,
            agentState = AgentExecutionState.REASONING,
            agentStatusMessage = "Calibrating pattern with Gemini 3.5 Flash...",
            isAgentActive = true
        )

        viewModelScope.launch {
            val agentResponse = repository.checkInAgent.processUserTurn(
                userPrompt = userText,
                conversationHistory = updatedMessages,
                onStateChange = { state, status ->
                    _uiState.value = _uiState.value.copy(
                        agentState = state,
                        agentStatusMessage = status
                    )
                },
                onToolExecuted = { toolRecord ->
                    val toolsList = _uiState.value.executedTools + toolRecord
                    _uiState.value = _uiState.value.copy(executedTools = toolsList)
                }
            )

            val newPulse = agentResponse.structuredPulse ?: _uiState.value.latestPulse
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + agentResponse,
                agentState = AgentExecutionState.IDLE,
                agentStatusMessage = "Wisteria is calibrated & ready",
                latestPulse = newPulse,
                isAgentActive = false
            )
        }
    }

    fun sendQuickOption(rating: Int, label: String) {
        sendMessage("$rating ($label)")
    }

    fun toggleCareAction(id: String, isCompleted: Boolean) {
        viewModelScope.launch {
            repository.toggleCareAction(id, isCompleted)
        }
    }

    /** Runs the real Night Shift pattern worker against local check-in history. */
    fun runNightShift() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isNightShiftRunning = true, agentStatusMessage = "Running Night Shift...")
            try {
                val job = repository.runNightShift()
                val logs = _uiState.value.cloudRunLogs + job
                _uiState.value = _uiState.value.copy(
                    cloudRunLogs = logs,
                    morningBrief = job.morningBrief,
                    isNightShiftRunning = false,
                    agentStatusMessage = job.morningBrief?.headline ?: "Night Shift complete."
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isNightShiftRunning = false,
                    agentStatusMessage = "Night Shift failed: ${e.message ?: "unknown error"}"
                )
            }
        }
    }

    /** Kept for back-compat with older call sites; Night Shift is now the real Cloud Run worker. */
    fun triggerCloudRunWorkflow(workflowType: String = "night_shift") {
        runNightShift()
    }

    fun triggerFirestoreSync() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(agentStatusMessage = "Syncing cycle timeline to Firestore...")
            try {
                val record = repository.triggerManualFirestoreSync(_uiState.value.latestPulse)
                val logs = _uiState.value.firestoreSyncLogs + record
                _uiState.value = _uiState.value.copy(
                    firestoreSyncLogs = logs,
                    agentStatusMessage = "Firestore synced to ${record.documentPath}"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    agentStatusMessage = "Firestore sync failed: ${e.message ?: "check google-services.json is configured"}"
                )
            }
        }
    }

    fun resetConversation() {
        val initialAgentMessage = AgentMessage(
            id = UUID.randomUUID().toString(),
            sender = MessageSender.AGENT,
            text = "Wisteria is ready. How are you feeling today? (1–5, an emoji, or one word)",
            thoughtTrace = "Session reset. Standing by for daily pulse."
        )
        _uiState.value = _uiState.value.copy(
            messages = listOf(initialAgentMessage),
            agentState = AgentExecutionState.IDLE,
            agentStatusMessage = "Ready for check-in"
        )
    }
}
