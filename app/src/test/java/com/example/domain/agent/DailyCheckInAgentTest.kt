package com.example.domain.agent

import com.example.domain.agent.model.AgentMessage
import com.example.domain.agent.model.AgentTurnIntent
import com.example.domain.agent.model.CareActionData
import com.example.domain.agent.model.DailyPulseData
import com.example.domain.agent.model.DailyTexture
import com.example.domain.agent.model.MessageSender
import com.example.domain.agent.tools.AgentTool
import com.example.domain.agent.tools.RecordSingleInputCheckInTool
import com.example.domain.agent.tools.TriggerProactiveCareActionTool
import com.example.testutil.FakeCompanionModelService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyCheckInAgentTest {

    private fun buildAgent(
        model: FakeCompanionModelService = FakeCompanionModelService(),
        recordedPulses: MutableList<DailyPulseData> = mutableListOf(),
        careActions: MutableList<CareActionData> = mutableListOf()
    ): DailyCheckInAgent {
        val tools: List<AgentTool> = listOf(
            RecordSingleInputCheckInTool { recordedPulses += it },
            TriggerProactiveCareActionTool { careActions += it }
        )
        return DailyCheckInAgent(modelService = model, tools = tools)
    }

    @Test
    fun `one records off and prepares optional ideas`() = runTest {
        val recorded = mutableListOf<DailyPulseData>()
        val ideas = mutableListOf<CareActionData>()
        val response = buildAgent(recordedPulses = recorded, careActions = ideas).processUserTurn(
            userPrompt = "1 (Off)",
            conversationHistory = emptyList(),
            onStateChange = { _, _ -> },
            onToolExecuted = { }
        )

        assertEquals(1, response.structuredPulse?.ratingValue)
        assertEquals(DailyTexture.OFF, response.structuredPulse?.texture)
        assertTrue(response.structuredPulse?.isOffDay == true)
        assertEquals(1, recorded.size)
        assertEquals(3, ideas.size)
    }

    @Test
    fun `two records heavy without turning it into off`() = runTest {
        val ideas = mutableListOf<CareActionData>()
        val response = buildAgent(careActions = ideas).processUserTurn(
            userPrompt = "2 (Heavy)",
            conversationHistory = emptyList(),
            onStateChange = { _, _ -> },
            onToolExecuted = { }
        )

        assertEquals(DailyTexture.HEAVY, response.structuredPulse?.texture)
        assertFalse(response.structuredPulse?.isOffDay == true)
        assertEquals(3, ideas.size)
    }

    @Test
    fun `five records bright without optional ideas`() = runTest {
        val ideas = mutableListOf<CareActionData>()
        val response = buildAgent(careActions = ideas).processUserTurn(
            userPrompt = "5 (Bright)",
            conversationHistory = emptyList(),
            onStateChange = { _, _ -> },
            onToolExecuted = { }
        )

        assertEquals(DailyTexture.BRIGHT, response.structuredPulse?.texture)
        assertTrue(ideas.isEmpty())
    }

    @Test
    fun `an unfamiliar word stays conversational and does not create a check-in`() = runTest {
        val recorded = mutableListOf<DailyPulseData>()
        val response = buildAgent(recordedPulses = recorded).processUserTurn(
            userPrompt = "purple",
            conversationHistory = emptyList(),
            onStateChange = { _, _ -> },
            onToolExecuted = { }
        )

        assertEquals(AgentTurnIntent.GENERAL, response.turnIntent)
        assertEquals(null, response.structuredPulse)
        assertTrue(recorded.isEmpty())
    }

    @Test
    fun `optional model wording is used when available`() = runTest {
        val model = FakeCompanionModelService(response = "I hear off. Would one easy idea help?")
        val response = buildAgent(model = model).processUserTurn(
            userPrompt = "I feel off",
            conversationHistory = emptyList(),
            onStateChange = { _, _ -> },
            onToolExecuted = { }
        )

        assertEquals("I hear off. Would one easy idea help?", response.text)
        assertEquals(1, model.prompts.size)
        assertEquals("Fake ADK runtime", response.runtimeTrace?.framework)
        assertEquals("fake-model", response.runtimeTrace?.model)
    }

    @Test
    fun `the current check-in appears once in the model prompt`() = runTest {
        val model = FakeCompanionModelService(response = "Saved.")
        val input = "4 (Clear)"
        val history = listOf(
            AgentMessage(id = "current", sender = MessageSender.USER, text = input)
        )

        buildAgent(model = model).processUserTurn(
            userPrompt = input,
            conversationHistory = history,
            onStateChange = { _, _ -> },
            onToolExecuted = { }
        )

        assertEquals(1, Regex(Regex.escape(input)).findAll(model.prompts.single()).count())
    }

    @Test
    fun `model wording with a body phase label falls back to everyday language`() = runTest {
        val model = FakeCompanionModelService(response = "You are in a follicular phase.")

        val response = buildAgent(model = model).processUserTurn(
            userPrompt = "I feel off",
            conversationHistory = emptyList(),
            onStateChange = { _, _ -> },
            onToolExecuted = { }
        )

        assertFalse(response.text.contains("follicular", ignoreCase = true))
        assertTrue(response.text.contains("feels off", ignoreCase = true))
        assertEquals(1, model.startedSessions)
    }

    @Test
    fun `a care follow-up does not record another pulse or duplicate ideas`() = runTest {
        val recorded = mutableListOf<DailyPulseData>()
        val ideas = mutableListOf<CareActionData>()
        val agent = buildAgent(recordedPulses = recorded, careActions = ideas)

        agent.processUserTurn(
            userPrompt = "I feel off",
            conversationHistory = emptyList(),
            onStateChange = { _, _ -> },
            onToolExecuted = { }
        )
        val followUp = agent.processUserTurn(
            userPrompt = "yes, give me one idea",
            conversationHistory = emptyList(),
            onStateChange = { _, _ -> },
            onToolExecuted = { }
        )

        assertEquals(AgentTurnIntent.CARE_REQUEST, followUp.turnIntent)
        assertEquals(1, recorded.size)
        assertEquals(3, ideas.size)
        assertTrue(followUp.toolInvocations.isEmpty())
        assertTrue(followUp.text.contains("One easy option"))
    }

    @Test
    fun `an identical voice turn is recorded only once per session`() = runTest {
        val recorded = mutableListOf<DailyPulseData>()
        val ideas = mutableListOf<CareActionData>()
        val agent = buildAgent(recordedPulses = recorded, careActions = ideas)

        val first = agent.processUserTurn(
            userPrompt = "I feel off",
            conversationHistory = emptyList(),
            onStateChange = { _, _ -> },
            onToolExecuted = { }
        )
        val repeated = agent.processUserTurn(
            userPrompt = "I feel off",
            conversationHistory = listOf(first),
            onStateChange = { _, _ -> },
            onToolExecuted = { }
        )

        assertEquals(AgentTurnIntent.DUPLICATE_CHECK_IN, repeated.turnIntent)
        assertEquals(1, recorded.size)
        assertEquals(3, ideas.size)
        assertTrue(repeated.toolInvocations.isEmpty())
    }

    @Test
    fun `pattern and reminder questions are read-only turns`() = runTest {
        val recorded = mutableListOf<DailyPulseData>()
        val agent = buildAgent(recordedPulses = recorded)

        val pattern = agent.processUserTurn(
            userPrompt = "What did last week look like?",
            conversationHistory = emptyList(),
            onStateChange = { _, _ -> },
            onToolExecuted = { }
        )
        val reminder = agent.processUserTurn(
            userPrompt = "Set my reminder for 4",
            conversationHistory = emptyList(),
            onStateChange = { _, _ -> },
            onToolExecuted = { }
        )

        assertEquals(AgentTurnIntent.PATTERN_QUESTION, pattern.turnIntent)
        assertEquals(AgentTurnIntent.REMINDER_CHANGE, reminder.turnIntent)
        assertTrue(recorded.isEmpty())
        assertTrue(pattern.toolInvocations.isEmpty())
        assertTrue(reminder.toolInvocations.isEmpty())
    }

    @Test
    fun `starting a new session permits a deliberate matching check-in`() = runTest {
        val recorded = mutableListOf<DailyPulseData>()
        val model = FakeCompanionModelService()
        val agent = buildAgent(model = model, recordedPulses = recorded)

        agent.processUserTurn(
            userPrompt = "3 (Steady)",
            conversationHistory = emptyList(),
            onStateChange = { _, _ -> },
            onToolExecuted = { }
        )
        agent.startNewSession()
        val secondSession = agent.processUserTurn(
            userPrompt = "3 (Steady)",
            conversationHistory = emptyList(),
            onStateChange = { _, _ -> },
            onToolExecuted = { }
        )

        assertEquals(AgentTurnIntent.CHECK_IN, secondSession.turnIntent)
        assertEquals(2, recorded.size)
        assertEquals(1, model.startedSessions)
    }

    @Test
    fun `ending a conversation rotates the model session`() {
        val model = FakeCompanionModelService(response = "I'm here.")
        val agent = buildAgent(model = model)

        agent.endSession()

        assertEquals(1, model.endedSessions)
    }

    @Test
    fun `local companion output stays in everyday language`() = runTest {
        val banned = listOf("pmdd", "medication", "follicular", "luteal", "spotting", "period")
        val agent = buildAgent()

        listOf("1", "2", "3", "4", "5", "I feel off", "today feels heavy").forEach { input ->
            val response = agent.processUserTurn(
                userPrompt = input,
                conversationHistory = emptyList(),
                onStateChange = { _, _ -> },
                onToolExecuted = { }
            )
            val output = "${response.text} ${response.thoughtTrace}".lowercase()
            banned.forEach { term -> assertFalse("found '$term' in: $output", output.contains(term)) }
        }
    }

}
