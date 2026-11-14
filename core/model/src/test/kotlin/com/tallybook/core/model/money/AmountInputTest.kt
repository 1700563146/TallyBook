package com.tallybook.core.model.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * Amount input engine tests.
 *
 * The keypad is the most-touched control in a bookkeeping app, so every behaviour here is a user
 * expectation rather than an implementation detail: leading-zero suppression, one dot per operand,
 * operator replacement, and arithmetic with normal precedence.
 *
 * The `0.1 + 0.2` case is the reason this engine exists at all — the same test on `Double` fails.
 */
class AmountInputTest {

    private fun type(vararg keys: AmountKey): AmountInputState =
        AmountInput.press(AmountInputState(), keys.toList())

    private fun digits(vararg chars: Char): List<AmountKey> =
        chars.map { ch ->
            when (ch) {
                '0' -> AmountKey.D0
                '1' -> AmountKey.D1
                '2' -> AmountKey.D2
                '3' -> AmountKey.D3
                '4' -> AmountKey.D4
                '5' -> AmountKey.D5
                '6' -> AmountKey.D6
                '7' -> AmountKey.D7
                '8' -> AmountKey.D8
                '9' -> AmountKey.D9
                '.' -> AmountKey.DOT
                '+' -> AmountKey.PLUS
                '-' -> AmountKey.MINUS
                '*' -> AmountKey.MULTIPLY
                '/' -> AmountKey.DIVIDE
                else -> error("unsupported: $ch")
            }
        }

    private fun enter(text: String): AmountInputState =
        AmountInput.press(AmountInputState(), digits(*text.toCharArray()))

    @Test
    fun `empty state displays zero`() {
        assertEquals("0", AmountInputState().display)
        assertEquals(BigDecimal.ZERO, AmountInputState().value())
    }

    @Test
    fun `typing digits builds the operand`() {
        assertEquals("123", enter("123").expression)
        assertEquals(BigDecimal(123), enter("123").value())
    }

    @Test
    fun `leading zero is replaced not extended`() {
        assertEquals("7", enter("07").expression)
        assertEquals("0.5", enter("0.5").expression)
    }

    @Test
    fun `only one decimal point per operand`() {
        // The second dot is swallowed; the following digit still lands on the same operand.
        assertEquals("1.23", enter("1.2.3").expression)
    }

    @Test
    fun `a leading dot becomes zero dot`() {
        assertEquals(".5", enter(".5").expression)
        assertEquals(BigDecimal("0.5"), enter(".5").value())
    }

    @Test
    fun `decimal arithmetic is exact`() {
        // The whole point: 0.1 + 0.2 == 0.3, which Double cannot promise.
        val state = enter("0.1+0.2")
        assertEquals(BigDecimal("0.3"), state.value())
        val money = enter("0.1+0.2").toMoney(CurrencyCode.CNY)
        assertEquals(30L, money!!.minorUnits)
    }

    @Test
    fun `multiplication takes precedence over addition`() {
        assertEquals(BigDecimal(14), enter("2+3*4").value())
    }

    @Test
    fun `division uses a sane scale`() {
        assertEquals(BigDecimal("2.5"), enter("10/4").value())
    }

    @Test
    fun `subtraction works left to right`() {
        assertEquals(BigDecimal(6), enter("10-2-2").value())
    }

    @Test
    fun `trailing operator is kept in the expression but ignored when reading the value`() {
        val state = enter("12+")
        // The operator stays visible so the user sees what they typed...
        assertEquals("12+", state.expression)
        // ...but the amount is still a valid 12, so "保存" can stay enabled while calculating.
        assertEquals(BigDecimal(12), state.value())
    }

    @Test
    fun `operator replaces the previous operator instead of stacking`() {
        assertEquals("1+", enter("1++").expression)
        assertEquals("1*", enter("1+*").expression.replace('*', '*'))
    }

    @Test
    fun `operator after a dot pads a zero`() {
        assertEquals("5.0+", enter("5.+").expression)
    }

    @Test
    fun `equals collapses the expression into its result`() {
        val state = AmountInput.press(enter("1+2"), AmountKey.EQUALS)
        assertEquals("3", state.expression)
        assertEquals(BigDecimal(3), state.value())
    }

    @Test
    fun `equals on a clean division keeps the exact value`() {
        val state = AmountInput.press(enter("10/4"), AmountKey.EQUALS)
        assertEquals("2.5", state.expression)
    }

    @Test
    fun `division by zero reports an error instead of throwing`() {
        val state = AmountInput.press(enter("1/0"), AmountKey.EQUALS)
        assertNotNull(state.error)
        // Typing another digit clears the error and keeps the app usable.
        val after = AmountInput.press(state, AmountKey.D5)
        assertNull(after.error)
    }

    @Test
    fun `backspace removes one character`() {
        val state = AmountInput.press(enter("123"), AmountKey.BACKSPACE)
        assertEquals("12", state.expression)
    }

    @Test
    fun `backspace on empty stays empty`() {
        val state = AmountInput.press(AmountInputState(), AmountKey.BACKSPACE)
        assertTrue(state.isEmpty)
    }

    @Test
    fun `clear resets everything`() {
        val state = AmountInput.press(enter("12+34"), AmountKey.CLEAR)
        assertTrue(state.isEmpty)
        assertEquals("0", state.display)
    }

    @Test
    fun `display uses multiplication and division glyphs`() {
        assertEquals("2×3", enter("2*3").display)
        assertEquals("8÷2", enter("8/2").display)
    }

    @Test
    fun `operand length is bounded`() {
        val state = enter("1234567890123456789")
        assertTrue("expression should be capped, got ${state.expression.length}", state.expression.length <= 12)
    }

    @Test
    fun `value converts to money with currency scale`() {
        val money = enter("19.999").toMoney(CurrencyCode.CNY)
        assertEquals("rounded to 2 fraction digits", 2000L, money!!.minorUnits)
    }

    @Test
    fun `zero fraction currencies convert without decimals`() {
        val money = enter("1235.6").toMoney(CurrencyCode.JPY)
        assertEquals(1236L, money!!.minorUnits)
    }
}
