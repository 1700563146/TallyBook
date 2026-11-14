package com.tallybook.core.model.money

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Parses a user-typed major-unit amount ("1200", "12.5", "-30.00") into minor units.
 *
 * Exists as a pure function because a wrong parse is silent: the account is saved, and every balance
 * derived from it is off by the error without anything failing. The tricky inputs are the ones people
 * actually type — a thousands separator copied from a bank app, a trailing dot mid-typing, or a minus
 * sign for a credit card.
 */
object AmountParser {

    /** @return minor units, or null when [text] is not a plain decimal number. */
    fun parseMinorUnits(text: String, fractionDigits: Int = 2): Long? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return 0L
        // "1,000" is a formatting convention, not a number: accepting it silently would make
        // "1,5" ambiguous, so it is rejected and the dialog can say so.
        if (trimmed.any { it == ',' || it == ' ' || it == '¥' || it == '￥' }) return null

        return runCatching {
            BigDecimal(trimmed)
                .movePointRight(fractionDigits)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
                .takeIf { it in -Money.MAX_ABS_MINOR_UNITS..Money.MAX_ABS_MINOR_UNITS }
        }.getOrNull()
    }
}
