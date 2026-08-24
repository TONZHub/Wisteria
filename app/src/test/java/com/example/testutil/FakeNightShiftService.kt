package com.example.testutil

import com.example.data.cloud.NightShiftExecution
import com.example.data.cloud.NightShiftService
import com.example.domain.agent.CheckInHistoryEntry
import com.example.domain.agent.NightShiftAnalyzer

class FakeNightShiftService : NightShiftService {
    val receivedHistory = mutableListOf<List<CheckInHistoryEntry>>()

    override suspend fun run(history: List<CheckInHistoryEntry>): NightShiftExecution {
        receivedHistory += history
        val brief = NightShiftAnalyzer.analyze(history)
        return NightShiftExecution(
            runId = "test-night-shift-${receivedHistory.size}",
            executionLocation = "on-device",
            executionTimeMs = 0,
            status = "COMPLETED",
            resultSummary = brief.body,
            morningBrief = brief
        )
    }
}
