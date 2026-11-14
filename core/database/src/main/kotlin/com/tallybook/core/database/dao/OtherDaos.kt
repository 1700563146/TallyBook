package com.tallybook.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.tallybook.core.database.entity.AccountEntity
import com.tallybook.core.database.entity.BudgetEntity
import com.tallybook.core.database.entity.CategoryEntity
import com.tallybook.core.database.model.CategoryUsageRow
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts WHERE is_archived = 0 ORDER BY sort_order ASC, name ASC")
    fun observeActive(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY sort_order ASC, name ASC")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: String): AccountEntity?

    @Upsert
    suspend fun upsert(account: AccountEntity)

    @Upsert
    suspend fun upsertAll(accounts: List<AccountEntity>)

    @Query("UPDATE accounts SET is_archived = 1, updated_at = :at WHERE id = :id")
    suspend fun archive(id: String, at: Long)

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int
}

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories WHERE is_archived = 0 ORDER BY sort_order ASC, name ASC")
    fun observeActive(): Flow<List<CategoryEntity>>

    /** Includes archived categories: historical rows still reference them and must show a name. */
    @Query("SELECT * FROM categories ORDER BY sort_order ASC, name ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE type = :type AND is_archived = 0 ORDER BY sort_order ASC")
    suspend fun getByType(type: String): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: String): CategoryEntity?

    /**
     * Usage counts since [sinceInclusive], used to order the category picker by habit.
     *
     * Counts every non-deleted row including transfers' counter legs (those have a null category, so
     * they are excluded by the `category_id IS NOT NULL` filter) and refunds (a refund is still an
     * interaction with that category).
     */
    @Query(
        """
        SELECT category_id AS categoryId, COUNT(*) AS usageCount
        FROM transactions
        WHERE deleted_at IS NULL
          AND category_id IS NOT NULL
          AND occurred_at >= :sinceInclusive
        GROUP BY category_id
        """,
    )
    fun observeUsage(sinceInclusive: Long): Flow<List<CategoryUsageRow>>

    @Query("SELECT COUNT(*) FROM categories WHERE parent_id = :parentId")
    suspend fun childCount(parentId: String): Int

    /**
     * Whole subtree of a category, for budgets that include subcategories.
     * Recursive CTE keeps this to one query regardless of depth (the UI caps depth at 2, but the
     * query should not encode that assumption).
     */
    @Query(
        """
        WITH RECURSIVE subtree(id) AS (
            SELECT id FROM categories WHERE id = :rootId
            UNION ALL
            SELECT c.id FROM categories c JOIN subtree s ON c.parent_id = s.id
        )
        SELECT id FROM subtree
        """,
    )
    suspend fun subtreeIds(rootId: String): List<String>

    @Upsert
    suspend fun upsert(category: CategoryEntity)

    @Upsert
    suspend fun upsertAll(categories: List<CategoryEntity>)

    @Query("UPDATE categories SET is_archived = 1 WHERE id = :id")
    suspend fun archive(id: String)

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int
}

@Dao
interface BudgetDao {

    @Query("SELECT * FROM budgets WHERE is_active = 1 ORDER BY name ASC")
    fun observeActive(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets ORDER BY name ASC")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun getById(id: String): BudgetEntity?

    @Upsert
    suspend fun upsert(budget: BudgetEntity)

    /**
     * Inserts a new budget, or updates an existing one **without resetting `created_at`**.
     *
     * Room's `@Upsert` falls back to updating *every* column on conflict, so a plain upsert rewrites
     * `created_at` with whatever the caller passed (usually "now") — an edit silently rewrites the
     * creation time. Reading the stored value and putting it back is the cheapest correct fix, and
     * doing it inside `@Transaction` keeps read+write atomic.
     */
    @Transaction
    suspend fun upsertPreservingCreatedAt(budget: BudgetEntity) {
        val storedCreatedAt = getById(budget.id)?.createdAt
        upsert(if (storedCreatedAt != null) budget.copy(createdAt = storedCreatedAt) else budget)
    }

    @Query("UPDATE budgets SET is_active = 0 WHERE id = :id")
    suspend fun deactivate(id: String)

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun delete(id: String)
}
