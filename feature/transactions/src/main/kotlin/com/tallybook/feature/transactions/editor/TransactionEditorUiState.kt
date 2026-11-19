package com.tallybook.feature.transactions.editor

import com.tallybook.core.model.money.AmountInputState
import com.tallybook.core.model.money.AmountKey
import com.tallybook.core.model.transaction.AccountType
import com.tallybook.core.model.transaction.TransactionType

/** Which selector is currently expanded in the editor. */
enum class EditorType { EXPENSE, INCOME, TRANSFER }

/**
 * Editor state.
 *
 * The amount lives as an [AmountInputState] (an expression, not a number) so `12.5+3.8` survives
 * every recomposition and can still be evaluated on save.
 */
data class TransactionEditorUiState(
    val mode: EditorType = EditorType.EXPENSE,
    val amount: AmountInputState = AmountInputState(),
    val categories: List<CategoryOption> = emptyList(),
    val selectedCategoryId: String? = null,
    val accounts: List<AccountOption> = emptyList(),
    val selectedAccountId: String? = null,
    val counterAccountId: String? = null,
    val occurredAt: Long = 0L,
    val dateLabel: String = "",
    val note: String = "",
    val isEditing: Boolean = false,
    /** Id of the row being edited, needed by the screen to start a refund of it. */
    val transactionId: String? = null,
    val isSaving: Boolean = false,
    val error: String? = null,
    /**
     * True when this editor is creating a refund of [refundOfId].
     *
     * A refund is forced to INCOME and cannot be switched to expense/transfer: it is defined by its
     * relationship to another row, so letting the user flip the direction would produce a normal
     * income row that happens to carry a `refundOfId` — exactly the state the aggregate queries
     * treat as "subtract from expense".
     */
    val refundOfId: String? = null,
    val refundSourceLabel: String? = null,
    /** Whether the row being edited is a plain expense, i.e. eligible for the refund action. */
    val originalIsExpense: Boolean = false,
) {
    /** Save is enabled only when there is a positive amount and the required references are set. */
    val canSave: Boolean
        get() {
            val value = amount.value() ?: return false
            if (value.signum() <= 0) return false
            if (selectedAccountId == null) return false
            if (mode == EditorType.TRANSFER && counterAccountId == null) return false
            if (mode != EditorType.TRANSFER && selectedCategoryId == null) return false
            return true
        }

    val isTransfer: Boolean get() = mode == EditorType.TRANSFER

    val isRefund: Boolean get() = refundOfId != null

    /** The refund action is only offered for a saved expense. */
    val canRefund: Boolean get() = isEditing && originalIsExpense && !isRefund
}

data class CategoryOption(
    val id: String,
    val name: String,
    val iconKey: String?,
    val colorArgb: Long?,
)

data class AccountOption(
    val id: String,
    val name: String,
    val type: AccountType,
)

/** Keypad intents, kept as plain functions on the state so the UI stays dumb. */
fun TransactionEditorUiState.withKey(key: AmountKey): TransactionEditorUiState {
    var next = copy(amount = com.tallybook.core.model.money.AmountInput.press(amount, key), error = null)
    // After "=" the user usually wants to keep calculating, so the type is left untouched.
    return next
}

/** Maps a transaction direction to the editor's mode. */
fun editorTypeOf(type: TransactionType, isTransfer: Boolean): EditorType = when {
    isTransfer -> EditorType.TRANSFER
    type == TransactionType.INCOME -> EditorType.INCOME
    else -> EditorType.EXPENSE
}
