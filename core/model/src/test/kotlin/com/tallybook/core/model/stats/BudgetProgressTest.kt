package com.tallybook.core.model.stats

import com.tallybook.core.model.money.CurrencyCode
import com.tallybook.core.model.money.Money
import com.tallybook.core.model.transaction.BudgetPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Budget progress math.
 *
 * Budget arithmetic has three states that must not be confused: under budget, exactly at the limit,
 * and over budget. The UI renders all three differently, and "exactly at the limit" being reported
 * as an overrun (or silently clamped to look fine) is the kind of bug users report as "the app
 * doesn't trust my numbers".
 */
class BudgetProgressTest {

    private val cny = CurrencyCode.CNY

    private fun progress(
        limitMinor: Long,
        spentMinor: Long,
        daysElapsed: Int = 15,
        daysTotal: Int = 30,
    ) = BudgetProgress(
        budgetId = "b1",
        name = "餐饮",
        period = BudgetPeriod.MONTHLY,
        limit = Money(limitMinor, cny),
        spent = Money(spentMinor, cny),
        periodStartMillis = 0,
        periodEndExclusiveMillis = 1,
        daysElapsed = daysElapsed,
        daysTotal = daysTotal,
    )

    @Test
    fun `progress is the ratio of spent to limit`() {
        val p = progress(limitMinor = 300000, spentMinor = 150000)
        assertEquals(0.5f, p.progress, 0.001f)
    }

    @Test
    fun `progress is clamped at 1 when over budget`() {
        val p = progress(limitMinor = 100000, spentMinor = 150000)
        assertEquals(1f, p.progress, 0.001f)
        assertTrue(p.isOverBudget)
    }

    @Test
    fun `spending exactly the limit is not an overrun`() {
        val p = progress(limitMinor = 100000, spentMinor = 100000)
        assertFalse(p.isOverBudget)
        assertEquals(0L, p.overrun.minorUnits)
        assertEquals(0L, p.remaining.minorUnits)
        assertEquals(1f, p.progress, 0.001f)
    }

    @Test
    fun `overrun reports the exact excess`() {
        val p = progress(limitMinor = 100000, spentMinor = 132500)
        assertTrue(p.isOverBudget)
        assertEquals(32500L, p.overrun.minorUnits)
        assertEquals(-32500L, p.remaining.minorUnits)
    }

    @Test
    fun `zero limit with zero spend is not an overrun`() {
        val p = progress(limitMinor = 0, spentMinor = 0)
        assertFalse(p.isOverBudget)
        assertEquals(0f, p.progress, 0.001f)
    }

    @Test
    fun `zero limit with any spend is an overrun`() {
        val p = progress(limitMinor = 0, spentMinor = 100)
        assertTrue(p.isOverBudget)
    }

    @Test
    fun `time progress reflects elapsed fraction of the period`() {
        val p = progress(limitMinor = 100000, spentMinor = 1000, daysElapsed = 15, daysTotal = 30)
        assertEquals(0.5f, p.timeProgress, 0.001f)
    }

    @Test
    fun `time progress never exceeds one`() {
        val p = progress(limitMinor = 100000, spentMinor = 1000, daysElapsed = 40, daysTotal = 30)
        assertEquals(1f, p.timeProgress, 0.001f)
    }

    @Test
    fun `time progress handles a zero-length period without dividing by zero`() {
        val p = progress(limitMinor = 100000, spentMinor = 0, daysElapsed = 0, daysTotal = 0)
        assertEquals(0f, p.timeProgress, 0.001f)
    }

    @Test
    fun `projected overrun is null when not provided`() {
        assertNull(progress(100000, 1000).projectedOverrun)
    }
}
