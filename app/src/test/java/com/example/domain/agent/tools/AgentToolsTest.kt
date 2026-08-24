package com.example.domain.agent.tools

import com.example.domain.agent.model.CareActionData
import com.example.domain.agent.model.DailyPulseData
import com.example.domain.agent.model.DailyTexture
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentToolsTest {

    @Test
    fun `record tool preserves an off texture`() = runTest {
        var recorded: DailyPulseData? = null
        val tool = RecordSingleInputCheckInTool { recorded = it }

        tool.execute(
            mapOf(
                "inputVal" to "1 (Off)",
                "rating" to "1",
                "detectedTexture" to "OFF",
                "confidence" to "1.0"
            )
        )

        assertEquals(DailyTexture.OFF, recorded?.texture)
        assertTrue(recorded?.isOffDay == true)
        assertEquals(1f, recorded?.confidenceScore)
    }

    @Test
    fun `record tool keeps heavy separate from off`() = runTest {
        var recorded: DailyPulseData? = null
        val tool = RecordSingleInputCheckInTool { recorded = it }

        tool.execute(
            mapOf(
                "inputVal" to "2 (Heavy)",
                "rating" to "2",
                "detectedTexture" to "HEAVY"
            )
        )

        assertEquals(DailyTexture.HEAVY, recorded?.texture)
        assertFalse(recorded?.isOffDay == true)
    }

    @Test
    fun `record tool falls back to unlabeled for an unknown texture`() = runTest {
        var recorded: DailyPulseData? = null
        val tool = RecordSingleInputCheckInTool { recorded = it }

        tool.execute(mapOf("detectedTexture" to "NOT_A_TEXTURE"))

        assertEquals(DailyTexture.UNKNOWN, recorded?.texture)
        assertEquals("Unlabeled", recorded?.textureLabel)
    }

    @Test
    fun `care tool saves the requested optional idea`() = runTest {
        var idea: CareActionData? = null
        val tool = TriggerProactiveCareActionTool { idea = it }

        val result = tool.execute(
            mapOf(
                "type" to "SIMPLIFY",
                "title" to "One less decision",
                "description" to "Leave one non-urgent choice for later.",
                "iconName" to "Spa"
            )
        )

        assertTrue(result.success)
        assertEquals("SIMPLIFY", idea?.type)
        assertEquals("One less decision", idea?.title)
    }

    @Test
    fun `firestore tool forwards the complete daily pulse`() = runTest {
        var synced: DailyPulseData? = null
        val tool = FirestoreSyncTool {
            synced = it
            "users/test-user/daily_timeline/today"
        }

        val result = tool.execute(
            mapOf(
                "rating" to "2",
                "texture" to "HEAVY",
                "textureLabel" to "Heavy",
                "singleInputResponse" to "today feels heavy",
                "agentAcknowledgment" to "Heavy is logged.",
                "isOffDay" to "false",
                "restOrHydrationLogged" to "true",
                "lowEffortMealSuggested" to "Simple meal",
                "comfortContent" to "Quiet audio",
                "confidenceScore" to "1.0"
            )
        )

        assertTrue(result.success)
        assertEquals(DailyTexture.HEAVY, synced?.texture)
        assertEquals("today feels heavy", synced?.singleInputResponse)
        assertTrue(synced?.restOrHydrationLogged == true)
    }
}
