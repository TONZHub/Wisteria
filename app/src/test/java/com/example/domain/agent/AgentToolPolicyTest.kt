package com.example.domain.agent

import com.example.domain.agent.model.AgentTurnIntent
import com.example.domain.agent.model.DailyPulseData
import com.example.domain.agent.model.DailyTexture
import com.example.domain.agent.tools.RecordSingleInputCheckInTool
import com.example.domain.agent.tools.TriggerProactiveCareActionTool
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentToolPolicyTest {
    private val policy = AgentToolPolicy()
    private val recordTool = RecordSingleInputCheckInTool { }
    private val careTool = TriggerProactiveCareActionTool { }
    private val offPulse = DailyPulseData(
        ratingValue = 1,
        texture = DailyTexture.OFF,
        isOffDay = true
    )

    @Test
    fun `only a check-in intent may record a pulse`() {
        assertTrue(
            policy.authorize(recordTool, AgentTurnIntent.CHECK_IN, offPulse, AgentSessionState()).allowed
        )
        assertFalse(
            policy.authorize(recordTool, AgentTurnIntent.FOLLOW_UP, offPulse, AgentSessionState()).allowed
        )
        assertFalse(
            policy.authorize(recordTool, AgentTurnIntent.REMINDER_CHANGE, offPulse, AgentSessionState()).allowed
        )
    }

    @Test
    fun `optional ideas can be queued only once in a low-check-in session`() {
        assertTrue(
            policy.authorize(careTool, AgentTurnIntent.CHECK_IN, offPulse, AgentSessionState()).allowed
        )
        assertFalse(
            policy.authorize(
                careTool,
                AgentTurnIntent.CHECK_IN,
                offPulse,
                AgentSessionState(careIdeasQueued = true)
            ).allowed
        )
        assertFalse(
            policy.authorize(careTool, AgentTurnIntent.CARE_REQUEST, offPulse, AgentSessionState()).allowed
        )
    }
}
