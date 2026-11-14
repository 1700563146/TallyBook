package com.tallybook.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.tallybook.core.model.money.CurrencyCode
import com.tallybook.core.model.transaction.AccountType
import com.tallybook.core.model.transaction.BudgetPeriod
import com.tallybook.core.model.transaction.TransactionKind
import com.tallybook.core.model.transaction.TransactionType

/**
 * Room entities are intentionally separate from [com.tallybook.core.model] domain types.
 *
 * Why not annotate the domain model directly: the domain model is pure Kotlin (testable on the JVM
 * with plain JUnit, no Robolectric), and DB concerns — nullable FKs, denormalised columns for query
 * speed, migration history — would leak into it. The mapper layer is one small file, and it is
 * where schema/domain drift becomes a compile error instead of a runtime null.
 */

@Entity(
    tableName = "accounts",
    indices = [Index("is_archived"), Index("sort_order")],
)
data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: AccountType,
    val currency: String,
    @ColumnInfo(name = "initial_balance_minor") val initialBalanceMinor: Long,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean = false,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "categories",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["parent_id"],
            // RESTRICT: deleting a parent must be an explicit re-parent operation, never a silent
            // cascade that quietly reassigns thousands of transactions.
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("parent_id"), Index("type"), Index("sort_order")],
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: TransactionType,
    @ColumnInfo(name = "parent_id") val parentId: String? = null,
    @ColumnInfo(name = "icon_key") val iconKey: String? = null,
    @ColumnInfo(name = "color_argb") val colorArgb: Long? = null,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean = false,
)

/**
 * The central ledger table.
 *
 * Denormalisation notes:
 * - `occurred_at` is epoch millis (Long). A formatted date string would break range scans and
 *   timezone-correct month bucketing.
 * - `amount_minor` + `currency` instead of a DECIMAL column: SQLite has no DECIMAL type and would
 *   store REAL, reintroducing float error. SUM() over INTEGER is exact.
 * - No `original_amount_minor` / `fx_rate_scaled`: the ledger is single-currency by design. Removing
 *   those columns also removes an entire class of "which amount is the real one?" bugs.
 */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["account_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("occurred_at"),
        Index("account_id"),
        Index("category_id"),
        Index("type"),
        Index(value = ["occurred_at", "account_id"]),
        // The dominant chart query: expenses/income for a range, ordered by time
        Index(value = ["type", "occurred_at"]),
        Index(value = ["category_id", "occurred_at"]),
    ],
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val type: TransactionType,
    val kind: TransactionKind,
    @ColumnInfo(name = "amount_minor") val amountMinor: Long,
    val currency: String,
    @ColumnInfo(name = "account_id") val accountId: String,
    @ColumnInfo(name = "category_id") val categoryId: String? = null,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    val note: String? = null,
    @ColumnInfo(name = "counter_account_id") val counterAccountId: String? = null,
    /**
     * Set on the INCOME row that represents a refund of an earlier EXPENSE.
     *
     * A refund is NOT a negative expense: the model keeps `amount_minor` positive and derives
     * direction from `type`, so encoding a refund as a negative amount would make every `SUM()`
     * ambiguous. Instead the refund is an INCOME row carrying `refund_of_id`, and the aggregate
     * queries subtract such rows from the *expense* side (and exclude them from income).
     */
    @ColumnInfo(name = "refund_of_id") val refundOfId: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    /**
     * Soft delete. Financial records must never hard-delete: an accidental wipe is unrecoverable and
     * an audit trail matters. Every read filters `deleted_at IS NULL` in the DAO so a forgotten
     * filter shows up as a review comment, not as quietly inflated totals.
     */
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
)

/** Tags are a separate table, not a comma-joined column, so tag filters stay indexable. */
@Entity(
    tableName = "transaction_tags",
    primaryKeys = ["transaction_id", "tag"],
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transaction_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tag")],
)
data class TransactionTagEntity(
    @ColumnInfo(name = "transaction_id") val transactionId: String,
    val tag: String,
)

/**
 * A budget. `start_day`/`period` are both needed: a WEEKLY budget historically started on Monday,
 * but users coming from Chinese apps often budget Monday–Sunday while US-centric apps use
 * Sunday–Saturday. Storing the week start explicitly avoids a global setting that breaks when the
 * user moves.
 */
@Entity(tableName = "budgets", indices = [Index("category_id"), Index("is_active")])
data class BudgetEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "category_id") val categoryId: String? = null,
    val period: BudgetPeriod,
    @ColumnInfo(name = "limit_minor") val limitMinor: Long,
    val currency: String,
    @ColumnInfo(name = "include_subcategories") val includeSubcategories: Boolean = true,
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Long = 0,
)

/**
 * Converters for value types.
 *
 * Money is deliberately NOT persisted through a converter: an amount without its currency is
 * meaningless, and a converter that invents one hides bugs. Entities store `amount_minor` +
 * `currency` as two explicit columns; converters only handle trivially-safe scalars.
 */
class Converters {

    @TypeConverter
    fun currencyToString(code: CurrencyCode): String = code.value

    @TypeConverter
    fun stringToCurrency(value: String): CurrencyCode = CurrencyCode(value)

    @TypeConverter
    fun transactionTypeToString(value: TransactionType): String = value.name

    @TypeConverter
    fun stringToTransactionType(value: String): TransactionType = TransactionType.valueOf(value)

    @TypeConverter
    fun transactionKindToString(value: TransactionKind): String = value.name

    @TypeConverter
    fun stringToTransactionKind(value: String): TransactionKind = TransactionKind.valueOf(value)

    @TypeConverter
    fun accountTypeToString(value: AccountType): String = value.name

    @TypeConverter
    fun stringToAccountType(value: String): AccountType = AccountType.valueOf(value)

    @TypeConverter
    fun budgetPeriodToString(value: BudgetPeriod): String = value.name

    @TypeConverter
    fun stringToBudgetPeriod(value: String): BudgetPeriod = BudgetPeriod.valueOf(value)
}
