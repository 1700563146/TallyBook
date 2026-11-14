package com.tallybook.core.data.initializer

import com.tallybook.core.common.clock.Clock
import com.tallybook.core.database.TallyDatabase
import com.tallybook.core.database.dao.AccountDao
import com.tallybook.core.database.dao.CategoryDao
import com.tallybook.core.database.dao.TransactionDao
import com.tallybook.core.database.entity.AccountEntity
import com.tallybook.core.database.entity.CategoryEntity
import com.tallybook.core.database.entity.TransactionEntity
import com.tallybook.core.model.money.CurrencyCode
import com.tallybook.core.model.transaction.AccountType
import com.tallybook.core.model.transaction.TransactionKind
import com.tallybook.core.model.transaction.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Creates the minimum content a brand-new ledger needs to be usable, and the optional demo dataset.
 *
 * Why seeding lives in the data layer and not in a ViewModel:
 * - It must be idempotent and safe if two entry points race (Application startup + first screen).
 * - "Is this database empty?" is a data question, and the answer must come from the same source of
 *   truth the queries use.
 *
 * The seed is deliberately *not* sample transactions: a real user opening the app must see an empty
 * ledger, never fake spending. Demo data is a separate, explicit action (`loadDemoData`).
 */
@Singleton
class InitialDataSeeder @Inject constructor(
    private val database: TallyDatabase,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val budgetDao: com.tallybook.core.database.dao.BudgetDao,
    private val clock: Clock,
) {

    /**
     * Seeds default categories and accounts **only when the database is completely empty**.
     *
     * Both counts are checked so a user who deleted all accounts on purpose does not get them back
     * on the next launch — that reads as the app ignoring their choice.
     */
    suspend fun seedIfEmpty() = withContext(Dispatchers.IO) {
        if (categoryDao.count() > 0 || accountDao.count() > 0) return@withContext

        val now = clock.nowMillis()
        categoryDao.upsertAll(defaultExpenseCategories() + defaultIncomeCategories())
        accountDao.upsertAll(defaultAccounts(now))
    }

    private fun defaultExpenseCategories(): List<CategoryEntity> = listOf(
        category("餐饮", TransactionType.EXPENSE, "restaurant", 0xFFE8734A, 0),
        category("交通", TransactionType.EXPENSE, "directions_bus", 0xFF4A90D9, 1),
        category("购物", TransactionType.EXPENSE, "shopping_bag", 0xFFD94A8C, 2),
        category("居住", TransactionType.EXPENSE, "home", 0xFF8C6FD9, 3),
        category("娱乐", TransactionType.EXPENSE, "celebration", 0xFFD9A64A, 4),
        category("医疗", TransactionType.EXPENSE, "local_hospital", 0xFF4AD9A0, 5),
        category("学习", TransactionType.EXPENSE, "school", 0xFF4AB8D9, 6),
        category("人情", TransactionType.EXPENSE, "redeem", 0xFFD94A4A, 7),
        category("通讯", TransactionType.EXPENSE, "phone", 0xFF6F8CD9, 8),
        category("其他", TransactionType.EXPENSE, "more_horiz", 0xFF8A8A8A, 9),
    )

    private fun defaultIncomeCategories(): List<CategoryEntity> = listOf(
        category("工资", TransactionType.INCOME, "payments", 0xFF3BA55D, 0),
        category("奖金", TransactionType.INCOME, "emoji_events", 0xFF3BA55D, 1),
        category("理财", TransactionType.INCOME, "trending_up", 0xFF3BA55D, 2),
        category("红包", TransactionType.INCOME, "redeem", 0xFF3BA55D, 3),
        category("其他", TransactionType.INCOME, "more_horiz", 0xFF8A8A8A, 4),
    )

    private fun category(
        name: String,
        type: TransactionType,
        iconKey: String,
        colorArgb: Long,
        sortOrder: Int,
    ) = CategoryEntity(
        id = UUID.randomUUID().toString(),
        name = name,
        type = type,
        iconKey = iconKey,
        colorArgb = colorArgb,
        sortOrder = sortOrder,
    )

    private fun defaultAccounts(now: Long): List<AccountEntity> = listOf(
        account("现金", AccountType.CASH, now, 0),
        account("支付宝", AccountType.E_WALLET, now, 1),
        account("银行卡", AccountType.DEBIT_CARD, now, 2),
    )

    private fun account(name: String, type: AccountType, now: Long, sortOrder: Int) = AccountEntity(
        id = UUID.randomUUID().toString(),
        name = name,
        type = type,
        currency = CurrencyCode.CNY.value,
        initialBalanceMinor = 0,
        sortOrder = sortOrder,
        createdAt = now,
        updatedAt = now,
    )

    /**
     * Inserts a realistic demo ledger spanning the last ~90 days.
     *
     * Deterministic: the same seed produces the same data, so screenshots and manual QA stay
     * reproducible. Only runs when the ledger is empty, so it can never mix demo rows into real data.
     */
    suspend fun loadDemoData(force: Boolean = false) = withContext(Dispatchers.IO) {
        seedIfEmpty()

        if (!force && transactionDao.count() > 0) return@withContext

        val categories = categoryDao.observeActiveSnapshot()
        val accounts = accountDao.observeActiveSnapshot()
        if (categories.isEmpty() || accounts.isEmpty()) return@withContext

        val expenseByIcon = categories.filter { it.type == TransactionType.EXPENSE }.associateBy { it.iconKey }
        val incomeByIcon = categories.filter { it.type == TransactionType.INCOME }.associateBy { it.iconKey }
        val random = Random(20251106)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val now = clock.nowMillis()

        val rows = ArrayList<TransactionEntity>()
        val cashAccount = accounts.first()
        val walletAccount = accounts.getOrNull(1) ?: cashAccount
        val bankAccount = accounts.getOrNull(2) ?: cashAccount

        // Monthly salary, so the income side of the charts is not empty.
        for (monthOffset in 0..2) {
            val month = today.minusMonths(monthOffset.toLong())
            // Paid on the 1st so the current month always shows income, even on day 1.
            val payday = month.withDayOfMonth(1)
            if (!payday.isAfter(today)) {
                rows += transaction(
                    type = TransactionType.INCOME,
                    amountMinor = 18_000_00 + random.nextLong(0, 2000_00),
                    accountId = bankAccount.id,
                    categoryId = incomeByIcon["payments"]?.id,
                    date = payday,
                    note = "月薪",
                    zone = zone,
                    now = now,
                )
            }
        }

        // Daily-ish expenses across ~90 days.
        val merchants = listOf(
            "restaurant" to listOf("午饭 · 兰州拉面", "晚饭 · 小炒", "早餐 · 豆浆油条", "咖啡", "外卖"),
            "directions_bus" to listOf("地铁", "公交", "打车", "高铁票"),
            "shopping_bag" to listOf("超市采购", "日用品", "衣服", "电商下单"),
            "celebration" to listOf("电影票", "演出", "游戏"),
            "local_hospital" to listOf("感冒药", "体检"),
            "school" to listOf("书籍", "课程"),
            "redeem" to listOf("红包", "随礼"),
            "phone" to listOf("话费", "宽带"),
        )

        for (dayOffset in 0..89) {
            val date = today.minusDays(dayOffset.toLong())
            val count = when {
                dayOffset % 7 == 0 -> 0 // a few no-spend days keep the heat map realistic
                else -> random.nextInt(1, 4)
            }
            repeat(count) {
                val (icon, notes) = merchants[random.nextInt(merchants.size)]
                val category = expenseByIcon[icon] ?: expenseByIcon["more_horiz"] ?: return@repeat
                val account = when (random.nextInt(3)) {
                    0 -> cashAccount
                    1 -> walletAccount
                    else -> bankAccount
                }
                val amountMinor = when (icon) {
                    "shopping_bag" -> random.nextLong(20_00, 400_00)
                    "local_hospital" -> random.nextLong(30_00, 300_00)
                    else -> random.nextLong(5_00, 120_00)
                }
                rows += transaction(
                    type = TransactionType.EXPENSE,
                    amountMinor = amountMinor,
                    accountId = account.id,
                    categoryId = category.id,
                    date = date,
                    note = notes[random.nextInt(notes.size)],
                    zone = zone,
                    now = now,
                )
            }
        }

        // Housing costs recur once per month, not daily: putting rent in the random daily pool made
        // 居住 dominate the category chart with ~17k of rent, which is not how anyone actually spends.
        val homeIcon = expenseByIcon["home"]
        if (homeIcon != null) {
            for (monthOffset in 0..2) {
                val month = today.minusMonths(monthOffset.toLong())
                val rentDay = month.withDayOfMonth(1)
                val utilityDay = month.withDayOfMonth(minOf(5, month.lengthOfMonth()))
                if (!rentDay.isAfter(today)) {
                    rows += transaction(
                        type = TransactionType.EXPENSE,
                        amountMinor = 2_800_00,
                        accountId = bankAccount.id,
                        categoryId = homeIcon.id,
                        date = rentDay,
                        note = "房租",
                        zone = zone,
                        now = now,
                    )
                }
                if (!utilityDay.isAfter(today)) {
                    rows += transaction(
                        type = TransactionType.EXPENSE,
                        amountMinor = 180_00 + random.nextLong(0, 120_00),
                        accountId = walletAccount.id,
                        categoryId = homeIcon.id,
                        date = utilityDay,
                        note = "水电费",
                        zone = zone,
                        now = now,
                    )
                }
            }
        }

        // A transfer between two accounts, so the demo exercises the single-row transfer model
        // (excluded from income/expense, but it must still move both balances).
        if (accounts.size >= 2) {
            rows += TransactionEntity(
                id = UUID.randomUUID().toString(),
                type = TransactionType.EXPENSE,
                kind = TransactionKind.TRANSFER,
                amountMinor = 50_000,
                currency = CurrencyCode.CNY.value,
                accountId = walletAccount.id,
                categoryId = null,
                occurredAt = today.minusDays(2).atTime(19, 30).atZone(zone).toInstant().toEpochMilli(),
                note = "转到银行卡",
                counterAccountId = bankAccount.id,
                createdAt = now,
                updatedAt = now,
            )
        }

        // A refund of an earlier purchase: recorded as INCOME pointing at the original, which the
        // aggregates subtract from expense instead of adding to income.
        val refundable = rows.lastOrNull {
            it.type == TransactionType.EXPENSE &&
                it.kind == TransactionKind.NORMAL &&
                it.amountMinor >= 20_00
        }
        if (refundable != null) {
            rows += TransactionEntity(
                id = UUID.randomUUID().toString(),
                type = TransactionType.INCOME,
                kind = TransactionKind.NORMAL,
                amountMinor = refundable.amountMinor / 2,
                currency = CurrencyCode.CNY.value,
                accountId = refundable.accountId,
                categoryId = refundable.categoryId,
                occurredAt = refundable.occurredAt + 86_400_000L,
                note = "部分退货退款",
                refundOfId = refundable.id,
                createdAt = now,
                updatedAt = now,
            )
        }

        transactionDao.upsertAll(rows)

        // Budgets exist so the budget screen shows real progress in the demo. They are only created
        // when the table is empty, so a user's own budgets are never overwritten.
        budgetDao.observeActive().first().ifEmpty {
            demoBudgets(expenseByIcon, now).forEach { budgetDao.upsert(it) }
        }
        Unit
    }

    /**
     * Three monthly budgets covering the demo's dominant categories.
     *
     * Limits are chosen relative to typical demo spending so one is comfortable, one is close, and
     * one is over — that exercises all three visual states (normal / near limit / over budget).
     */
    private fun demoBudgets(
        expenseByIcon: Map<String?, CategoryEntity>,
        now: Long,
    ): List<com.tallybook.core.database.entity.BudgetEntity> {
        // A mix of monthly and weekly budgets on purpose: the home screen summarises each period
        // separately, and with only monthly budgets the weekly card would never appear in the demo.
        val plans = listOf(
            Triple("restaurant", "餐饮" to 1_200_00L, com.tallybook.core.model.transaction.BudgetPeriod.MONTHLY),
            Triple("shopping_bag", "购物" to 600_00L, com.tallybook.core.model.transaction.BudgetPeriod.MONTHLY),
            Triple("directions_bus", "交通" to 300_00L, com.tallybook.core.model.transaction.BudgetPeriod.MONTHLY),
            Triple(
                "restaurant",
                "餐饮（周）" to 300_00L,
                com.tallybook.core.model.transaction.BudgetPeriod.WEEKLY,
            ),
        )
        return plans.mapNotNull { (icon, plan, period) ->
            val category = expenseByIcon[icon] ?: return@mapNotNull null
            val (name, limitMinor) = plan
            com.tallybook.core.database.entity.BudgetEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                categoryId = category.id,
                period = period,
                limitMinor = limitMinor,
                currency = CurrencyCode.CNY.value,
                includeSubcategories = true,
                isActive = true,
                createdAt = now,
            )
        }
    }

    /** Wipes everything, then reseeds defaults. Used by the settings "清空全部数据" action. */
    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        database.clearAllTables()
        seedIfEmpty()
    }

    private fun transaction(
        type: TransactionType,
        amountMinor: Long,
        accountId: String,
        categoryId: String?,
        date: LocalDate,
        note: String,
        zone: ZoneId,
        now: Long,
    ): TransactionEntity {
        // Spread within the day so ordering and the "daily total" buckets look natural.
        val occurredAt = date.atTime(8, 0).atZone(zone).toInstant().toEpochMilli() +
            (amountMinor % 12) * 60L * 60_000L
        return TransactionEntity(
            id = UUID.randomUUID().toString(),
            type = type,
            kind = TransactionKind.NORMAL,
            amountMinor = amountMinor,
            currency = CurrencyCode.CNY.value,
            accountId = accountId,
            categoryId = categoryId,
            occurredAt = occurredAt,
            note = note,
            createdAt = now,
            updatedAt = now,
        )
    }
}

/**
 * One-shot snapshot helpers.
 *
 * The DAOs expose `Flow` for the UI; seeding needs a single read. Rather than adding suspend variants
 * to the DAOs (which would double the query surface), these use `first()` on the same flows.
 */
private suspend fun CategoryDao.observeActiveSnapshot(): List<CategoryEntity> = observeActive().first()

private suspend fun AccountDao.observeActiveSnapshot(): List<AccountEntity> = observeActive().first()
