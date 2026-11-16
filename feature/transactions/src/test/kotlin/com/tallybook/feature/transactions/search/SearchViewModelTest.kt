package com.tallybook.feature.transactions.search

import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagingData
import com.tallybook.core.common.clock.Clock
import com.tallybook.core.common.time.JavaTimeDateProvider
import com.tallybook.core.data.repository.AccountRepository
import com.tallybook.core.data.repository.CategoryRepository
import com.tallybook.core.data.repository.PeriodTotals
import com.tallybook.core.data.repository.SearchEntryType
import com.tallybook.core.data.repository.TransactionListItem
import com.tallybook.core.data.repository.TransactionQuery
import com.tallybook.core.data.repository.TransactionRepository
import com.tallybook.core.data.repository.TransactionSearchFilter
import com.tallybook.core.model.money.CurrencyCode
import com.tallybook.core.model.money.Money
import com.tallybook.core.model.transaction.Account
import com.tallybook.core.model.transaction.Category
import com.tallybook.core.model.transaction.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val clock = object : Clock {
        override fun nowMillis(): Long = 1_760_000_000_000L
    }

    @Test
    fun filters_survive_view_model_recreation_through_saved_state() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val handle = SavedStateHandle()
            val accountRepository = FakeAccountRepository()
            val categoryRepository = FakeCategoryRepository()

            val first = viewModel(handle, accountRepository, categoryRepository)
            first.setQuery("咖啡")
            first.setEntryType(SearchEntryType.TRANSFER)
            first.setAccount("a1")
            first.setCategory("c1")

            val restored = viewModel(handle, accountRepository, categoryRepository)
            advanceUntilIdle()

            assertEquals("咖啡", restored.query.value)
            assertEquals(SearchEntryType.TRANSFER, restored.entryType.value)
            assertEquals("a1", restored.accountId.value)
            assertEquals("c1", restored.categoryId.value)

            restored.clearFilters()
            advanceUntilIdle()
            assertEquals(SearchEntryType.ALL, restored.entryType.value)
            assertEquals(null, restored.accountId.value)
            assertEquals(null, restored.categoryId.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun viewModel(
        handle: SavedStateHandle,
        accountRepository: AccountRepository,
        categoryRepository: CategoryRepository,
    ) = SearchViewModel(
        savedStateHandle = handle,
        transactionRepository = FakeTransactionRepository(),
        accountRepository = accountRepository,
        categoryRepository = categoryRepository,
        dateProvider = JavaTimeDateProvider(ZoneId.of("Asia/Shanghai")),
        clock = clock,
    )
}

private class FakeTransactionRepository : TransactionRepository {
    override fun observeInRange(fromInclusive: Long, toExclusive: Long): Flow<List<Transaction>> =
        flowOf(emptyList())

    override fun observeListItems(
        fromInclusive: Long,
        toExclusive: Long,
    ): Flow<List<TransactionListItem>> = flowOf(emptyList())

    override fun observeSearchResults(
        filter: TransactionSearchFilter,
    ): Flow<PagingData<TransactionListItem>> = flowOf(PagingData.empty())

    override fun observeDeletedItems(): Flow<List<TransactionListItem>> = flowOf(emptyList())

    override fun observeCategoryItems(
        categoryId: String?,
        fromInclusive: Long,
        toExclusive: Long,
    ): Flow<List<TransactionListItem>> = flowOf(emptyList())

    override suspend fun purge(id: String) = Unit
    override suspend fun purgeAll(): Int = 0
    override suspend fun countAll(): Int = 0
    override suspend fun getAllForBackup(): List<Transaction> = emptyList()
    override suspend fun getPage(query: TransactionQuery, limit: Int, offset: Int): List<Transaction> =
        emptyList()

    override suspend fun getById(id: String): Transaction? = null
    override suspend fun upsert(transaction: Transaction) = Unit
    override suspend fun delete(id: String) = Unit
    override suspend fun restore(id: String) = Unit
    override suspend fun deleteMany(ids: Set<String>) = Unit
    override suspend fun restoreMany(ids: Set<String>) = Unit

    override fun observePeriodTotals(
        fromInclusive: Long,
        toExclusive: Long,
    ): Flow<PeriodTotals> = flowOf(
        PeriodTotals(
            expense = Money.ZERO_CNY,
            income = Money.ZERO_CNY,
            count = 0,
        ),
    )
}

private class FakeAccountRepository : AccountRepository {
    override fun observeActive(): Flow<List<Account>> = observeAll()
    override fun observeAll(): Flow<List<Account>> = flowOf(
        listOf(
            Account(
                id = "a1",
                name = "现金",
                type = com.tallybook.core.model.transaction.AccountType.CASH,
                currency = CurrencyCode.CNY,
                initialBalance = Money.ZERO_CNY,
            ),
        ),
    )

    override fun observeBalances(asOfExclusive: Long): Flow<Map<String, Money>> =
        flowOf(emptyMap())

    override suspend fun upsert(account: Account) = Unit
    override suspend fun archive(id: String) = Unit
}

private class FakeCategoryRepository : CategoryRepository {
    override fun observeActive(): Flow<List<Category>> = observeAll()
    override fun observeAll(): Flow<List<Category>> = flowOf(
        listOf(
            Category(
                id = "c1",
                name = "餐饮",
                type = com.tallybook.core.model.transaction.TransactionType.EXPENSE,
            ),
        ),
    )

    override fun observeUsage(sinceInclusive: Long): Flow<Map<String, Int>> = flowOf(emptyMap())
    override suspend fun upsert(category: Category) = Unit
    override suspend fun archive(id: String) = Unit
}
