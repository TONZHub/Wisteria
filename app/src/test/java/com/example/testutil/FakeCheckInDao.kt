package com.example.testutil

import com.example.data.local.dao.CheckInDao
import com.example.data.local.entity.AgentMemoryEntity
import com.example.data.local.entity.CareActionEntity
import com.example.data.local.entity.DailyCheckInEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [CheckInDao] for unit tests - no Room/Robolectric required. */
class FakeCheckInDao : CheckInDao {
    private val checkIns = MutableStateFlow<List<DailyCheckInEntity>>(emptyList())
    private val careActions = MutableStateFlow<List<CareActionEntity>>(emptyList())
    private val memories = MutableStateFlow<List<AgentMemoryEntity>>(emptyList())

    override fun getAllCheckInsFlow(): Flow<List<DailyCheckInEntity>> =
        checkIns.map { list -> list.sortedByDescending { it.timestamp } }

    override suspend fun getCheckInByDate(date: String): DailyCheckInEntity? =
        checkIns.value.firstOrNull { it.date == date }

    override fun getCheckInByDateFlow(date: String): Flow<DailyCheckInEntity?> =
        checkIns.map { list -> list.firstOrNull { it.date == date } }

    override fun getLatestCheckInFlow(): Flow<DailyCheckInEntity?> =
        checkIns.map { list -> list.maxByOrNull { it.timestamp } }

    override suspend fun insertCheckIn(checkIn: DailyCheckInEntity) {
        checkIns.value = checkIns.value.filterNot { it.id == checkIn.id } + checkIn
    }

    override suspend fun updateCheckIn(checkIn: DailyCheckInEntity) {
        checkIns.value = checkIns.value.map { if (it.id == checkIn.id) checkIn else it }
    }

    override fun getCareActionsForCheckInFlow(checkInId: String): Flow<List<CareActionEntity>> =
        careActions.map { list -> list.filter { it.checkInId == checkInId } }

    override fun getAllCareActionsFlow(): Flow<List<CareActionEntity>> = careActions

    override suspend fun insertCareActions(items: List<CareActionEntity>) {
        val ids = items.map { it.id }.toSet()
        careActions.value = careActions.value.filterNot { it.id in ids } + items
    }

    override suspend fun insertCareAction(item: CareActionEntity) {
        careActions.value = careActions.value.filterNot { it.id == item.id } + item
    }

    override suspend fun updateCareActionStatus(id: String, isCompleted: Boolean) {
        careActions.value = careActions.value.map {
            if (it.id == id) it.copy(isCompleted = isCompleted) else it
        }
    }

    override fun getAllMemoriesFlow(): Flow<List<AgentMemoryEntity>> = memories

    override suspend fun getMemory(key: String): AgentMemoryEntity? =
        memories.value.firstOrNull { it.memoryKey == key }

    override suspend fun insertMemory(memory: AgentMemoryEntity) {
        memories.value = memories.value.filterNot { it.memoryKey == memory.memoryKey } + memory
    }
}
