package com.tallybook.core.model.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Amount parsing tests.
 *
 * A wrong parse here does not crash anything — it saves a wrong initial balance, and every balance
 * the app shows afterwards is off. So the tests cover what people actually type, including the
 * formatting they copy from a bank app.
 */
class AmountParserTest {

    @Test
    fun `plain integer`() {
        assertEquals(100_000L, AmountParser.parseMinorUnits("1000"))
    }

    @Test
    fun `one decimal place`() {
        assertEquals(1_250L, AmountParser.parseMinorUnits("12.5"))
    }

    @Test
    fun `two decimal places`() {
        assertEquals(12_050L, AmountParser.parseMinorUnits("120.50"))
    }

    @Test
    fun `negative for a credit card`() {
        assertEquals(-3_000L, AmountParser.parseMinorUnits("-30"))
    }

    @Test
    fun `empty means zero`() {
        assertEquals(0L, AmountParser.parseMinorUnits(""))
        assertEquals(0L, AmountParser.parseMinorUnits("   "))
    }

    @Test
    fun `thousands separator is rejected rather than guessed`() {
        assertNull(AmountParser.parseMinorUnits("1,000"))
    }

    @Test
    fun `currency symbol is rejected`() {
        assertNull(AmountParser.parseMinorUnits("¥100"))
    }

    @Test
    fun `letters are rejected`() {
        assertNull(AmountParser.parseMinorUnits("abc"))
        assertNull(AmountParser.parseMinorUnits("12a"))
    }

    @Test
    fun `trailing dot is read as a whole number`() {
        // Found by the test itself: BigDecimal accepts "12." and returns 12. That is the friendlier
        // behaviour for a balance field — someone who typed "12." and hit save meant 12.00, and
        // rejecting it would look like the app is broken.
        assertEquals(1_200L, AmountParser.parseMinorUnits("12."))
    }

    @Test
    fun `more decimals than the currency allows are rounded not rejected`() {
        assertEquals(1_235L, AmountParser.parseMinorUnits("12.345"))
    }

    @Test
    fun `absurdly large input does not wrap around`() {
        assertNull(AmountParser.parseMinorUnits("999999999999999999999"))
        assertNull(AmountParser.parseMinorUnits("100000000001"))
    }

    @Test
    fun `a currency with no fraction digits`() {
        assertEquals(1_000L, AmountParser.parseMinorUnits("1000", fractionDigits = 0))
    }
}
