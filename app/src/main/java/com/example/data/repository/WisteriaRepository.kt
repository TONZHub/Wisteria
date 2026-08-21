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
import kotlinx.coroutines.flow.Flow
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
            val record = firestoreService.syncDailyCheckIn("zoe_wisteria_companion", pulse)
            record.documentPath
        },
        CloudRunWorkflowTool { workflowType ->
            val job = cloudRunService.dispatchWorkflowJob(workflowType, mapOf("triggeredBy" to "Wisteria_Agent"))
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
            syncStatus = "SYNCED_TO_FIRESTORE",
            firestoreDocPath = "users/zoe_wisteria_companion/cycle_timeline/$todayStr",
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

    suspend fun triggerManualCloudRunSync(): CloudRunJobExecution {
        return cloudRunService.dispatchWorkflowJob("pmdd_pattern_analysis", mapOf("source" to "CompanionUI"))
    }

    suspend fun triggerManualFirestoreSync(pulse: DailyPulseData): FirestoreSyncRecord {
        return firestoreService.syncDailyCheckIn("zoe_wisteria_companion", pulse)
    }

    suspend fun getCycleMemorySnapshot(): List<Map<String, Any>> {
        return firestoreService.fetchCycleMemorySnapshot("zoe_wisteria_companion")
    }

    fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }
}
