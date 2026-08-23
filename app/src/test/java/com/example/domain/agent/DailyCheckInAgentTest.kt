package com.example.domain.agent

import com.example.domain.agent.model.AgentMessage
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
    fun `an unfamiliar word stays unlabeled`() = runTest {
        val response = buildAgent().processUserTurn(
            userPrompt = "purple",
            conversationHistory = emptyList(),
            onStateChange = { _, _ -> },
            onToolExecuted = { }
        )

        assertEquals(DailyTexture.UNKNOWN, response.structuredPulse?.texture)
        assertEquals(0f, response.structuredPulse?.confidenceScore)
    }

    @Test
    fun `optional model wording is used when available`() = runTest {
        val model = FakeCompanionModelService(response = "Saved. Want one easy idea?")
        val response = buildAgent(model = model).processUserTurn(
            userPrompt = "I feel off",
            conversationHistory = emptyList(),
            onStateChange = { _, _ -> },
            onToolExecuted = { }
        )

        assertEquals("Saved. Want one easy idea?", response.text)
        assertEquals(1, model.prompts.size)
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
