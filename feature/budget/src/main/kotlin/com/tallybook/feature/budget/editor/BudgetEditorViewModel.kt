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
import com.tallybook.core.model.transaction.Category
import com.tallybook.core.model.transaction.TransactionType
import com.tallybook.core.model.transaction.toCategoryTree
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
        viewModelScope.launch { loadBudget() }
        viewModelScope.launch {
            categoryRepository.observeActive().collect { categories ->
                val options = categories
                    .filter { it.type == TransactionType.EXPENSE && !it.isArchived }
                    .toCategoryTree()
                    .map { node ->
                        BudgetCategoryOption(
                            id = node.category.id,
                            name = node.category.name,
                            parentId = node.category.parentId,
                            depth = node.depth,
                            childCount = node.childCount,
                            sortOrder = node.category.sortOrder,
                            iconKey = node.category.iconKey,
                            colorArgb = node.category.colorArgb,
                        )
                    }
                _uiState.update { it.copy(categories = options) }
            }
        }
    }

    private suspend fun loadBudget() {
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

    fun openSubcategoryDialog() {
        val selected = _uiState.value.selectedCategory
        _uiState.update {
            it.copy(
                categoryError = null,
                subcategoryDialog = SubcategoryDialogUi(
                    defaultParentId = when {
                        selected == null -> null
                        selected.depth == 0 -> selected.id
                        else -> selected.parentId
                    },
                ),
            )
        }
    }

    fun dismissSubcategoryDialog() {
        _uiState.update {
            it.copy(subcategoryDialog = null, categoryError = null, isCreatingCategory = false)
        }
    }

    /**
     * Creates a child category as part of the budget flow.
     *
     * A null [parentId] deliberately means a top-level category, which is useful when the user has
     * not created any expense categories yet. The two-level cap matches the domain model and keeps
     * every parent picker unambiguous.
     */
    fun createSubcategory(name: String, parentId: String?) {
        val trimmed = name.trim()
        val current = _uiState.value
        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(categoryError = "请输入子分类名称") }
            return
        }
        if (current.isCreatingCategory) return

        val parent = parentId?.let { id ->
            current.categories.firstOrNull { it.id == id && it.depth == 0 }
        }
        if (parentId != null && parent == null) {
            _uiState.update { it.copy(categoryError = "只能把顶级分类设为上级分类") }
            return
        }
        val duplicate = current.categories.any {
            it.parentId == parentId && it.name.equals(trimmed, ignoreCase = true)
        }
        if (duplicate) {
            _uiState.update { it.copy(categoryError = "这个上级分类下已经有同名分类") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingCategory = true, categoryError = null) }
            val allCategories = categoryRepository.observeAll().first()
            val allDuplicate = allCategories.any {
                it.type == TransactionType.EXPENSE &&
                    it.parentId == parentId &&
                    it.name.equals(trimmed, ignoreCase = true)
            }
            if (allDuplicate) {
                _uiState.update {
                    it.copy(
                        isCreatingCategory = false,
                        categoryError = "这个上级分类下已经有同名分类",
                    )
                }
                return@launch
            }
            val siblingOrder = allCategories
                .filter { it.type == TransactionType.EXPENSE && it.parentId == parentId }
                .maxOfOrNull { it.sortOrder }
                ?.plus(1)
                ?: 0
            val category = Category(
                id = UUID.randomUUID().toString(),
                name = trimmed,
                type = TransactionType.EXPENSE,
                parentId = parentId,
                iconKey = parent?.iconKey,
                colorArgb = parent?.colorArgb,
                sortOrder = siblingOrder,
            )
            runCatching { categoryRepository.upsert(category) }
                .onSuccess {
                    _uiState.update {
                        val adoptName = it.name.isBlank() || it.name.trim() == parent?.name
                        it.copy(
                            name = if (adoptName) trimmed else it.name,
                            categoryId = category.id,
                            includeSubcategories = true,
                            subcategoryDialog = null,
                            categoryError = null,
                            isCreatingCategory = false,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isCreatingCategory = false,
                            categoryError = error.message ?: "创建分类失败",
                        )
                    }
                }
        }
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
