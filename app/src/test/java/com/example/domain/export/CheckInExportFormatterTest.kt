package com.example.domain.export

import com.example.data.local.entity.DailyCheckInEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckInExportFormatterTest {
    @Test
    fun `summary is chronological and preserves the person's words`() {
        val result = CheckInExportFormatter.clinicianSummary(
            listOf(
                checkIn(date = "2026-08-24", timestamp = 2, texture = "OFF", input = "I feel off"),
                checkIn(date = "2026-08-23", timestamp = 1, texture = "HEAVY", input = "foggy")
            )
        )

        assertTrue(result.indexOf("2026-08-23") < result.indexOf("2026-08-24"))
        assertTrue(result.contains("foggy"))
        assertTrue(result.contains("I feel off"))
        assertTrue(result.contains("not a diagnosis"))
    }

    @Test
    fun `summary does not invent phase or cause language`() {
        val result = CheckInExportFormatter.clinicianSummary(
            listOf(checkIn(date = "2026-08-24", timestamp = 1, texture = "STEADY", input = "okay"))
        ).lowercase()

        assertTrue(result.contains("does not assign a body phase"))
        assertFalse(result.contains("follicular"))
        assertFalse(result.contains("luteal"))
        assertFalse(result.contains("ovulation"))
    }

    private fun checkIn(
        date: String,
        timestamp: Long,
        texture: String,
        input: String
    ) = DailyCheckInEntity(
        id = "$date-$timestamp",
        date = date,
        timestamp = timestamp,
        ratingValue = 3,
        singleInputResponse = input,
        detectedTexture = texture,
        agentAcknowledgment = "Saved",
        isOffDay = texture == "OFF"
    )
}
