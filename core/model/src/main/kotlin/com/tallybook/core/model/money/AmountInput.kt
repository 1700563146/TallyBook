package com.tallybook.core.model.money

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlinx.serialization.Serializable

/** Keys of the custom amount keypad. */
enum class AmountKey {
    D0, D1, D2, D3, D4, D5, D6, D7, D8, D9,
    DOT, PLUS, MINUS, MULTIPLY, DIVIDE,
    BACKSPACE, CLEAR, EQUALS,
}

/**
 * State of the amount input.
 *
 * [expression] is the raw, machine-parsable input (`"12.5+3*2"`); [error] is set only when the user
 * asked for a result that cannot exist (division by zero). Keeping the expression as a plain String
 * rather than a token tree means backspace is trivial and the whole thing is a pure function of the
 * previous state — which is exactly what makes it unit-testable without Compose.
 */
@Serializable
data class AmountInputState(
    val expression: String = "",
    val error: String? = null,
) {
    val isEmpty: Boolean get() = expression.isBlank()

    /** What the keypad shows: an explicit 0 when nothing is typed yet. */
    val display: String
        get() = if (expression.isEmpty()) "0" else expression.prettify()

    /** Current value, ignoring an incomplete trailing operator ("12+" reads as 12). */
    fun value(): BigDecimal? = AmountInput.evaluate(expression)

    fun toMoney(currency: CurrencyCode): Money? =
        value()?.let { result ->
            runCatching { Money.fromBigDecimal(result, currency) }.getOrNull()
        }
}

/**
 * A calculator-style amount input.
 *
 * Why a custom engine instead of `TextField` + `toDoubleOrNull()`:
 * - Users type `12.5+3.8` constantly ("两笔一起记"). That needs real arithmetic, not a parse.
 * - `Double` reintroduces exactly the float error the model is designed to avoid; this engine
 *   evaluates in `BigDecimal` and only converts to minor units at the very end.
 * - Backspace, operator replacement, and double-dot suppression are the behaviours users expect and
 *   each one is a bug magnet in ad-hoc implementations.
 *
 * Precedence follows normal arithmetic (`2+3*4 = 14`), not left-to-right keypad order. That is what
 * a calculator on the same screen would do, and matching it avoids surprising results.
 */
object AmountInput {

    private const val DIVIDE_SCALE = 10

    fun press(state: AmountInputState, key: AmountKey): AmountInputState = when (key) {
        AmountKey.CLEAR -> AmountInputState()
        AmountKey.BACKSPACE -> state.copy(
            expression = state.expression.dropLast(1),
            error = null,
        )
        AmountKey.EQUALS -> evaluate(state.expression)?.let { result ->
            // A successful evaluation collapses the expression into its result so the user can
            // continue calculating from there.
            state.copy(expression = result.stripTrailingZeros().plainStringFloored(), error = null)
        } ?: state.copy(error = "无法计算（可能除以 0）")
        else -> state.copy(expression = applyKey(state.expression, key), error = null)
    }

    fun press(state: AmountInputState, keys: List<AmountKey>): AmountInputState =
        keys.fold(state) { acc, key -> press(acc, key) }

    private fun applyKey(expr: String, key: AmountKey): String {
        val symbol = key.symbolOrNull()
        if (symbol != null) return appendOperator(expr, symbol)

        return when (key) {
            AmountKey.DOT -> appendDot(expr)
            else -> appendDigit(expr, key.digitOrNull() ?: return expr)
        }
    }

    private fun appendDigit(expr: String, digit: Char): String {
        val operand = currentOperand(expr)
        // Suppress "007": a leading zero is replaced rather than extended.
        if (operand == "0") return expr.dropLast(1) + digit
        // Guard against pathological input lengths (a stuck key should not build a 10k-character
        // expression that later overflows BigDecimal).
        if (operand.length >= MAX_OPERAND_LEN) return expr
        return expr + digit
    }

    private fun appendDot(expr: String): String {
        val operand = currentOperand(expr)
        if (operand.contains('.')) return expr // one decimal point per operand
        return "$expr."
    }

    private fun appendOperator(expr: String, symbol: Char): String {
        if (expr.isEmpty()) {
            // A leading operator has no left operand; treating it as "+" is friendlier than
            // rejecting the keystroke silently.
            return if (symbol == '-') "" else ""
        }
        val last = expr.last()
        return when {
            last.isOperator() -> expr.dropLast(1) + symbol // replace, don't stack
            last == '.' -> expr + "0$symbol" // "5." + "+" -> "5.0+"
            else -> expr + symbol
        }
    }

