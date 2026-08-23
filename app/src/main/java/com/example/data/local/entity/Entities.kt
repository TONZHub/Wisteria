package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_checkins")
data class DailyCheckInEntity(
    @PrimaryKey val id: String,
    val date: String,
    val timestamp: Long,
    val ratingValue: Int, // 1 - 5
    val singleInputResponse: String, // Number, Color, Emoji, Word
    val detectedTexture: String, // BRIGHT, STEADY, HEAVY, OFF, UNKNOWN
    val agentAcknowledgment: String,
    val isOffDay: Boolean,
    val restOrHydrationLogged: Boolean = false,
    val lowEffortMeal: String = "",
    val comfortContent: String = "",
    val confidenceScore: Float = 0f,
    val syncStatus: String = "PENDING_SYNC",
    val firestoreDocPath: String? = null,
    val nightShiftRunId: String? = null
)

@Entity(tableName = "care_actions")
data class CareActionEntity(
    @PrimaryKey val id: String,
    val checkInId: String,
    val title: String,
    val type: String, // "REST_SUPPORT", "LOW_EFFORT_MEAL", "SIMPLIFY", "COMFORT_QUEUE"
    val description: String,
    val isAutoTriggered: Boolean = true,
    val isCompleted: Boolean = false,
    val iconName: String = "Spa",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "agent_memories")
data class AgentMemoryEntity(
    @PrimaryKey val memoryKey: String,
    val memoryValue: String,
    val category: String, // "DAILY_PATTERN", "USER_PREFERENCES", "CARE_ACTIONS"
    val importance: Float = 1.0f,
    val updatedAt: Long = System.currentTimeMillis()
)
