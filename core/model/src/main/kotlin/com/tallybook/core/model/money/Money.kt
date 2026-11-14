package com.tallybook.core.model.money

import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency

/**
 * A currency's ISO-4217 code plus the number of minor units it uses.
 *
 * NOTE ON SCOPE: TallyBook keeps a **single ledger currency**. This type still exists because the
 * number of fraction digits and the symbol are display correctness concerns (JPY has 0 fraction
 * digits, KRW has 0, most others 2) — not because the app converts between currencies.
 * There is deliberately no FxRate, no conversion path, and no cross-currency total.
 */
@JvmInline
@Serializable
value class CurrencyCode(val value: String) {

    init {
        require(value.length == 3 && value.all { it.isUpperCase() }) {
            "Currency code must be 3 uppercase letters, got '$value'"
        }
    }

    val javaCurrency: Currency get() = Currency.getInstance(value)

    /** Cached by the JDK; kept here so call sites stay allocation-free at call time. */
    val fractionDigits: Int get() = javaCurrency.defaultFractionDigits.takeIf { it >= 0 } ?: 2

    val symbol: String get() = javaCurrency.getSymbol(java.util.Locale.getDefault())

    companion object {
        val CNY = CurrencyCode("CNY")
        val USD = CurrencyCode("USD")
        val EUR = CurrencyCode("EUR")
        val JPY = CurrencyCode("JPY")
    }
}

/**
 * An immutable monetary amount stored as an integer count of **minor units** (cents/fen).
 *
 * Why not Double: binary floating point cannot represent 0.10 exactly, and summing a month of
 * expenses accumulates visible error. Why not BigDecimal in the model: BigDecimal is a heap object
 * with allocation cost, lacks value semantics, and Room cannot persist it without a Converter that
 * loses type safety. Int64 minor units is the representation Stripe/Shopify/Square all use.
 *
 * Because the ledger is single-currency, [plus]/[minus]/[compareTo] no longer need a currency guard
 * on every call: mixing currency codes is prevented at the app-config boundary instead. The guard
 * remains in [requireSameCurrency] for the few places that accept two amounts.
 */
