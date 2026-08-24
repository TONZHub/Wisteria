package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.cloud.FirestoreSyncRecord
import com.example.data.cloud.NightShiftExecution
import com.example.data.health.HealthConnectManager
import com.example.data.reminder.CheckInReminderManager
import com.example.data.local.WisteriaDatabase
import com.example.data.local.entity.CareActionEntity
import com.example.data.local.entity.AgentMemoryEntity
import com.example.data.local.entity.DailyCheckInEntity
import com.example.data.repository.WisteriaRepository
import com.example.domain.agent.model.AgentExecutionState
import com.example.domain.agent.model.AgentMessage
import com.example.domain.agent.model.CareActionData
import com.example.domain.agent.model.DailyTexture
import com.example.domain.agent.model.DailyPulseData
import com.example.domain.agent.model.MessageSender
import com.example.domain.agent.model.ToolCallRecord
import com.example.domain.agent.MorningBrief
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

enum class ThemeMode {
    AUTO, LIGHT, DARK
}

data class CheckInUiState(
    val messages: List<AgentMessage> = emptyList(),
    val agentState: AgentExecutionState = AgentExecutionState.IDLE,
    val agentStatusMessage: String = "Ready for a check-in",
    val latestPulse: DailyPulseData = DailyPulseData(),
    val executedTools: List<ToolCallRecord> = emptyList(),
    val nightShiftRuns: List<NightShiftExecution> = emptyList(),
    val firestoreSyncLogs: List<FirestoreSyncRecord> = emptyList(),
    val textureSummary: List<Map<String, Any>> = emptyList(),
    val morningBrief: MorningBrief? = null,
    val isNightShiftRunning: Boolean = false,
    val isAgentActive: Boolean = false,
    val isUserLoggedIn: Boolean = false,
    val userEmail: String? = null,
    val showStartupAnimation: Boolean = false,
    val isFullScreenTakeoverActive: Boolean = false,
    val startupBootLog: List<String> = emptyList(),
    val isHealthConnected: Boolean = false,
    val isHealthAvailable: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.AUTO,
    val reminderHour: Int? = null,
    val reminderMinute: Int? = null,
    val isReminderEnabled: Boolean = false
)