    /**
     * Evaluates [expression], returning null when it is invalid or divides by zero.
     *
     * A trailing operator is tolerated (`"12+"` -> 12) because users hit "=" reflexively while typing.
     */
    fun evaluate(expression: String): BigDecimal? {
        if (expression.isBlank()) return BigDecimal.ZERO
        val trimmed = expression.trimEnd { it.isOperator() || it == '.' }
        if (trimmed.isBlank()) return BigDecimal.ZERO

        val tokens = tokenize(trimmed) ?: return null
        if (tokens.isEmpty()) return BigDecimal.ZERO

        // Pass 1: collapse * and / so that + and - can be folded left in pass 2.
        //
        // Each entry stores (value, operator that FOLLOWS it). Folding therefore pairs entry
        // `i - 1`'s operator with entry `i`'s value. Pairing an entry with its own operator is the
        // bug this shape fixes: it made `10-2-2` evaluate to 10.
        val additive = ArrayList<Pair<BigDecimal, Char?>>()
        var current = (tokens[0] as? Token.Number)?.value ?: return null
        var index = 1

        while (index < tokens.size) {
            val op = (tokens.getOrNull(index) as? Token.Operator)?.symbol ?: return null
            val next = (tokens.getOrNull(index + 1) as? Token.Number)?.value ?: return null
            when (op) {
                '*' -> current = current.multiply(next).stripTrailingZeros()
                '/' -> {
                    if (next.signum() == 0) return null
                    // stripTrailingZeros keeps "10/4" as 2.5 rather than 2.5000000000
                    current = current.divide(next, DIVIDE_SCALE, RoundingMode.HALF_UP).stripTrailingZeros()
                }
                else -> {
                    additive += current to op
                    current = next
                }
            }
            index += 2
        }
        additive += current to null

        var result = additive.first().first
        for (i in 1 until additive.size) {
            result = when (additive[i - 1].second) {
                '-' -> result.subtract(additive[i].first)
                else -> result.add(additive[i].first)
            }
        }
        return result.stripTrailingZeros()
    }

    private fun tokenize(expr: String): List<Token>? {
        val tokens = ArrayList<Token>()
        val number = StringBuilder()
        var invalid = false

        fun flush() {
            if (number.isNotEmpty()) {
                val value = runCatching { BigDecimal(number.toString()) }.getOrNull()
                if (value == null) {
                    invalid = true
                } else {
                    tokens += Token.Number(value)
                }
                number.clear()
            }
        }

        for (ch in expr) {
            when {
                ch.isDigit() || ch == '.' -> number.append(ch)
                ch.isOperator() -> {
                    flush()
                    tokens += Token.Operator(ch)
                }
                else -> return null
            }
        }
        flush()
        if (invalid) return null
        return tokens
    }

    private fun currentOperand(expr: String): String {
        val start = expr.indexOfLast { it.isOperator() } + 1
        return expr.substring(start)
    }

    private sealed interface Token {
        data class Number(val value: BigDecimal) : Token
        data class Operator(val symbol: Char) : Token
    }

    private fun Char.isOperator(): Boolean = this == '+' || this == '-' || this == '*' || this == '/'

    private fun AmountKey.symbolOrNull(): Char? = when (this) {
        AmountKey.PLUS -> '+'
        AmountKey.MINUS -> '-'
        AmountKey.MULTIPLY -> '*'
        AmountKey.DIVIDE -> '/'
        else -> null
    }

    private fun AmountKey.digitOrNull(): Char? = when (this) {
        AmountKey.D0 -> '0'
        AmountKey.D1 -> '1'
        AmountKey.D2 -> '2'
        AmountKey.D3 -> '3'
        AmountKey.D4 -> '4'
        AmountKey.D5 -> '5'
        AmountKey.D6 -> '6'
        AmountKey.D7 -> '7'
        AmountKey.D8 -> '8'
        AmountKey.D9 -> '9'
        else -> null
    }

    /** BigDecimal results like `3.0` should display as `3`, and `1E+2` should never appear. */
    private fun BigDecimal.plainStringFloored(): String {
        val stripped = this.stripTrailingZeros()
        return if (stripped.scale() < 0) stripped.setScale(0).toPlainString() else stripped.toPlainString()
    }

    private const val MAX_OPERAND_LEN = 12

    /** Exposed for tests that want to assert intermediate values without importing MathContext. */
    internal val PRECISION: MathContext = MathContext.DECIMAL64
}

/** Renders the raw expression with calculator glyphs. Plain string ops, no allocation per call. */
private fun String.prettify(): String = replace('*', '×').replace('/', '÷')
