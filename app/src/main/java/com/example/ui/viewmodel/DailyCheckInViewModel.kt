package com.example.ui.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.cloud.FirestoreSyncRecord
import com.example.data.cloud.NightShiftExecution
import com.example.data.cloud.FirebaseMemoryBankSyncService
import com.example.data.health.HealthConnectManager
import com.example.data.reminder.AlarmSchedulePrecision
import com.example.data.reminder.CheckInReminderManager
import com.example.data.local.WisteriaDatabase
import com.example.data.local.entity.CareActionEntity
import com.example.data.local.entity.AgentMemoryEntity
import com.example.data.local.entity.DailyCheckInEntity
import com.example.data.memory.ConversationMemoryManager
import com.example.data.repository.WisteriaRepository
import com.example.domain.agent.model.AgentExecutionState
import com.example.domain.agent.model.AgentMessage
import com.example.domain.agent.model.AgentTurnIntent
import com.example.domain.agent.model.CareActionData
import com.example.domain.agent.model.DailyTexture
import com.example.domain.agent.model.DailyPulseData
import com.example.domain.agent.model.MessageSender
import com.example.domain.agent.model.ToolCallRecord
import com.example.domain.agent.MorningBrief
import com.example.voice.VoiceConversationController
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
    val healthConnectionSummary: String = "Not connected · integration optional",
    val themeMode: ThemeMode = ThemeMode.AUTO,
    val reminderHour: Int? = null,
    val reminderMinute: Int? = null,
    val isReminderEnabled: Boolean = false,
    val hasAlarmNotificationAccess: Boolean = false,
    val hasExactAlarmAccess: Boolean = false,
    val hasFullScreenAlarmAccess: Boolean = false,
    val isConversationMemoryEnabled: Boolean = false
)

class DailyCheckInViewModel(application: Application) : AndroidViewModel(application) {
    private val database = WisteriaDatabase.getInstance(application)
    private val repository = WisteriaRepository(
        database.checkInDao(),
        memoryBankService = FirebaseMemoryBankSyncService()
    )
    private val healthManager = HealthConnectManager(application)
    private val reminderManager = CheckInReminderManager(application)
    private val conversationMemoryManager = ConversationMemoryManager(application)
    private val voiceController = VoiceConversationController(application) { transcript ->
        sendMessageInternal(transcript, speakResponse = true)
    }

