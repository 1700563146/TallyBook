package com.tallybook.core.common.time

import com.tallybook.core.common.clock.DateProvider
import com.tallybook.core.model.stats.TimeGranularity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Zone-aware calendar math.
 *
 * minSdk 26 means java.time is available without desugaring, which is why the project does not need
 * `desugar_jdk_libs`. If minSdk ever drops below 26, enable core library desugaring rather than
 * rewriting this.
 *
 * Every boundary here is computed in the user's zone. An expense at 23:30 local time must land in
 * the local day/month, not the UTC one — getting this wrong shifts a chunk of every report by a day
 * for half the planet.
 */
class JavaTimeDateProvider(
    private val zoneId: ZoneId,
    /** ISO week starts Monday. Stored explicitly because budgets and charts must agree on it. */
    private val weekStartsOn: DayOfWeek = DayOfWeek.MONDAY,
) : DateProvider {

    override fun startOfMonth(epochMillis: Long): Long {
        val date = Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate()
        return date.withDayOfMonth(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    override fun endOfMonthExclusive(epochMillis: Long): Long {
        val date = Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate()
        return date.withDayOfMonth(1).plusMonths(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    override fun startOfDay(epochMillis: Long): Long = atStartOfDay(toLocalDate(epochMillis))

    override fun endOfDayExclusive(epochMillis: Long): Long =
        atStartOfDay(toLocalDate(epochMillis).plusDays(1))

    override fun startOfWeek(epochMillis: Long): Long {
        val date = toLocalDate(epochMillis)
        val adjusted = date.with(TemporalAdjusters.previousOrSame(weekStartsOn))
        return atStartOfDay(adjusted)
    }

    override fun endOfWeekExclusive(epochMillis: Long): Long {
        val start = Instant.ofEpochMilli(startOfWeek(epochMillis)).atZone(zoneId).toLocalDate()
        return atStartOfDay(start.plusWeeks(1))
    }

    override fun startOfYear(epochMillis: Long): Long {
        val date = toLocalDate(epochMillis)
        return atStartOfDay(date.withDayOfYear(1))
    }

    override fun endOfYearExclusive(epochMillis: Long): Long {
        val date = toLocalDate(epochMillis)
        return atStartOfDay(date.withDayOfYear(1).plusYears(1))
    }

    override fun rangeFor(granularity: TimeGranularity, anchorMillis: Long): LongRange {
        val start = when (granularity) {
            TimeGranularity.DAY -> startOfDay(anchorMillis)
            TimeGranularity.WEEK -> startOfWeek(anchorMillis)
            TimeGranularity.MONTH -> startOfMonth(anchorMillis)
            TimeGranularity.YEAR -> startOfYear(anchorMillis)
        }
        val endExclusive = when (granularity) {
            TimeGranularity.DAY -> endOfDayExclusive(anchorMillis)
            TimeGranularity.WEEK -> endOfWeekExclusive(anchorMillis)
            TimeGranularity.MONTH -> endOfMonthExclusive(anchorMillis)
            TimeGranularity.YEAR -> endOfYearExclusive(anchorMillis)
        }
        return start until endExclusive
    }

    /**
     * The one authoritative bucket-start rule.
     *
     * Charts call this per day to group daily aggregates; budgets call [rangeFor] which uses the same
     * `weekStartsOn`. Keeping both on this class is what prevents "chart says this week, budget says
     * last week".
     */
    override fun bucketStartOf(date: LocalDate, granularity: TimeGranularity): LocalDate =
        when (granularity) {
            TimeGranularity.DAY -> date
            TimeGranularity.WEEK -> date.with(TemporalAdjusters.previousOrSame(weekStartsOn))
            TimeGranularity.MONTH -> date.withDayOfMonth(1)
            TimeGranularity.YEAR -> date.withDayOfYear(1)
        }

    override fun startOfDay(date: LocalDate): Long = atStartOfDay(date)

    override fun toLocalDate(epochMillis: Long): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate()

    fun atStartOfDay(date: LocalDate): Long = date.atStartOfDay(zoneId).toInstant().toEpochMilli()

    /** Number of distinct calendar days between two instants, inclusive of both ends. */
    override fun daysBetweenInclusive(fromMillis: Long, toExclusiveMillis: Long): Int {
        val start = toLocalDate(fromMillis)
        val end = toLocalDate(toExclusiveMillis - 1)
        return (end.toEpochDay() - start.toEpochDay() + 1).toInt().coerceAtLeast(0)
    }

    /**
     * Days elapsed within `[startMillis, endExclusiveMillis)`, clamped to `0..total`.
     *
     * The first day of a period counts as **1 day elapsed**, not 0: on the 1st of the month the user
     * has already had a full day to spend, and returning 0 there divides by zero in every pace ratio
     * the budget screen computes. Before the period starts it is 0; at or after the end it is total.
     */
    override fun daysElapsed(startMillis: Long, endExclusiveMillis: Long, nowMillis: Long): Int {
        val total = daysBetweenInclusive(startMillis, endExclusiveMillis)
        if (nowMillis < startMillis) return 0
        if (nowMillis >= endExclusiveMillis) return total
        val startDate = toLocalDate(startMillis)
        val nowDate = toLocalDate(nowMillis)
        return (nowDate.toEpochDay() - startDate.toEpochDay() + 1).toInt().coerceIn(1, total)
    }
}
