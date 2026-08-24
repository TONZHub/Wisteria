package com.example.domain.agent

import com.example.domain.agent.model.DailyTexture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NightShiftAnalyzerTest {

    private fun day(number: Int, rating: Int, texture: DailyTexture) =
        CheckInHistoryEntry(
            date = "2024-01-%02d".format(number),
            rating = rating,
            texture = texture,
            inputText = rating.toString()
        )

    @Test
    fun `empty history is still learning`() {
        val brief = NightShiftAnalyzer.analyze(emptyList())

        assertEquals("Still learning", brief.headline)
        assertEquals(0, brief.sampleSize)
        assertNull(brief.daysUntilOff)
        assertFalse(brief.isOffActive)
    }

    @Test
    fun `learns heavy and off lengths from completed stretches`() {
        val entries =
            (1..4).map { day(it, 2, DailyTexture.HEAVY) } +
                (5..6).map { day(it, 1, DailyTexture.OFF) } +
                (7..13).map { day(it, 5, DailyTexture.BRIGHT) } +
                listOf(day(14, 2, DailyTexture.HEAVY))

        val brief = NightShiftAnalyzer.analyze(entries)

        assertEquals(4, brief.medianHeavyDays)
        assertEquals(2, brief.medianOffDays)
        assertEquals(1, brief.currentHeavyStreak)
        assertEquals(3, brief.daysUntilOff)
        assertEquals(1, brief.learnedTransitionCount)

        val trace = brief.traces.first { it.toolName == "NoticePattern" }
        assertEquals("1", trace.args["heavyStreak"])
        assertEquals("4", trace.args["usualHeavyDays"])
        assertEquals("2", trace.args["usualOffDays"])
    }

    @Test
    fun `an active off stretch is reported immediately`() {
        val entries =
            (1..4).map { day(it, 2, DailyTexture.HEAVY) } +
                (5..7).map { day(it, 1, DailyTexture.OFF) }

        val brief = NightShiftAnalyzer.analyze(entries)

        assertTrue(brief.isOffActive)
        assertEquals(0, brief.daysUntilOff)
        assertEquals(3, brief.currentOffStreak)
        assertEquals("Today feels off", brief.headline)
        assertEquals("3", brief.traces.first { it.toolName == "PrepareIdeas" }.args["count"])
    }

    @Test
    fun `a familiar heavy stretch can produce a gentle heads up`() {
        val entries =
            (1..5).map { day(it, 2, DailyTexture.HEAVY) } +
                (6..7).map { day(it, 1, DailyTexture.OFF) } +
                listOf(day(8, 4, DailyTexture.BRIGHT)) +
                (9..12).map { day(it, 2, DailyTexture.HEAVY) }

        val brief = NightShiftAnalyzer.analyze(entries)

        assertFalse(brief.isOffActive)
        assertTrue(brief.isOffNear)
        assertEquals(1, brief.daysUntilOff)
        assertEquals("An off stretch may be near", brief.headline)
    }

    @Test
    fun `confidence grows with evidence and stays capped`() {
        val small = NightShiftAnalyzer.analyze((1..2).map { day(it, 3, DailyTexture.STEADY) })
        val large = NightShiftAnalyzer.analyze((1..30).map { day(it, 3, DailyTexture.STEADY) })

        assertTrue(large.confidence > small.confidence)
        assertTrue(large.confidence <= 0.45f)
    }

    @Test
    fun `a low number alone does not override the saved texture`() {
        val brief = NightShiftAnalyzer.analyze(listOf(day(1, 1, DailyTexture.STEADY)))

        assertFalse(brief.isOffActive)
        assertEquals("0", brief.traces.first { it.toolName == "PrepareIdeas" }.args["count"])
    }
}
