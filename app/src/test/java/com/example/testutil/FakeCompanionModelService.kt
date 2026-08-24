package com.example.testutil

import com.example.core.network.CompanionModelService

class FakeCompanionModelService(
    private val response: String? = null,
    private val shouldThrow: Boolean = response == null
) : CompanionModelService {
    val prompts = mutableListOf<String>()

    override suspend fun generateReply(prompt: String): String? {
        prompts += prompt
        if (shouldThrow) error("Optional response layer unavailable in this test")
        return response
    }
}
