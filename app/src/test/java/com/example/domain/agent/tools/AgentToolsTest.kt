package com.example.domain.agent.tools

import com.example.domain.agent.model.CareActionData
import com.example.domain.agent.model.CyclePatternState
import com.example.domain.agent.model.CycleTexture
import com.example.domain.agent.model.DailyPulseData
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentToolsTest {

    @Test
    fun `RecordSingleInputCheckInTool flags PMDD window on low rating`() = runTest {
        var recorded: DailyPulseData? = null
        val tool = RecordSingleInputCheckInTool { recorded = it }

        val result = tool.execute(mapOf("inputVal" to "1", "rating" to "1", "detectedTexture" to "MEDS_DROP_WINDOW"))

        assertTrue(result.success)
        assertEquals(1, recorded?.ratingValue)
        assertEquals(CycleTexture.MEDS_DROP_WINDOW, recorded?.texture)
        assertTrue(recorded?.isPmddWindowActive == true)
    }

    @Test
    fun `RecordSingleInputCheckInTool does not flag PMDD window on a good rating`() = runTest {
        var recorded: DailyPulseData? = null
        val tool = RecordSingleInputCheckInTool { recorded = it }

        tool.execute(mapOf("inputVal" to "great", "rating" to "5", "detectedTexture" to "FEELING_GOOD"))

        assertTrue(recorded?.isPmddWindowActive == false)
    }

    @Test
    fun `RecordSingleInputCheckInTool falls back to FEELING_GOOD on an unknown texture`() = runTest {
        var recorded: DailyPulseData? = null
        val tool = RecordSingleInputCheckInTool { recorded = it }

        tool.execute(mapOf("inputVal" to "?", "rating" to "3", "detectedTexture" to "NOT_A_REAL_TEXTURE"))

        assertEquals(CycleTexture.FEELING_GOOD, recorded?.texture)
    }

    @Test
    fun `DetectPMDDWindowTool marks meds drop window active`() = runTest {
        var pattern: CyclePatternState? = null
        val tool = DetectPMDDWindowTool { pattern = it }

        tool.execute(mapOf("daysInSpotting" to "6", "medsEfficacyDrop" to "true", "confidence" to "0.9"))

        assertEquals(CycleTexture.MEDS_DROP_WINDOW, pattern?.detectedTexture)
        assertEquals(0, pattern?.daysUntilDropWindow)
        assertTrue(pattern?.cognitiveLoadReduced == true)
    }

    @Test
    fun `DetectPMDDWindowTool flags an imminent window when spotting runs long`() = runTest {
        var pattern: CyclePatternState? = null
        val tool = DetectPMDDWindowTool { pattern = it }

        tool.execute(mapOf("daysInSpotting" to "8", "medsEfficacyDrop" to "false", "confidence" to "0.9"))

        assertTrue(pattern?.isPmddWindowImminent == true)
        assertEquals(2, pattern?.daysUntilDropWindow)
    }

    @Test
    fun `TriggerProactiveCareActionTool creates a care action with the requested type`() = runTest {
        var action: CareActionData? = null
        val tool = TriggerProactiveCareActionTool { action = it }

        tool.execute(mapOf("type" to "NERVE_TONIC", "title" to "Take Nerve Tonic", "description" to "desc", "iconName" to "Spa"))

        assertEquals("NERVE_TONIC", action?.type)
        assertEquals("Take Nerve Tonic", action?.title)
        assertTrue(action?.isAutoTriggered == true)
    }

    @Test
    fun `FirestoreSyncTool reconstructs the full pulse from its parameters`() = runTest {
        var synced: DailyPulseData? = null
        val tool = FirestoreSyncTool { pulse ->
            synced = pulse
            "users/test/cycle_timeline/test-date"
        }

        val result = tool.execute(
            mapOf(
                "rating" to "2",
                "texture" to "SPOTTING_PHASE",
                "textureLabel" to "Long Spotting Window",
                "singleInputResponse" to "spotting",
                "agentAcknowledgment" to "ack",
                "isPmddWindowActive" to "false",
                "nerveTonicTaken" to "true",
                "lowEffortMealSuggested" to "soup",
                "comfortContent" to "rain sounds",
                "confidenceScore" to "0.77"
            )
        )

        assertTrue(result.success)
        // Regression: this tool used to always sync an empty default DailyPulseData()
        // regardless of what was passed in, silently discarding the real check-in.
        assertEquals(2, synced?.ratingValue)
        assertEquals(CycleTexture.SPOTTING_PHASE, synced?.texture)
        assertEquals("spotting", synced?.singleInputResponse)
        assertTrue(synced?.nerveTonicTaken == true)
        assertEquals(0.77f, synced?.confidenceScore)
    }

    @Test
    fun `FirestoreSyncTool falls back to safe defaults for missing or unknown parameters`() = runTest {
        var synced: DailyPulseData? = null
        val tool = FirestoreSyncTool { pulse ->
            synced = pulse
            "doc-path"
        }

        tool.execute(emptyMap())

        assertEquals(3, synced?.ratingValue)
        assertEquals(CycleTexture.FEELING_GOOD, synced?.texture)
    }

    @Test
    fun `CloudRunWorkflowTool dispatches the requested workflow type`() = runTest {
        var dispatchedType: String? = null
        val tool = CloudRunWorkflowTool { workflowType ->
            dispatchedType = workflowType
            "job-42"
        }

        val result = tool.execute(mapOf("workflowType" to "nerve_tonic_prewarning"))

        assertEquals("nerve_tonic_prewarning", dispatchedType)
        assertTrue(result.summary.contains("job-42"))
    }
}
