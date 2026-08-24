package com.example.data.health

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivateHealthContextPolicyTest {
    @Test
    fun `no granted signal produces no model context`() {
        assertNull(PrivateHealthContextPolicy.modelToneInstruction(PrivateHealthSignals()))
    }

    @Test
    fun `a lower-pressure window becomes only a tone instruction`() {
        val instruction = PrivateHealthContextPolicy.modelToneInstruction(
            PrivateHealthSignals(daysSinceLoggedStart = 23)
        ).orEmpty()

        assertTrue(instruction.contains("low-pressure"))
        assertFalse(instruction.contains("23"))
        assertFalse(instruction.contains("period", ignoreCase = true))
        assertFalse(instruction.contains("phase", ignoreCase = true))
        assertFalse(instruction.contains("day", ignoreCase = true))
    }

    @Test
    fun `short recent sleep can lower pressure without exposing the value`() {
        val instruction = PrivateHealthContextPolicy.modelToneInstruction(
            PrivateHealthSignals(recentSleepHours = 5.25)
        ).orEmpty()

        assertTrue(instruction.contains("low-pressure"))
        assertFalse(instruction.contains("5.25"))
        assertFalse(instruction.contains("sleep", ignoreCase = true))
    }

    @Test
    fun `steps alone never become a cause or raw count`() {
        val instruction = PrivateHealthContextPolicy.modelToneInstruction(
            PrivateHealthSignals(recentSteps = 4_321)
        ).orEmpty()

        assertTrue(instruction.contains("warm, concise"))
        assertTrue(instruction.contains("never", ignoreCase = true))
        assertFalse(instruction.contains("4321"))
        assertFalse(instruction.contains("steps", ignoreCase = true))
    }
}
