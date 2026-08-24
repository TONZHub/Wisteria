package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.CareActionEntity
import com.example.data.local.entity.AgentMemoryEntity
import com.example.data.local.entity.DailyCheckInEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CheckInDao {
    @Query("SELECT * FROM daily_checkins ORDER BY timestamp DESC")
    fun getAllCheckInsFlow(): Flow<List<DailyCheckInEntity>>

    @Query("SELECT * FROM daily_checkins WHERE date = :date LIMIT 1")
    suspend fun getCheckInByDate(date: String): DailyCheckInEntity?

    @Query("SELECT * FROM daily_checkins WHERE date = :date LIMIT 1")
    fun getCheckInByDateFlow(date: String): Flow<DailyCheckInEntity?>

    @Query("SELECT * FROM daily_checkins ORDER BY timestamp DESC LIMIT 1")
    fun getLatestCheckInFlow(): Flow<DailyCheckInEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCheckIn(checkIn: DailyCheckInEntity)

    @Update
    suspend fun updateCheckIn(checkIn: DailyCheckInEntity)

    // Care Actions
    @Query("SELECT * FROM care_actions WHERE checkInId = :checkInId ORDER BY isCompleted ASC")
    fun getCareActionsForCheckInFlow(checkInId: String): Flow<List<CareActionEntity>>

    @Query("SELECT * FROM care_actions ORDER BY createdAt DESC")
    fun getAllCareActionsFlow(): Flow<List<CareActionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCareActions(items: List<CareActionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCareAction(item: CareActionEntity)

    @Query("UPDATE care_actions SET isCompleted = :isCompleted WHERE id = :id")
    suspend fun updateCareActionStatus(id: String, isCompleted: Boolean)

    // Agent Memory
    @Query("SELECT * FROM agent_memories ORDER BY updatedAt DESC")
    fun getAllMemoriesFlow(): Flow<List<AgentMemoryEntity>>

    @Query("SELECT * FROM agent_memories WHERE memoryKey = :key LIMIT 1")
    suspend fun getMemory(key: String): AgentMemoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: AgentMemoryEntity)
}