@Serializable
data class Money(
    val minorUnits: Long,
    val currency: CurrencyCode,
) : Comparable<Money> {

    init {
        require(
            minorUnits in -MAX_ABS_MINOR_UNITS..MAX_ABS_MINOR_UNITS,
        ) {
            "Amount is outside TallyBook's supported range: $minorUnits minor units"
        }
    }

    val fractionDigits: Int get() = currency.fractionDigits

    /** Major-unit value, e.g. 12345 minor units of CNY -> 123.45. For display/rounding only. */
    fun toBigDecimal(): BigDecimal =
        BigDecimal.valueOf(minorUnits).movePointLeft(fractionDigits)

    fun plus(other: Money): Money {
        requireSameCurrency(other)
        return Money(Math.addExact(minorUnits, other.minorUnits), currency)
    }

    fun minus(other: Money): Money {
        requireSameCurrency(other)
        return Money(Math.subtractExact(minorUnits, other.minorUnits), currency)
    }

    fun times(multiplier: Int): Money =
        Money(Math.multiplyExact(minorUnits, multiplier.toLong()), currency)

    fun negate(): Money {
        require(minorUnits != MINOR_UNITS_MIN_VALUE) { "Amount cannot be negated without overflow" }
        return Money(-minorUnits, currency)
    }

    val isZero: Boolean get() = minorUnits == 0L
    val isNegative: Boolean get() = minorUnits < 0L

    /**
     * Ratio of this amount to [limit], clamped to 0..1 for progress bars.
     * Returns 1f when [limit] is zero so a zero limit renders as "full", not as a divide-by-zero.
     */
    fun progressAgainst(limit: Money): Float {
        requireSameCurrency(limit)
        if (limit.minorUnits <= 0L) return if (minorUnits <= 0L) 0f else 1f
        return (minorUnits.toDouble() / limit.minorUnits.toDouble()).toFloat().coerceIn(0f, 1f)
    }

    /** True overrun, not clamped — budgets must be able to tell the user *how far* over they are. */
    fun isOver(limit: Money): Boolean {
        requireSameCurrency(limit)
        return minorUnits > limit.minorUnits
    }

    /**
     * Guard only — returns Unit on purpose.
     *
     * An earlier version returned `other` so callers could inline it as
     * `x.op(y).minorUnits`, which is exactly the shape of a silent data bug: the receiver/argument
     * ordering at each call site decides whether you get `x` or `y`, and the compiler cannot help.
     * Keeping this a pure assertion forces every arithmetic site to name both operands explicitly.
     */
    private fun requireSameCurrency(other: Money) {
        require(currency == other.currency) {
            "Currency mismatch: ${currency.value} vs ${other.currency.value}. " +
                "TallyBook is single-currency; this indicates a config bug, not user data."
        }
    }

    override fun compareTo(other: Money): Int {
        requireSameCurrency(other)
        return minorUnits.compareTo(other.minorUnits)
    }

    /**
     * Formats using the currency's own fraction digits, e.g. "¥123.45".
     *
     * Why not `NumberFormat.getCurrencyInstance()`: its output depends on the *device* locale, so a
     * user with an English phone sees "CN¥123.45" while a Chinese phone shows "¥123.45" — same data,
     * two different-looking ledgers, and the longer form overflows narrow list rows.
     *
     * Instead the symbol is chosen deterministically for the currencies this app actually supports,
     * and grouping/decimals come from the locale. That keeps排版 stable across devices while still
     * honouring comma vs. point decimal separators.
     */
    fun format(locale: java.util.Locale = java.util.Locale.getDefault()): String {
        val numberFormat = java.text.NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = fractionDigits
            maximumFractionDigits = fractionDigits
        }
        return displaySymbol(locale) + numberFormat.format(toBigDecimal())
    }

    /**
     * A stable symbol per currency.
     *
     * Unknown codes fall back to the JDK symbol for the user's locale, which is the only sensible
     * guess for a currency we have no opinion about.
     */
    private fun displaySymbol(locale: java.util.Locale): String = when (currency.value) {
        "CNY" -> "¥"
        "USD" -> "$"
        "EUR" -> "€"
        "GBP" -> "£"
        "JPY" -> "¥"
        "KRW" -> "₩"
        "HKD" -> "HK$"
        else -> currency.javaCurrency.getSymbol(locale)
    }

    companion object {
        /**
         * ±100 billion yuan in fen. Every UI and import path stays well inside Int64 and arithmetic
         * between two valid amounts cannot silently cross the supported product boundary.
         */
        const val MAX_ABS_MINOR_UNITS: Long = 10_000_000_000_000L
        private const val MINOR_UNITS_MIN_VALUE: Long = -MAX_ABS_MINOR_UNITS

        val ZERO_CNY = Money(0, CurrencyCode.CNY)

        fun zero(currency: CurrencyCode): Money = Money(0, currency)

        /** Builds from a major-unit string without going through Double: "12.34" -> 1234. */
        fun parse(amount: String, currency: CurrencyCode): Money {
            val bd = BigDecimal(amount).setScale(currency.fractionDigits, RoundingMode.HALF_UP)
            return Money(
                bd.movePointRight(currency.fractionDigits).longValueExact(),
                currency,
            )
        }

        /** Converts a Double from user input (e.g. a slider) safely at the edge only. */
        fun fromMajorUnits(value: Double, currency: CurrencyCode): Money {
            val bd = BigDecimal.valueOf(value).setScale(currency.fractionDigits, RoundingMode.HALF_UP)
            return Money(
                bd.movePointRight(currency.fractionDigits).longValueExact(),
                currency,
            )
        }

        fun fromBigDecimal(value: BigDecimal, currency: CurrencyCode): Money {
            val scaled = value.setScale(currency.fractionDigits, RoundingMode.HALF_UP)
            return Money(
                scaled.movePointRight(currency.fractionDigits).longValueExact(),
                currency,
            )
        }
    }
}
