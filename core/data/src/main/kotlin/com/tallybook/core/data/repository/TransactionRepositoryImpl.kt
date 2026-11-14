package com.tallybook.core.data.repository

import com.tallybook.core.common.clock.Clock
import com.tallybook.core.data.mapper.toDomain
import com.tallybook.core.data.mapper.toEntity
import com.tallybook.core.database.dao.AccountDao
import com.tallybook.core.database.dao.CategoryDao
import com.tallybook.core.database.dao.TransactionDao
import com.tallybook.core.database.entity.TransactionTagEntity
import com.tallybook.core.database.model.TransactionSearchRow
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.tallybook.core.model.money.CurrencyCode
import com.tallybook.core.model.money.Money
import com.tallybook.core.model.transaction.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ledger reads/writes.
 *
 * There is no currency conversion anywhere in this class — the ledger is single-currency by product
 * decision, which removes the rate lookup, the "missing rate" null state, and the class of bugs where
 * rows silently drop out of a total.
 */
@Singleton
class TransactionRepositoryImpl @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val accountDao: AccountDao,
    private val clock: Clock,
) : TransactionRepository {

    override fun observeInRange(fromInclusive: Long, toExclusive: Long): Flow<List<Transaction>> =
        transactionDao.observeInRange(fromInclusive, toExclusive)
            .map { rows -> rows.map { it.toDomain() } }

    /**
     * Resolves names by combining three flows instead of joining in SQL.
     *
     * `observeAll()` is used deliberately (not `observeActive()`): an archived category still owns
     * historical rows, and showing its real name beats showing "已删除分类".
     */
    override fun observeListItems(
        fromInclusive: Long,
        toExclusive: Long,
    ): Flow<List<TransactionListItem>> = combine(
        transactionDao.observeInRange(fromInclusive, toExclusive),
        categoryDao.observeAll(),
        accountDao.observeAll(),
    ) { rows, categories, accounts -> rows.withNames(categories, accounts) }

    override fun observeSearchResults(
        filter: TransactionSearchFilter,
    ): Flow<PagingData<TransactionListItem>> = Pager(
        config = PagingConfig(
            pageSize = SEARCH_PAGE_SIZE,
            prefetchDistance = SEARCH_PAGE_SIZE / 2,
            enablePlaceholders = false,
        ),
        pagingSourceFactory = {
            transactionDao.search(
                pattern = filter.query.trim()
                    .takeIf { it.isNotEmpty() }
                    ?.let { "%${escapeLikePattern(it)}%" },
                entryType = filter.entryType.name,
                accountId = filter.accountId,
                categoryId = filter.categoryId,
            )
        },
    ).flow.map { page ->
        page.map(TransactionSearchRow::toListItem)
    }

    override fun observeDeletedItems(): Flow<List<TransactionListItem>> = combine(
        transactionDao.observeDeleted(),
        categoryDao.observeAll(),
        accountDao.observeAll(),
    ) { rows, categories, accounts -> rows.withNames(categories, accounts) }

    override fun observeCategoryItems(
        categoryId: String?,
        fromInclusive: Long,
        toExclusive: Long,
    ): Flow<List<TransactionListItem>> = combine(
        if (categoryId == null) {
            transactionDao.observeUncategorizedInRange(fromInclusive, toExclusive)
        } else {
            transactionDao.observeCategoryInRange(fromInclusive, toExclusive, categoryId)
        },
        categoryDao.observeAll(),
        accountDao.observeAll(),
    ) { rows, categories, accounts -> rows.withNames(categories, accounts) }

    override suspend fun purge(id: String) {
        transactionDao.purge(id)
    }

    override suspend fun purgeAll(): Int = transactionDao.purgeAll()

    /**
     * Resolves category/account names for a list of rows.
     *
     * Shared by the ledger and the recycle bin so the two can never disagree about how a row is
     * labelled — an archived category, for instance, keeps its real name in both places.
     */
    private fun List<com.tallybook.core.database.entity.TransactionEntity>.withNames(
        categories: List<com.tallybook.core.database.entity.CategoryEntity>,
        accounts: List<com.tallybook.core.database.entity.AccountEntity>,
    ): List<TransactionListItem> {
        val categoryById = categories.associateBy { it.id }
        val accountNames = accounts.associate { it.id to it.name }

        return map { row ->
            val category = row.categoryId?.let { categoryById[it] }
            TransactionListItem(
                transaction = row.toDomain(),
                categoryName = when {
                    row.categoryId == null -> UNCATEGORIZED
                    else -> category?.name ?: DELETED
                },
                accountName = accountNames[row.accountId] ?: DELETED,
                categoryIconKey = category?.iconKey,
                categoryColorArgb = category?.colorArgb,
                counterAccountName = row.counterAccountId?.let { accountNames[it] },
            )
        }
    }

    override suspend fun countAll(): Int = transactionDao.count()

    override suspend fun getAllForBackup(): List<Transaction> {
        val tagsByTransaction = transactionDao.getAllTags()
            .groupBy(TransactionTagEntity::transactionId, TransactionTagEntity::tag)
        return transactionDao.getAllForBackup().map { row ->
            row.toDomain(tagsByTransaction[row.id].orEmpty())
        }
    }

    override suspend fun getPage(
        query: TransactionQuery,
        limit: Int,
        offset: Int,
    ): List<Transaction> = transactionDao.getPage(
        fromInclusive = query.fromInclusive,
        toExclusive = query.toExclusive,
        accountId = query.accountId,
        categoryId = query.categoryId,
        type = query.type?.name,
        query = query.text?.takeIf { it.isNotBlank() },
        limit = limit,
        offset = offset,
    ).map { it.toDomain() }

    override suspend fun getById(id: String): Transaction? = transactionDao.getById(id)?.toDomain()

    override suspend fun upsert(transaction: Transaction) {
        val now = clock.nowMillis()
        require(transaction.occurredAt > 0) { "occurredAt must be a real timestamp" }
        transactionDao.upsert(transaction.copy(updatedAt = now).toEntity())
    }

    override suspend fun delete(id: String) {
        transactionDao.softDelete(id, clock.nowMillis())
    }

    override suspend fun restore(id: String) {
        transactionDao.restore(id, clock.nowMillis())
    }

    override fun observePeriodTotals(fromInclusive: Long, toExclusive: Long): Flow<PeriodTotals> =
        transactionDao.observePeriodTotals(fromInclusive, toExclusive).map { row ->
            PeriodTotals(
                expense = Money(row.expenseMinor, LEDGER_CURRENCY),
                income = Money(row.incomeMinor, LEDGER_CURRENCY),
                count = row.count,
            )
        }

    companion object {
        /** One place to change if the app ever gains a "switch ledger currency" setting. */
        val LEDGER_CURRENCY = CurrencyCode.CNY

        const val UNCATEGORIZED = "未分类"
        const val DELETED = "已删除"

        private const val SEARCH_PAGE_SIZE = 30
    }
}

private fun TransactionSearchRow.toListItem(): TransactionListItem = TransactionListItem(
    transaction = transaction.toDomain(),
    categoryName = when (transaction.categoryId) {
        null -> TransactionRepositoryImpl.UNCATEGORIZED
        else -> categoryName ?: TransactionRepositoryImpl.DELETED
    },
    accountName = accountName ?: TransactionRepositoryImpl.DELETED,
    categoryIconKey = categoryIconKey,
    categoryColorArgb = categoryColorArgb,
    counterAccountName = counterAccountName,
)

/**
 * Escapes the three characters SQLite treats specially in LIKE. The DAO supplies the escape clause;
 * doing this in Kotlin keeps raw user input out of SQL string construction.
 */
internal fun escapeLikePattern(value: String): String = buildString(value.length) {
    value.forEach { char ->
        if (char == '\\' || char == '%' || char == '_') append('\\')
        append(char)
    }
}
