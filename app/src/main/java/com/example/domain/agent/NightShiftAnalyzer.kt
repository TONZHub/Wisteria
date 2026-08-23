package com.example.domain.agent

import com.example.domain.agent.model.DailyTexture
import kotlin.math.min

/** One saved daily check-in, read from local history for on-demand pattern learning. */
data class CheckInHistoryEntry(
    val date: String,
    val rating: Int,
    val texture: DailyTexture,
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
    val texture: DailyTexture,
    val isOffActive: Boolean,
    val isOffNear: Boolean,
    val daysUntilOff: Int?,
    val confidence: Float,
    val medianHeavyDays: Int?,
    val medianOffDays: Int?,
    val currentHeavyStreak: Int,
    val currentOffStreak: Int,
    val learnedTransitionCount: Int,
    val sampleSize: Int,
    val traces: List<NightShiftToolTrace>
)

/**
 * Learns recurring heavy-to-off stretches from the person's own words and ratings.
 * It does not assign a body phase or cause, and it runs only when requested.
 */
object NightShiftAnalyzer {

    private enum class RunCategory { HEAVY, OFF, OTHER }

    private data class Run(val category: RunCategory, val length: Int)

    fun analyze(history: List<CheckInHistoryEntry>): MorningBrief {
        val traces = mutableListOf<NightShiftToolTrace>()
        traces += NightShiftToolTrace(
            toolName = "ReadLocalHistory",
            args = mapOf("checkIns" to history.size.toString()),
            result = "Loaded ${history.size} check-in(s)"
        )

        if (history.isEmpty()) {
            traces += NightShiftToolTrace("NoticePattern", emptyMap(), "Not enough history yet")
            traces += NightShiftToolTrace("PrepareIdeas", mapOf("count" to "0"), "No care ideas prepared")
            val headline = "Still learning"
            traces += NightShiftToolTrace("WriteBrief", mapOf("headline" to headline), "Brief ready")
            return MorningBrief(
                headline = headline,
                body = "A few daily check-ins will help Wisteria notice your own rhythm.",
                texture = DailyTexture.UNKNOWN,
                isOffActive = false,
                isOffNear = false,
                daysUntilOff = null,
                confidence = 0f,
                medianHeavyDays = null,
                medianOffDays = null,
                currentHeavyStreak = 0,
                currentOffStreak = 0,
                learnedTransitionCount = 0,
                sampleSize = 0,
                traces = traces
            )
        }

        val sorted = history.sortedBy { it.date }
        val runs = collapseRuns(sorted)
        val completedRuns = runs.dropLast(1)
        val trailingRun = runs.last()

        val heavyBeforeOff = runs.zipWithNext()
            .filter { (first, second) ->
                first.category == RunCategory.HEAVY && second.category == RunCategory.OFF
            }
            .map { (heavy, _) -> heavy.length }
        val medianHeavy = median(heavyBeforeOff)
        val medianOff = median(completedRuns.filter { it.category == RunCategory.OFF }.map { it.length })

        val currentHeavyStreak = if (trailingRun.category == RunCategory.HEAVY) trailingRun.length else 0
        val currentOffStreak = if (trailingRun.category == RunCategory.OFF) trailingRun.length else 0
        val isOffActive = trailingRun.category == RunCategory.OFF

        val daysUntilOff: Int? = when {
            isOffActive -> 0
            medianHeavy == null -> null
            currentHeavyStreak == 0 -> null
            else -> (medianHeavy - currentHeavyStreak).coerceAtLeast(0)
        }
        val isOffNear = !isOffActive && daysUntilOff != null && daysUntilOff <= 2

        val confidence = if (heavyBeforeOff.isEmpty()) {
            min(0.45f, 0.1f + sorted.size * 0.02f)
        } else {
            min(0.9f, 0.2f + sorted.size * 0.025f + heavyBeforeOff.size * 0.15f)
        }

        traces += NightShiftToolTrace(
            toolName = "NoticePattern",
            args = mapOf(
                "heavyStreak" to currentHeavyStreak.toString(),
                "offStreak" to currentOffStreak.toString(),
                "usualHeavyDays" to (medianHeavy?.toString() ?: "unknown"),
                "usualOffDays" to (medianOff?.toString() ?: "unknown"),
                "seenTransitions" to heavyBeforeOff.size.toString()
            ),
            result = "offNow=$isOffActive, offMayBeNear=$isOffNear, days=${daysUntilOff?.toString() ?: "unknown"}"
        )

        if (isOffActive) {
            traces += NightShiftToolTrace(
                toolName = "PrepareIdeas",
                args = mapOf("count" to "3"),
                result = "Prepared optional ideas: pause, easy food, one less decision"
            )
        } else {
            traces += NightShiftToolTrace("PrepareIdeas", mapOf("count" to "0"), "No care ideas prepared")
        }

        val (headline, body) = when {
            isOffActive ->
                "Today feels off" to "You marked today as off. A few low-effort ideas are ready if you want them."
            isOffNear ->
                "An off stretch may be near" to "This heavy stretch resembles ${heavyBeforeOff.size} earlier pattern(s). Treat it as a gentle heads-up, not a certainty."
            medianHeavy == null ->
                "Still learning" to "Keep checking in when it feels easy. Wisteria needs to see more than one heavy-to-off stretch before it calls anything a pattern."
            else ->
                "Your rhythm looks steady" to "Nothing in the recent check-ins resembles an earlier off stretch."
        }

        traces += NightShiftToolTrace("WriteBrief", mapOf("headline" to headline), "Brief ready")

        return MorningBrief(
            headline = headline,
            body = body,
            texture = sorted.last().texture,
            isOffActive = isOffActive,
            isOffNear = isOffNear,
            daysUntilOff = daysUntilOff,
            confidence = confidence,
            medianHeavyDays = medianHeavy,
            medianOffDays = medianOff,
            currentHeavyStreak = currentHeavyStreak,
            currentOffStreak = currentOffStreak,
            learnedTransitionCount = heavyBeforeOff.size,
            sampleSize = sorted.size,
            traces = traces
        )
    }

    private fun categorize(entry: CheckInHistoryEntry): RunCategory = when (entry.texture) {
        DailyTexture.OFF -> RunCategory.OFF
        DailyTexture.HEAVY -> RunCategory.HEAVY
        else -> RunCategory.OTHER
    }

    private fun collapseRuns(sorted: List<CheckInHistoryEntry>): List<Run> {
        val runs = mutableListOf<Run>()
        var currentCategory = categorize(sorted.first())
        var currentLength = 1
        for (index in 1 until sorted.size) {
            val category = categorize(sorted[index])
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
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
    }
}
