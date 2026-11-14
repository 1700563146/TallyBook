package com.tallybook.feature.transactions.ui

import com.tallybook.core.data.repository.TransactionListItem
import com.tallybook.core.model.transaction.TransactionKind
import com.tallybook.core.model.transaction.TransactionType

/** Immutable list state. Every field is either pre-formatted or a plain value, never a live source. */
data class TransactionsUiState(
    val monthLabel: String = "",
    /** Anchor for prev/next month navigation; the ViewModel re-derives the range from it. */
    val anchorMillis: Long = 0L,
    val expenseTotal: String = "",
    val incomeTotal: String = "",
    val groups: List<DayGroupUi> = emptyList(),
    /** Last 7 days of spending, for the home mini chart. */
    val miniChart: List<MiniBarUi> = emptyList(),
    /** Budget summaries shown above the chart; empty when the user has no budgets. */
    val budgetCards: List<BudgetCardUi> = emptyList(),
    val isLoading: Boolean = true,
    /**
     * Snackbar message after a delete, plus the id to undo. Kept in state (not a one-shot event)
     * because the undo window must survive a recomposition during the snackbar's lifetime.
     */
    val undoMessage: String? = null,
    val undoTransactionId: String? = null,
    /**
     * Incremented whenever the list should jump back to the top.
     *
     * Needed because `LazyColumn` anchors to the first visible item: a row restored above the
     * viewport keeps the user looking at unchanged content, so an undo appears to have done nothing.
     */
    val scrollToTopTick: Int = 0,
) {
    val isEmpty: Boolean get() = groups.isEmpty()
}

/** One day of the ledger, with its own header totals. */
data class DayGroupUi(
    val dateKey: String,
    val dayLabel: String,
    val expenseLabel: String,
    val items: List<TransactionRowUi>,
)

data class TransactionRowUi(
    val id: String,
    val categoryName: String,
    val note: String,
    val accountName: String,
    /** Destination account for a transfer. */
    val counterAccountName: String? = null,
    val amountLabel: String,
    val isExpense: Boolean,
    val isTransfer: Boolean,
    val iconKey: String?,
    val colorArgb: Long?,
)

fun TransactionListItem.toTransactionRowUi(): TransactionRowUi {
    val transaction = transaction
    val isTransfer = transaction.kind == TransactionKind.TRANSFER
    val isExpense = transaction.type == TransactionType.EXPENSE
    val sign = when {
        isTransfer -> ""
        isExpense -> "-"
        else -> "+"
    }
    return TransactionRowUi(
        id = transaction.id.value,
        categoryName = if (isTransfer) "转账" else categoryName,
        note = transaction.note.orEmpty(),
        accountName = accountName,
        counterAccountName = counterAccountName,
        amountLabel = "$sign${transaction.amount.format()}",
        isExpense = isExpense && !isTransfer,
        isTransfer = isTransfer,
        iconKey = categoryIconKey,
        colorArgb = categoryColorArgb,
    )
}

/** One bar of the home mini chart. */
data class MiniBarUi(
    /** Single-character weekday label ("一".."日"). */
    val label: String,
    val amountLabel: String,
    /** 0..1 relative to the tallest day; drives the bar height. */
    val fraction: Float,
    val isToday: Boolean,
)

/** One budget summary row on the home screen. */
data class BudgetCardUi(
    val title: String,
    /** Explains which budget is shown when multiple budgets exist. */
    val subtitle: String? = null,
    /** "还剩 ¥120.00" or "超支 ¥30.00" — already worded, so the UI cannot mis-render a negative. */
    val amountLabel: String,
    val progress: Float,
    val isOverBudget: Boolean,
)
