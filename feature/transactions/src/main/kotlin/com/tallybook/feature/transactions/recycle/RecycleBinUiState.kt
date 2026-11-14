package com.tallybook.feature.transactions.recycle

/**
 * Recycle bin state.
 *
 * Rows are deliberately a separate type from the ledger's [com.tallybook.feature.transactions.ui.TransactionRowUi]:
 * the actions and affordances differ (restore / delete forever instead of edit), and reusing the
 * ledger row model would tempt the UI into offering "edit" on a deleted record.
 */
data class RecycleBinUiState(
    val rows: List<DeletedRowUi> = emptyList(),
    val isLoading: Boolean = true,
    val message: String? = null,
) {
    val isEmpty: Boolean get() = rows.isEmpty()

    val count: Int get() = rows.size
}

data class DeletedRowUi(
    val id: String,
    val categoryName: String,
    val note: String,
    val accountName: String,
    val amountLabel: String,
    val deletedAtLabel: String,
    val isExpense: Boolean,
    val iconKey: String?,
    val colorArgb: Long?,
)
