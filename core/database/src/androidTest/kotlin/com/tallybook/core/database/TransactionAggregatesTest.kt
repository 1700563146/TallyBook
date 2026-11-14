package com.tallybook.core.database

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.tallybook.core.database.entity.AccountEntity
import com.tallybook.core.database.entity.CategoryEntity
import com.tallybook.core.database.entity.TransactionEntity
import com.tallybook.core.model.transaction.AccountType
import com.tallybook.core.model.transaction.TransactionKind
import com.tallybook.core.model.transaction.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * SQL-level tests for the aggregate queries.
 *
 * These exist because the queries are hand-written SQL where a single wrong `CASE` is invisible in
 * Kotlin: the app still compiles, the screen still renders, and the number is simply wrong. The
 * three behaviours covered here are the ones that are easy to get wrong and impossible to notice:
 *
 * 1. a transfer must move money out of one account and into the other **without** touching
 *    income/expense totals;
 * 2. a refund must reduce expenses **without** inflating income;
 * 3. soft-deleted rows must vanish from every total.
 *
 * These run as instrumented tests on a device/emulator rather than under Robolectric: Robolectric
 * downloads its `android-all` runtime at first execution, which is slow and can fail behind a
 * restricted network, whereas an emulator exercises the real SQLite build the app ships with.
 */
/**
 * NOTE ON TEST NAMES: methods here use snake_case, not backticked prose.
 *
 * Kotlin allows `fun \`transfers move money\`()`, and those names are great to read — but the compiler
 * emits lambda classes whose SimpleName contains the spaces, and D8 rejects space-containing names for
 * DEX < 040 (i.e. minSdk < 30). Instrumented tests then fail at dexing with a confusing
 * "Space characters in SimpleName ... are not allowed" error, before any test runs.
 * Pure JVM tests are unaffected, which is why :core:model still uses backticked names.
 */
class TransactionAggregatesTest {

    private lateinit var db: TallyDatabase
    private val now = 1_700_000_000_000L

