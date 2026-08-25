package com.example.domain.export

import com.example.data.local.entity.DailyCheckInEntity
import java.util.Locale

/** Builds a small, human-readable history without adding a phase, cause, or diagnosis. */
object CheckInExportFormatter {
    fun clinicianSummary(checkIns: List<DailyCheckInEntity>): String {
        val ordered = checkIns.sortedBy { it.timestamp }
        if (ordered.isEmpty()) {
            return "Wisteria check-in history\n\nNo saved check-ins yet."
        }

        val dateRange = if (ordered.first().date == ordered.last().date) {
            ordered.first().date
        } else {
            "${ordered.first().date} to ${ordered.last().date}"
        }

        val textureCounts = ordered
            .groupingBy { it.detectedTexture.normalizedTexture() }
            .eachCount()
            .toSortedMap()

        return buildString {
            appendLine("Wisteria check-in history")
            appendLine("$dateRange · ${ordered.size} saved check-in${if (ordered.size == 1) "" else "s"}")
            appendLine()
            appendLine("This is a personal record, not a diagnosis. Wisteria does not assign a body phase or explain why a person felt a certain way.")
            appendLine()
            appendLine("Everyday textures")
            textureCounts.forEach { (texture, count) ->
                appendLine("- $texture: $count")
            }
            appendLine()
            appendLine("Daily history")
            ordered.forEach { checkIn ->
                val input = checkIn.singleInputResponse
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .ifBlank { "No word saved" }
                appendLine("- ${checkIn.date} · ${checkIn.detectedTexture.normalizedTexture()} · ${checkIn.ratingValue}/5 · $input")
            }
            appendLine()
            append("These entries reflect only what the person chose to save. A healthcare professional can interpret them alongside other information.")
        }
    }

    private fun String.normalizedTexture(): String =
        lowercase(Locale.ROOT).replaceFirstChar { character ->
            if (character.isLowerCase()) character.titlecase(Locale.ROOT) else character.toString()
        }
}
