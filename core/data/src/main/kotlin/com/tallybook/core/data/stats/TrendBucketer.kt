package com.tallybook.core.data.stats

import com.tallybook.core.common.clock.DateProvider
import com.tallybook.core.model.money.CurrencyCode
import com.tallybook.core.model.money.Money
import com.tallybook.core.model.stats.TimeGranularity
import com.tallybook.core.model.stats.WeekdayLabels
import com.tallybook.core.model.stats.TrendPoint
import com.tallybook.core.model.stats.TrendSeries
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** One day of already-aggregated activity, as delivered by SQL. */
data class DayActivity(
    val date: LocalDate,
    val expenseMinor: Long,
    val incomeMinor: Long,
    val count: Int,
)

/**
 * Groups daily activity into week / month / year buckets and fills empty buckets with zeros.
 *
 * Why this class exists as a separate, injectable unit instead of a private method:
 *
 * 1. **It is the only place bucketing happens.** The SQL query stops at day granularity, so there is
 *    no second implementation that can drift. The previous design computed a week key in SQL
 *    (`strftime('%W')`) and another in Kotlin (`WeekFields`), and they disagreed on ~50% of days,
 *    which silently blanked the weekly chart.
 * 2. **It is pure and trivially testable.** No Room, no coroutines, no Android — the test feeds
 *    `DayActivity` lists around year boundaries and asserts the bucket assignment directly.
 * 3. **It shares the week definition with budgets**, because both go through
 *    `DateProvider.bucketStartOf`. That is what makes "chart says this week" and "budget says this
 *    week" agree by construction.
 */
@Singleton
class TrendBucketer @Inject constructor(
    private val dateProvider: DateProvider,
) {

    /**
     * Builds a continuous series over [range] (`start until endExclusive` as a LongRange).
     *
     * Days that fall outside the range are ignored rather than trusted, because the DAO range and the
     * caller's range can differ by a millisecond after a timezone change.
     */
    fun bucket(
        range: LongRange,
        granularity: TimeGranularity,
        days: List<DayActivity>,
        currency: CurrencyCode = CurrencyCode.CNY,
    ): TrendSeries {
        if (range.isEmpty()) {
            return TrendSeries(granularity = granularity, points = emptyList())
        }

        val startDate = dateProvider.toLocalDate(range.first)
        val endDate = dateProvider.toLocalDate(range.last)

        // Aggregate into bucket starts. Missing buckets are filled from the calendar grid below,
        // not by inventing entries here.
        val expenseByBucket = HashMap<LocalDate, Long>()
        val incomeByBucket = HashMap<LocalDate, Long>()

        for (day in days) {
            if (day.date.isBefore(startDate) || day.date.isAfter(endDate)) continue
            val key = dateProvider.bucketStartOf(day.date, granularity)
            expenseByBucket.merge(key, day.expenseMinor, Long::plus)
            incomeByBucket.merge(key, day.incomeMinor, Long::plus)
        }

        val points = ArrayList<TrendPoint>()
        var cursor = dateProvider.bucketStartOf(startDate, granularity)
        while (!cursor.isAfter(endDate) && points.size < MAX_BUCKETS) {
            points += TrendPoint(
                bucketStartMillis = dateProvider.startOfDay(cursor),
                label = labelFor(cursor, granularity),
                expense = Money(expenseByBucket[cursor] ?: 0L, currency),
                income = Money(incomeByBucket[cursor] ?: 0L, currency),
            )
            cursor = advance(cursor, granularity)
        }

        return TrendSeries(granularity = granularity, points = points)
    }

    /** Fills every calendar day in the range, including days with no activity. */
    fun fillDaily(
        range: LongRange,
        days: List<DayActivity>,
    ): Map<LocalDate, DayActivity> {
        if (range.isEmpty()) return emptyMap()
        val byDate = days.associateBy { it.date }
        val result = LinkedHashMap<LocalDate, DayActivity>()
        var cursor = dateProvider.toLocalDate(range.first)
        val endDate = dateProvider.toLocalDate(range.last)
        while (!cursor.isAfter(endDate) && result.size < MAX_DAILY_ROWS) {
            result[cursor] = byDate[cursor]
                ?: DayActivity(cursor, expenseMinor = 0L, incomeMinor = 0L, count = 0)
            cursor = cursor.plusDays(1)
        }
        return result
    }

    private fun advance(date: LocalDate, granularity: TimeGranularity): LocalDate =
        when (granularity) {
            TimeGranularity.DAY -> date.plusDays(1)
            TimeGranularity.WEEK -> date.plusWeeks(1)
            TimeGranularity.MONTH -> date.plusMonths(1)
            TimeGranularity.YEAR -> date.plusYears(1)
        }

    private fun labelFor(date: LocalDate, granularity: TimeGranularity): String =
        when (granularity) {
            // A day inside a weekly report is read as a weekday ("周一"), not as a date — the report
            // already tells the user which week it is.
            TimeGranularity.DAY -> WeekdayLabels.short(date.dayOfWeek)
            // Week label is the start date, e.g. "11/3" — a week number ("W45") means little to users
            // and resets across years, which is exactly the ambiguity we removed from the SQL key.
            TimeGranularity.WEEK -> WEEK_FORMAT.format(date)
            TimeGranularity.MONTH -> MONTH_FORMAT.format(date)
            TimeGranularity.YEAR -> YEAR_FORMAT.format(date)
        }

    companion object {
        /**
         * Upper bound on rendered buckets. A phone chart cannot show more than a couple dozen
         * readable labels; this guard turns an accidental multi-decade range into a bounded render
         * instead of a frozen frame.
         */
        const val MAX_BUCKETS = 400

        /** Roughly 3 years of daily rows; the heat map never needs more. */
        const val MAX_DAILY_ROWS = 1200

        private val WEEK_FORMAT = DateTimeFormatter.ofPattern("M/d", Locale.getDefault())
        private val MONTH_FORMAT = DateTimeFormatter.ofPattern("M月", Locale.getDefault())
        private val YEAR_FORMAT = DateTimeFormatter.ofPattern("yyyy", Locale.getDefault())
    }
}
