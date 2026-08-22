package com.example.testutil

import com.example.data.cloud.CloudRunJobExecution
import com.example.data.cloud.CloudRunWorkflowService

class FakeCloudRunWorkflowService : CloudRunWorkflowService {
    val dispatchedWorkflows = mutableListOf<String>()

    override suspend fun dispatchWorkflowJob(
        workflowType: String,
        parameters: Map<String, String>
    ): CloudRunJobExecution {
        dispatchedWorkflows.add(workflowType)
        return CloudRunJobExecution(
            jobId = "test-job-${dispatchedWorkflows.size}",
            endpointUrl = "https://test.local/$workflowType",
            workflowType = workflowType,
            executionTimeMs = 0,
            status = "COMPLETED_200_OK",
            resultSummary = "test worker run"
        )
    }

    override fun getCloudRunEndpoint(): String = "https://test.local"
}
