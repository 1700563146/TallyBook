package com.tallybook.feature.insights.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tallybook.core.common.clock.Clock
import com.tallybook.core.common.clock.DateProvider
import com.tallybook.core.data.repository.StatsRepository
import com.tallybook.core.data.repository.TransactionRepository
import com.tallybook.core.model.stats.ComparisonDirection
import com.tallybook.core.model.stats.PeriodComparison
import com.tallybook.core.model.stats.ReportPeriod
import com.tallybook.core.model.stats.SeriesMode
import com.tallybook.core.model.stats.TimeGranularity
import com.tallybook.core.model.transaction.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * Statistics screen state holder.
 *
 * The user picks a **report period** (周/月/年) and the bucket size follows from it
 * ([ReportPeriod.bucket]): a weekly report is charted per day, a monthly one per week, a yearly one
 * per month. That keeps every chart at 4-12 readable points instead of letting a year turn into a
 * 365-bar chart nobody can read.
 *
 * `flatMapLatest` on (period, anchor) is what prevents a slow query for the previous selection from
 * landing after the new one and repainting the chart with stale data.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InsightsViewModel @Inject constructor(
    private val statsRepository: StatsRepository,
    private val transactionRepository: TransactionRepository,
    private val dateProvider: DateProvider,
    private val clock: Clock,
    private val zoneId: ZoneId,
) : ViewModel() {

    private val _uiState = MutableStateFlow(InsightsUiState())
    val uiState: StateFlow<InsightsUiState> = _uiState.asStateFlow()

    private val period = MutableStateFlow(ReportPeriod.MONTH)

    /** Any instant inside the period being shown. */
    private val anchor = MutableStateFlow(clock.nowMillis())
    private val mode = MutableStateFlow(SeriesMode.BOTH)

    init {
        viewModelScope.launch {
            period.collect { value ->
                // Switching period jumps back to the current one: keeping a "3 months ago" anchor
                // while switching to the weekly view would land on an arbitrary week.
                anchor.value = clock.nowMillis()
                _uiState.update { it.copy(period = value, isLoading = true) }
            }
        }
        viewModelScope.launch {
            mode.collect { value -> _uiState.update { it.copy(mode = value) } }
        }

        viewModelScope.launch {
            combine(period, anchor) { p, a -> p to a }
                .flatMapLatest { (p, a) ->
                    val range = dateProvider.rangeFor(p.rangeUnit, a)
                    val previousRange = dateProvider.rangeFor(p.rangeUnit, range.first - 1)

                    combine(
                        statsRepository.observeTrend(range, p.bucket),
                        statsRepository.observeCategoryBreakdown(range, TransactionType.EXPENSE),
                        transactionRepository.observePeriodTotals(range.first, range.last + 1),
                        transactionRepository.observePeriodTotals(
                            previousRange.first,
                            previousRange.last + 1,
                        ),
                    ) { trend, breakdown, totals, previousTotals ->
                        val comparison = PeriodComparison.of(totals.expense, previousTotals.expense)
                        InsightsUiState(
                            period = p,
                            mode = mode.value,
                            trend = trend,
                            breakdown = breakdown,
                            expenseTotalLabel = totals.expense.format(),
                            incomeTotalLabel = totals.income.format(),
                            netTotalLabel = totals.net.format(),
                            rangeLabel = rangeLabelOf(range, p),
                            bucketHint = bucketHintOf(p.bucket),
                            comparisonLabel = comparisonLabelOf(comparison),
                            comparisonDirection = comparison.direction,
                            rangeStartInclusive = range.first,
                            rangeEndExclusive = range.last + 1,
                            canGoNext = range.last + 1 <= clock.nowMillis(),
                            isLoading = false,
                            isEmpty = trend.points.all { it.expense.isZero && it.income.isZero },
                        )
                    }
                }
                .collect { state ->
                    // Preserve the user's selected mode: it changes without re-querying.
                    _uiState.update { state.copy(mode = it.mode) }
                }
        }
    }

    fun onAction(action: InsightsAction) {
        when (action) {
            is InsightsAction.SetPeriod -> period.value = action.period
            is InsightsAction.SetMode -> mode.value = action.mode
            InsightsAction.PreviousPeriod -> step(-1)
            InsightsAction.NextPeriod -> step(+1)
            InsightsAction.BackToCurrent -> anchor.value = clock.nowMillis()
        }
    }

    /** Moves the anchor by one whole period using range bounds, not "±1 month" day arithmetic. */
    private fun step(delta: Int) {
        val p = period.value
        val range = dateProvider.rangeFor(p.rangeUnit, anchor.value)
        val target = if (delta < 0) range.first - 1 else range.last + 1
        if (delta > 0 && target > clock.nowMillis()) return
        anchor.value = target
    }

    private fun rangeLabelOf(range: LongRange, period: ReportPeriod): String {
        val formatter = when (period) {
            ReportPeriod.WEEK -> DateTimeFormatter.ofPattern("yyyy/M/d")
            ReportPeriod.MONTH -> DateTimeFormatter.ofPattern("yyyy年M月")
            ReportPeriod.YEAR -> DateTimeFormatter.ofPattern("yyyy年")
        }.withZone(zoneId)

        val startText = formatter.format(Instant.ofEpochMilli(range.first))
        val endText = formatter.format(Instant.ofEpochMilli(range.last))
        // A month or a year is identified by one label; only a week needs both ends, because
        // "11月3日" alone would not say which week of the month it is.
        return if (period == ReportPeriod.WEEK) "$startText – $endText" else startText
    }

    private fun bucketHintOf(bucket: TimeGranularity): String = when (bucket) {
        TimeGranularity.DAY -> "按日"
        TimeGranularity.WEEK -> "按周"
        TimeGranularity.MONTH -> "按月"
        TimeGranularity.YEAR -> "按年"
    }

    /**
     * Wording for the comparison chip.
     *
     * "上期无记录" instead of "+∞%" when there is no baseline, and a signed one-decimal percentage
     * otherwise — a bare "+0%" for a 0.4% move would look like a bug.
     */
    private fun comparisonLabelOf(comparison: PeriodComparison): String =
        when (comparison.direction) {
            ComparisonDirection.NO_BASELINE -> "较上期：上期无记录"
            ComparisonDirection.SAME -> "较上期持平"
            ComparisonDirection.UP, ComparisonDirection.DOWN -> {
                val percent = (comparison.ratio ?: 0f) * 100f
                val sign = if (percent >= 0f) "+" else "−"
                "较上期 $sign${"%.1f".format(kotlin.math.abs(percent))}%"
            }
        }
}
