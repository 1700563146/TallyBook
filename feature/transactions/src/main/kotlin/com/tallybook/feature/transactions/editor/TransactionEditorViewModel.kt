package com.tallybook.feature.transactions.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tallybook.core.common.clock.Clock
import com.tallybook.core.data.notify.BudgetAlertChecker
import com.tallybook.core.data.repository.AccountRepository
import com.tallybook.core.data.repository.CategoryRepository
import com.tallybook.core.data.repository.TransactionRepository
import com.tallybook.core.model.money.AmountKey
import com.tallybook.core.model.money.CurrencyCode
import com.tallybook.core.model.transaction.Category
import com.tallybook.core.model.transaction.Transaction
import com.tallybook.core.model.transaction.TransactionId
import com.tallybook.core.model.transaction.TransactionKind
import com.tallybook.core.model.transaction.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

/**
 * Create / edit a single transaction, or record a refund of an existing expense.
 *
 * One ViewModel serves all three because the form is the same; the nav route decides which mode:
 * - `transactionId == null, refundOfId == null` → 记一笔
 * - `transactionId != null` → 编辑
 * - `refundOfId != null` → 记退款（强制 INCOME，指向原支出）
 */
@HiltViewModel
class TransactionEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val budgetAlertChecker: BudgetAlertChecker,
    private val clock: Clock,
    private val zoneId: ZoneId,
) : ViewModel() {

    private val transactionId: String? = savedStateHandle.get<String>("transactionId")
    private val refundOfId: String? = savedStateHandle.get<String>("refundOfId")

    private val _uiState = MutableStateFlow(
        TransactionEditorUiState(isEditing = transactionId != null || refundOfId != null),
    )
    val uiState: StateFlow<TransactionEditorUiState> = _uiState.asStateFlow()

    private val _finished = MutableStateFlow(false)
    val finished: StateFlow<Boolean> = _finished.asStateFlow()

    private var existing: Transaction? = null
    private var refundSource: Transaction? = null
    private var allCategories: List<Category> = emptyList()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val accounts = accountRepository.observeActive().first()
        allCategories = categoryRepository.observeActive().first()

        val now = clock.nowMillis()
        // Recent frequency, not all-time: a category used heavily two years ago should not stay on
        // top forever.
        val usage = categoryRepository
            .observeUsage(now - USAGE_WINDOW_DAYS * 86_400_000L)
            .first()

        val transaction = transactionId?.let { transactionRepository.getById(it) }
        val refundSourceRow = refundOfId?.let { transactionRepository.getById(it) }
        existing = transaction
        refundSource = refundSourceRow

        val mode = when {
            refundSourceRow != null -> EditorType.INCOME
            transaction != null -> editorTypeOf(transaction.type, transaction.kind == TransactionKind.TRANSFER)
            else -> EditorType.EXPENSE
        }

        val source = refundSourceRow ?: transaction
        val occurredAt = when {
            // A refund happens now, not on the original purchase date.
            refundSourceRow != null -> now
            transaction != null -> transaction.occurredAt
            else -> now
        }

        // A refund is recorded as INCOME for accounting, but it must carry an EXPENSE category:
        // the category breakdown subtracts refunds from the expense side, so pointing it at an
        // income category would both fail to offset the purchase and inflate income categories.
        val categoryType = if (refundSourceRow != null) TransactionType.EXPENSE else null
        val sortedCategories = categoriesFor(allCategories, mode, usage, categoryType)

        _uiState.update { current ->
            current.copy(
                transactionId = transactionId,
                mode = mode,
                amount = when {
                    refundSourceRow != null -> com.tallybook.core.model.money.AmountInputState(
                        expression = refundSourceRow.amount.toBigDecimal().stripTrailingZeros().toPlainString(),
                    )
                    transaction != null -> com.tallybook.core.model.money.AmountInputState(
                        expression = transaction.amount.toBigDecimal().stripTrailingZeros().toPlainString(),
                    )
                    else -> current.amount
                },
                accounts = accounts.map { AccountOption(it.id, it.name) },
                selectedAccountId = source?.accountId ?: accounts.firstOrNull()?.id,
                counterAccountId = transaction?.counterAccountId,
                categories = sortedCategories,
                selectedCategoryId = source?.categoryId
                    ?: sortedCategories.firstOrNull()?.id,
                occurredAt = occurredAt,
                dateLabel = DATE_LABEL.format(Instant.ofEpochMilli(occurredAt).atZone(zoneId)),
                note = when {
                    refundSourceRow != null -> refundSourceRow.note?.let { "退款 · $it" } ?: "退款"
                    transaction != null -> transaction.note.orEmpty()
                    else -> ""
                },
                refundOfId = refundIdOf(transaction),
                refundSourceLabel = refundSourceRow?.let { source ->
                    val categoryName = allCategories.firstOrNull { it.id == source.categoryId }?.name
                        ?: "这笔支出"
                    "$categoryName ${source.amount.format()}"
                },
                originalIsExpense = transaction?.let {
                    it.type == TransactionType.EXPENSE && it.kind == TransactionKind.NORMAL
                } ?: false,
            )
        }
    }

    /**
     * Only preserve `refundOfId` when editing an existing refund row. A plain expense must never keep
     * one, or it would silently start subtracting from expense totals.
     */
    private fun refundIdOf(transaction: Transaction?): String? = when {
        refundOfId != null -> refundOfId
        transaction != null && transaction.type == TransactionType.INCOME -> transaction.refundOfId
        else -> null
    }

    /**
     * Orders categories for the picker: recent usage first, then the author-defined order.
     *
     * Deliberately simple and predictable — no ML, no time-of-day heuristics. A user can explain
     * "餐饮 is on top because I use it most", which matters more than a marginally better guess.
     */
    private fun categoriesFor(
        categories: List<Category>,
        mode: EditorType,
        usage: Map<String, Int>,
        forceType: TransactionType? = null,
    ): List<CategoryOption> {
        val type = forceType ?: when (mode) {
            EditorType.INCOME -> TransactionType.INCOME
            else -> TransactionType.EXPENSE
        }
        return categories
            .filter { it.type == type && !it.isArchived }
            .sortedWith(
                compareByDescending<Category> { usage[it.id] ?: 0 }
                    .thenBy { it.sortOrder }
                    .thenBy { it.name },
            )
            .map { CategoryOption(it.id, it.name, it.iconKey, it.colorArgb) }
    }

    fun onKey(key: AmountKey) {
        _uiState.update { it.withKey(key) }
    }

    fun onModeChange(mode: EditorType) {
        // A refund is structurally tied to an expense; switching direction would break that meaning.
        if (_uiState.value.isRefund) return

        _uiState.update { current ->
            val categories = categoriesFor(allCategories, mode, emptyMap())
            current.copy(
                transactionId = transactionId,
                mode = mode,
                categories = categories,
                selectedCategoryId = categories.firstOrNull { it.id == current.selectedCategoryId }?.id
                    ?: categories.firstOrNull()?.id,
                counterAccountId = if (mode == EditorType.TRANSFER) {
                    current.counterAccountId
                        ?: current.accounts.firstOrNull { it.id != current.selectedAccountId }?.id
                } else {
                    null
                },
                error = null,
            )
        }
    }

    fun onCategorySelected(id: String) {
        _uiState.update { it.copy(selectedCategoryId = id, error = null) }
    }

    fun onAccountSelected(id: String) {
        _uiState.update { current ->
            current.copy(
                selectedAccountId = id,
                counterAccountId = current.counterAccountId.takeIf { it != id },
            )
        }
    }

    fun onCounterAccountSelected(id: String) {
        _uiState.update { it.copy(counterAccountId = id) }
    }

    fun onDateSelected(epochMillis: Long) {
        val existingTime = Instant.ofEpochMilli(_uiState.value.occurredAt).atZone(zoneId).toLocalTime()
        val newDateTime = Instant.ofEpochMilli(epochMillis)
            .atZone(zoneId)
            .toLocalDate()
            .atTime(existingTime)
        _uiState.update {
            it.copy(
                occurredAt = newDateTime.atZone(zoneId).toInstant().toEpochMilli(),
                dateLabel = DATE_LABEL.format(newDateTime.atZone(zoneId)),
            )
        }
    }

    fun onNoteChange(note: String) {
        _uiState.update { it.copy(note = note) }
    }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) {
            _uiState.update { it.copy(error = "请填写金额并选择分类与账户") }
            return
        }
        val amount = state.amount.toMoney(CurrencyCode.CNY) ?: run {
            _uiState.update { it.copy(error = "金额格式不正确") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            val type = when {
                state.isRefund -> TransactionType.INCOME
                state.mode == EditorType.INCOME -> TransactionType.INCOME
                else -> TransactionType.EXPENSE
            }
            val kind = if (state.mode == EditorType.TRANSFER && !state.isRefund) {
                TransactionKind.TRANSFER
            } else {
                TransactionKind.NORMAL
            }

            val transaction = Transaction(
                id = existing?.id ?: TransactionId(UUID.randomUUID().toString()),
                type = type,
                kind = kind,
                amount = amount,
                accountId = state.selectedAccountId!!,
                categoryId = if (kind == TransactionKind.TRANSFER) null else state.selectedCategoryId,
                occurredAt = state.occurredAt,
                note = state.note.ifBlank { null },
                counterAccountId = if (kind == TransactionKind.TRANSFER) state.counterAccountId else null,
                refundOfId = state.refundOfId,
                createdAt = existing?.createdAt ?: clock.nowMillis(),
                updatedAt = clock.nowMillis(),
            )

            runCatching { transactionRepository.upsert(transaction) }
                .onSuccess {
                    _finished.value = true
                    // Saving is exactly when a budget threshold is most likely to be crossed, so check
                    // now instead of waiting for the next app launch. Failures here must never surface
                    // to the user: the transaction was saved successfully.
                    viewModelScope.launch { runCatching { budgetAlertChecker.checkAndNotify() } }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isSaving = false, error = error.message ?: "保存失败") }
                }
        }
    }

    fun delete() {
        val id = transactionId ?: return
        viewModelScope.launch {
            runCatching { transactionRepository.delete(id) }
                .onSuccess { _finished.value = true }
                .onFailure { error -> _uiState.update { it.copy(error = error.message ?: "删除失败") } }
        }
    }

    private companion object {
        val DATE_LABEL: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy年M月d日 EEE", Locale.getDefault())

        const val USAGE_WINDOW_DAYS = 90L
    }
}
