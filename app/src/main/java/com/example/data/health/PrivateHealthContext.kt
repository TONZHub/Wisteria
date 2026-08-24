package com.example.data.health

internal data class PrivateHealthSignals(
    val recentSleepHours: Double? = null,
    val recentSteps: Long? = null,
    val daysSinceLoggedStart: Long? = null
) {
    val hasAnySignal: Boolean
        get() = recentSleepHours != null || recentSteps != null || daysSinceLoggedStart != null
}

/**
 * Reduces private Health Connect records to a tone-only instruction on-device.
 * Raw values, dates, record names, and inferred labels never enter the model prompt.
 */
internal object PrivateHealthContextPolicy {
    fun modelToneInstruction(signals: PrivateHealthSignals): String? {
        if (!signals.hasAnySignal) return null

        val lowerPressureTone =
            signals.recentSleepHours?.let { it < 6.0 } == true ||
                signals.daysSinceLoggedStart?.let { it in 0L..6L || it in 17L..35L } == true

        return if (lowerPressureTone) {
            "Use an especially gentle, low-pressure tone. Offer at most one optional idea. " +
                "Never infer or mention why."
        } else {
            "Use a warm, concise tone. Private device context must never be mentioned or used " +
                "to infer a cause."
        }
    }
}
