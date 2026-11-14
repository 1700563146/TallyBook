package com.tallybook.core.data.repository

import com.tallybook.core.common.clock.Clock
import com.tallybook.core.data.mapper.toDomain
import com.tallybook.core.data.mapper.toEntity
import com.tallybook.core.database.dao.AccountDao
import com.tallybook.core.database.dao.CategoryDao
import com.tallybook.core.database.dao.TransactionDao
import com.tallybook.core.model.money.CurrencyCode
import com.tallybook.core.model.money.Money
import com.tallybook.core.model.transaction.Account
import com.tallybook.core.model.transaction.Category
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepositoryImpl @Inject constructor(
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao,
    private val clock: Clock,
) : AccountRepository {

    override fun observeActive(): Flow<List<Account>> =
        accountDao.observeActive().map { rows -> rows.map { it.toDomain() } }

    override fun observeAll(): Flow<List<Account>> =
        accountDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    /**
     * Balances as of [asOfExclusive].
     *
     * The DAO returns one row per account with the currency already resolved from the account, so
     * no conversion is needed here. A missing account (should not happen) is skipped rather than
     * invented with a zero balance.
     */
    override fun observeBalances(asOfExclusive: Long): Flow<Map<String, Money>> =
        transactionDao.observeAccountBalances(asOfExclusive).map { rows ->
            rows.associate { row ->
                row.accountId to Money(row.balanceMinor, CurrencyCode(row.currency))
            }
        }

    override suspend fun upsert(account: Account) {
        val now = clock.nowMillis()
        val stored = accountDao.getById(account.id)
        accountDao.upsert(
            account.copy(
                createdAt = stored?.createdAt ?: account.createdAt.takeIf { it > 0 } ?: now,
                updatedAt = now,
            ).toEntity(now),
        )
    }

    override suspend fun archive(id: String) {
        accountDao.archive(id, clock.nowMillis())
    }
}

@Singleton
class CategoryRepositoryImpl @Inject constructor(
    private val categoryDao: CategoryDao,
) : CategoryRepository {

    override fun observeAll(): Flow<List<Category>> =
        categoryDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeActive(): Flow<List<Category>> =
        categoryDao.observeActive().map { rows -> rows.map { it.toDomain() } }

    override fun observeUsage(sinceInclusive: Long): Flow<Map<String, Int>> =
        categoryDao.observeUsage(sinceInclusive).map { rows ->
            rows.associate { it.categoryId to it.usageCount }
        }

    override suspend fun upsert(category: Category) {
        categoryDao.upsert(category.toEntity())
    }

    override suspend fun archive(id: String) {
        // Re-parenting children is a deliberate, separate operation: archiving a parent that still
        // has children would orphan them. The FK is RESTRICT, so the DB would reject it anyway —
        // failing here gives a usable message instead of a SQLite constraint error.
        require(categoryDao.childCount(id) == 0) {
            "Cannot archive a category with child categories; move or delete them first"
        }
        categoryDao.archive(id)
    }
}
