package com.example.domain.agent

import com.example.domain.agent.model.CycleTexture
import kotlin.math.min

/** One day's check-in, as read back out of local history for overnight analysis. */
data class CheckInHistoryEntry(
    val date: String,
    val rating: Int,
    val texture: CycleTexture,
    val inputText: String
)

data class NightShiftToolTrace(
    val toolName: String,
    val args: Map<String, String>,
    val result: String
)

data class MorningBrief(
    val headline: String,
    val body: String,
    val texture: CycleTexture,
    val isWindowActive: Boolean,
    val isWindowNear: Boolean,
    val daysUntilDrop: Int?,
    val confidence: Float,
    val medianSpottingDays: Int?,
    val medianDropDays: Int?,
    val currentSpottingStreak: Int,
    val currentDropStreak: Int,
    val sampleSize: Int,
    val traces: List<NightShiftToolTrace>
)

/**
 * Learns a user's own spotting/drop-window length from their check-in history instead
 * of assuming any fixed schedule. This is the same logic a Cloud Run worker would run
 * overnight in production - [com.example.data.cloud.CloudRunWorkflowServiceImpl] just
 * calls it in-process today.
 */
object NightShiftAnalyzer {

    private enum class RunCategory { SPOTTING, DROP, OTHER }

    private data class Run(val category: RunCategory, val length: Int)

    fun analyze(history: List<CheckInHistoryEntry>): MorningBrief {
        val traces = mutableListOf<NightShiftToolTrace>()
        traces += NightShiftToolTrace(
            toolName = "ReadCycleMemory",
            args = mapOf("checkIns" to history.size.toString()),
            result = "Loaded ${history.size} check-in(s) from local history"
        )

        if (history.isEmpty()) {
            traces += NightShiftToolTrace("DetectWindow", emptyMap(), "No history yet - cannot detect a window")
            traces += NightShiftToolTrace("QueueCare", mapOf("count" to "0"), "No care actions queued")
            val headline = "Still calibrating"
            traces += NightShiftToolTrace("WriteMorningBrief", mapOf("headline" to headline), "Morning brief written")
            return MorningBrief(
                headline = headline,
                body = "No check-ins yet - Night Shift needs a few days of history to learn your rhythm.",
                texture = CycleTexture.UNKNOWN_CALIBRATING,
                isWindowActive = false,
                isWindowNear = false,
                daysUntilDrop = null,
                confidence = 0f,
                medianSpottingDays = null,
                medianDropDays = null,
                currentSpottingStreak = 0,
                currentDropStreak = 0,
                sampleSize = 0,
                traces = traces
            )
        }

        val sorted = history.sortedBy { it.date }
        val runs = collapseRuns(sorted)
        val completedRuns = runs.dropLast(1)
        val trailingRun = runs.last()

        val medianSpotting = median(completedRuns.filter { it.category == RunCategory.SPOTTING }.map { it.length })
        val medianDrop = median(completedRuns.filter { it.category == RunCategory.DROP }.map { it.length })

        val currentSpottingStreak = if (trailingRun.category == RunCategory.SPOTTING) trailingRun.length else 0
        val currentDropStreak = if (trailingRun.category == RunCategory.DROP) trailingRun.length else 0
        val isWindowActive = trailingRun.category == RunCategory.DROP

        val daysUntilDrop: Int? = when {
            isWindowActive -> 0
            medianSpotting == null -> null
            else -> (medianSpotting - currentSpottingStreak).coerceAtLeast(0)
        }

        val isWindowNear = !isWindowActive && daysUntilDrop != null && daysUntilDrop <= 2 && currentSpottingStreak > 0

        val bonus = if (medianSpotting != null || medianDrop != null) 0.1f else 0f
        val confidence = min(0.9f, 0.28f + sorted.size * 0.035f + bonus)

        traces += NightShiftToolTrace(
            toolName = "DetectWindow",
            args = mapOf(
                "spottingStreak" to currentSpottingStreak.toString(),
                "lowRatingStreak" to currentDropStreak.toString(),
                "learnedSpottingDays" to (medianSpotting?.toString() ?: "unknown"),
                "learnedDropDays" to (medianDrop?.toString() ?: "unknown")
            ),
            result = "active=$isWindowActive, near=$isWindowNear, daysUntilDrop=${daysUntilDrop?.toString() ?: "unknown"}"
        )

        val queuedCare = isWindowActive
        if (queuedCare) {
            traces += NightShiftToolTrace(
                toolName = "QueueCare",
                args = mapOf("count" to "3"),
                result = "Queued 3 care action(s): rest, low-effort meal, cognitive shield"
            )
        } else {
            traces += NightShiftToolTrace("QueueCare", mapOf("count" to "0"), "No care actions queued")
        }

        val headline: String
        val body: String
        when {
            isWindowActive -> {
                headline = "Drop window active"
                body = "Rest and lower cognitive load today - this pattern has shown up before."
            }
            isWindowNear -> {
                headline = "Drop window may be near (~${daysUntilDrop}d)"
                body = "Spotting has run $currentSpottingStreak day(s); your learned pattern suggests a harder window could be close."
            }
            medianSpotting == null -> {
                headline = "Still calibrating"
                body = "Keep checking in daily - Night Shift needs to see a few full spotting-to-drop cycles before it can predict anything."
            }
            else -> {
                headline = "Pattern steady"
                body = "No drop window predicted soon, based on ${sorted.size} days of check-ins."
            }
        }

        traces += NightShiftToolTrace("WriteMorningBrief", mapOf("headline" to headline), "Morning brief written")

        return MorningBrief(
            headline = headline,
            body = body,
            texture = sorted.last().texture,
            isWindowActive = isWindowActive,
            isWindowNear = isWindowNear,
            daysUntilDrop = daysUntilDrop,
            confidence = confidence,
            medianSpottingDays = medianSpotting,
            medianDropDays = medianDrop,
            currentSpottingStreak = currentSpottingStreak,
            currentDropStreak = currentDropStreak,
            sampleSize = sorted.size,
            traces = traces
        )
    }

    private fun categorize(entry: CheckInHistoryEntry): RunCategory = when {
        entry.texture == CycleTexture.MEDS_DROP_WINDOW || entry.rating <= 2 -> RunCategory.DROP
        entry.texture == CycleTexture.SPOTTING_PHASE -> RunCategory.SPOTTING
        else -> RunCategory.OTHER
    }

    private fun collapseRuns(sorted: List<CheckInHistoryEntry>): List<Run> {
        val runs = mutableListOf<Run>()
        var currentCategory = categorize(sorted.first())
        var currentLength = 1
        for (i in 1 until sorted.size) {
            val category = categorize(sorted[i])
            if (category == currentCategory) {
                currentLength++
            } else {
                runs += Run(currentCategory, currentLength)
                currentCategory = category
                currentLength = 1
            }
        }
        runs += Run(currentCategory, currentLength)
        return runs
    }

    private fun median(values: List<Int>): Int? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }
}