    private val _uiState = MutableStateFlow(CheckInUiState())
    val uiState: StateFlow<CheckInUiState> = _uiState.asStateFlow()
    val voiceState = voiceController.state

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
        refreshReminderState()
        refreshConversationMemoryState()
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
            val access = healthManager.getAccessStatus()
            _uiState.value = _uiState.value.copy(
                isHealthAvailable = healthManager.isAvailable.value,
                isHealthConnected = access.hasAnyAccess,
                healthConnectionSummary = access.summary
            )
        }
    }

    fun getHealthPermissions() = healthManager.permissions
    fun getHealthInstallIntent() = healthManager.getHealthConnectInstallIntent()

    fun onHealthPermissionsResult() {
        checkHealthState()
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

    fun signInAnonymously() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(agentStatusMessage = "Creating a private session...")
            try {
                repository.signInAnonymously()
                refreshLoginState()
                _uiState.value = _uiState.value.copy(agentStatusMessage = "Private session ready")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    agentStatusMessage = "Private sign-in failed: ${e.message ?: "unknown error"}"
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

    private fun clearStatusAfter(message: String, delayMillis: Long = 4_000L) {
        viewModelScope.launch {
            delay(delayMillis)
            if (_uiState.value.agentStatusMessage == message) {
                _uiState.value = _uiState.value.copy(agentStatusMessage = "Ready for a check-in")
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        _uiState.value = _uiState.value.copy(themeMode = mode)
    }

    private fun refreshConversationMemoryState() {
        _uiState.value = _uiState.value.copy(
            isConversationMemoryEnabled = conversationMemoryManager.isEnabled()
        )
    }

    fun setConversationMemoryEnabled(enabled: Boolean) {
        conversationMemoryManager.setEnabled(enabled)
        refreshConversationMemoryState()
        val message = if (enabled) {
            "Conversation memory on. New useful context can be remembered locally."
        } else {
            "Conversation memory paused. Existing notes remain until you delete them."
        }
        _uiState.value = _uiState.value.copy(agentStatusMessage = message)
        clearStatusAfter(message)
    }

    fun deleteMemory(key: String) {
        viewModelScope.launch {
            repository.deleteMemory(key)
            val message = "Memory deleted."
            _uiState.value = _uiState.value.copy(agentStatusMessage = message)
            clearStatusAfter(message)
        }
    }

    fun forgetConversationMemories() {
        viewModelScope.launch {
            repository.deleteConversationMemories()
            val message = "Conversation memories cleared."
            _uiState.value = _uiState.value.copy(agentStatusMessage = message)
            clearStatusAfter(message)
        }
    }

    fun setReminder(hour: Int, minute: Int) {
        android.util.Log.d("Wisteria", "DailyCheckInViewModel: set check-in alarm at $hour:$minute")
        val precision = reminderManager.enableDailyAlarm(hour, minute)
        refreshReminderState()
        val alarmState = _uiState.value
        val missingAccess = buildList {
            if (!alarmState.hasAlarmNotificationAccess) add("notifications")
            if (!alarmState.hasExactAlarmAccess) add("precise timing")
            if (!alarmState.hasFullScreenAlarmAccess) add("full-screen display")
        }
        _uiState.value = alarmState.copy(
            agentStatusMessage = when {
                !alarmState.hasAlarmNotificationAccess ->
                    "Check-in alarm saved. Allow notifications so it can alert you."
                missingAccess.isNotEmpty() ->
                    "Check-in alarm active. Optional upgrades: ${missingAccess.joinToString()}."
                precision == AlarmSchedulePrecision.EXACT -> "Daily check-in alarm is ready."
                else -> "Check-in alarm saved with approximate timing."
            }
        )
    }

    fun disableReminder() {
        reminderManager.disableAlarm()
        refreshReminderState()
        _uiState.value = _uiState.value.copy(agentStatusMessage = "Daily check-in alarm disabled.")
    }

    fun refreshReminderState(rescheduleIfEnabled: Boolean = false) {
        if (rescheduleIfEnabled) {
            reminderManager.scheduleNextDailyAlarm()
        }
        val alarm = reminderManager.snapshot()
        _uiState.value = _uiState.value.copy(
            reminderHour = alarm.hour.takeIf { alarm.enabled },
            reminderMinute = alarm.minute.takeIf { alarm.enabled },
            isReminderEnabled = alarm.enabled,
            hasAlarmNotificationAccess = alarm.hasNotificationAccess,
            hasExactAlarmAccess = alarm.hasExactAlarmAccess,
            hasFullScreenAlarmAccess = alarm.hasFullScreenAccess
        )
    }

    fun getExactAlarmSettingsIntent(): Intent? = reminderManager.exactAlarmSettingsIntent()

    fun getFullScreenAlarmSettingsIntent(): Intent? = reminderManager.fullScreenSettingsIntent()

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
        android.util.Log.d("Wisteria", "DailyCheckInViewModel: openFullScreenTakeover called")
        _uiState.value = _uiState.value.copy(isFullScreenTakeoverActive = true)
    }

    fun closeFullScreenTakeover() {
        android.util.Log.d("Wisteria", "DailyCheckInViewModel: closeFullScreenTakeover called")
        _uiState.value = _uiState.value.copy(isFullScreenTakeoverActive = false)
    }

    fun submitSingleInputCheckIn(input: String) {
        if (input.isBlank()) return
        closeFullScreenTakeover()
        sendMessageInternal(
            userText = input,
            speakResponse = false,
            requestedIntent = AgentTurnIntent.CHECK_IN
        )
    }

    fun sendMessage(userText: String) {
        sendMessageInternal(userText, speakResponse = false)
    }

    private fun sendMessageInternal(
        userText: String,
        speakResponse: Boolean,
        requestedIntent: AgentTurnIntent? = null
    ) {
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
            agentStatusMessage = "Understanding this turn…",
            isAgentActive = true
        )

        viewModelScope.launch {
            try {
                val healthContext = healthManager.fetchPrivateResponseContext()
                val rememberedContext = if (conversationMemoryManager.isEnabled()) {
                    repository.getConversationMemories(userText)
                } else {
                    emptyList()
                }
                val agentResponse = repository.checkInAgent.processUserTurn(
                    userPrompt = userText,
                    conversationHistory = updatedMessages,
                    rememberedContext = rememberedContext,
                    healthContext = healthContext,
                    requestedIntent = requestedIntent,
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

                val remembered = conversationMemoryManager.extract(userText, agentResponse.turnIntent)
                if (remembered != null) {
                    repository.saveConversationMemory(remembered)
                }

                val newPulse = agentResponse.structuredPulse ?: _uiState.value.latestPulse
                val completionStatus = if (remembered != null) {
                    "Remembered one conversation note locally."
                } else {
                    completedTurnStatus(agentResponse)
                }
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + agentResponse,
                    agentState = AgentExecutionState.IDLE,
                    agentStatusMessage = completionStatus,
                    latestPulse = newPulse,
                    isAgentActive = false
                )
                if (remembered != null) clearStatusAfter(completionStatus)
                if (speakResponse) {
                    voiceController.onAgentResponse(
                        text = agentResponse.text,
                        endCallAfterSpeech = agentResponse.turnIntent == AgentTurnIntent.END_SESSION
                    )
                }
            } catch (error: Exception) {
                val fallback = "I couldn't finish that turn. Your text is still here, and you can try again."
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + AgentMessage(
                        id = UUID.randomUUID().toString(),
                        sender = MessageSender.AGENT,
                        text = fallback
                    ),
                    agentState = AgentExecutionState.ERROR,
                    agentStatusMessage = error.message ?: "The agent turn could not finish",
                    isAgentActive = false
                )
                if (speakResponse) {
                    voiceController.onAgentResponse(fallback)
                }
            }
        }
    }

    fun startVoiceInput() = voiceController.startDictation()

    fun stopVoiceInput() = voiceController.stopListening()

    fun startVoiceCall() {
        voiceController.startCall(handsFree = true)
    }

    fun endVoiceCall() {
        repository.checkInAgent.endSession()
        voiceController.endCall()
    }

    fun toggleVoiceListening() = voiceController.toggleListening()

    fun toggleVoiceHandsFree() = voiceController.toggleHandsFree()

    fun toggleVoiceMicMuted() = voiceController.toggleMicMuted()

    fun toggleVoiceSpeaker() = voiceController.toggleSpeaker()

    fun onMicrophonePermissionDenied() = voiceController.onMicrophonePermissionDenied()

    fun sendQuickOption(rating: Int, label: String) {
        sendMessageInternal(
            userText = "$rating ($label)",
            speakResponse = false,
            requestedIntent = AgentTurnIntent.CHECK_IN
        )
    }

    private fun completedTurnStatus(message: AgentMessage): String = when (message.turnIntent) {
        AgentTurnIntent.CHECK_IN -> {
            val saved = message.toolInvocations.any {
                it.toolName == "RecordSingleInputCheckInTool" && it.status == "SUCCESS"
            }
            if (saved) "Check-in saved once." else "Check-in not saved."
        }
        AgentTurnIntent.DUPLICATE_CHECK_IN -> "Already saved; duplicate blocked."
        AgentTurnIntent.REMINDER_CHANGE -> "Reminder unchanged."
        AgentTurnIntent.END_SESSION -> "Conversation closed."
        else -> "Ready for a check-in"
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
            } catch (e: Throwable) {
                val errorMessage = if (e is kotlinx.coroutines.TimeoutCancellationException) {
                    "Sync timed out. Check your internet connection."
                } else {
                    "Firestore sync failed: ${e.localizedMessage ?: "check your network or Firebase rules"}"
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
        repository.checkInAgent.startNewSession()
        val initialAgentMessage = AgentMessage(
            id = UUID.randomUUID().toString(),
            sender = MessageSender.AGENT,
            text = "Wisteria is ready. How are you feeling today? (1–5, an emoji, or one word)",
            thoughtTrace = "Session reset. Standing by for daily pulse."
        )
        _uiState.value = _uiState.value.copy(
            messages = listOf(initialAgentMessage),
            agentState = AgentExecutionState.IDLE,
            agentStatusMessage = "Ready for a check-in"
        )
    }

    override fun onCleared() {
        voiceController.destroy()
        super.onCleared()
    }
}
