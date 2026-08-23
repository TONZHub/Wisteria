package com.example.domain.agent

import com.example.domain.agent.model.CareActionData
import com.example.domain.agent.model.CyclePatternState
import com.example.domain.agent.model.CycleTexture
import com.example.domain.agent.model.DailyPulseData
import com.example.domain.agent.model.ToolCallRecord
import com.example.domain.agent.tools.AdkAgentTool
import com.example.domain.agent.tools.CloudRunWorkflowTool
import com.example.domain.agent.tools.DetectPMDDWindowTool
import com.example.domain.agent.tools.FirestoreSyncTool
import com.example.domain.agent.tools.RecordSingleInputCheckInTool
import com.example.domain.agent.tools.TriggerProactiveCareActionTool
import com.example.testutil.FakeGeminiApiService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyCheckInAgentTest {

    private fun buildAgent(
        recordedPulses: MutableList<DailyPulseData> = mutableListOf(),
        careActions: MutableList<CareActionData> = mutableListOf(),
        firestorePulses: MutableList<DailyPulseData> = mutableListOf(),
        firestoreShouldThrow: Boolean = false,
        cloudRunWorkflows: MutableList<String> = mutableListOf()
    ): DailyCheckInAgent {
        val tools: List<AdkAgentTool> = listOf(
            RecordSingleInputCheckInTool { recordedPulses.add(it) },
            DetectPMDDWindowTool { },
            TriggerProactiveCareActionTool { careActions.add(it) },
            FirestoreSyncTool { pulse ->
                if (firestoreShouldThrow) throw IllegalStateException("Firestore not configured")
                firestorePulses.add(pulse)
                "users/test/cycle_timeline/test-date"
            },
            CloudRunWorkflowTool { workflowType ->
                cloudRunWorkflows.add(workflowType)
                "job-123"
            }
        )
        return DailyCheckInAgent(geminiService = FakeGeminiApiService(), tools = tools)
    }

    @Test
    fun `hard day input triggers PMDD texture and proactive care`() = runTest {
        val recordedPulses = mutableListOf<DailyPulseData>()
        val careActions = mutableListOf<CareActionData>()
        val firestorePulses = mutableListOf<DailyPulseData>()
        val agent = buildAgent(recordedPulses = recordedPulses, careActions = careActions, firestorePulses = firestorePulses)

        val response = agent.processUserTurn(
            userPrompt = "1",
            conversationHistory = emptyList(),
            onStateChange = { _, _ -> },
            onToolExecuted = { }
        )

        val pulse = response.structuredPulse
        assertEquals(1, pulse?.ratingValue)
        assertEquals(CycleTexture.MEDS_DROP_WINDOW, pulse?.texture)
        assertTrue(pulse?.isPmddWindowActive == true)
        assertEquals(1, recordedPulses.size)
        assertEquals(3, careActions.size)

        // Regression: FirestoreSyncTool must receive the real extracted pulse, not an
        // empty default DailyPulseData() (see AgentTools.kt history).
        assertEquals(1, firestorePulses.size)
        assertEquals(1, firestorePulses[0].ratingValue)
        assertEquals(CycleTexture.MEDS_DROP_WINDOW, firestorePulses[0].texture)
        assertTrue(firestorePulses[0].isPmddWindowActive)
    }

    @Test
    fun `good day input does not trigger proactive care`() = runTest {
        val careActions = mutableListOf<CareActionData>()
        val agent = buildAgent(careActions = careActions)

        val response = agent.processUserTurn(
            userPrompt = "5",
            conversationHistory = emptyList(),
            onStateChange = { _, _ -> },
            onToolExecuted = { }
        )

        assertEquals(5, response.structuredPulse?.ratingValue)
        assertEquals(CycleTexture.FEELING_GOOD, response.structuredPulse?.texture)
        assertTrue(careActions.isEmpty())
    }

    @Test
    fun `a firestore failure is recorded as a failed tool call without crashing the turn`() = runTest {
        val toolRecords = mutableListOf<ToolCallRecord>()
        val agent = buildAgent(firestoreShouldThrow = true)

        val response = agent.processUserTurn(
            userPrompt = "3",
            conversationHistory = emptyList(),
            onStateChange = { _, _ -> },
            onToolExecuted = { toolRecords.add(it) }
        )

        // The turn still completes normally even though the cloud sync failed.
        assertEquals("3", response.structuredPulse?.singleInputResponse)

        val firestoreRecord = toolRecords.first { it.toolName == "FirestoreSyncTool" }
        assertEquals("FAILED", firestoreRecord.status)
        assertTrue(firestoreRecord.resultSummary.contains("Firestore sync failed"))
    }

    @Test
    fun `cloud run workflow is dispatched every turn`() = runTest {
        val cloudRunWorkflows = mutableListOf<String>()
        val agent = buildAgent(cloudRunWorkflows = cloudRunWorkflows)

        agent.processUserTurn(
            userPrompt = "doing okay",
            conversationHistory = emptyList(),
            onStateChange = { _, _ -> },
            onToolExecuted = { }
        )

        assertEquals(listOf("pmdd_pattern_analysis"), cloudRunWorkflows)
    }

    @Test
    fun `agent output never names a specific person`() = runTest {
        val agent = buildAgent()

        val inputs = listOf("1", "2", "3", "4", "5", "spotting started", "feeling great", "crash")
        for (input in inputs) {
            val response = agent.processUserTurn(
                userPrompt = input,
                conversationHistory = emptyList(),
                onStateChange = { _, _ -> },
                onToolExecuted = { }
            )

            assertFalse(
                "response text leaked a personal name for input '$input': ${response.text}",
                response.text.contains("Zoe", ignoreCase = true)
            )
            assertFalse(
                "thought trace leaked a personal name for input '$input': ${response.thoughtTrace}",
                response.thoughtTrace?.contains("Zoe", ignoreCase = true) == true
            )
        }
    }
}
