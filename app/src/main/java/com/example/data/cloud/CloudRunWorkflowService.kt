package com.example.data.cloud

import com.example.domain.agent.CheckInHistoryEntry
import com.example.domain.agent.MorningBrief
import com.example.domain.agent.NightShiftAnalyzer
import java.util.UUID

data class CloudRunJobExecution(
    val jobId: String,
    val endpointUrl: String,
    val workflowType: String,
    val executionTimeMs: Long,
    val status: String,
    val resultSummary: String,
    val morningBrief: MorningBrief? = null
)

interface CloudRunWorkflowService {
    suspend fun dispatchWorkflowJob(
        workflowType: String,
        parameters: Map<String, String>,
        history: List<CheckInHistoryEntry> = emptyList()
    ): CloudRunJobExecution

    fun getCloudRunEndpoint(): String
}

/**
 * Runs the Night Shift pattern-recognition worker in-process. This is the real logic,
 * not a placeholder for it: the same [NightShiftAnalyzer] call is what would run behind
 * a Cloud Run endpoint in production - deploying it there is a follow-up, not something
 * to fake with a synthetic delay and a made-up hostname.
 */
class CloudRunWorkflowServiceImpl(
    private val endpoint: String = "local://night-shift"
) : CloudRunWorkflowService {

    override suspend fun dispatchWorkflowJob(
        workflowType: String,
        parameters: Map<String, String>,
        history: List<CheckInHistoryEntry>
    ): CloudRunJobExecution {
        val startTime = System.currentTimeMillis()
        val brief = NightShiftAnalyzer.analyze(history)
        val duration = System.currentTimeMillis() - startTime

        return CloudRunJobExecution(
            jobId = "night-shift-" + UUID.randomUUID().toString().take(8),
            endpointUrl = "$endpoint/$workflowType",
            workflowType = workflowType,
            executionTimeMs = duration,
            status = "COMPLETED",
            resultSummary = brief.body,
            morningBrief = brief
        )
    }

    override fun getCloudRunEndpoint(): String = endpoint
}
