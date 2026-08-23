package com.example.domain.agent

import com.example.domain.agent.model.CycleTexture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NightShiftAnalyzerTest {

    private fun day(n: Int, rating: Int, texture: CycleTexture) =
        CheckInHistoryEntry(date = "2024-01-%02d".format(n), rating = rating, texture = texture, inputText = "$rating")

    @Test
    fun `empty history is still calibrating`() {
        val brief = NightShiftAnalyzer.analyze(emptyList())

        assertEquals("Still calibrating", brief.headline)
        assertEquals(0, brief.sampleSize)
        assertNull(brief.daysUntilDrop)
        assertFalse(brief.isWindowActive)
    }

    @Test
    fun `learns spotting and drop length from completed runs, not a fixed 10 or 5 day assumption`() {
        val entries =
            (1..4).map { day(it, 3, CycleTexture.SPOTTING_PHASE) } +
                (5..6).map { day(it, 1, CycleTexture.MEDS_DROP_WINDOW) } +
                (7..13).map { day(it, 4, CycleTexture.FEELING_GOOD) } +
                listOf(day(14, 3, CycleTexture.SPOTTING_PHASE))

        val brief = NightShiftAnalyzer.analyze(entries)

        assertEquals(14, brief.sampleSize)
        assertEquals(4, brief.medianSpottingDays)
        assertEquals(2, brief.medianDropDays)
        assertEquals(1, brief.currentSpottingStreak)
        assertFalse(brief.isWindowActive)
        // Regression: comes from the learned median run length, never a hardcoded 10.
        assertEquals(3, brief.daysUntilDrop)

        val detectWindowTrace = brief.traces.first { it.toolName == "DetectWindow" }
        assertEquals("1", detectWindowTrace.args["spottingStreak"])
        assertEquals("4", detectWindowTrace.args["learnedSpottingDays"])
        assertEquals("2", detectWindowTrace.args["learnedDropDays"])
    }

    @Test
    fun `an active drop window is reported immediately with zero days until drop`() {
        val entries =
            (1..4).map { day(it, 3, CycleTexture.SPOTTING_PHASE) } +
                (5..7).map { day(it, 1, CycleTexture.MEDS_DROP_WINDOW) }

        val brief = NightShiftAnalyzer.analyze(entries)

        assertTrue(brief.isWindowActive)
        assertEquals(0, brief.daysUntilDrop)
        assertEquals(3, brief.currentDropStreak)
        assertEquals("Drop window active", brief.headline)
        assertEquals("3", brief.traces.first { it.toolName == "QueueCare" }.args["count"])
    }

    @Test
    fun `a drop window is flagged as near when the learned spotting length is almost up`() {
        val entries =
            (1..5).map { day(it, 3, CycleTexture.SPOTTING_PHASE) } +
                listOf(day(6, 4, CycleTexture.FEELING_GOOD)) +
                (7..10).map { day(it, 3, CycleTexture.SPOTTING_PHASE) }

        val brief = NightShiftAnalyzer.analyze(entries)

        assertFalse(brief.isWindowActive)
        assertTrue(brief.isWindowNear)
        assertEquals(1, brief.daysUntilDrop)
        assertEquals("Drop window may be near (~1d)", brief.headline)
    }

    @Test
    fun `confidence grows with sample size but is capped, never a hardcoded fixed value`() {
        val small = NightShiftAnalyzer.analyze((1..2).map { day(it, 3, CycleTexture.FEELING_GOOD) })
        val large = NightShiftAnalyzer.analyze((1..30).map { day(it, 3, CycleTexture.FEELING_GOOD) })

        assertTrue(large.confidence > small.confidence)
        assertTrue(large.confidence <= 0.9f)
    }

    @Test
    fun `no care is queued when no drop window is active`() {
        val entries = (1..5).map { day(it, 4, CycleTexture.FEELING_GOOD) }

        val brief = NightShiftAnalyzer.analyze(entries)

        assertEquals("0", brief.traces.first { it.toolName == "QueueCare" }.args["count"])
    }
}
