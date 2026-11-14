package com.tallybook.core.database

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.tallybook.core.database.entity.AccountEntity
import com.tallybook.core.database.entity.CategoryEntity
import com.tallybook.core.database.entity.TransactionEntity
import com.tallybook.core.model.transaction.AccountType
import com.tallybook.core.model.transaction.TransactionKind
import com.tallybook.core.model.transaction.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class TransactionSearchDaoTest {

    private lateinit var db: TallyDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            TallyDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun search_matches_note_category_and_account_names() = runTest {
        seed()

        assertEquals(
            setOf("note-match"),
            search("%咖啡%").map { it.transaction.id }.toSet(),
        )
        assertEquals(
            setOf("category-match"),
            search("%交通%").map { it.transaction.id }.toSet(),
        )
        assertEquals(
            setOf("category-match", "account-match", "transfer"),
            search("%银行卡%").map { it.transaction.id }.toSet(),
        )
    }

    @Test
    fun search_filters_direction_and_account() = runTest {
        seed()

        assertEquals(
            setOf("note-match"),
            search(
                pattern = null,
                entryType = TransactionType.EXPENSE.name,
                accountId = "cash",
            ).map { it.transaction.id }.toSet(),
        )
        assertEquals(
            setOf("transfer"),
            search(
                pattern = null,
                entryType = "TRANSFER",
                accountId = "bank",
            ).map { it.transaction.id }.toSet(),
        )
    }

    private suspend fun search(
        pattern: String?,
        entryType: String = "ALL",
        accountId: String? = null,
    ) = (
        db.transactionDao().search(pattern, entryType, accountId, null)
            .load(
                PagingSource.LoadParams.Refresh(
                    key = null,
                    loadSize = 50,
                    placeholdersEnabled = false,
                ),
            ) as PagingSource.LoadResult.Page
        ).data

    private suspend fun seed() {
        val now = 1_760_000_000_000L
        db.accountDao().upsertAll(
            listOf(
                account("cash", "现金", 0),
                account("bank", "银行卡", 1),
            ),
        )
        db.categoryDao().upsertAll(
            listOf(
                CategoryEntity(id = "food", name = "餐饮", type = TransactionType.EXPENSE),
                CategoryEntity(id = "transport", name = "交通", type = TransactionType.EXPENSE),
            ),
        )
        db.transactionDao().upsertAll(
            listOf(
                transaction(
                    id = "note-match",
                    accountId = "cash",
                    categoryId = "food",
                    note = "咖啡",
                    occurredAt = now,
                ),
                transaction(
                    id = "category-match",
                    accountId = "bank",
                    categoryId = "transport",
                    note = "午饭",
                    occurredAt = now - 1,
                ),
                transaction(
                    id = "account-match",
                    accountId = "bank",
                    categoryId = null,
                    note = "账户名匹配",
                    occurredAt = now - 2,
                ),
                transaction(
                    id = "transfer",
                    accountId = "cash",
                    categoryId = null,
                    note = "转账",
                    kind = TransactionKind.TRANSFER,
                    counterAccountId = "bank",
                    occurredAt = now - 3,
                ),
            ),
        )
    }

    private fun account(id: String, name: String, order: Int) = AccountEntity(
        id = id,
        name = name,
        type = AccountType.CASH,
        currency = "CNY",
        initialBalanceMinor = 0,
        sortOrder = order,
        createdAt = 1,
        updatedAt = 1,
    )

    private fun transaction(
        id: String,
        accountId: String,
        categoryId: String?,
        note: String,
        occurredAt: Long,
        kind: TransactionKind = TransactionKind.NORMAL,
        counterAccountId: String? = null,
    ) = TransactionEntity(
        id = id,
        type = TransactionType.EXPENSE,
        kind = kind,
        amountMinor = 100,
        currency = "CNY",
        accountId = accountId,
        categoryId = categoryId,
        occurredAt = occurredAt,
        note = note,
        counterAccountId = counterAccountId,
        createdAt = 1,
        updatedAt = 1,
    )
}
