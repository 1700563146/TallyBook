package com.tallybook.core.model.stats

import com.tallybook.core.model.money.Money
import com.tallybook.core.model.transaction.BudgetPeriod
import kotlinx.serialization.Serializable

/**
 * Bucketing granularity for the trend chart.
 *
 * This is a *presentation* concern that leaks into the data layer on purpose: the SQL for weekly
 * and monthly buckets differs, and doing the grouping in Kotlin would require loading every row of
 * the range into memory. Keeping the enum here (pure JVM) lets the DAO accept it and the UI display
 * it without duplicating the concept in three modules.
 */
@Serializable
enum class TimeGranularity { DAY, WEEK, MONTH, YEAR }

/**
 * The report period chosen by the user, and the bucket size that goes with it.
 *
 * The pairing is the point: a weekly report shown per week would be a single bar, and a yearly report
 * shown per day would be 365 bars nobody can read. Each period picks the bucket that keeps the chart
 * at 4-12 points:
 *
 * - 本周 → 按日（7 点）
 * - 本月 → 按周（4~6 点）
 * - 本年 → 按月（12 点）
 */
@Serializable
enum class ReportPeriod(val rangeUnit: TimeGranularity, val bucket: TimeGranularity) {
    WEEK(TimeGranularity.WEEK, TimeGranularity.DAY),
    MONTH(TimeGranularity.MONTH, TimeGranularity.WEEK),
    YEAR(TimeGranularity.YEAR, TimeGranularity.MONTH),
}

/** What the trend chart is showing. Both can be on at once (two series). */
@Serializable
enum class SeriesMode { EXPENSE, INCOME, BOTH }

/**
 * One point on the trend chart.
 *
 * [bucketStartMillis] is the *inclusive* start of the bucket in the user's zone (Monday 00:00 for a
 * week, 1st 00:00 for a month). Storing the boundary rather than a formatted label keeps sorting and
 * axis maths exact; the label is derived for display only.
 *
 * [label] is precomputed by the data layer because the correct label depends on granularity AND
 * locale ("11月" vs "Nov"), and duplicating that logic in every chart composable invites drift.
 */
@Serializable
data class TrendPoint(
    val bucketStartMillis: Long,
    val label: String,
    val expense: Money,
    val income: Money,
) {
    val net: Money get() = income.minus(expense)

    /**
     * Value plotted per [SeriesMode]. Returns 0 for a hidden series so the chart still renders
     * a continuous axis instead of dropping points.
     */
    fun valueFor(mode: SeriesMode): Money = when (mode) {
        SeriesMode.EXPENSE -> expense
        SeriesMode.INCOME -> income
        SeriesMode.BOTH -> expense
    }
}

/**
 * A full trend series for one range and granularity.
 *
 * Empty buckets are filled with zero-value points: a gap in a line chart is ambiguous (no data vs
 * no spending), and users read "flat at zero" correctly as "didn't spend anything".
 */
@Serializable
data class TrendSeries(
    val granularity: TimeGranularity,
    val points: List<TrendPoint>,
) {
    val isEmpty: Boolean get() = points.isEmpty()

    fun maxOf(mode: SeriesMode): Money = when (mode) {
        SeriesMode.BOTH -> points.maxOfOrNull { maxOf(it.expense, it.income) } ?: Money.ZERO_CNY
        else -> points.maxOfOrNull { it.valueFor(mode) } ?: Money.ZERO_CNY
    }

    fun totalOf(mode: SeriesMode): Money = when (mode) {
        SeriesMode.EXPENSE -> points.fold(Money.ZERO_CNY) { acc, p -> acc.plus(p.expense) }
        SeriesMode.INCOME -> points.fold(Money.ZERO_CNY) { acc, p -> acc.plus(p.income) }
        SeriesMode.BOTH -> points.fold(Money.ZERO_CNY) { acc, p -> acc.plus(p.expense) }
    }
}

/** One slice of the category breakdown (donut / bar ranking). */
@Serializable
data class CategorySlice(
    val categoryId: String?,
    val categoryName: String,
    val amount: Money,
    val transactionCount: Int,
    /** 0..1 share of the total for the selected range and type. */
    val share: Float,
)

/**
 * Budget progress for the current period.
 *
 * [spent] counts only what falls inside [periodStartMillis]..[periodEndExclusive], and only
 * expenses (income never consumes a budget). Transfers are excluded upstream.
 *
 * [projectedOverrun] answers the question users actually ask — "at this pace, will I blow it?" —
 * by extrapolating spend to the end of the period. It is null in the first few days of a period,
 * where extrapolation is meaningless noise.
 */
@Serializable
data class BudgetProgress(
    val budgetId: String,
    val name: String,
    val period: BudgetPeriod,
    val limit: Money,
    val spent: Money,
    val periodStartMillis: Long,
    val periodEndExclusiveMillis: Long,
    val daysElapsed: Int,
    val daysTotal: Int,
    val projectedOverrun: Money? = null,
) {
    /** True overrun (can exceed 1), used to show "超出 ¥32.50". */
    val overrun: Money get() = spent.minus(limit)

    val isOverBudget: Boolean get() = spent.isOver(limit)

    /** Clamped 0..1 for progress indicators. */
    val progress: Float get() = spent.progressAgainst(limit)

    /** 0..1 of the period that has elapsed, for comparing spend pace against time pace. */
    val timeProgress: Float
        get() = if (daysTotal <= 0) 0f else (daysElapsed.toFloat() / daysTotal).coerceIn(0f, 1f)

    val remaining: Money get() = limit.minus(spent)
}
