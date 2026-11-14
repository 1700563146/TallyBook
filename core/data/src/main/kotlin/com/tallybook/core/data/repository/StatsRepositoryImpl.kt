package com.tallybook.core.data.repository

import com.tallybook.core.common.clock.DateProvider
import com.tallybook.core.data.stats.DayActivity
import com.tallybook.core.data.stats.TrendBucketer
import com.tallybook.core.database.dao.CategoryDao
import com.tallybook.core.database.dao.TransactionDao
import com.tallybook.core.model.money.CurrencyCode
import com.tallybook.core.model.money.Money
import com.tallybook.core.model.stats.CategorySlice
import com.tallybook.core.model.stats.TimeGranularity
import com.tallybook.core.model.stats.TrendSeries
import com.tallybook.core.model.transaction.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds chart-ready series from daily SQL aggregates.
 *
 * SQL is responsible for scanning and summing; this class is responsible for turning "a flat list of
 * days" into "a continuous bucket series" — grouping, zero-filling, and labeling. The split matters:
 * the previous version also let SQL decide the week boundary, and its `%W` keys disagreed with the
 * JVM calendar on half of all days, blanking the weekly chart with no error.
 */
@Singleton
class StatsRepositoryImpl @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val dateProvider: DateProvider,
    private val bucketer: TrendBucketer,
) : StatsRepository {

    override fun observeTrend(
        range: LongRange,
        granularity: TimeGranularity,
    ): Flow<TrendSeries> =
        transactionDao.observeDailyTotals(range.first, range.last + 1)
            .map { rows -> bucketer.bucket(range, granularity, rows.toDayActivity(), LEDGER_CURRENCY) }

    override fun observeCategoryBreakdown(
        range: LongRange,
        type: TransactionType,
    ): Flow<List<CategorySlice>> =
        combine(
            transactionDao.observeTotalsByCategory(range.first, range.last + 1, type.name),
            categoryDao.observeAll(),
        ) { totals, categories ->
            val names = categories.associate { it.id to it.name }
            val grandTotal = totals.sumOf { it.totalMinor }

            totals.map { row ->
                CategorySlice(
                    categoryId = row.categoryId,
                    // An archived or deleted category still owns historical rows. "未分类" would be a
                    // lie (that means categoryId == null), so it is called out separately.
                    categoryName = when (row.categoryId) {
                        null -> UNCATEGORIZED_LABEL
                        else -> names[row.categoryId] ?: DELETED_CATEGORY_LABEL
                    },
                    amount = Money(row.totalMinor, LEDGER_CURRENCY),
                    transactionCount = row.count,
                    share = if (grandTotal <= 0L) 0f else (row.totalMinor.toFloat() / grandTotal),
                )
            }
        }

    /**
     * Day-by-day totals for the calendar heat map.
     *
     * Zero days are included: a heat map with holes reads as "unknown", while a zero day reads as
     * "didn't spend", and the latter is the truth.
     */
    override fun observeDailyTotals(range: LongRange): Flow<Map<String, PeriodTotals>> =
        transactionDao.observeDailyTotals(range.first, range.last + 1).map { rows ->
            bucketer.fillDaily(range, rows.toDayActivity())
                .mapKeys { (date, _) -> date.toString() }
                .mapValues { (_, day) ->
                    PeriodTotals(
                        expense = Money(day.expenseMinor, LEDGER_CURRENCY),
                        income = Money(day.incomeMinor, LEDGER_CURRENCY),
                        count = day.count,
                    )
                }
        }

    /**
     * Parses the DAO's ISO `yyyy-MM-dd` day keys.
     *
     * A malformed key cannot happen (it is produced by `strftime`), but `parse` throwing inside a
     * Flow collector would crash the app, so a bad row is skipped rather than propagated.
     */
    private fun List<com.tallybook.core.database.model.DailyTotalRow>.toDayActivity(): List<DayActivity> =
        mapNotNull { row ->
            val date = runCatching { LocalDate.parse(row.day) }.getOrNull() ?: return@mapNotNull null
            DayActivity(
                date = date,
                expenseMinor = row.expenseMinor,
                incomeMinor = row.incomeMinor,
                count = row.count,
            )
        }

    companion object {
        /** One place to change if the app ever gains a "switch ledger currency" setting. */
        val LEDGER_CURRENCY = CurrencyCode.CNY

        const val UNCATEGORIZED_LABEL = "未分类"
        const val DELETED_CATEGORY_LABEL = "已删除分类"
    }
}
