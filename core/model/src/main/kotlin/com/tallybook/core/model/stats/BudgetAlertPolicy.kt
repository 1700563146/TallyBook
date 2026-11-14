package com.tallybook.core.model.stats

import com.tallybook.core.model.money.CurrencyCode
import com.tallybook.core.model.money.Money

/** How serious a budget alert is. Ordered by escalation. */
enum class AlertLevel { NEAR_LIMIT, OVER_BUDGET }

/**
 * One notification to post, already fully worded.
 *
 * The policy returns *text*, not just a flag, so the wording is unit-testable and cannot drift
 * between the place that decides and the place that displays.
 */
data class BudgetAlert(
    val budgetId: String,
    val level: AlertLevel,
    /** Identifies the budget period this alert belongs to; used for de-duplication. */
    val periodKey: String,
    val title: String,
    val message: String,
) {
    /** Stable key used to remember "this level was already shown for this period". */
    val dedupKey: String get() = "$budgetId|$periodKey|$level"
}

/**
 * Decides which budget alerts to post.
 *
 * Kept as a pure function in `:core:model` on purpose: notification logic is where "works on my
 * machine" bugs live (fires twice, fires every hour, fires on an already-dismissed budget), and none
 * of that is visible without a deterministic unit test.
 *
 * Two design rules:
 * 1. **Two escalation steps, not a stream.** A budget notifies once when it crosses
 *    [NEAR_LIMIT_THRESHOLD] and again when it actually goes over. Anything more is spam, anything
 *    less hides the moment the user needs to act.
 * 2. **De-duplication is keyed by (budget, period, level).** The same level is never repeated inside
 *    a period, but a new period resets automatically because the period key changes.
 */
object BudgetAlertPolicy {

    /** Fraction of the limit at which the "you're close" alert fires. */
    const val NEAR_LIMIT_THRESHOLD = 0.8f

    /**
     * @param progresses live budget progress for the current period
     * @param alreadyNotified dedup keys ([BudgetAlert.dedupKey]) already shown
     * @param currency currency used to word the amounts
     */
    fun evaluate(
        progresses: List<BudgetProgress>,
        alreadyNotified: Set<String>,
        currency: CurrencyCode = CurrencyCode.CNY,
    ): List<BudgetAlert> = progresses.mapNotNull { progress ->
        // Nothing spent yet: a 0-limit budget would otherwise look "over budget" on day one.
        if (progress.spent.isZero) return@mapNotNull null

        val level = when {
            progress.isOverBudget -> AlertLevel.OVER_BUDGET
            progress.progress >= NEAR_LIMIT_THRESHOLD -> AlertLevel.NEAR_LIMIT
            else -> return@mapNotNull null
        }

        val alert = BudgetAlert(
            budgetId = progress.budgetId,
            level = level,
            periodKey = periodKeyOf(progress),
            title = titleOf(progress, level),
            message = messageOf(progress, level, currency),
        )

        alert.takeIf { it.dedupKey !in alreadyNotified }
    }

    /**
     * A period is identified by its start instant plus the period length.
     *
     * Using the start instant instead of a formatted date means the key is correct for weekly and
     * monthly budgets alike, with no calendar formatting to get wrong.
     */
    fun periodKeyOf(progress: BudgetProgress): String =
        "${progress.period.name}@${progress.periodStartMillis}"

    private fun titleOf(progress: BudgetProgress, level: AlertLevel): String = when (level) {
        AlertLevel.OVER_BUDGET -> "「${progress.name}」已超支"
        AlertLevel.NEAR_LIMIT -> "「${progress.name}」快超支了"
    }

    private fun messageOf(
        progress: BudgetProgress,
        level: AlertLevel,
        currency: CurrencyCode,
    ): String {
        val spent = progress.spent
        val limit = progress.limit
        val percent = (progress.progress * 100).toInt()
        return when (level) {
            AlertLevel.OVER_BUDGET ->
                "已用 ${spent.format()} / ${limit.format()}，超出 ${progress.overrun.format()}"
            AlertLevel.NEAR_LIMIT ->
                "已用 ${spent.format()} / ${limit.format()}（$percent%），还剩 ${progress.remaining.format()}"
        }
    }

    /** Convenience for callers that only need the key of one amount pair; keeps tests readable. */
    internal fun formatMinor(minorUnits: Long, currency: CurrencyCode): String =
        Money(minorUnits, currency).format()
}
