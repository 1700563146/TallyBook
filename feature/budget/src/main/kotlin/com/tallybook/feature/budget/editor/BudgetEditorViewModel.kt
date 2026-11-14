package com.tallybook.feature.budget.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tallybook.core.data.repository.BudgetRepository
import com.tallybook.core.data.repository.CategoryRepository
import com.tallybook.core.model.money.CurrencyCode
import com.tallybook.core.model.money.Money
import com.tallybook.core.model.transaction.Budget
import com.tallybook.core.model.transaction.BudgetPeriod
import com.tallybook.core.model.transaction.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * Create / edit / delete a budget.
 *
 * Only WEEKLY and MONTHLY are offered as periods because those are the two the product promises.
 * The model and schema also carry YEARLY/CUSTOM so adding them later is a UI change, not a migration
 * — but exposing an option the budget screen cannot render would be worse than not showing it.
 */
@HiltViewModel
class BudgetEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val budgetRepository: BudgetRepository,
    private val categoryRepository: CategoryRepository,
) : ViewModel() {

    private val budgetId: String? = savedStateHandle.get<String>("budgetId")

    private val _uiState = MutableStateFlow(BudgetEditorUiState(isEditing = budgetId != null))
    val uiState: StateFlow<BudgetEditorUiState> = _uiState.asStateFlow()

    private val _finished = MutableStateFlow(false)
    val finished: StateFlow<Boolean> = _finished.asStateFlow()

    private var existing: Budget? = null

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val categories = categoryRepository.observeActive().first()
            .filter { it.type == TransactionType.EXPENSE && !it.isArchived }
            .map { BudgetCategoryOption(it.id, it.name) }

        val budget = budgetId?.let { id ->
            budgetRepository.observeActive().first().firstOrNull { it.id == id }
        }
        existing = budget

        _uiState.update { current ->
            current.copy(
                budgetId = budget?.id,
                name = budget?.name.orEmpty(),
                limitText = budget?.limit?.toBigDecimal()
                    ?.stripTrailingZeros()?.toPlainString()
                    ?: "",
                period = budget?.period ?: BudgetPeriod.MONTHLY,
                categoryId = budget?.categoryId,
                categories = categories,
                includeSubcategories = budget?.includeSubcategories ?: true,
            )
        }
    }

    fun onNameChange(value: String) {
        _uiState.update { it.copy(name = value, error = null) }
    }

    /**
     * Accepts digits and at most one decimal point.
     *
     * Filtering here (instead of validating on save) means the keyboard can stay cheap and the user
     * never sees a value that will be rejected later.
     */
    fun onLimitChange(value: String) {
        val filtered = value.filter { it.isDigit() || it == '.' }
        val singleDot = filtered.indexOf('.').let { first ->
            if (first == -1) {
                filtered
            } else {
                filtered.substring(0, first + 1) + filtered.substring(first + 1).replace(".", "")
            }
        }
        _uiState.update { it.copy(limitText = singleDot.take(12), error = null) }
    }

    fun onPeriodChange(period: BudgetPeriod) {
        _uiState.update { it.copy(period = period) }
    }

    fun onCategorySelected(id: String?) {
        _uiState.update { it.copy(categoryId = id) }
    }

    fun onIncludeSubcategoriesChange(value: Boolean) {
        _uiState.update { it.copy(includeSubcategories = value) }
    }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) {
            _uiState.update { it.copy(error = "请填写名称与限额") }
            return
        }
        val limit = runCatching { Money.parse(state.limitText, CurrencyCode.CNY) }.getOrNull()
        if (limit == null || limit.isZero) {
            _uiState.update { it.copy(error = "限额格式不正确") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            val budget = Budget(
                id = existing?.id ?: UUID.randomUUID().toString(),
                name = state.name.trim(),
                categoryId = state.categoryId,
                period = state.period,
                limit = limit,
                includeSubcategories = state.includeSubcategories,
                isActive = true,
            )
            runCatching { budgetRepository.upsert(budget) }
                .onSuccess { _finished.value = true }
                .onFailure { error ->
                    _uiState.update { it.copy(isSaving = false, error = error.message ?: "保存失败") }
                }
        }
    }

    fun delete() {
        val id = budgetId ?: return
        viewModelScope.launch {
            // Delete (not deactivate): the user asked to remove this budget, and re-creating it with
            // the same name is a normal thing to do afterwards.
            runCatching { budgetRepository.delete(id) }
                .onSuccess { _finished.value = true }
                .onFailure { error -> _uiState.update { it.copy(error = error.message ?: "删除失败") } }
        }
    }
}
