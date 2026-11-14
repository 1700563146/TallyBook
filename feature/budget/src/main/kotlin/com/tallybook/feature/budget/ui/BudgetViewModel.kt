package com.tallybook.feature.budget.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tallybook.core.common.clock.Clock
import com.tallybook.core.data.repository.BudgetRepository
import com.tallybook.core.model.money.Money
import com.tallybook.core.model.stats.BudgetProgress
import com.tallybook.core.model.transaction.BudgetPeriod
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Budget screen state holder.
 *
 * Weekly and monthly progress share one screen because the user's mental model is "my budgets", not
 * two separate destinations; switching tabs to compare them is pure friction.
 */
@HiltViewModel
class BudgetViewModel @Inject constructor(
    private val budgetRepository: BudgetRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BudgetUiState())
    val uiState: StateFlow<BudgetUiState> = _uiState.asStateFlow()

    /**
     * Tracks the running collection so [refresh] replaces it instead of stacking another one.
     *
     * Called naively, `refresh()` would leak one collector per invocation (onResume, pull-to-refresh,
     * rotation): every collector keeps querying the database and several of them race to write
     * `_uiState`, producing flicker and wasted queries that never stop for the lifetime of the
     * ViewModel.
     */
    private var progressJob: Job? = null

    init {
        refresh()
    }

    /**
     * Recomputes progress against the current instant.
     *
     * Progress is time-dependent even when no data changed ("days elapsed" ticks at midnight), so the
     * screen re-invokes this on resume rather than relying on the Flow alone.
     */
    fun refresh() {
        val now = clock.nowMillis()

        // Cancel before starting: a stale collector would otherwise emit an old snapshot after the
        // new one and win the race.
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            budgetRepository.observeProgress(now).collect { all ->
                _uiState.update { previous ->
                    previous.copy(
                        monthProgress = all.filter { it.period == BudgetPeriod.MONTHLY },
                        weekProgress = all.filter { it.period == BudgetPeriod.WEEKLY },
                        // YEARLY/CUSTOM budgets are filtered out here rather than in the repository:
                        // the data layer reports every active budget honestly, and the screen decides
                        // what it can render today.
                        nowMillis = now,
                        isLoading = false,
                    )
                }
            }
        }
    }
}

/** Pure formatting helpers, kept out of the composable so they are unit-testable. */
internal object BudgetFormat {

    fun paceLabel(progress: BudgetProgress): String = when {
        progress.isOverBudget -> "已超支 ${progress.overrun.format()}"
        progress.daysElapsed == 0 -> "本周期尚未开始"
        else -> {
            val remaining = progress.remaining
            val daysLeft = (progress.daysTotal - progress.daysElapsed).coerceAtLeast(0)
            "还剩 ${remaining.format()} · ${daysLeft} 天"
        }
    }

    /**
     * Human-readable pace verdict.
     *
     * Compares spend pace against time pace rather than only against the limit: being 60% through the
     * budget on day 10 of 30 is fine, on day 28 it is not. No single percentage conveys that, which is
     * why [BudgetProgress] exposes both ratios.
     */
    fun paceHint(progress: BudgetProgress): String {
        if (progress.isOverBudget) return "放慢一点"
        if (progress.daysElapsed == 0) return "等周期开始"
        val spendPace = progress.progress
        val timePace = progress.timeProgress
        return when {
            spendPace > timePace + 0.15f -> "花得比时间快"
            spendPace < timePace - 0.15f -> "节奏良好"
            else -> "节奏持平"
        }
    }

    fun projectedLabel(progress: BudgetProgress): String? =
        progress.projectedOverrun?.let { "按当前节奏，期末将超支 ${it.format()}" }

    fun zero(): Money = Money.ZERO_CNY
}
