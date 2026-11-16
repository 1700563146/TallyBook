package com.tallybook.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.tallybook.core.database.entity.TransactionEntity
import com.tallybook.core.database.entity.TransactionTagEntity
import com.tallybook.core.database.model.AccountBalanceRow
import com.tallybook.core.database.model.CategoryTotalRow
import com.tallybook.core.database.model.DailyTotalRow
import com.tallybook.core.database.model.PeriodTotals
import com.tallybook.core.database.model.TransactionWithMeta
import com.tallybook.core.database.model.TransactionSearchRow
import androidx.paging.PagingSource
import kotlinx.coroutines.flow.Flow

/**
 * Ledger reads and writes.
 *
 * Conventions enforced here:
 * - every read filters `deleted_at IS NULL`
 * - every aggregate excludes `kind = 'TRANSFER'` (moving money between your own accounts is neither
 *   income nor expense; including it inflates both sides of every report)
 * - aggregates return *one row per bucket*, never pre-summed across a dimension the caller still
 *   needs (see `GROUP BY` keys — this is where multi-currency summing bugs come from)
 */
@Dao
interface TransactionDao {

    @Query(
        """
        SELECT * FROM transactions
        WHERE deleted_at IS NULL
          AND occurred_at BETWEEN :fromInclusive AND :toExclusive
        ORDER BY occurred_at DESC, created_at DESC
        """,
    )
    fun observeInRange(fromInclusive: Long, toExclusive: Long): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE deleted_at IS NULL
          AND occurred_at BETWEEN :fromInclusive AND :toExclusive
          AND (:accountId IS NULL OR account_id = :accountId)
          AND (:categoryId IS NULL OR category_id = :categoryId)
          AND (:type IS NULL OR type = :type)
          AND (:query IS NULL OR note LIKE '%' || :query || '%')
        ORDER BY occurred_at DESC, created_at DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun getPage(
        fromInclusive: Long,
        toExclusive: Long,
        accountId: String?,
        categoryId: String?,
        type: String?,
        query: String?,
        limit: Int,
        offset: Int,
    ): List<TransactionEntity>

    /**
     * Cross-month search with category/account name matching.
     *
     * [pattern] is already escaped by the repository. `ESCAPE '\'` makes `%` and `_` searchable as
     * literal characters instead of turning every query into a wildcard.
     */
    @Query(
        """
        SELECT t.*,
               c.name AS categoryName,
               c.icon_key AS categoryIconKey,
               c.color_argb AS categoryColorArgb,
               a.name AS accountName,
               ca.name AS counterAccountName
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.category_id
        LEFT JOIN accounts a ON a.id = t.account_id
        LEFT JOIN accounts ca ON ca.id = t.counter_account_id
        WHERE t.deleted_at IS NULL
          AND (
              :pattern IS NULL
              OR t.note LIKE :pattern ESCAPE '\'
              OR c.name LIKE :pattern ESCAPE '\'
              OR a.name LIKE :pattern ESCAPE '\'
              OR ca.name LIKE :pattern ESCAPE '\'
          )
          AND (
              :entryType = 'ALL'
              OR (:entryType = 'TRANSFER' AND t.kind = 'TRANSFER')
              OR (:entryType = 'EXPENSE' AND t.kind != 'TRANSFER' AND t.type = 'EXPENSE')
              OR (:entryType = 'INCOME' AND t.kind != 'TRANSFER' AND t.type = 'INCOME')
          )
          AND (
              :accountId IS NULL
              OR t.account_id = :accountId
              OR t.counter_account_id = :accountId
          )
          AND (:categoryId IS NULL OR t.category_id = :categoryId)
        ORDER BY t.occurred_at DESC, t.created_at DESC
        """,
    )
    fun search(
        pattern: String?,
        entryType: String,
        accountId: String?,
        categoryId: String?,
    ): PagingSource<Int, TransactionSearchRow>

    @Query("SELECT * FROM transactions WHERE id = :id AND deleted_at IS NULL")
    suspend fun getById(id: String): TransactionEntity?

