package com.tallybook.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import com.tallybook.core.database.entity.TransactionEntity

/** Projection rows returned by aggregate DAO queries. Plain data classes, never entities. */

/** Headline totals for a range. One row because the ledger is single-currency. */
data class PeriodTotals(
    val expenseMinor: Long,
    val incomeMinor: Long,
    val count: Int,
)

/** One category's total for the selected type and range. */
data class CategoryTotalRow(
    val categoryId: String?,
    val totalMinor: Long,
    val count: Int,
)

/**
 * One day of aggregated activity — the only bucketing unit SQL knows about.
 *
 * [day] is an ISO `yyyy-MM-dd` string local to the device's zone. Week/month/year grouping is done in
 * Kotlin by `DateProvider.bucketStartOf`, so there is exactly one week-start definition in the whole
 * app (charts and budgets share it).
 *
 * This replaced a `BucketTotalRow` that carried SQL-computed `%Y-%m` / `%W` keys. Those keys are not
 * compatible with the JVM's ISO week-based calendar, which made the weekly chart read zero.
 */
data class DailyTotalRow(
    val day: String,
    val expenseMinor: Long,
    val incomeMinor: Long,
    val count: Int,
)

/** How many times a category was used in a window; drives the order of the category picker. */
data class CategoryUsageRow(
    val categoryId: String,
    val usageCount: Int,
)

data class AccountBalanceRow(
    val accountId: String,
    val currency: String,
    val balanceMinor: Long,
)

/**
 * A transaction joined with its category/account display names.
 *
 * The `@ColumnInfo` names are required: Room matches projection properties to *column* names, and
 * SQLite columns here are snake_case. Without them Room either fails the compile for non-null
 * primitives or silently leaves fields at defaults — exactly the drift the entity/domain split is
 * meant to catch.
 */
data class TransactionWithMeta(
    val id: String,
    val type: String,
    val kind: String,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    val currency: String,
    @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "category_id") val categoryId: String?,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    val note: String?,
    val categoryName: String?,
    val accountName: String?,
)

/** A paged ledger search row with all display names resolved in the same SQLite query. */
data class TransactionSearchRow(
    @Embedded val transaction: TransactionEntity,
    val categoryName: String?,
    val categoryIconKey: String?,
    val categoryColorArgb: Long?,
    val accountName: String?,
    val counterAccountName: String?,
)
