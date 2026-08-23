package com.example.testutil

import com.example.data.cloud.CloudRunJobExecution
import com.example.data.cloud.CloudRunWorkflowService
import com.example.domain.agent.CheckInHistoryEntry

class FakeCloudRunWorkflowService : CloudRunWorkflowService {
    val dispatchedWorkflows = mutableListOf<String>()
    val receivedHistory = mutableListOf<List<CheckInHistoryEntry>>()

    override suspend fun dispatchWorkflowJob(
        workflowType: String,
        parameters: Map<String, String>,
        history: List<CheckInHistoryEntry>
    ): CloudRunJobExecution {
        dispatchedWorkflows.add(workflowType)
        receivedHistory.add(history)
        return CloudRunJobExecution(
            jobId = "test-job-${dispatchedWorkflows.size}",
            endpointUrl = "local://night-shift/$workflowType",
            workflowType = workflowType,
            executionTimeMs = 0,
            status = "COMPLETED",
            resultSummary = "test worker run"
        )
    }

    override fun getCloudRunEndpoint(): String = "local://night-shift"
}