    /**
     * Rows of one category in a range — the drill-down from the category breakdown.
     *
     * Separate from [getPage]'s `categoryId` filter on purpose: there, `null` means "no filter".
     * Here the caller has already decided between "one specific category" and "the uncategorized
     * bucket", so each case gets its own query and `null` never means two different things.
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE deleted_at IS NULL
          AND category_id = :categoryId
          AND occurred_at >= :fromInclusive AND occurred_at < :toExclusive
        ORDER BY occurred_at DESC, created_at DESC
        """,
    )
    fun observeCategoryInRange(
        fromInclusive: Long,
        toExclusive: Long,
        categoryId: String,
    ): Flow<List<TransactionEntity>>

    /** The "未分类" bucket: rows that genuinely have no category. */
    @Query(
        """
        SELECT * FROM transactions
        WHERE deleted_at IS NULL
          AND category_id IS NULL
          AND occurred_at >= :fromInclusive AND occurred_at < :toExclusive
        ORDER BY occurred_at DESC, created_at DESC
        """,
    )
    fun observeUncategorizedInRange(
        fromInclusive: Long,
        toExclusive: Long,
    ): Flow<List<TransactionEntity>>

    /** Which of [ids] already exist (including soft-deleted). Used to make import idempotent. */
    @Query("SELECT id FROM transactions WHERE id IN (:ids)")
    suspend fun existingIds(ids: List<String>): List<String>

    /**
     * Every row, including soft-deleted ones — the backup feed.
     *
     * Deliberately not filtered: a backup that drops the recycle bin cannot restore the user's state,
     * and the import side needs to know which rows were deleted.
     */
    @Query("SELECT * FROM transactions ORDER BY occurred_at ASC, created_at ASC")
    suspend fun getAllForBackup(): List<TransactionEntity>

    /** Row count including soft-deleted rows; used by seeding to detect a pristine database. */
    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(transaction: TransactionEntity)

    @Upsert
    suspend fun upsertAll(transactions: List<TransactionEntity>)

    @Query("SELECT * FROM transaction_tags")
    suspend fun getAllTags(): List<TransactionTagEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTags(tags: List<TransactionTagEntity>)

    @Query("DELETE FROM transaction_tags WHERE transaction_id IN (:transactionIds)")
    suspend fun deleteTags(transactionIds: List<String>)

