package com.tallybook.core.model.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * These tests exist because money math is the one area of a bookkeeping app where a silent bug
 * destroys user trust: a total that is off by a cent is indistinguishable from theft.
 */
class MoneyTest {

    @Test
    fun `parsing decimal string keeps exact minor units`() {
        val money = Money.parse("12.34", CurrencyCode.CNY)
        assertEquals(1234L, money.minorUnits)
    }

    @Test
    fun `parsing does not accumulate binary float error`() {
        // 0.1 + 0.2 in Double is 0.30000000000000004; as minor units it must be exactly 30.
        val sum = Money.parse("0.10", CurrencyCode.CNY)
            .plus(Money.parse("0.20", CurrencyCode.CNY))
        assertEquals(30L, sum.minorUnits)
    }

    @Test
    fun `summing a thousand cents is exact`() {
        val oneCent = Money(1, CurrencyCode.CNY)
        var total = Money.ZERO_CNY
        repeat(1000) { total = total.plus(oneCent) }
        assertEquals(1000L, total.minorUnits)
        assertEquals(Money.parse("10.00", CurrencyCode.CNY), total)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `adding different currencies is rejected`() {
        Money(100, CurrencyCode.CNY).plus(Money(100, CurrencyCode.USD))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `comparing different currencies is rejected`() {
        Money(100, CurrencyCode.CNY).compareTo(Money(200, CurrencyCode.USD))
    }

    @Test
    fun `currency fraction digits drive display scale`() {
        // JPY has zero fraction digits: ¥1235, not ¥12.35.
        val yen = Money.fromMajorUnits(1235.0, CurrencyCode("JPY"))
        assertEquals(1235L, yen.minorUnits)
        assertEquals(0, CurrencyCode("JPY").fractionDigits)
    }

    @Test
    fun `rounding uses HALF_UP at the currency scale`() {
        val rounded = Money.fromMajorUnits(1.005, CurrencyCode.CNY)
        assertEquals(101L, rounded.minorUnits)
    }

    @Test
    fun `sign transitions are explicit`() {
        val expense = Money(500, CurrencyCode.CNY)
        assertFalse(expense.isNegative)
        assertTrue(expense.negate().isNegative)
        assertTrue(Money(0, CurrencyCode.CNY).isZero)
    }

    @Test
    fun `subtraction to exactly zero is zero not negative zero`() {
        val result = Money(100, CurrencyCode.CNY).minus(Money(100, CurrencyCode.CNY))
        assertTrue(result.isZero)
        assertEquals(0L, result.minorUnits)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `currency code rejects lowercase`() {
        CurrencyCode("cny")
    }

    @Test
    fun `format uses a stable currency symbol regardless of device locale`() {
        val money = Money(12345, CurrencyCode.CNY)
        // The English locale must NOT produce "CN¥": the ledger renders the same on every device.
        assertEquals("¥123.45", money.format(java.util.Locale.US))
        assertEquals("¥123.45", money.format(java.util.Locale.CHINA))
    }

    @Test
    fun `format keeps the currency's own fraction digits`() {
        // JPY has zero fraction digits but normal grouping.
        assertEquals("¥1,235", Money(1235, CurrencyCode("JPY")).format(java.util.Locale.US))
        assertEquals("$12.05", Money(1205, CurrencyCode.USD).format(java.util.Locale.US))
    }

    @Test
    fun `constructing an amount beyond the supported range is rejected`() {
        val result = runCatching {
            Money(Money.MAX_ABS_MINOR_UNITS + 1, CurrencyCode.CNY)
        }
        assertTrue(result.isFailure)
    }

    @Test
    fun `parsing an overflowing major unit amount is rejected`() {
        val result = runCatching {
            Money.parse("999999999999999999999999", CurrencyCode.CNY)
        }
        assertTrue(result.isFailure)
    }
}
