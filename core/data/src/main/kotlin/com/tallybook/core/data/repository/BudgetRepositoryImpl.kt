package com.tallybook.core.data.repository

import com.tallybook.core.common.clock.Clock
import com.tallybook.core.common.clock.DateProvider
import com.tallybook.core.data.mapper.toDomain
import com.tallybook.core.data.mapper.toEntity
import com.tallybook.core.database.dao.BudgetDao
import com.tallybook.core.database.dao.CategoryDao
import com.tallybook.core.database.dao.TransactionDao
import com.tallybook.core.model.money.CurrencyCode
import com.tallybook.core.model.money.Money
import com.tallybook.core.model.stats.BudgetProgress
import com.tallybook.core.model.stats.TimeGranularity
import com.tallybook.core.model.transaction.Budget
import com.tallybook.core.model.transaction.BudgetPeriod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Budget evaluation.
 *
 * Design notes worth keeping:
 *
 * 1. **Weekly and monthly are the only user-facing periods**, but the period→range mapping is
 *    centralised here so adding YEARLY later is one `when` branch, not a schema change.
 *
 * 2. **Progress is evaluated at a fixed `nowMillis`**, passed in rather than read from the clock
 *    inside the flow. A Flow that reads the clock per emission produces different values on
 *    recomposition for the same underlying data, which makes the UI flicker and the tests flaky.
 *    Callers re-invoke with a new `now` (e.g. on resume or at midnight).
 *
 * 3. **Category subtrees are resolved with a single recursive query per budget**, not per
 *    subcategory. Budget screens with 5+ category budgets would otherwise issue N+1 queries on
 *    every emission.
 *
 * 4. **Projection is deliberately conservative**: `projectedOverrun` is null for the first
 *    [MIN_DAYS_FOR_PROJECTION] days of a period, because extrapolating 2 days of spending to a month
 *    produces alarming and useless numbers.
 */
@Singleton
class BudgetRepositoryImpl @Inject constructor(
    private val budgetDao: BudgetDao,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val dateProvider: DateProvider,
    private val clock: Clock,
    private val zoneId: ZoneId,
) : BudgetRepository {

    override fun observeActive(): Flow<List<Budget>> =
        budgetDao.observeActive().map { rows -> rows.map { it.toDomain() } }

    override suspend fun upsert(budget: Budget) {
        // created_at is preserved for existing rows by the DAO (Room's @Upsert would otherwise
        // overwrite it on every edit — see RISKS B3).
        budgetDao.upsertPreservingCreatedAt(budget.toEntity(createdAtFallback = clock.nowMillis()))
    }

    override suspend fun deactivate(id: String) {
        budgetDao.deactivate(id)
    }

    override suspend fun delete(id: String) {
        budgetDao.delete(id)
    }

    override fun observeProgress(nowMillis: Long): Flow<List<BudgetProgress>> =
        budgetDao.observeActive().map { budgets ->
            budgets.map { entity -> evaluate(entity.toDomain(), nowMillis) }
        }

    private suspend fun evaluate(budget: Budget, nowMillis: Long): BudgetProgress {
        val granularity = when (budget.period) {
            BudgetPeriod.WEEKLY -> TimeGranularity.WEEK
            BudgetPeriod.MONTHLY -> TimeGranularity.MONTH
            // YEARLY/CUSTOM are not exposed in the UI yet; treating them as monthly keeps the
            // screen honest instead of crashing on an enum added ahead of its UI.
            BudgetPeriod.YEARLY -> TimeGranularity.YEAR
            BudgetPeriod.CUSTOM -> TimeGranularity.MONTH
        }

        val range = dateProvider.rangeFor(granularity, nowMillis)
        val endExclusive = range.last + 1

        // Resolve the category set once per budget: either "all categories" or the explicit
        // subtree. `categoryId` is captured into a local first because it is a nullable property
        // on a type from another module, which Kotlin cannot smart-cast.
        val budgetCategoryId = budget.categoryId
        val categoryIds: List<String> = when {
            budgetCategoryId == null -> emptyList()
            budget.includeSubcategories -> categoryDao.subtreeIds(budgetCategoryId)
            else -> listOf(budgetCategoryId)
        }

        val spentMinor = transactionDao.spentInPeriod(
            fromInclusive = range.first,
            toExclusive = endExclusive,
            categoryId = budgetCategoryId,
            categoryIds = categoryIds,
        )

        val spent = Money(spentMinor, budget.limit.currency)
        val daysTotal = dateProvider.daysBetweenInclusive(range.first, endExclusive)
        val daysElapsed = dateProvider.daysElapsed(range.first, endExclusive, nowMillis)

        return BudgetProgress(
            budgetId = budget.id,
            name = budget.name,
            period = budget.period,
            limit = budget.limit,
            spent = spent,
            periodStartMillis = range.first,
            periodEndExclusiveMillis = endExclusive,
            daysElapsed = daysElapsed,
            daysTotal = daysTotal,
            projectedOverrun = projectOverrun(spent, budget.limit, daysElapsed, daysTotal),
        )
    }

    /**
     * Extrapolates period-end spend at the current daily rate and returns the projected overrun,
     * or null when the projection is not meaningful (very early in the period, or no overrun).
     */
    private fun projectOverrun(
        spent: Money,
        limit: Money,
        daysElapsed: Int,
        daysTotal: Int,
    ): Money? {
        if (daysElapsed < MIN_DAYS_FOR_PROJECTION || daysTotal <= 0) return null
        if (spent.isZero) return null

        val projectedMinor = BigDecimal.valueOf(spent.minorUnits)
            .multiply(BigDecimal.valueOf(daysTotal.toLong()))
            .divide(BigDecimal.valueOf(daysElapsed.toLong()), 0, RoundingMode.HALF_UP)
            .toLong()

        if (projectedMinor <= limit.minorUnits) return null
        return Money(projectedMinor - limit.minorUnits, limit.currency)
    }

    private companion object {
        /** Below this many elapsed days, extrapolation is noise rather than signal. */
        const val MIN_DAYS_FOR_PROJECTION = 3
    }
}
