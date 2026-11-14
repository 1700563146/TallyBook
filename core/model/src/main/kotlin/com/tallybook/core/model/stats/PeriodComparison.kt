package com.tallybook.core.model.stats

import com.tallybook.core.model.money.Money

/** Direction of a period-over-period change. */
enum class ComparisonDirection { UP, DOWN, SAME, NO_BASELINE }

/**
 * Compares one period against the equivalent previous period.
 *
 * Exists as a type (rather than an inline percentage in the ViewModel) because the interesting cases
 * are the ones that break the naive `(cur - prev) / prev`:
 * - previous is zero → division by zero, and "+∞%" is not a useful thing to show
 * - both are zero → "no change", not "no baseline"
 * - current is zero → "-100%", which is a real signal (spending stopped), not an error
 *
 * [ratio] is null only when there is no baseline to compare against; callers must render that case
 * with words instead of a percentage.
 */
data class PeriodComparison(
    val currentMinor: Long,
    val previousMinor: Long,
    val ratio: Float?,
    val direction: ComparisonDirection,
) {
    val hasBaseline: Boolean get() = direction != ComparisonDirection.NO_BASELINE

    val differenceMinor: Long get() = currentMinor - previousMinor

    companion object {
        fun of(current: Money, previous: Money): PeriodComparison {
            require(current.currency == previous.currency) {
                "comparison requires the same currency"
            }
            val cur = current.minorUnits
            val prev = previous.minorUnits

            return when {
                // Previous period had nothing: a percentage would be meaningless.
                prev == 0L && cur > 0L -> PeriodComparison(
                    currentMinor = cur,
                    previousMinor = prev,
                    ratio = null,
                    direction = ComparisonDirection.NO_BASELINE,
                )
                // Nothing in either period: genuinely unchanged.
                prev == 0L -> PeriodComparison(cur, prev, ratio = 0f, direction = ComparisonDirection.SAME)
                else -> {
                    val ratio = (cur - prev).toFloat() / prev.toFloat()
                    val direction = when {
                        cur > prev -> ComparisonDirection.UP
                        cur < prev -> ComparisonDirection.DOWN
                        else -> ComparisonDirection.SAME
                    }
                    PeriodComparison(cur, prev, ratio, direction)
                }
            }
        }
    }
}
