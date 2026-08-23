package com.example.data.repository

import com.example.data.cloud.FirestoreSyncRecord
import com.example.data.cloud.FirestoreSyncService
import com.example.data.cloud.FirestoreSyncServiceImpl
import com.example.data.cloud.LocalNightShiftService
import com.example.data.cloud.NightShiftExecution
import com.example.data.cloud.NightShiftService
import com.example.data.local.dao.CheckInDao
import com.example.data.local.entity.CareActionEntity
import com.example.data.local.entity.AgentMemoryEntity
import com.example.data.local.entity.DailyCheckInEntity
import com.example.domain.agent.CheckInHistoryEntry
import com.example.domain.agent.DailyCheckInAgent
import com.example.domain.agent.model.DailyTexture
import com.example.domain.agent.model.DailyPulseData
import com.example.domain.agent.tools.RecordSingleInputCheckInTool
import com.example.domain.agent.tools.TriggerProactiveCareActionTool
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class WisteriaRepository(
    private val checkInDao: CheckInDao,
    private val firestoreService: FirestoreSyncService = FirestoreSyncServiceImpl(),
    private val nightShiftService: NightShiftService = LocalNightShiftService()
) {

    private val activeTools = listOf(
        RecordSingleInputCheckInTool { pulse ->
            saveDailyCheckIn(pulse)
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
            isOffDay = pulse.isOffDay,
            restOrHydrationLogged = pulse.restOrHydrationLogged,
            lowEffortMeal = pulse.lowEffortMealSuggested,
            comfortContent = pulse.comfortContent,
            confidenceScore = pulse.confidenceScore,
            syncStatus = "PENDING_SYNC",
            firestoreDocPath = null,
            nightShiftRunId = null
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

    /** Runs deterministic pattern analysis now, on demand, against local history. */
    suspend fun runNightShift(): NightShiftExecution {
        val execution = nightShiftService.run(loadCheckInHistory())
        val brief = execution.morningBrief
        checkInDao.insertMemory(
            AgentMemoryEntity(
                memoryKey = "night_shift_pattern",
                memoryValue = "${brief.headline} | samples=${brief.sampleSize} | confidence=${"%.2f".format(brief.confidence)} | daysUntilOff=${brief.daysUntilOff ?: "unknown"}",
                category = "DAILY_PATTERN"
            )
        )
        return execution
    }

    suspend fun triggerManualFirestoreSync(): FirestoreSyncRecord {
        val latest = checkInDao.getLatestCheckInFlow().first()
            ?: error("Complete a check-in before syncing")
        check(latest.syncStatus != "DEMO_LOCAL_ONLY") {
            "Demo data stays local; complete a real check-in before syncing"
        }
        val texture = runCatching { DailyTexture.valueOf(latest.detectedTexture) }
            .getOrDefault(DailyTexture.UNKNOWN)
        val pulse = DailyPulseData(
            ratingValue = latest.ratingValue,
            texture = texture,
            textureLabel = texture.name.lowercase().replaceFirstChar { it.uppercase() },
            singleInputResponse = latest.singleInputResponse,
            agentAcknowledgment = latest.agentAcknowledgment,
            restOrHydrationLogged = latest.restOrHydrationLogged,
            lowEffortMealSuggested = latest.lowEffortMeal,
            comfortContent = latest.comfortContent,
            isOffDay = latest.isOffDay,
            confidenceScore = latest.confidenceScore
        )
        val record = firestoreService.syncDailyCheckIn(pulse)
        markTodaysCheckInSynced(record.documentPath)
        return record
    }

    suspend fun getTextureSummary(): List<Map<String, Any>> {
        return firestoreService.fetchTextureSummary()
    }

    /** Inserts clearly labeled, local-only sample check-ins for the hackathon demo. */
    suspend fun loadDemoHistory(): Int {
        val textures = listOf(
            DailyTexture.HEAVY,
            DailyTexture.HEAVY,
            DailyTexture.HEAVY,
            DailyTexture.OFF,
            DailyTexture.OFF,
            DailyTexture.BRIGHT,
            DailyTexture.BRIGHT,
            DailyTexture.HEAVY,
            DailyTexture.HEAVY,
            DailyTexture.HEAVY
        )
        val start = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -(textures.size - 1)) }
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        textures.forEachIndexed { index, texture ->
            val day = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, index) }
            val date = formatter.format(day.time)
            val rating = when (texture) {
                DailyTexture.OFF -> 1
                DailyTexture.HEAVY -> 2
                DailyTexture.STEADY -> 3
                DailyTexture.BRIGHT -> 5
                DailyTexture.UNKNOWN -> 3
            }
            checkInDao.insertCheckIn(
                DailyCheckInEntity(
                    id = "demo_checkin_$date",
                    date = date,
                    timestamp = day.timeInMillis,
                    ratingValue = rating,
                    singleInputResponse = "Demo: ${texture.name.lowercase()}",
                    detectedTexture = texture.name,
                    agentAcknowledgment = "Demo check-in",
                    isOffDay = texture == DailyTexture.OFF,
                    syncStatus = "DEMO_LOCAL_ONLY"
                )
            )
        }
        return textures.size
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
                    DailyTexture.valueOf(entity.detectedTexture)
                } catch (e: Exception) {
                    DailyTexture.UNKNOWN
                },
                inputText = entity.singleInputResponse
            )
        }
    }
}
