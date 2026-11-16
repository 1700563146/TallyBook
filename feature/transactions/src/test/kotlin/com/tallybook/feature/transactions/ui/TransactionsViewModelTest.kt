package com.tallybook.feature.transactions.ui

import androidx.paging.PagingData
import com.tallybook.core.common.clock.Clock
import com.tallybook.core.common.time.JavaTimeDateProvider
import com.tallybook.core.data.repository.BudgetRepository
import com.tallybook.core.data.repository.PeriodTotals
import com.tallybook.core.data.repository.StatsRepository
import com.tallybook.core.data.repository.TransactionListItem
import com.tallybook.core.data.repository.TransactionQuery
import com.tallybook.core.data.repository.TransactionRepository
import com.tallybook.core.data.repository.TransactionSearchFilter
import com.tallybook.core.model.money.CurrencyCode
import com.tallybook.core.model.money.Money
import com.tallybook.core.model.stats.BudgetProgress
import com.tallybook.core.model.stats.TimeGranularity
import com.tallybook.core.model.stats.TrendSeries
import com.tallybook.core.model.transaction.Budget
import com.tallybook.core.model.transaction.Transaction
import com.tallybook.core.model.transaction.TransactionId
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModelTest {

    private val now = 1_760_000_000_000L

    @Test
    fun bulk_delete_and_undo_keep_the_whole_selection_together() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val transactions = FakeTransactionRepository(now)
            val viewModel = viewModel(transactions)
            advanceUntilIdle()

            viewModel.startSelection("first")
            viewModel.toggleSelection("second")
            assertEquals(setOf("first", "second"), viewModel.uiState.value.selectedIds)

            viewModel.deleteSelected()
            advanceUntilIdle()

            assertEquals(setOf("first", "second"), transactions.deletedIds)
            assertFalse(viewModel.uiState.value.isSelectionMode)
            assertEquals(setOf("first", "second"), viewModel.uiState.value.undoTransactionIds)
            assertTrue(viewModel.uiState.value.groups.isEmpty())

            viewModel.undoDelete()
            advanceUntilIdle()

            assertTrue(transactions.deletedIds.isEmpty())
            assertEquals(2, viewModel.uiState.value.groups.single().items.size)
            assertEquals(null, viewModel.uiState.value.undoMessage)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun viewModel(transactions: FakeTransactionRepository) = TransactionsViewModel(
        transactionRepository = transactions,
        statsRepository = FakeStatsRepository,
        budgetRepository = FakeBudgetRepository,
        dateProvider = JavaTimeDateProvider(ZoneId.of("Asia/Shanghai")),
        clock = object : Clock {
            override fun nowMillis(): Long = now
        },
        zoneId = ZoneId.of("Asia/Shanghai"),
    )
}

private class FakeTransactionRepository(private val occurredAt: Long) : TransactionRepository {
    private val items = MutableStateFlow(
        listOf("first", "second").map { id -> item(id, occurredAt) },
    )
    val deletedIds = linkedSetOf<String>()

    override fun observeInRange(fromInclusive: Long, toExclusive: Long): Flow<List<Transaction>> =
        flowOf(items.value.map { it.transaction })

    override fun observeListItems(
        fromInclusive: Long,
        toExclusive: Long,
    ): Flow<List<TransactionListItem>> = items

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
    override suspend fun countAll(): Int = items.value.size
    override suspend fun getAllForBackup(): List<Transaction> = items.value.map { it.transaction }
    override suspend fun getPage(
        query: TransactionQuery,
        limit: Int,
        offset: Int,
    ): List<Transaction> = items.value.map { it.transaction }

    override suspend fun getById(id: String): Transaction? =
        items.value.firstOrNull { it.transaction.id.value == id }?.transaction

    override suspend fun upsert(transaction: Transaction) = Unit
    override suspend fun delete(id: String) {
        deletedIds += id
        publish()
    }

    override suspend fun restore(id: String) {
        deletedIds -= id
        publish()
    }

    override suspend fun deleteMany(ids: Set<String>) {
        deletedIds += ids
        publish()
    }

    override suspend fun restoreMany(ids: Set<String>) {
        deletedIds -= ids
        publish()
    }

    override fun observePeriodTotals(
        fromInclusive: Long,
        toExclusive: Long,
    ): Flow<PeriodTotals> = flowOf(
        PeriodTotals(
            expense = Money.ZERO_CNY,
            income = Money.ZERO_CNY,
            count = items.value.size,
        ),
    )

    private fun publish() {
        val all = listOf("first", "second")
        items.value = all.filterNot { it in deletedIds }.map { item(it, occurredAt) }
    }

    private fun item(id: String, timestamp: Long) = TransactionListItem(
        transaction = Transaction(
            id = TransactionId(id),
            type = TransactionType.EXPENSE,
            amount = Money(1_000, CurrencyCode.CNY),
            accountId = "cash",
            categoryId = "food",
            occurredAt = timestamp,
        ),
        categoryName = "餐饮",
        accountName = "现金",
    )
}

private object FakeStatsRepository : StatsRepository {
    override fun observeTrend(
        range: LongRange,
        granularity: TimeGranularity,
    ): Flow<TrendSeries> = flowOf(
        TrendSeries(
            granularity = granularity,
            points = emptyList(),
        ),
    )

    override fun observeCategoryBreakdown(
        range: LongRange,
        type: TransactionType,
    ) = flowOf(emptyList<com.tallybook.core.model.stats.CategorySlice>())

    override fun observeDailyTotals(
        range: LongRange,
    ): Flow<Map<String, PeriodTotals>> = flowOf(emptyMap())
}

private object FakeBudgetRepository : BudgetRepository {
    override fun observeActive(): Flow<List<Budget>> = flowOf(emptyList())
    override suspend fun upsert(budget: Budget) = Unit
    override suspend fun deactivate(id: String) = Unit
    override suspend fun delete(id: String) = Unit
    override fun observeProgress(nowMillis: Long): Flow<List<BudgetProgress>> = flowOf(emptyList())
}
