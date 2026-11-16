package com.tallybook.core.data.repository

import com.tallybook.core.model.money.Money
import com.tallybook.core.model.stats.BudgetProgress
import com.tallybook.core.model.stats.CategorySlice
import com.tallybook.core.model.stats.SeriesMode
import com.tallybook.core.model.stats.TimeGranularity
import com.tallybook.core.model.stats.TrendSeries
import com.tallybook.core.model.transaction.Account
import com.tallybook.core.model.transaction.Budget
import com.tallybook.core.model.transaction.Category
import com.tallybook.core.model.transaction.Transaction
import com.tallybook.core.model.transaction.TransactionType
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow

/**
 * Repository contracts live here but model only domain types.
 *
 * Why interface + impl even with a single implementation: feature modules depend on the contract
 * only, so a future import/sync implementation can be swapped in without touching UI, and test fakes
 * need no mocking framework.
 */

/** Filters for a ledger page. Null means "no filter on this dimension". */
data class TransactionQuery(
    val fromInclusive: Long,
    val toExclusive: Long,
    val accountId: String? = null,
    val categoryId: String? = null,
    val type: TransactionType? = null,
    val text: String? = null,
)

/** User-visible ledger direction; transfers are a kind, not a [TransactionType]. */
enum class SearchEntryType { ALL, EXPENSE, INCOME, TRANSFER }

/** Cross-month search and filter state. Null ids mean "all". */
data class TransactionSearchFilter(
    val query: String = "",
    val entryType: SearchEntryType = SearchEntryType.ALL,
    val accountId: String? = null,
    val categoryId: String? = null,
)

/**
 * Headline totals for a period.
 *
 * There is no `convertedTotal` / `unconvertedCurrencies` any more: the ledger is single-currency,
 * so a total is simply a total. That removes an entire class of "the number looks right but three
 * rows were silently dropped" bugs.
 */
data class PeriodTotals(
    val expense: Money,
    val income: Money,
    val count: Int,
) {
    val net: Money get() = income.minus(expense)
}

/**
 * A ledger row ready for display: the transaction plus the names it references.
 *
 * Names are resolved in the repository rather than by joining in SQL so that archived categories and
 * accounts still render with their real name (a LEFT JOIN against active-only rows would show a blank
 * or "已删除"), and so the UI never sees raw ids.
 */
data class TransactionListItem(
    val transaction: Transaction,
    val categoryName: String,
    val accountName: String,
    /** Icon key from the category, resolved to a drawable by the UI layer. */
    val categoryIconKey: String? = null,
    val categoryColorArgb: Long? = null,
    /** Destination account name for transfers; null for normal rows. */
    val counterAccountName: String? = null,
)

interface TransactionRepository {

    fun observeInRange(fromInclusive: Long, toExclusive: Long): Flow<List<Transaction>>

    /** Rows with category/account names resolved, newest first. */
    fun observeListItems(fromInclusive: Long, toExclusive: Long): Flow<List<TransactionListItem>>

    /** Cross-month paged search. The repository escapes LIKE wildcards before querying. */
    fun observeSearchResults(filter: TransactionSearchFilter): Flow<PagingData<TransactionListItem>>

    /** The recycle bin: soft-deleted rows with names resolved, most recently deleted first. */
    fun observeDeletedItems(): Flow<List<TransactionListItem>>

    /**
     * Rows of one category (or the uncategorized bucket when [categoryId] is null) in a range.
     * Backs the drill-down from the statistics breakdown.
     */
    fun observeCategoryItems(
        categoryId: String?,
        fromInclusive: Long,
        toExclusive: Long,
    ): Flow<List<TransactionListItem>>

    /** Permanently removes one soft-deleted row. */
    suspend fun purge(id: String)

    /** Permanently removes every soft-deleted row; returns how many were removed. */
    suspend fun purgeAll(): Int

    /** Total rows including soft-deleted ones, used to detect a pristine database. */
    suspend fun countAll(): Int

    /** Every row including soft-deleted ones, for backup export. */
    suspend fun getAllForBackup(): List<Transaction>

    suspend fun getPage(query: TransactionQuery, limit: Int, offset: Int): List<Transaction>

    suspend fun getById(id: String): Transaction?

    suspend fun upsert(transaction: Transaction)

    suspend fun delete(id: String)

    suspend fun restore(id: String)

    /** Atomically soft-deletes [ids], so a bulk action cannot leave a partially updated selection. */
    suspend fun deleteMany(ids: Set<String>)

    /** Atomically restores [ids], used by the bulk-delete undo action. */
    suspend fun restoreMany(ids: Set<String>)

    fun observePeriodTotals(fromInclusive: Long, toExclusive: Long): Flow<PeriodTotals>
}

/**
 * All read models the charts need, in one place.
 *
 * Kept separate from [TransactionRepository] on purpose: the statistics screen has different cache
 * and recomputation characteristics from the ledger list, and mixing them would make every chart
 * tweak risk the write path.
 */
interface StatsRepository {

    /**
     * Trend series for the given range and bucket size.
     *
     * Guarantees: buckets with no activity are still present with zero values, so the chart shows a
     * flat zero instead of a gap (a gap is ambiguous — "no data" vs "no spending").
     */
    fun observeTrend(
        range: LongRange,
        granularity: TimeGranularity,
    ): Flow<TrendSeries>

    /** Category breakdown for one direction and range, sorted by amount descending. */
    fun observeCategoryBreakdown(
        range: LongRange,
        type: TransactionType,
    ): Flow<List<CategorySlice>>

    /** Daily heat-map totals for the range (used by the calendar view). */
    fun observeDailyTotals(range: LongRange): Flow<Map<String, PeriodTotals>>
}

interface AccountRepository {
    fun observeActive(): Flow<List<Account>>
    fun observeAll(): Flow<List<Account>>
    fun observeBalances(asOfExclusive: Long): Flow<Map<String, Money>>
    suspend fun upsert(account: Account)
    suspend fun archive(id: String)
}

interface CategoryRepository {
    fun observeActive(): Flow<List<Category>>

    /** Every category including archived ones; the management screen shows both. */
    fun observeAll(): Flow<List<Category>>

    /**
     * Category id -> number of times it was used since [sinceInclusive].
     *
     * The picker orders by this so the categories a user actually uses float to the top. Not a
     * "smart" ranking: it is a plain recent-frequency count, which is predictable and needs no model.
     */
    fun observeUsage(sinceInclusive: Long): Flow<Map<String, Int>>
    suspend fun upsert(category: Category)
    suspend fun archive(id: String)
}

interface BudgetRepository {
    fun observeActive(): Flow<List<Budget>>
    suspend fun upsert(budget: Budget)
    suspend fun deactivate(id: String)
    suspend fun delete(id: String)

    /**
     * Live progress for every active budget, evaluated at [nowMillis].
     *
     * Returns one [BudgetProgress] per budget. Implementations must count ONLY expenses, exclude
     * transfers, and restrict to the budget's own period — a weekly budget must not be judged
     * against a month of spending.
     */
    fun observeProgress(nowMillis: Long): Flow<List<BudgetProgress>>
}

/** Convenience: which series the chart is asked to display. */
data class TrendRequest(
    val range: LongRange,
    val granularity: TimeGranularity,
    val mode: SeriesMode = SeriesMode.BOTH,
)
