package com.tallybook.core.model.transaction

import com.tallybook.core.model.money.CurrencyCode
import com.tallybook.core.model.money.Money
import kotlinx.serialization.Serializable

/**
 * Ids are strings (UUIDs) rather than auto-increment longs.
 *
 * Rationale: a bookkeeping app will eventually sync/backup/import, and device- or server-generated
 * ids must not collide. Auto-increment RowIds also leak write order and make "insert into an old
 * date" semantically odd. 36 bytes per id is acceptable; the public API stays `String` if it ever
 * needs to become a 16-byte BLOB.
 */
@JvmInline
@Serializable
value class TransactionId(val value: String)

/** Money in or money out. Direction lives here, never in the sign of [Transaction.amount]. */
@Serializable
enum class TransactionType { EXPENSE, INCOME }

/**
 * Why a separate kind instead of a nullable `counterAccountId`:
 * transfers need two ledger legs that must succeed or fail together, and every income/expense
 * aggregate must exclude them. Encoding that as a nullable FK invites double-counting in each
 * aggregate query — one forgotten `WHERE` and moving money between your own accounts looks like
 * income *and* expense.
 */
@Serializable
enum class TransactionKind { NORMAL, TRANSFER }

/**
 * A single ledger entry.
 *
 * [amount] is always **positive**; direction comes from [type]. Storing signed amounts makes
 * "expense of -50" vs "expense of 50" ambiguous and corrupts `SUM()` aggregates.
 *
 * There is no `originalAmount`/`fxRate` pair: the ledger is single-currency by design, so a
 * transaction has exactly one amount in exactly one currency.
 */
@Serializable
data class Transaction(
    val id: TransactionId,
    val type: TransactionType,
    val kind: TransactionKind = TransactionKind.NORMAL,
    val amount: Money,
    val accountId: String,
    val categoryId: String?,
    /** Epoch millis at the transaction's own timestamp (not insert time). */
    val occurredAt: Long,
    val note: String? = null,
    val tags: List<String> = emptyList(),
    /** Set when [kind] == TRANSFER: the other side of the movement. */
    val counterAccountId: String? = null,
    /**
     * Set when this row is a refund of an earlier EXPENSE.
     *
     * A refund is recorded as an INCOME row pointing at the original, never as a negative expense:
     * see [TransactionKind] for why the model refuses to encode direction in the sign.
     */
    val refundOfId: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    /**
     * Set when the row is soft-deleted.
     *
     * Surfaced in the domain model (not just the entity) because export must be able to say "this row
     * is in the recycle bin" — otherwise a backup silently resurrects deleted records on restore.
     */
    val deletedAt: Long? = null,
) {
    init {
        require(amount.minorUnits >= 0) { "amount must be non-negative; encode direction via type" }
        if (refundOfId != null) {
            require(type == TransactionType.INCOME) {
                "a refund must be recorded as INCOME pointing at the original expense"
            }
        }
        if (kind == TransactionKind.TRANSFER) {
            require(counterAccountId != null) { "TRANSFER requires counterAccountId" }
            require(counterAccountId != accountId) { "TRANSFER cannot target the same account" }
        }
    }

    /** Signed value for aggregation: expenses negative, income positive. */
    val signedMinorUnits: Long
        get() = when (type) {
            TransactionType.EXPENSE -> -amount.minorUnits
            TransactionType.INCOME -> amount.minorUnits
        }
}

/**
 * A hierarchical category.
 *
 * Depth is capped at 2 (parent -> child) by the UI. Self-referencing [parentId] with ON DELETE
 * RESTRICT prevents orphaning; deleting a parent requires re-parenting children first, which the
 * domain layer enforces rather than relying on SQLite cascade behaviour.
 */
@Serializable
data class Category(
    val id: String,
    val name: String,
    val type: TransactionType,
    val parentId: String? = null,
    val iconKey: String? = null,
    val colorArgb: Long? = null,
    val sortOrder: Int = 0,
    val isArchived: Boolean = false,
)

/**
 * An account is a container of money (cash, bank card, credit card, e-wallet).
 *
 * The ledger has one currency, so every account shares it; [currency] is carried on the account for
 * display of legacy/imported data, not for conversion.
 */
@Serializable
data class Account(
    val id: String,
    val name: String,
    val type: AccountType,
    val currency: CurrencyCode,
    /** Negative for credit cards / debt. */
    val initialBalance: Money,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

@Serializable
enum class AccountType { CASH, DEBIT_CARD, CREDIT_CARD, E_WALLET, INVESTMENT, OTHER }

/**
 * A spending cap for a period, optionally scoped to a category subtree.
 *
 * [period] is limited to WEEKLY / MONTHLY in the UI (see ROADMAP); YEARLY and CUSTOM exist in the
 * model so adding them later is a UI change, not a schema migration.
 */
@Serializable
data class Budget(
    val id: String,
    val name: String,
    /** Null means "all categories", i.e. a total spending cap. */
    val categoryId: String? = null,
    val period: BudgetPeriod,
    val limit: Money,
    /** Whether child categories roll up into this budget. */
    val includeSubcategories: Boolean = true,
    val isActive: Boolean = true,
    val createdAt: Long = 0,
)

@Serializable
enum class BudgetPeriod { WEEKLY, MONTHLY, YEARLY, CUSTOM }

/** A reusable template for fast entry (e.g. "早餐 12 元"). */
@Serializable
data class TransactionTemplate(
    val id: String,
    val name: String,
    val type: TransactionType,
    val amount: Money?,
    val accountId: String,
    val categoryId: String?,
    val note: String? = null,
    val sortOrder: Int = 0,
)