class DailyCheckInViewModel(application: Application) : AndroidViewModel(application) {
    private val database = WisteriaDatabase.getInstance(application)
    private val repository = WisteriaRepository(database.checkInDao())
    private val healthManager = HealthConnectManager(application)
    private val reminderManager = CheckInReminderManager(application)

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
        refreshLoginState()
        checkHealthState()
        loadChatHistory()
    }

    private fun loadChatHistory() {
        viewModelScope.launch {
            try {
                val checkIns = repository.getAllCheckInsFlow().first()
                val history = checkIns.reversed().flatMap { checkIn ->
                    listOf(
                        AgentMessage(
                            id = "${checkIn.id}_user",
                            sender = MessageSender.USER,
                            text = checkIn.singleInputResponse,
                            timestamp = checkIn.timestamp
                        ),
                        AgentMessage(
                            id = "${checkIn.id}_agent",
                            sender = MessageSender.AGENT,
                            text = checkIn.agentAcknowledgment,
                            timestamp = checkIn.timestamp + 10,
                            thoughtTrace = "Saved record from ${checkIn.date}"
                        )
                    )
                }

                val today = repository.getTodayDateString()
                val hasToday = checkIns.any { it.date == today }

                _uiState.value = _uiState.value.copy(
                    messages = if (hasToday) history else history + _uiState.value.messages
                )
            } catch (e: Exception) {
                // Fallback to initial greeting if history fails
            }
        }
    }

    private fun checkHealthState() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isHealthAvailable = healthManager.isAvailable.value,
                isHealthConnected = healthManager.hasAllPermissions()
            )
        }
    }

    fun getHealthPermissions() = healthManager.permissions
    fun getHealthInstallIntent() = healthManager.getHealthConnectInstallIntent()

    fun onHealthPermissionsResult(granted: Boolean) {
        _uiState.value = _uiState.value.copy(isHealthConnected = granted)
    }

    private fun refreshLoginState() {
        _uiState.value = _uiState.value.copy(
            isUserLoggedIn = repository.isUserLoggedIn(),
            userEmail = repository.getUserEmail()
        )
    }

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(agentStatusMessage = "Signing in with Google...")
            try {
                repository.signInWithGoogle(idToken)
                refreshLoginState()
                _uiState.value = _uiState.value.copy(agentStatusMessage = "Signed in as ${_uiState.value.userEmail}")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    agentStatusMessage = "Sign-in failed: ${e.message ?: "unknown error"}"
                )
            }
        }
    }

    fun signOut() {
        repository.signOut()
        refreshLoginState()
        _uiState.value = _uiState.value.copy(agentStatusMessage = "Signed out")
    }

    fun setStatusMessage(message: String) {
        _uiState.value = _uiState.value.copy(agentStatusMessage = message)
    }

    fun setThemeMode(mode: ThemeMode) {
        _uiState.value = _uiState.value.copy(themeMode = mode)
    }

    fun setReminder(hour: Int, minute: Int) {
        android.util.Log.d("Wisteria", "DailyCheckInViewModel: setReminder at $hour:$minute")
        _uiState.value = _uiState.value.copy(
            reminderHour = hour,
            reminderMinute = minute,
            isReminderEnabled = true
        )
        reminderManager.scheduleReminder(hour, minute)
    }

    fun disableReminder() {
        _uiState.value = _uiState.value.copy(isReminderEnabled = false)
        reminderManager.cancelReminder()
    }

    private fun loadTextureSummary() {
        viewModelScope.launch {
            try {
                val summary = repository.getTextureSummary()
                _uiState.value = _uiState.value.copy(textureSummary = summary)
            } catch (e: Exception) {
                // Firestore is optional. Local check-ins and Night Shift remain available.
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
                "checking optional Firebase services...",
                "opening your local daily timeline...",
                "ready to keep learning...",
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
            agentStatusMessage = "Reading your check-in...",
            isAgentActive = true
        )

        viewModelScope.launch {
            val healthContext = healthManager.fetchLastCycleContext()
            val agentResponse = repository.checkInAgent.processUserTurn(
                userPrompt = userText,
                conversationHistory = updatedMessages,
                healthContext = healthContext,
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
                agentStatusMessage = "Ready for a check-in",
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

    /** Runs Night Shift against local check-in history only when requested. */
    fun runNightShift() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isNightShiftRunning = true, agentStatusMessage = "Running Night Shift...")
            try {
                val job = repository.runNightShift()
                val logs = _uiState.value.nightShiftRuns + job
                _uiState.value = _uiState.value.copy(
                    nightShiftRuns = logs,
                    morningBrief = job.morningBrief,
                    isNightShiftRunning = false,
                    agentStatusMessage = job.morningBrief.headline
                )
                // Clear the status message after 4 seconds so the "window" closes
                delay(4000)
                if (_uiState.value.agentStatusMessage == job.morningBrief.headline) {
                    _uiState.value = _uiState.value.copy(agentStatusMessage = "Ready for a check-in")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isNightShiftRunning = false,
                    agentStatusMessage = "Night Shift failed: ${e.message ?: "unknown error"}"
                )
                delay(4000)
                if (_uiState.value.agentStatusMessage.contains("failed")) {
                    _uiState.value = _uiState.value.copy(agentStatusMessage = "Ready for a check-in")
                }
            }
        }
    }

    fun loadDemoHistory() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isNightShiftRunning = true,
                agentStatusMessage = "Loading 10 local demo days..."
            )
            try {
                val count = repository.loadDemoHistory()
                val run = repository.runNightShift()
                val finalMsg = "$count demo days loaded · ${run.morningBrief.headline}"
                _uiState.value = _uiState.value.copy(
                    nightShiftRuns = _uiState.value.nightShiftRuns + run,
                    morningBrief = run.morningBrief,
                    isNightShiftRunning = false,
                    agentStatusMessage = finalMsg
                )
                delay(4000)
                if (_uiState.value.agentStatusMessage == finalMsg) {
                    _uiState.value = _uiState.value.copy(agentStatusMessage = "Ready for a check-in")
                }
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(
                    isNightShiftRunning = false,
                    agentStatusMessage = "Could not load demo history: ${error.message ?: "unknown error"}"
                )
                delay(4000)
                if (_uiState.value.agentStatusMessage.contains("Could not load")) {
                    _uiState.value = _uiState.value.copy(agentStatusMessage = "Ready for a check-in")
                }
            }
        }
    }

    fun triggerFirestoreSync() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(agentStatusMessage = "Syncing the daily timeline to Firestore...")
            try {
                // Add a 10-second timeout to prevent indefinite hangs
                withTimeout(10000L) {
                    val record = repository.triggerManualFirestoreSync()
                    val logs = _uiState.value.firestoreSyncLogs + record
                    val successMsg = "Firestore synced to ${record.documentPath}"
                    _uiState.value = _uiState.value.copy(
                        firestoreSyncLogs = logs,
                        agentStatusMessage = successMsg
                    )
                    loadTextureSummary()
                    delay(4000)
                    if (_uiState.value.agentStatusMessage == successMsg) {
                        _uiState.value = _uiState.value.copy(agentStatusMessage = "Ready for a check-in")
                    }
                }
            } catch (e: Exception) {
                val errorMessage = if (e is kotlinx.coroutines.TimeoutCancellationException) {
                    "Sync timed out. Check your internet connection."
                } else {
                    "Firestore sync failed: ${e.message ?: "check your network or Firebase rules"}"
                }
                _uiState.value = _uiState.value.copy(agentStatusMessage = errorMessage)
                delay(4000)
                if (_uiState.value.agentStatusMessage == errorMessage) {
                    _uiState.value = _uiState.value.copy(agentStatusMessage = "Ready for a check-in")
                }
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
