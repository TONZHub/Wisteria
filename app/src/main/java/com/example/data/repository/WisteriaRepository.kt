package com.example.data.repository

import com.example.data.cloud.CloudRunJobExecution
import com.example.data.cloud.CloudRunWorkflowService
import com.example.data.cloud.CloudRunWorkflowServiceImpl
import com.example.data.cloud.FirestoreSyncRecord
import com.example.data.cloud.FirestoreSyncService
import com.example.data.cloud.FirestoreSyncServiceImpl
import com.example.data.local.dao.CheckInDao
import com.example.data.local.entity.CareActionEntity
import com.example.data.local.entity.AgentMemoryEntity
import com.example.data.local.entity.DailyCheckInEntity
import com.example.domain.agent.CheckInHistoryEntry
import com.example.domain.agent.DailyCheckInAgent
import com.example.domain.agent.model.CareActionData
import com.example.domain.agent.model.CyclePatternState
import com.example.domain.agent.model.CycleTexture
import com.example.domain.agent.model.DailyPulseData
import com.example.domain.agent.tools.CloudRunWorkflowTool
import com.example.domain.agent.tools.DetectPMDDWindowTool
import com.example.domain.agent.tools.FirestoreSyncTool
import com.example.domain.agent.tools.RecordSingleInputCheckInTool
import com.example.domain.agent.tools.TriggerProactiveCareActionTool
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class WisteriaRepository(
    private val checkInDao: CheckInDao,
    private val firestoreService: FirestoreSyncService = FirestoreSyncServiceImpl(),
    private val cloudRunService: CloudRunWorkflowService = CloudRunWorkflowServiceImpl()
) {

    private val activeTools = listOf(
        RecordSingleInputCheckInTool { pulse ->
            saveDailyCheckIn(pulse)
        },
        DetectPMDDWindowTool { pattern ->
            checkInDao.insertMemory(
                AgentMemoryEntity(
                    memoryKey = "pmdd_pattern_state",
                    memoryValue = "${pattern.detectedTexture.name}|DaysUntilDrop:${pattern.daysUntilDropWindow}|Confidence:${pattern.pmddConfidence}|Insight:${pattern.summaryInsight}",
                    category = "CYCLE_PATTERN"
                )
            )
        },
        TriggerProactiveCareActionTool { careAction ->
            val todayStr = getTodayDateString()
            checkInDao.insertCareAction(
                CareActionEntity(
                    id = careAction.id,
                    checkInId = "checkin_$todayStr",
                    title = careAction.title,
                    type = careAction.type,
                    description = careAction.description,
                    isAutoTriggered = careAction.isAutoTriggered,
                    isCompleted = careAction.isCompleted,
                    iconName = careAction.iconName
                )
            )
        },
        FirestoreSyncTool { pulse ->
            val record = firestoreService.syncDailyCheckIn(currentUserId(), pulse)
            markTodaysCheckInSynced(record.documentPath)
            record.documentPath
        },
        CloudRunWorkflowTool { workflowType ->
            val job = cloudRunService.dispatchWorkflowJob(workflowType, mapOf("triggeredBy" to "Wisteria_Agent"), loadCheckInHistory())
            job.jobId
        }
    )

    val checkInAgent = DailyCheckInAgent(tools = activeTools)

    fun getAllCheckInsFlow(): Flow<List<DailyCheckInEntity>> = checkInDao.getAllCheckInsFlow()
    fun getLatestCheckInFlow(): Flow<DailyCheckInEntity?> = checkInDao.getLatestCheckInFlow()
    fun getCareActionsFlow(checkInId: String): Flow<List<CareActionEntity>> = checkInDao.getCareActionsForCheckInFlow(checkInId)
    fun getAllCareActionsFlow(): Flow<List<CareActionEntity>> = checkInDao.getAllCareActionsFlow()
    fun getAllMemoriesFlow(): Flow<List<AgentMemoryEntity>> = checkInDao.getAllMemoriesFlow()

    suspend fun toggleCareAction(id: String, isCompleted: Boolean) {
        checkInDao.updateCareActionStatus(id, isCompleted)
    }

    suspend fun saveDailyCheckIn(pulse: DailyPulseData): DailyCheckInEntity {
        val todayStr = getTodayDateString()
        val checkInId = "checkin_$todayStr"

        val entity = DailyCheckInEntity(
            id = checkInId,
            date = todayStr,
            timestamp = System.currentTimeMillis(),
            ratingValue = pulse.ratingValue,
            singleInputResponse = pulse.singleInputResponse,
            detectedTexture = pulse.texture.name,
            agentAcknowledgment = pulse.agentAcknowledgment,
            isPmddWindowActive = pulse.isPmddWindowActive,
            nerveTonicTaken = pulse.nerveTonicTaken,
            lowEffortMeal = pulse.lowEffortMealSuggested,
            comfortContent = pulse.comfortContent,
            confidenceScore = pulse.confidenceScore,
            syncStatus = "PENDING_SYNC",
            firestoreDocPath = null,
            cloudRunJobId = "crun-agent-auto"
        )
        checkInDao.insertCheckIn(entity)

        if (pulse.careActions.isNotEmpty()) {
            val entities = pulse.careActions.map { action ->
                CareActionEntity(
                    id = action.id,
                    checkInId = checkInId,
                    title = action.title,
                    type = action.type,
                    description = action.description,
                    isAutoTriggered = action.isAutoTriggered,
                    isCompleted = action.isCompleted,
                    iconName = action.iconName
                )
            }
            checkInDao.insertCareActions(entities)
        }

        return entity
    }

    private suspend fun markTodaysCheckInSynced(documentPath: String) {
        val todayStr = getTodayDateString()
        val existing = checkInDao.getCheckInByDate(todayStr) ?: return
        checkInDao.updateCheckIn(
            existing.copy(syncStatus = "SYNCED_TO_FIRESTORE", firestoreDocPath = documentPath)
        )
    }

    suspend fun triggerManualCloudRunSync(): CloudRunJobExecution {
        return cloudRunService.dispatchWorkflowJob("pmdd_pattern_analysis", mapOf("source" to "CompanionUI"), loadCheckInHistory())
    }

    /** Runs the real overnight pattern worker now, on demand, against local check-in history. */
    suspend fun runNightShift(): CloudRunJobExecution {
        return cloudRunService.dispatchWorkflowJob("night_shift", mapOf("source" to "CompanionUI"), loadCheckInHistory())
    }

    suspend fun triggerManualFirestoreSync(pulse: DailyPulseData): FirestoreSyncRecord {
        val record = firestoreService.syncDailyCheckIn(currentUserId(), pulse)
        markTodaysCheckInSynced(record.documentPath)
        return record
    }

    suspend fun getCycleMemorySnapshot(): List<Map<String, Any>> {
        return firestoreService.fetchCycleMemorySnapshot(currentUserId())
    }

    fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    private suspend fun loadCheckInHistory(): List<CheckInHistoryEntry> {
        return checkInDao.getAllCheckInsFlow().first().map { entity ->
            CheckInHistoryEntry(
                date = entity.date,
                rating = entity.ratingValue,
                texture = try {
                    CycleTexture.valueOf(entity.detectedTexture)
                } catch (e: Exception) {
                    CycleTexture.UNKNOWN_CALIBRATING
                },
                inputText = entity.singleInputResponse
            )
        }
    }

    /** Falls back to "anonymous" until Firebase anonymous sign-in has completed at least once. */
    private fun currentUserId(): String = try {
        FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"
    } catch (e: Exception) {
        "anonymous"
    }
}
