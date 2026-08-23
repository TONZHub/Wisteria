package com.example.data.cloud

import com.example.domain.agent.CheckInHistoryEntry
import com.example.domain.agent.model.CycleTexture
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudRunWorkflowServiceImplTest {

    @Test
    fun `dispatch has no synthetic delay and no fake run app host`() = runTest {
        val service = CloudRunWorkflowServiceImpl()

        val job = service.dispatchWorkflowJob("night_shift", emptyMap(), emptyList())

        assertTrue(job.endpointUrl.startsWith("local://night-shift"))
        assertTrue(!job.endpointUrl.contains(".run.app"))
    }

    @Test
    fun `the job result summary comes from the real analyzer, not a canned string`() = runTest {
        val service = CloudRunWorkflowServiceImpl()
        val history = (1..7).map {
            CheckInHistoryEntry(date = "2024-01-0$it", rating = 1, texture = CycleTexture.MEDS_DROP_WINDOW, inputText = "1")
        }

        val job = service.dispatchWorkflowJob("night_shift", emptyMap(), history)

        assertEquals(job.morningBrief?.body, job.resultSummary)
        assertTrue(job.morningBrief?.isWindowActive == true)
    }

    @Test
    fun `an empty history still completes and reports still calibrating`() = runTest {
        val service = CloudRunWorkflowServiceImpl()

        val job = service.dispatchWorkflowJob("night_shift", emptyMap(), emptyList())

        assertEquals("COMPLETED", job.status)
        assertEquals("Still calibrating", job.morningBrief?.headline)
    }
}