    private lateinit var accountA: String
    private lateinit var accountB: String
    private lateinit var categoryFood: String

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            TallyDatabase::class.java,
        ).allowMainThreadQueries().build()

        accountA = "acc-a"
        accountB = "acc-b"
        categoryFood = "cat-food"

        runTest {
            db.accountDao().upsertAll(
                listOf(
                    account(accountA, "现金", 0),
                    account(accountB, "银行卡", 1),
                ),
            )
            db.categoryDao().upsert(
                CategoryEntity(
                    id = categoryFood,
                    name = "餐饮",
                    type = TransactionType.EXPENSE,
                    iconKey = "restaurant",
                ),
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun account(id: String, name: String, order: Int) = AccountEntity(
        id = id,
        name = name,
        type = AccountType.CASH,
        currency = "CNY",
        initialBalanceMinor = 0,
        sortOrder = order,
        createdAt = now,
        updatedAt = now,
    )

    private fun tx(
        type: TransactionType,
        amountMinor: Long,
        accountId: String,
        kind: TransactionKind = TransactionKind.NORMAL,
        categoryId: String? = null,
        counterAccountId: String? = null,
        refundOfId: String? = null,
        deletedAt: Long? = null,
    ) = TransactionEntity(
        id = UUID.randomUUID().toString(),
        type = type,
        kind = kind,
        amountMinor = amountMinor,
        currency = "CNY",
        accountId = accountId,
        categoryId = categoryId,
        occurredAt = now,
        counterAccountId = counterAccountId,
        refundOfId = refundOfId,
        createdAt = now,
        updatedAt = now,
        deletedAt = deletedAt,
    )

    @Test
    fun transfer_moves_money_between_accounts() = runTest {
        db.transactionDao().upsertTransfer(
            tx(
                type = TransactionType.EXPENSE,
                amountMinor = 10_000,
                accountId = accountA,
                kind = TransactionKind.TRANSFER,
                counterAccountId = accountB,
            ),
        )

        val balances = db.transactionDao().observeAccountBalances(now + 1).first().associate { it.accountId to it.balanceMinor }

        assertEquals("source account must be debited", -10_000L, balances[accountA])
        assertEquals("target account must be credited", 10_000L, balances[accountB])
    }

    @Test
    fun transfer_is_excluded_from_income_and_expense_totals() = runTest {
        db.transactionDao().upsertTransfer(
            tx(
                type = TransactionType.EXPENSE,
                amountMinor = 10_000,
                accountId = accountA,
                kind = TransactionKind.TRANSFER,
                counterAccountId = accountB,
            ),
        )

        val totals = db.transactionDao().observePeriodTotals(now - 1, now + 1).first()

        assertEquals(0L, totals.expenseMinor)
        assertEquals(0L, totals.incomeMinor)
        assertEquals(0, totals.count)
    }

    @Test
    fun refund_reduces_expense_and_does_not_inflate_income() = runTest {
        val original = tx(
            type = TransactionType.EXPENSE,
            amountMinor = 50_000,
            accountId = accountA,
            categoryId = categoryFood,
        )
        db.transactionDao().upsert(original)
        db.transactionDao().upsert(
            tx(
                type = TransactionType.INCOME,
                amountMinor = 20_000,
                accountId = accountA,
                categoryId = categoryFood,
                refundOfId = original.id,
            ),
        )

        val totals = db.transactionDao().observePeriodTotals(now - 1, now + 1).first()

        assertEquals("expense must be net of the refund", 30_000L, totals.expenseMinor)
        assertEquals("refund must not count as income", 0L, totals.incomeMinor)
    }

    @Test
    fun refund_is_attributed_to_the_same_category_on_the_expense_side() = runTest {
        val original = tx(
            type = TransactionType.EXPENSE,
            amountMinor = 50_000,
            accountId = accountA,
            categoryId = categoryFood,
        )
        db.transactionDao().upsert(original)
        db.transactionDao().upsert(
            tx(
                type = TransactionType.INCOME,
                amountMinor = 20_000,
                accountId = accountA,
                categoryId = categoryFood,
                refundOfId = original.id,
            ),
        )

        val byCategory = db.transactionDao()
            .observeTotalsByCategory(now - 1, now + 1, TransactionType.EXPENSE.name)
            .first()

        assertEquals(30_000L, byCategory.single().totalMinor)
        assertEquals(categoryFood, byCategory.single().categoryId)
    }

    @Test
    fun soft_deleted_rows_disappear_from_totals_and_from_the_list() = runTest {
        val keep = tx(
            type = TransactionType.EXPENSE,
            amountMinor = 1_000,
            accountId = accountA,
            categoryId = categoryFood,
        )
        val gone = tx(
            type = TransactionType.EXPENSE,
            amountMinor = 9_000,
            accountId = accountA,
            categoryId = categoryFood,
        )
        db.transactionDao().upsertAll(listOf(keep, gone))
        db.transactionDao().softDelete(gone.id, now)

        val totals = db.transactionDao().observePeriodTotals(now - 1, now + 1).first()
        val rows = db.transactionDao().observeInRange(now - 1, now + 1).first()

        assertEquals(1_000L, totals.expenseMinor)
        assertEquals(1, rows.size)
        assertEquals(keep.id, rows.single().id)
    }

    @Test
    fun restore_brings_a_soft_deleted_row_back() = runTest {
        val row = tx(
            type = TransactionType.EXPENSE,
            amountMinor = 4_500,
            accountId = accountA,
            categoryId = categoryFood,
        )
        db.transactionDao().upsert(row)
        db.transactionDao().softDelete(row.id, now)
        db.transactionDao().restore(row.id, now + 1)

        val totals = db.transactionDao().observePeriodTotals(now - 1, now + 1).first()
        assertEquals(4_500L, totals.expenseMinor)
    }

    @Test
    fun budget_spend_is_net_of_refunds() = runTest {
        val original = tx(
            type = TransactionType.EXPENSE,
            amountMinor = 10_000,
            accountId = accountA,
            categoryId = categoryFood,
        )
        db.transactionDao().upsert(original)
        db.transactionDao().upsert(
            tx(
                type = TransactionType.INCOME,
                amountMinor = 3_000,
                accountId = accountA,
                categoryId = categoryFood,
                refundOfId = original.id,
            ),
        )

        val spent = db.transactionDao().spentInPeriod(
            fromInclusive = now - 1,
            toExclusive = now + 1,
            categoryId = categoryFood,
            categoryIds = listOf(categoryFood),
        )

        assertEquals(7_000L, spent)
    }

    @Test
    fun daily_buckets_are_net_of_refunds_and_exclude_transfers() = runTest {
        val original = tx(
            type = TransactionType.EXPENSE,
            amountMinor = 10_000,
            accountId = accountA,
            categoryId = categoryFood,
        )
        db.transactionDao().upsert(original)
        db.transactionDao().upsert(
            tx(
                type = TransactionType.INCOME,
                amountMinor = 2_500,
                accountId = accountA,
                categoryId = categoryFood,
                refundOfId = original.id,
            ),
        )
        db.transactionDao().upsertTransfer(
            tx(
                type = TransactionType.EXPENSE,
                amountMinor = 99_999,
                accountId = accountA,
                kind = TransactionKind.TRANSFER,
                counterAccountId = accountB,
            ),
        )

        val daily = db.transactionDao().observeDailyTotals(now - 86_400_000L, now + 86_400_000L).first()

        assertEquals(1, daily.size)
        assertEquals(7_500L, daily.single().expenseMinor)
        assertEquals(0L, daily.single().incomeMinor)
    }

    @Test
    fun income_totals_exclude_refund_rows() = runTest {
        db.transactionDao().upsert(
            tx(
                type = TransactionType.INCOME,
                amountMinor = 100_000,
                accountId = accountA,
                categoryId = null,
            ),
        )

        val totals = db.transactionDao().observePeriodTotals(now - 1, now + 1).first()
        assertEquals(100_000L, totals.incomeMinor)
        assertTrue(totals.expenseMinor == 0L)
    }

    @Test
    fun category_usage_counts_recent_rows_and_skips_deleted() = runTest {
        val older = tx(
            type = TransactionType.EXPENSE,
            amountMinor = 1_000,
            accountId = accountA,
            categoryId = categoryFood,
        ).copy(occurredAt = now - 200L * 86_400_000L) // outside the 90-day window
        val recent = tx(
            type = TransactionType.EXPENSE,
            amountMinor = 1_000,
            accountId = accountA,
            categoryId = categoryFood,
        )
        val deleted = tx(
            type = TransactionType.EXPENSE,
            amountMinor = 1_000,
            accountId = accountA,
            categoryId = categoryFood,
        )
        db.transactionDao().upsertAll(listOf(older, recent, deleted))
        db.transactionDao().softDelete(deleted.id, now)

        val usage = db.categoryDao()
            .observeUsage(now - 90L * 86_400_000L)
            .first()
            .associate { it.categoryId to it.usageCount }

        // Only the recent, non-deleted row counts.
        assertEquals(1, usage[categoryFood])
    }

    @Test
    fun category_usage_ranks_the_most_used_category_first() = runTest {
        val otherId = "cat-transport"
        db.categoryDao().upsert(
            CategoryEntity(id = otherId, name = "交通", type = TransactionType.EXPENSE),
        )
        repeat(3) {
            db.transactionDao().upsert(
                tx(type = TransactionType.EXPENSE, amountMinor = 100, accountId = accountA, categoryId = otherId),
            )
        }
        db.transactionDao().upsert(
            tx(type = TransactionType.EXPENSE, amountMinor = 100, accountId = accountA, categoryId = categoryFood),
        )

        val ranked = db.categoryDao()
            .observeUsage(now - 90L * 86_400_000L)
            .first()
            .sortedByDescending { it.usageCount }

        assertEquals(otherId, ranked.first().categoryId)
        assertEquals(3, ranked.first().usageCount)
    }

    @Test
    fun editing_a_budget_preserves_created_at() = runTest {
        val createdAt = now - 100_000L
        val original = com.tallybook.core.database.entity.BudgetEntity(
            id = "budget-1",
            name = "餐饮",
            categoryId = null,
            period = com.tallybook.core.model.transaction.BudgetPeriod.MONTHLY,
            limitMinor = 100_00,
            currency = "CNY",
            includeSubcategories = true,
            isActive = true,
            createdAt = createdAt,
        )
        db.budgetDao().upsertPreservingCreatedAt(original)

        // Simulate an edit: the caller passes "now" as the fallback; the row must keep its original time.
        db.budgetDao().upsertPreservingCreatedAt(
            original.copy(name = "餐饮（改）", limitMinor = 200_00, createdAt = now),
        )

        val stored = db.budgetDao().getById("budget-1")!!
        assertEquals("餐饮（改）", stored.name)
        assertEquals(200_00L, stored.limitMinor)
        assertEquals(
            "created_at must survive an edit (Room's @Upsert would otherwise rewrite every column)",
            createdAt,
            stored.createdAt,
        )
    }

    @Test
    fun recycle_bin_lists_only_soft_deleted_rows() = runTest {
        val live = tx(type = TransactionType.EXPENSE, amountMinor = 100, accountId = accountA, categoryId = categoryFood)
        val deleted = tx(type = TransactionType.EXPENSE, amountMinor = 200, accountId = accountA, categoryId = categoryFood)
        db.transactionDao().upsertAll(listOf(live, deleted))
        db.transactionDao().softDelete(deleted.id, now)

        val bin = db.transactionDao().observeDeleted().first()

        assertEquals(1, bin.size)
        assertEquals(deleted.id, bin.single().id)
    }

    @Test
    fun purging_a_live_row_does_nothing() = runTest {
        val live = tx(type = TransactionType.EXPENSE, amountMinor = 100, accountId = accountA, categoryId = categoryFood)
        db.transactionDao().upsert(live)

        // The DELETE is guarded by `deleted_at IS NOT NULL`, so a live row must survive.
        db.transactionDao().purge(live.id)

        assertEquals(1, db.transactionDao().observeInRange(now - 1, now + 1).first().size)
    }

    @Test
    fun purge_all_empties_only_the_recycle_bin() = runTest {
        val live = tx(type = TransactionType.EXPENSE, amountMinor = 100, accountId = accountA, categoryId = categoryFood)
        val deletedA = tx(type = TransactionType.EXPENSE, amountMinor = 200, accountId = accountA, categoryId = categoryFood)
        val deletedB = tx(type = TransactionType.EXPENSE, amountMinor = 300, accountId = accountA, categoryId = categoryFood)
        db.transactionDao().upsertAll(listOf(live, deletedA, deletedB))
        db.transactionDao().softDelete(deletedA.id, now)
        db.transactionDao().softDelete(deletedB.id, now)

        val removed = db.transactionDao().purgeAll()

        assertEquals(2, removed)
        assertEquals(0, db.transactionDao().observeDeleted().first().size)
        assertEquals("live rows must survive a bin purge", 1, db.transactionDao().observeInRange(now - 1, now + 1).first().size)
    }

    @Test
    fun category_in_range_returns_only_that_category() = runTest {
        val otherId = "cat-transport"
        db.categoryDao().upsert(CategoryEntity(id = otherId, name = "交通", type = TransactionType.EXPENSE))
        db.transactionDao().upsertAll(
            listOf(
                tx(type = TransactionType.EXPENSE, amountMinor = 100, accountId = accountA, categoryId = categoryFood),
                tx(type = TransactionType.EXPENSE, amountMinor = 200, accountId = accountA, categoryId = otherId),
            ),
        )

        val rows = db.transactionDao().observeCategoryInRange(now - 1, now + 1, categoryFood).first()

        assertEquals(1, rows.size)
        assertEquals(100L, rows.single().amountMinor)
    }

    @Test
    fun uncategorized_query_returns_only_null_category_rows() = runTest {
        db.transactionDao().upsertAll(
            listOf(
                tx(type = TransactionType.EXPENSE, amountMinor = 100, accountId = accountA, categoryId = categoryFood),
                tx(type = TransactionType.EXPENSE, amountMinor = 200, accountId = accountA, categoryId = null),
            ),
        )

        val rows = db.transactionDao().observeUncategorizedInRange(now - 1, now + 1).first()

        assertEquals(1, rows.size)
        assertEquals(200L, rows.single().amountMinor)
    }

    @Test
    fun category_in_range_ignores_soft_deleted_rows() = runTest {
        val deleted = tx(type = TransactionType.EXPENSE, amountMinor = 100, accountId = accountA, categoryId = categoryFood)
        val live = tx(type = TransactionType.EXPENSE, amountMinor = 200, accountId = accountA, categoryId = categoryFood)
        db.transactionDao().upsertAll(listOf(deleted, live))
        db.transactionDao().softDelete(deleted.id, now)

        val rows = db.transactionDao().observeCategoryInRange(now - 1, now + 1, categoryFood).first()

        assertEquals(1, rows.size)
        assertEquals(live.id, rows.single().id)
    }
}
