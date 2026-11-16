package com.tallybook.feature.budget.editor

import androidx.lifecycle.SavedStateHandle
import com.tallybook.core.data.repository.BudgetRepository
import com.tallybook.core.data.repository.CategoryRepository
import com.tallybook.core.model.stats.BudgetProgress
import com.tallybook.core.model.transaction.Budget
import com.tallybook.core.model.transaction.Category
import com.tallybook.core.model.transaction.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetEditorViewModelTest {

    @Test
    fun creating_a_subcategory_selects_it_and_adopts_its_name() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val categories = FakeCategoryRepository()
            val viewModel = BudgetEditorViewModel(
                savedStateHandle = SavedStateHandle(),
                budgetRepository = FakeBudgetRepository(),
                categoryRepository = categories,
            )
            advanceUntilIdle()

            viewModel.onCategorySelected("food")
            viewModel.openSubcategoryDialog()
            assertEquals("food", viewModel.uiState.value.subcategoryDialog?.defaultParentId)

            viewModel.createSubcategory("早餐", "food")
            advanceUntilIdle()

            val created = categories.all.value.single { it.name == "早餐" }
            assertEquals("food", created.parentId)
            assertEquals(TransactionType.EXPENSE, created.type)
            assertEquals(created.id, viewModel.uiState.value.categoryId)
            assertEquals("早餐", viewModel.uiState.value.name)
            assertEquals(null, viewModel.uiState.value.subcategoryDialog)
            assertNotNull(viewModel.uiState.value.selectedCategory)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun duplicate_child_name_is_rejected_without_writing() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val categories = FakeCategoryRepository()
            categories.requireUpsert(
                Category(
                    id = "breakfast",
                    name = "早餐",
                    type = TransactionType.EXPENSE,
                    parentId = "food",
                ),
            )
            val viewModel = BudgetEditorViewModel(
                savedStateHandle = SavedStateHandle(),
                budgetRepository = FakeBudgetRepository(),
                categoryRepository = categories,
            )
            advanceUntilIdle()

            viewModel.createSubcategory("早餐", "food")
            advanceUntilIdle()

            assertEquals("这个上级分类下已经有同名分类", viewModel.uiState.value.categoryError)
            assertEquals(2, categories.all.value.size)
        } finally {
            Dispatchers.resetMain()
        }
    }
}

private class FakeCategoryRepository : CategoryRepository {
    val all = MutableStateFlow(
        listOf(
            Category(
                id = "food",
                name = "餐饮",
                type = TransactionType.EXPENSE,
                iconKey = "restaurant",
            ),
        ),
    )

    fun requireUpsert(category: Category) {
        all.value = all.value + category
    }

    override fun observeActive(): Flow<List<Category>> = all
    override fun observeAll(): Flow<List<Category>> = all
    override fun observeUsage(sinceInclusive: Long): Flow<Map<String, Int>> = flowOf(emptyMap())

    override suspend fun upsert(category: Category) {
        all.value = all.value.filterNot { it.id == category.id } + category
    }

    override suspend fun archive(id: String) = Unit
}

private class FakeBudgetRepository : BudgetRepository {
    override fun observeActive(): Flow<List<Budget>> = flowOf(emptyList())
    override suspend fun upsert(budget: Budget) = Unit
    override suspend fun deactivate(id: String) = Unit
    override suspend fun delete(id: String) = Unit
    override fun observeProgress(nowMillis: Long): Flow<List<BudgetProgress>> = flowOf(emptyList())
}
