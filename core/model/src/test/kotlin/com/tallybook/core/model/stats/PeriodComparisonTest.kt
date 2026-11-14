package com.tallybook.core.model.stats

import com.tallybook.core.model.money.CurrencyCode
import com.tallybook.core.model.money.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Period-over-period comparison tests.
 *
 * Every edge case here is a division-by-zero or a nonsense percentage waiting to happen; the screen
 * shows this number right under the headline total, so being wrong is very visible.
 */
class PeriodComparisonTest {

    private val cny = CurrencyCode.CNY
    private fun money(minor: Long) = Money(minor, cny)

    @Test
    fun `increase produces a positive ratio`() {
        val result = PeriodComparison.of(current = money(150), previous = money(100))
        assertEquals(ComparisonDirection.UP, result.direction)
        assertEquals(0.5f, result.ratio!!, 0.0001f)
        assertEquals(50L, result.differenceMinor)
    }

    @Test
    fun `decrease produces a negative ratio`() {
        val result = PeriodComparison.of(current = money(150), previous = money(200))
        assertEquals(ComparisonDirection.DOWN, result.direction)
        assertEquals(-0.25f, result.ratio!!, 0.0001f)
    }

    @Test
    fun `doubling is exactly one hundred percent`() {
        val result = PeriodComparison.of(current = money(200), previous = money(100))
        assertEquals(1f, result.ratio!!, 0.0001f)
    }

    @Test
    fun `equal amounts are unchanged`() {
        val result = PeriodComparison.of(current = money(100), previous = money(100))
        assertEquals(ComparisonDirection.SAME, result.direction)
        assertEquals(0f, result.ratio!!, 0.0001f)
        assertEquals(0L, result.differenceMinor)
    }

    @Test
    fun `spending nothing in both periods is unchanged not missing baseline`() {
        val result = PeriodComparison.of(current = money(0), previous = money(0))
        assertEquals(ComparisonDirection.SAME, result.direction)
        assertTrue(result.hasBaseline)
    }

    @Test
    fun `previous zero with current spending has no baseline`() {
        val result = PeriodComparison.of(current = money(500), previous = money(0))
        assertEquals(ComparisonDirection.NO_BASELINE, result.direction)
        assertNull("a percentage would be infinite", result.ratio)
        assertFalse(result.hasBaseline)
    }

    @Test
    fun `spending dropping to zero is a real minus one hundred percent`() {
        val result = PeriodComparison.of(current = money(0), previous = money(400))
        assertEquals(ComparisonDirection.DOWN, result.direction)
        assertEquals(-1f, result.ratio!!, 0.0001f)
    }

    @Test
    fun `one minor unit change is still detected`() {
        val result = PeriodComparison.of(current = money(10_001), previous = money(10_000))
        assertEquals(ComparisonDirection.UP, result.direction)
        assertEquals(1L, result.differenceMinor)
    }
}
