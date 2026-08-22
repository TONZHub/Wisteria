package com.example.testutil

import com.example.core.model.GeminiGenerateRequest
import com.example.core.model.GeminiGenerateResponse
import com.example.core.network.GeminiApiService

/**
 * Fails loudly if actually invoked. In tests, [com.example.domain.agent.DailyCheckInAgent]
 * never calls Gemini because no real API key is configured (BuildConfig falls back to the
 * ".env.example" placeholder), so the agent always takes its local fallback path.
 */
class FakeGeminiApiService : GeminiApiService {
    override suspend fun generateContent(apiKey: String, request: GeminiGenerateRequest): GeminiGenerateResponse {
        throw UnsupportedOperationException("Gemini should not be called in this test - no API key is configured")
    }
}
