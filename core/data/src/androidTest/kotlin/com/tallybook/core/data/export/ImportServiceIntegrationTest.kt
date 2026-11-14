package com.tallybook.core.data.export

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.tallybook.core.common.clock.Clock
import com.tallybook.core.database.TallyDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ImportServiceIntegrationTest {

    private lateinit var db: TallyDatabase
    private lateinit var service: ImportService

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, TallyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        service = ImportService(
            context = context,
            database = db,
            transactionDao = db.transactionDao(),
            accountDao = db.accountDao(),
            categoryDao = db.categoryDao(),
            budgetDao = db.budgetDao(),
            clock = object : Clock {
                override fun nowMillis(): Long = 2_000L
            },
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun archived_category_tags_and_metadata_restore_together() = runTest {
        val envelope = ExportEnvelope(
            exportedAt = 1_000L,
            currency = "CNY",
            accounts = listOf(
                ExportAccount(
                    id = "a1",
                    name = "现金",
                    type = "CASH",
                    currency = "CNY",
                    initialBalanceMinor = 10_000,
                    isArchived = false,
                    sortOrder = 3,
                    createdAt = 100,
                    updatedAt = 200,
                ),
            ),
            categories = listOf(
                ExportCategory(
                    id = "c-old",
                    name = "旧分类",
                    type = "EXPENSE",
                    sortOrder = 8,
                    isArchived = true,
                ),
            ),
            transactions = listOf(
                ExportTransaction(
                    id = "t1",
                    type = "EXPENSE",
                    kind = "NORMAL",
                    amountMinor = 1_200,
                    currency = "CNY",
                    accountId = "a1",
                    categoryId = "c-old",
                    occurredAt = 500,
                    tags = listOf("报销", "工作"),
                    createdAt = 400,
                    updatedAt = 450,
                ),
            ),
            budgets = listOf(
                ExportBudget(
                    id = "b1",
                    name = "旧分类预算",
                    categoryId = "c-old",
                    period = "MONTHLY",
                    limitMinor = 10_000,
                    currency = "CNY",
                    includeSubcategories = true,
                    createdAt = 300,
                ),
            ),
        )
        val preview = ImportPlanner.plan(
            envelope = envelope,
            existingTransactionIds = emptySet(),
            existingAccountIds = emptySet(),
        )

        val result = service.apply(
            ImportEnvelope(
                envelope = envelope,
                existingIds = emptySet(),
                preview = preview,
            ),
        )

        val category = db.categoryDao().observeAll().first().single()
        val transaction = db.transactionDao().getById("t1")!!
        val tags = db.transactionDao().getAllTags().map { it.tag }.toSet()
        val account = db.accountDao().observeAll().first().single()
        val budget = db.budgetDao().getById("b1")!!

        assertEquals(1, result.inserted)
        assertTrue(category.isArchived)
        assertEquals(8, category.sortOrder)
        assertEquals("c-old", transaction.categoryId)
        assertEquals(setOf("报销", "工作"), tags)
        assertEquals(3, account.sortOrder)
        assertEquals(100L, account.createdAt)
        assertTrue(budget.isActive)
        assertFalse(preview.hasBlockingProblems)
    }
}
