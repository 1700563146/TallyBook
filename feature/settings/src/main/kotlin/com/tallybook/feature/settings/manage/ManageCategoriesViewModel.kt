package com.tallybook.feature.settings.manage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tallybook.core.common.clock.Clock
import com.tallybook.core.data.repository.CategoryRepository
import com.tallybook.core.model.transaction.Category
import com.tallybook.core.model.transaction.TransactionType
import com.tallybook.core.model.transaction.toCategoryTree
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** One row in the category manager. */
data class CategoryRowUi(
    val id: String,
    val name: String,
    val parentId: String?,
    val depth: Int,
    val childCount: Int,
    val usageLabel: String,
    val isArchived: Boolean,
)

data class ManageCategoriesUiState(
    val type: TransactionType = TransactionType.EXPENSE,
    val categories: List<CategoryRowUi> = emptyList(),
    /** Non-null while the add/rename dialog is open. */
    val editing: CategoryEditDialog? = null,
    val message: String? = null,
)

data class CategoryEditDialog(
    val categoryId: String?,
    val initialName: String,
    val parentId: String?,
    val isNew: Boolean,
)

/**
 * Category management.
 *
 * The rule that matters: a category that has ever been used is **archived, never deleted**. Deleting
 * would orphan historical transactions or drop them from reports, so the ledger would silently change
 * its mind about numbers the user already saw. Archiving keeps the history intact and only removes the
 * category from the picker.
 */
@HiltViewModel
class ManageCategoriesViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ManageCategoriesUiState())
    val uiState: StateFlow<ManageCategoriesUiState> = _uiState.asStateFlow()

    private val selectedType = MutableStateFlow(TransactionType.EXPENSE)

    init {
        viewModelScope.launch {
            selectedType.collect { type -> _uiState.update { it.copy(type = type) } }
        }

        viewModelScope.launch {
            // Usage over the last 90 days drives the "· N 笔" hint, which is what tells a user whether
            // archiving a category will hide anything they care about.
            val since = clock.nowMillis() - USAGE_WINDOW_DAYS * 24L * 60 * 60 * 1000
            combine(
                categoryRepository.observeAll(),
                categoryRepository.observeUsage(since),
                selectedType,
            ) { all, usage, type ->
                all.filter { it.type == type }
                    .toCategoryTree()
                    .map { node ->
                        val category = node.category
                        val count = usage[category.id] ?: 0
                        CategoryRowUi(
                            id = category.id,
                            name = category.name,
                            parentId = category.parentId,
                            depth = node.depth,
                            childCount = node.childCount,
                            usageLabel = if (count > 0) "$count 笔" else "未使用",
                            isArchived = category.isArchived,
                        )
                    }
            }.collect { rows ->
                _uiState.update { it.copy(categories = rows) }
            }
        }
    }

    fun selectType(type: TransactionType) {
        selectedType.value = type
    }

    fun startAdd(parentId: String? = null) {
        _uiState.update {
            it.copy(
                editing = CategoryEditDialog(
                    categoryId = null,
                    initialName = "",
                    parentId = parentId,
                    isNew = true,
                ),
            )
        }
    }

    fun startRename(row: CategoryRowUi) {
        _uiState.update {
            it.copy(
                editing = CategoryEditDialog(
                    categoryId = row.id,
                    initialName = row.name,
                    parentId = row.parentId,
                    isNew = false,
                ),
            )
        }
    }

    fun dismissDialog() {
        _uiState.update { it.copy(editing = null) }
    }

    fun submit(name: String, parentId: String?) {
        val editing = _uiState.value.editing ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return

        viewModelScope.launch {
            // Read once here rather than caching a list that could go stale while the dialog is open.
            val existing = categoryRepository.observeAll().first()
            val duplicate = existing.any {
                it.type == _uiState.value.type &&
                    it.name == trimmed &&
                    it.parentId == parentId &&
                    it.id != editing.categoryId
            }
            if (duplicate) {
                _uiState.update { it.copy(message = "已经有同名的分类了") }
                return@launch
            }

            val original = existing.firstOrNull { it.id == editing.categoryId }
            val chosenParent = parentId?.let { id -> existing.firstOrNull { it.id == id } }
            if (parentId != null) {
                val originalHasChildren = existing.any { it.parentId == editing.categoryId }
                if (
                    chosenParent == null ||
                    chosenParent.type != _uiState.value.type ||
                    chosenParent.parentId != null
                ) {
                    _uiState.update { it.copy(message = "只能选择同级分类作为上级分类") }
                    return@launch
                }
                if (originalHasChildren) {
                    _uiState.update {
                        it.copy(message = "这个分类还有子分类，不能移动到其他分类下")
                    }
                    return@launch
                }
            }
            val category = Category(
                id = editing.categoryId ?: UUID.randomUUID().toString(),
                name = trimmed,
                type = original?.type ?: _uiState.value.type,
                parentId = parentId,
                iconKey = original?.iconKey ?: chosenParent?.iconKey,
                colorArgb = original?.colorArgb ?: chosenParent?.colorArgb,
                sortOrder = if (original == null || original.parentId != parentId) {
                    existing
                        .filter { it.parentId == parentId }
                        .maxOfOrNull { it.sortOrder }
                        ?.plus(1)
                        ?: 0
                } else {
                    original.sortOrder
                },
                isArchived = original?.isArchived ?: false,
            )
            categoryRepository.upsert(category)
            _uiState.update {
                it.copy(
                    editing = null,
                    message = if (editing.isNew) "已添加「$trimmed」" else "已重命名为「$trimmed」",
                )
            }
        }
    }

    /** Archives or restores; both directions are reversible, which is why there is no confirmation. */
    fun toggleArchive(row: CategoryRowUi) {
        viewModelScope.launch {
            val category = categoryRepository.observeAll().first()
                .firstOrNull { it.id == row.id } ?: return@launch
            if (!category.isArchived && row.childCount > 0) {
                _uiState.update {
                    it.copy(message = "请先把子分类移到顶级分类，再归档这个父分类")
                }
                return@launch
            }
            categoryRepository.upsert(category.copy(isArchived = !category.isArchived))
            _uiState.update {
                it.copy(
                    message = if (category.isArchived) {
                        "已恢复「${category.name}」"
                    } else {
                        "已归档「${category.name}」，历史记录不受影响"
                    },
                )
            }
        }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(message = null) }
    }

    private companion object {
        const val USAGE_WINDOW_DAYS = 90L
    }
}
