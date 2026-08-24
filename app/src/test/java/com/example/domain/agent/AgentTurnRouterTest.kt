package com.example.domain.agent

import com.example.domain.agent.model.AgentTurnIntent
import com.example.domain.agent.model.DailyPulseData
import com.example.domain.agent.model.DailyTexture
import org.junit.Assert.assertEquals
import org.junit.Test

class AgentTurnRouterTest {
    private val router = AgentTurnRouter()

    @Test
    fun `clear check-in language routes to a write-eligible turn`() {
        assertEquals(
            AgentTurnIntent.CHECK_IN,
            router.route("today feels heavy", AgentSessionState()).intent
        )
        assertEquals(
            AgentTurnIntent.CHECK_IN,
            router.route("4 (Clear)", AgentSessionState()).intent
        )
    }

    @Test
    fun `short replies stay inside the current conversation`() {
        val session = AgentSessionState(
            currentPulse = DailyPulseData(texture = DailyTexture.OFF, isOffDay = true),
            awaitingCareChoice = true
        )

        assertEquals(AgentTurnIntent.CARE_REQUEST, router.route("yes", session).intent)
        assertEquals(AgentTurnIntent.FOLLOW_UP, router.route("why?", session).intent)
    }

    @Test
    fun `a number inside a reminder request is not a rating`() {
        assertEquals(
            AgentTurnIntent.REMINDER_CHANGE,
            router.route("change my reminder to 4", AgentSessionState()).intent
        )
    }

    @Test
    fun `an already recorded normalized input is a duplicate`() {
        val session = AgentSessionState(recordedInputs = setOf("i feel off"))

        assertEquals(
            AgentTurnIntent.DUPLICATE_CHECK_IN,
            router.route("I FEEL OFF!", session).intent
        )
    }

    @Test
    fun `the check-in surface can explicitly route an unlabeled input`() {
        assertEquals(
            AgentTurnIntent.CHECK_IN,
            router.route(
                userPrompt = "purple",
                session = AgentSessionState(),
                requestedIntent = AgentTurnIntent.CHECK_IN
            ).intent
        )
    }
