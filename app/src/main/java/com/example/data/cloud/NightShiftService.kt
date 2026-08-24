package com.example.data.cloud

import com.example.domain.agent.CheckInHistoryEntry
import com.example.domain.agent.MorningBrief
import com.example.domain.agent.NightShiftAnalyzer
import java.util.UUID

data class NightShiftExecution(
    val runId: String,
    val executionLocation: String,
    val executionTimeMs: Long,
    val status: String,
    val resultSummary: String,
    val morningBrief: MorningBrief
)

interface NightShiftService {
    suspend fun run(history: List<CheckInHistoryEntry>): NightShiftExecution
}

/** Runs deterministic pattern analysis on-device when the user taps Run Night Shift. */
class LocalNightShiftService : NightShiftService {
    override suspend fun run(history: List<CheckInHistoryEntry>): NightShiftExecution {
        val startTime = System.currentTimeMillis()
        val brief = NightShiftAnalyzer.analyze(history)

        return NightShiftExecution(
            runId = "night-shift-${UUID.randomUUID().toString().take(8)}",
            executionLocation = "on-device",
            executionTimeMs = System.currentTimeMillis() - startTime,
            status = "COMPLETED",
            resultSummary = brief.body,
            morningBrief = brief
        )
    }
}
