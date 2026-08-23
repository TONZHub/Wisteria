package com.example.data.cloud

import com.example.domain.agent.CheckInHistoryEntry
import com.example.domain.agent.model.DailyTexture
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNightShiftServiceTest {

    @Test
    fun `execution is explicitly on device`() = runTest {
        val execution = LocalNightShiftService().run(emptyList())

        assertEquals("on-device", execution.executionLocation)
        assertEquals("COMPLETED", execution.status)
    }

    @Test
    fun `result summary comes from the analyzer`() = runTest {
        val history = (1..3).map {
            CheckInHistoryEntry(
                date = "2024-01-0$it",
                rating = 1,
                texture = DailyTexture.OFF,
                inputText = "1"
            )
        }

        val execution = LocalNightShiftService().run(history)

        assertEquals(execution.morningBrief.body, execution.resultSummary)
        assertTrue(execution.morningBrief.isOffActive)
    }

    @Test
    fun `empty history completes and reports still learning`() = runTest {
        val execution = LocalNightShiftService().run(emptyList())

        assertEquals("Still learning", execution.morningBrief.headline)
    }
}
