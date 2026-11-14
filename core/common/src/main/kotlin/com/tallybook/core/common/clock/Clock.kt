package com.tallybook.core.common.clock

import com.tallybook.core.model.stats.TimeGranularity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Time is injected rather than read from [System.currentTimeMillis] directly.
 *
 * Why this matters more than it looks: every period boundary, every "is this budget exceeded" rule,
 * and every chart bucket is timezone- and clock-sensitive. Production code calling
 * `System.currentTimeMillis()` can only be tested by sleeping or by refactoring later. Faking it
 * behind an interface costs one thin class and makes the whole domain deterministic — including
 * "run this test on the 1st of the month" cases that otherwise break CI once a month.
 */
interface Clock {
    fun nowMillis(): Long
}

@Singleton
class SystemClock @Inject constructor() : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}

/**
 * Calendar arithmetic boundary. All implementations must be zone-aware: month bucketing must use the
 * user's zone, never UTC.
 */
interface DateProvider {
    fun startOfMonth(epochMillis: Long): Long
    fun endOfMonthExclusive(epochMillis: Long): Long
    fun startOfDay(epochMillis: Long): Long
    fun endOfDayExclusive(epochMillis: Long): Long

    fun startOfWeek(epochMillis: Long): Long
    fun endOfWeekExclusive(epochMillis: Long): Long

    fun startOfYear(epochMillis: Long): Long
    fun endOfYearExclusive(epochMillis: Long): Long

    /** `start until endExclusive` for one calendar unit containing [anchorMillis]. */
    fun rangeFor(granularity: TimeGranularity, anchorMillis: Long): LongRange

    /**
     * Start of the calendar unit that [date] belongs to.
     *
     * This is the **single source of truth for bucketing**. Charts and budgets both derive their
     * periods here, so they cannot disagree — and the week definition (`weekStartsOn`) is applied in
     * exactly one place instead of once in SQL and once in Kotlin. Deriving one bucket key in SQL
     * and another in Kotlin was a real bug: `strftime('%W')` is "nth Monday of the year" while the
     * JVM's `WeekFields` is ISO week-based-year, and they disagree on ~50% of days.
     */
    fun bucketStartOf(date: java.time.LocalDate, granularity: TimeGranularity): java.time.LocalDate

    /** Epoch millis at the start of [date] in the configured zone. */
    fun startOfDay(date: java.time.LocalDate): Long

    /** Local calendar date of [epochMillis] in the configured zone. */
    fun toLocalDate(epochMillis: Long): java.time.LocalDate

    fun daysBetweenInclusive(fromMillis: Long, toExclusiveMillis: Long): Int

    /** Days elapsed so far inside the period, clamped to `1..total` once the period has started. */
    fun daysElapsed(startMillis: Long, endExclusiveMillis: Long, nowMillis: Long): Int
}