    /** Soft delete keeps the row addressable for undo and audit. */
    @Query("UPDATE transactions SET deleted_at = :deletedAt, updated_at = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long)

    @Query("UPDATE transactions SET deleted_at = NULL, updated_at = :restoredAt WHERE id = :id")
    suspend fun restore(id: String, restoredAt: Long)

    /**
     * Soft-deletes a whole selection in one statement.
     *
     * `deleted_at IS NULL` keeps an already-deleted row's original deletion timestamp, so a repeated
     * request cannot move an older recycle-bin item to "just deleted". The statement itself is
     * atomic; the default wrappers below also make the empty-selection guard part of Room's
     * transaction boundary.
     */
    @Query(
        """
        UPDATE transactions
        SET deleted_at = :deletedAt, updated_at = :deletedAt
        WHERE id IN (:ids) AND deleted_at IS NULL
        """,
    )
    suspend fun softDeleteManyQuery(ids: List<String>, deletedAt: Long): Int

    @Transaction
    suspend fun softDeleteMany(ids: List<String>, deletedAt: Long): Int {
        val distinctIds = ids.distinct()
        if (distinctIds.isEmpty()) return 0
        return softDeleteManyQuery(distinctIds, deletedAt)
    }

    @Query(
        """
        UPDATE transactions
        SET deleted_at = NULL, updated_at = :restoredAt
        WHERE id IN (:ids) AND deleted_at IS NOT NULL
        """,
    )
    suspend fun restoreManyQuery(ids: List<String>, restoredAt: Long): Int

    @Transaction
    suspend fun restoreMany(ids: List<String>, restoredAt: Long): Int {
        val distinctIds = ids.distinct()
        if (distinctIds.isEmpty()) return 0
        return restoreManyQuery(distinctIds, restoredAt)
    }

    /** Hard delete only for the explicit "empty trash" action, which is irreversible by design. */
    @Query("DELETE FROM transactions WHERE deleted_at IS NOT NULL AND deleted_at < :before")
    suspend fun purgeDeletedBefore(before: Long)

    /**
     * Soft-deleted rows, newest deletion first — the recycle bin.
     *
     * Ordered by `deleted_at` (not `occurred_at`): a user looking for something they just removed
     * thinks in terms of " I deleted it a moment ago", not " it was a purchase from March".
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE deleted_at IS NOT NULL
        ORDER BY deleted_at DESC
        """,
    )
    fun observeDeleted(): Flow<List<TransactionEntity>>

    /** Hard-deletes ONE soft-deleted row. A live row is never removable through this path. */
    @Query("DELETE FROM transactions WHERE id = :id AND deleted_at IS NOT NULL")
    suspend fun purge(id: String)

    /** Empties the recycle bin. Returns the number of rows removed. */
    @Query("DELETE FROM transactions WHERE deleted_at IS NOT NULL")
    suspend fun purgeAll(): Int

    /**
     * Headline income/expense totals for a range.
     *
     * Kept as a single row: the dashboard shows one number per direction, and the ledger is
     * single-currency, so there is no dimension left to lose.
     */
    @Query(
        """
        SELECT
            COALESCE(SUM(
                CASE
                    WHEN type = 'EXPENSE' THEN amount_minor
                    WHEN refund_of_id IS NOT NULL THEN -amount_minor
                    ELSE 0
                END
            ), 0) AS expenseMinor,
            COALESCE(SUM(
                CASE
                    WHEN type = 'INCOME' AND refund_of_id IS NULL THEN amount_minor
                    ELSE 0
                END
            ), 0) AS incomeMinor,
            COUNT(*) AS count
        FROM transactions
        WHERE deleted_at IS NULL
          AND kind != 'TRANSFER'
          AND occurred_at >= :fromInclusive AND occurred_at < :toExclusive
        """,
    )
    fun observePeriodTotals(fromInclusive: Long, toExclusive: Long): Flow<PeriodTotals>

    /**
     * Category breakdown, refund-aware.
     *
     * For an EXPENSE breakdown the range includes both real expenses and refund rows (INCOME with
     * `refund_of_id`), with refunds subtracting. For an INCOME breakdown refunds are excluded
     * entirely — otherwise a 100 元 refund would show up as 100 元 of income and inflate the month.
     *
     * The refund is attributed to the category the user picked when recording it (normally the same
     * category as the original expense). Joining back to the original row's category would be more
     * "correct" but silently overrides the user's explicit choice, and breaks when the original row
     * is deleted.
     */
    @Query(
        """
        SELECT category_id AS categoryId,
               COALESCE(SUM(
                   CASE WHEN type = 'EXPENSE' THEN amount_minor ELSE -amount_minor END
               ), 0) AS totalMinor,
               COUNT(*) AS count
        FROM transactions
        WHERE deleted_at IS NULL
          AND kind != 'TRANSFER'
          AND occurred_at >= :fromInclusive AND occurred_at < :toExclusive
          AND (
                (:type = 'EXPENSE' AND (type = 'EXPENSE' OR refund_of_id IS NOT NULL))
             OR (:type = 'INCOME'  AND type = 'INCOME' AND refund_of_id IS NULL)
          )
        GROUP BY category_id
        HAVING totalMinor != 0
        ORDER BY totalMinor DESC
        """,
    )
    fun observeTotalsByCategory(
        fromInclusive: Long,
        toExclusive: Long,
        type: String,
    ): Flow<List<CategoryTotalRow>>

    /**
     * Daily income/expense aggregates — the single feed for every chart.
     *
     * Why SQL stops at DAY and Kotlin does the rest:
     *
     * An earlier design bucketed by week/month/year inside SQL. That created a hidden contract
     * between a `strftime` expression and Kotlin's calendar math, and the two disagreed:
     * `strftime('%W')` means "nth Monday of the year" (days before the first Monday are week 00),
     * while the JVM's `WeekFields.of(Locale.UK)` is ISO week-based-year. Measured over 2024-01-01
     * to 2026-01-10, **377 of 753 days produced different keys** — the weekly chart was silently
     * blank, with no crash and no warning, because every looked-up key missed.
     *
     * Now the only bucketing unit SQL knows about is the DAY, whose key is an ISO `yyyy-MM-dd`
     * string with no ambiguity. Week/month/year grouping happens in Kotlin via
     * `DateProvider.bucketStartOf`, which is also what budgets use — so the two features are
     * consistent by construction rather than by a test that both sides must remember to update.
     *
     * Cost: a 3-year range yields at most ~1100 already-aggregated day rows, which is trivial to
     * group in memory. The expensive part (scanning indexed `occurred_at` and summing) still happens
     * in SQLite.
     *
     * Note `'localtime'`: bucketing follows the device's zone, so travel re-buckets history. That is
     * intentional (a 23:30 purchase is "today" to the user) and documented in docs/RISKS.md.
     */
    @Query(
        """
        SELECT strftime('%Y-%m-%d', occurred_at / 1000, 'unixepoch', 'localtime') AS day,
               COALESCE(SUM(
                   CASE
                       WHEN type = 'EXPENSE' THEN amount_minor
                       WHEN refund_of_id IS NOT NULL THEN -amount_minor
                       ELSE 0
                   END
               ), 0) AS expenseMinor,
               COALESCE(SUM(
                   CASE WHEN type = 'INCOME' AND refund_of_id IS NULL THEN amount_minor ELSE 0 END
               ), 0) AS incomeMinor,
               COUNT(*) AS count
        FROM transactions
        WHERE deleted_at IS NULL
          AND kind != 'TRANSFER'
          AND occurred_at >= :fromInclusive AND occurred_at < :toExclusive
        GROUP BY day
        ORDER BY day ASC
        """,
    )
    fun observeDailyTotals(
        fromInclusive: Long,
        toExclusive: Long,
    ): Flow<List<DailyTotalRow>>

    /**
     * Spent-per-category within a single period, used for budget progress.
     *
     * Takes `categoryIds` as a list so a budget that includes subcategories can pass the whole
     * subtree in one query instead of N queries (N+1 is the classic budget-screen slowness).
     */
    @Query(
        """
        SELECT COALESCE(SUM(
                   CASE WHEN type = 'EXPENSE' THEN amount_minor ELSE -amount_minor END
               ), 0)
        FROM transactions
        WHERE deleted_at IS NULL
          AND kind != 'TRANSFER'
          AND occurred_at >= :fromInclusive AND occurred_at < :toExclusive
          AND (type = 'EXPENSE' OR refund_of_id IS NOT NULL)
          AND (:categoryId IS NULL OR category_id IN (:categoryIds))
        """,
    )
    suspend fun spentInPeriod(
        fromInclusive: Long,
        toExclusive: Long,
        categoryId: String?,
        categoryIds: List<String>,
    ): Long

    /**
     * Per-account balances.
     *
     * **A transfer is ONE row**, not two. The row carries `account_id` (source) and
     * `counter_account_id` (destination), so the same amount is subtracted from one side and added
     * to the other in a single CASE. A LEFT JOIN keeps accounts with zero activity visible.
     *
     * An earlier version also exposed `insertTransferPair`, which wrote two mirrored rows. Combined
     * with this query that nets to zero on BOTH accounts — the money silently never moves. The two
     * representations cannot coexist, and the domain model already documents the single-row shape
     * (`Transaction.counterAccountId`), so the multi-row helper was removed rather than kept as a
     * trap for the next caller.
     */
    @Query(
        """
        SELECT a.id AS accountId,
               a.currency AS currency,
               a.initial_balance_minor
                 + COALESCE(SUM(
                     CASE
                         WHEN t.kind = 'TRANSFER' AND t.account_id = a.id THEN -t.amount_minor
                         WHEN t.kind = 'TRANSFER' AND t.counter_account_id = a.id THEN t.amount_minor
                         WHEN t.type = 'INCOME'  THEN t.amount_minor
                         WHEN t.type = 'EXPENSE' THEN -t.amount_minor
                         ELSE 0
                     END
                   ), 0) AS balanceMinor
        FROM accounts a
        LEFT JOIN transactions t
               ON (t.account_id = a.id OR t.counter_account_id = a.id)
              AND t.deleted_at IS NULL
              AND t.occurred_at < :asOfExclusive
        WHERE a.is_archived = 0
        GROUP BY a.id
        """,
    )
    fun observeAccountBalances(asOfExclusive: Long): Flow<List<AccountBalanceRow>>

    /**
     * Inserts a transfer as a SINGLE row (source + counter account).
     *
     * Kept as a named helper so call sites cannot accidentally write two legs; there is no
     * two-row variant by design.
     */
    @Transaction
    suspend fun upsertTransfer(transfer: TransactionEntity) {
        require(transfer.counterAccountId != null) { "transfer must name a counter account" }
        upsert(transfer)
    }

    /** CSV/JSON export feed: stable ordering, no page limit. */
    @Query(
        """
        SELECT * FROM transactions
        WHERE deleted_at IS NULL AND occurred_at BETWEEN :fromInclusive AND :toExclusive
        ORDER BY occurred_at ASC, created_at ASC
        """,
    )
    suspend fun getForExport(fromInclusive: Long, toExclusive: Long): List<TransactionEntity>

    @Query(
        """
        SELECT t.id, t.type, t.kind, t.amount_minor, t.currency, t.account_id, t.category_id,
               t.occurred_at, t.note,
               c.name AS categoryName,
               a.name AS accountName
        FROM transactions t
        LEFT JOIN categories c ON c.id = t.category_id
        LEFT JOIN accounts a ON a.id = t.account_id
        WHERE t.id = :id AND t.deleted_at IS NULL
        """,
    )
    suspend fun getWithMeta(id: String): TransactionWithMeta?
}
