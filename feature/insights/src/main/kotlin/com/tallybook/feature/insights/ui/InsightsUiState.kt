package com.tallybook.feature.insights.ui

import com.tallybook.core.model.stats.CategorySlice
import com.tallybook.core.model.stats.ComparisonDirection
import com.tallybook.core.model.stats.ReportPeriod
import com.tallybook.core.model.stats.SeriesMode
import com.tallybook.core.model.stats.TrendSeries

/**
 * Immutable UI state for the statistics screen.
 *
 * Report period, series mode and the anchor instant are part of the state, not local `remember`s:
 * they must survive configuration changes and be restored after process death, and they belong to the
 * same snapshot of data that was queried for them.
 */
data class InsightsUiState(
    val period: ReportPeriod = ReportPeriod.MONTH,
    val mode: SeriesMode = SeriesMode.BOTH,
    val trend: TrendSeries? = null,
    val breakdown: List<CategorySlice> = emptyList(),
    val expenseTotalLabel: String = "",
    val incomeTotalLabel: String = "",
    val netTotalLabel: String = "",
    /** e.g. "2026年11月" / "2026/11/3 – 2026/11/9". */
    val rangeLabel: String = "",
    /** Explains what one bar means, e.g. "按日" / "按周" / "按月". */
    val bucketHint: String = "",
    val comparisonLabel: String? = null,
    val comparisonDirection: ComparisonDirection? = null,
    val rangeStartInclusive: Long = 0L,
    val rangeEndExclusive: Long = 0L,
    /** False when the shown period is the current one; forward navigation stops there. */
    val canGoNext: Boolean = false,
    val isLoading: Boolean = true,
    val isEmpty: Boolean = false,
)

/** Actions the screen can dispatch. Sealed so an exhaustive `when` catches new cases at compile time. */
sealed interface InsightsAction {
    data class SetPeriod(val period: ReportPeriod) : InsightsAction
    data class SetMode(val mode: SeriesMode) : InsightsAction
    data object PreviousPeriod : InsightsAction
    data object NextPeriod : InsightsAction
    data object BackToCurrent : InsightsAction
}
