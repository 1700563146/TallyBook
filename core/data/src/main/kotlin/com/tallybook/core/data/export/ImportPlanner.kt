package com.tallybook.core.data.export

import com.tallybook.core.model.money.Money
import com.tallybook.core.model.transaction.AccountType
import com.tallybook.core.model.transaction.BudgetPeriod
import com.tallybook.core.model.transaction.TransactionKind
import com.tallybook.core.model.transaction.TransactionType

/**
 * What an import would do, computed before anything is written.
 *
 * Import is the one destructive-adjacent action in the app, so the user sees this summary and
 * confirms. It also gives the code a place to refuse *before* opening a transaction, instead of
 * failing halfway through with a foreign-key error.
 */
data class ImportPreview(
    val transactions: Int,
    val accounts: Int,
    val categories: Int,
    val budgets: Int,
    /** Rows whose id already exists locally; importing them again would be a no-op. */
    val duplicates: Int,
    /** Account ids referenced by the file but neither present locally nor defined in the file. */
    val missingAccountIds: Set<String> = emptySet(),
    val missingCategoryIds: Set<String> = emptySet(),
    val missingRefundIds: Set<String> = emptySet(),
    val duplicateIds: Set<String> = emptySet(),
    val invalidRecordIds: Set<String> = emptySet(),
    val currencyMismatch: Boolean = false,
    val schemaVersion: Int,
) {
    /** Nothing new to write. */
    val isNoOp: Boolean get() = transactions - duplicates <= 0

    /** The file is unusable as-is: its rows point at accounts that do not exist anywhere. */
    val hasBlockingProblems: Boolean
        get() = missingAccountIds.isNotEmpty() ||
            missingCategoryIds.isNotEmpty() ||
            missingRefundIds.isNotEmpty() ||
            duplicateIds.isNotEmpty() ||
            invalidRecordIds.isNotEmpty() ||
            currencyMismatch
}

data class ImportResult(
    val inserted: Int,
    val skipped: Int,
    val accountsUpserted: Int,
    val categoriesUpserted: Int,
    val budgetsUpserted: Int,
)

/**
 * Decides what an import will do.
 *
 * Pure and separate from the database work so the two risky rules can be tested directly:
 * - **Idempotency by id.** Re-importing the same backup must not duplicate anything; a UUID that is
 *   already present is skipped, not merged.
 * - **No dangling references.** A transaction pointing at an account that exists nowhere would fail
 *   the foreign key halfway through a multi-row insert. Detecting it up front turns a partial write
 *   into a clear message.
 */
object ImportPlanner {

    fun plan(
        envelope: ExportEnvelope,
        existingTransactionIds: Set<String>,
        existingAccountIds: Set<String>,
        existingCategoryIds: Set<String> = emptySet(),
        ledgerCurrency: String = "CNY",
    ): ImportPreview {
        val incoming = envelope.transactions
        val duplicates = incoming.count { it.id in existingTransactionIds }

        val knownAccounts = existingAccountIds + envelope.accounts.map { it.id }
        val knownCategories = existingCategoryIds + envelope.categories.map { it.id }
        val knownTransactions = existingTransactionIds + incoming.map { it.id }
        val missingAccounts = incoming
            .flatMap { listOfNotNull(it.accountId, it.counterAccountId) }
            .filter { it !in knownAccounts }
            .toSet()
        val missingCategories = buildSet {
            envelope.categories.mapNotNullTo(this) { it.parentId }
            incoming.mapNotNullTo(this) { it.categoryId }
            envelope.budgets.mapNotNullTo(this) { it.categoryId }
        }.filterTo(mutableSetOf()) { it !in knownCategories }
        val missingRefunds = incoming
            .mapNotNull { it.refundOfId }
            .filterTo(mutableSetOf()) { it !in knownTransactions }

        val allIds = envelope.accounts.map { it.id } +
            envelope.categories.map { it.id } +
            incoming.map { it.id } +
            envelope.budgets.map { it.id }
        val duplicateIds = allIds.groupingBy { it }.eachCount()
            .filterValues { it > 1 }
            .keys

        val invalidIds = buildSet {
            envelope.accounts.filter {
                runCatching { AccountType.valueOf(it.type) }.isFailure
            }.mapTo(this) { it.id }
            envelope.categories.filter {
                runCatching { TransactionType.valueOf(it.type) }.isFailure ||
                    it.parentId == it.id
            }.mapTo(this) { it.id }
            envelope.budgets.filter {
                runCatching { BudgetPeriod.valueOf(it.period) }.isFailure ||
                    it.limitMinor <= 0 ||
                    it.limitMinor > Money.MAX_ABS_MINOR_UNITS
            }.mapTo(this) { it.id }
            incoming.filter { transaction ->
                val type = runCatching { TransactionType.valueOf(transaction.type) }.getOrNull()
                val kind = runCatching { TransactionKind.valueOf(transaction.kind) }.getOrNull()
                val invalidAmount = transaction.amountMinor <= 0 ||
                    transaction.amountMinor > Money.MAX_ABS_MINOR_UNITS
                val invalidTransfer = kind == TransactionKind.TRANSFER &&
                    (
                        transaction.counterAccountId == null ||
                            transaction.counterAccountId == transaction.accountId
                        )
                val invalidRefund = transaction.refundOfId != null &&
                    (
                        type != TransactionType.INCOME ||
                            kind != TransactionKind.NORMAL ||
                            transaction.refundOfId == transaction.id
                        )
                type == null ||
                    kind == null ||
                    invalidAmount ||
                    transaction.occurredAt <= 0 ||
                    transaction.id.isBlank() ||
                    transaction.accountId.isBlank() ||
                    invalidTransfer ||
                    invalidRefund
            }.mapTo(this) { it.id }
        }

        val currencyMismatch =
            envelope.currency != ledgerCurrency ||
                envelope.accounts.any { it.currency != ledgerCurrency } ||
                incoming.any { it.currency != ledgerCurrency } ||
                envelope.budgets.any { it.currency != ledgerCurrency }

        return ImportPreview(
            transactions = incoming.size,
            accounts = envelope.accounts.size,
            categories = envelope.categories.size,
            budgets = envelope.budgets.size,
            duplicates = duplicates,
            missingAccountIds = missingAccounts,
            missingCategoryIds = missingCategories,
            missingRefundIds = missingRefunds,
            duplicateIds = duplicateIds,
            invalidRecordIds = invalidIds,
            currencyMismatch = currencyMismatch,
            schemaVersion = envelope.schemaVersion,
        )
    }

    /** Rows that would actually be written, preserving file order. */
    fun rowsToInsert(
        envelope: ExportEnvelope,
        existingTransactionIds: Set<String>,
    ): List<ExportTransaction> =
        envelope.transactions.filter { it.id !in existingTransactionIds }
}
