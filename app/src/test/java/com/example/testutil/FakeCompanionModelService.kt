package com.example.testutil

import com.example.core.network.CompanionModelService
import com.example.core.network.CompanionModelReply

class FakeCompanionModelService(
    private val response: String? = null,
    private val shouldThrow: Boolean = response == null
) : CompanionModelService {
    val prompts = mutableListOf<String>()
    var startedSessions = 0
    var endedSessions = 0

    override suspend fun generateReply(prompt: String): CompanionModelReply? {
        prompts += prompt
        if (shouldThrow) error("Optional response layer unavailable in this test")
        return response?.let {
            CompanionModelReply(
                text = it,
                runtime = "Fake ADK runtime",
                model = "fake-model",
                sessionId = "fake-session",
                eventCount = 1
            )
        }
    }

    override fun startNewSession() {
        startedSessions += 1
    }

    override fun endSession() {
        endedSessions += 1
    }
}
