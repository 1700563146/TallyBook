package com.tallybook.feature.budget.ui

import com.tallybook.core.model.stats.BudgetProgress

/**
 * Budget screen state.
 *
 * [nowMillis] is carried in the state rather than read inside composables: the "days elapsed" and
 * pace indicators must be computed against the same instant the progress was queried for, otherwise
 * a midnight boundary makes the bar disagree with the numbers next to it.
 */
data class BudgetUiState(
    val monthProgress: List<BudgetProgress> = emptyList(),
    val weekProgress: List<BudgetProgress> = emptyList(),
    val nowMillis: Long = 0L,
    val isLoading: Boolean = true,
) {
    val isEmpty: Boolean get() = monthProgress.isEmpty() && weekProgress.isEmpty()

    /** Sum of limits for a group, used for the "总预算" summary line. */
    fun totalLimit(items: List<BudgetProgress>) =
        items.fold(com.tallybook.core.model.money.Money.ZERO_CNY) { acc, p -> acc.plus(p.limit) }

    fun totalSpent(items: List<BudgetProgress>) =
        items.fold(com.tallybook.core.model.money.Money.ZERO_CNY) { acc, p -> acc.plus(p.spent) }
}
