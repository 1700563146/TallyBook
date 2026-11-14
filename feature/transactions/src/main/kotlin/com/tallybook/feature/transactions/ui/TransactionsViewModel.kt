package com.tallybook.feature.transactions.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tallybook.core.common.clock.Clock
import com.tallybook.core.common.clock.DateProvider
import com.tallybook.core.data.repository.BudgetRepository
import com.tallybook.core.data.repository.PeriodTotals
import com.tallybook.core.data.repository.StatsRepository
import com.tallybook.core.data.repository.TransactionListItem
import com.tallybook.core.data.repository.TransactionRepository
import com.tallybook.core.model.money.CurrencyCode
import com.tallybook.core.model.money.Money
import com.tallybook.core.model.stats.BudgetProgress
import com.tallybook.core.model.stats.WeekdayLabels
import com.tallybook.core.model.transaction.BudgetPeriod
import com.tallybook.core.model.transaction.TransactionKind
import com.tallybook.core.model.transaction.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/**
 * Home screen state holder.
 *
 * Besides the month's ledger, the home screen carries three "at a glance" summaries: the month's
 * income/expense, a 7-day mini chart, and how much budget is left. All of them come from the same
 * query snapshot — computing them separately in the composable would let the numbers disagree.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val statsRepository: StatsRepository,
    private val budgetRepository: BudgetRepository,
    private val dateProvider: DateProvider,
    private val clock: Clock,
    private val zoneId: ZoneId,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionsUiState())
    val uiState: StateFlow<TransactionsUiState> = _uiState.asStateFlow()

    private val anchor = MutableStateFlow(clock.nowMillis())

    init {
        observeMonth(anchor)

        viewModelScope.launch {
            anchor.collect { millis ->
                _uiState.update { it.copy(anchorMillis = millis, isLoading = true) }
            }
        }
    }

    private fun observeMonth(anchorFlow: MutableStateFlow<Long>) {
        viewModelScope.launch {
            anchorFlow
                .flatMapLatest { millis ->
                    val start = dateProvider.startOfMonth(millis)
                    val end = dateProvider.endOfMonthExclusive(millis)

                    // The mini chart is always "the last 7 days", independent of the month being
                    // browsed: it answers "how is this week going", not "what did I spend in a past
                    // month".
                    val now = clock.nowMillis()
                    val weekStart = dateProvider.startOfDay(dateProvider.toLocalDate(now).minusDays(6))
                    val weekEndExclusive = dateProvider.endOfDayExclusive(now)

                    combine(
                        transactionRepository.observeListItems(start, end),
                        transactionRepository.observePeriodTotals(start, end),
                        statsRepository.observeDailyTotals(weekStart until weekEndExclusive),
                        budgetRepository.observeProgress(now),
                    ) { items, totals, daily, budgets ->
                        MonthSnapshot(millis, items, totals, daily, budgets)
                    }
                }
                .collect { snapshot ->
                    _uiState.update { current ->
                        current.copy(
                            monthLabel = MONTH_LABEL.format(
                                Instant.ofEpochMilli(snapshot.anchorMillis).atZone(zoneId),
                            ),
                            anchorMillis = snapshot.anchorMillis,
                            expenseTotal = snapshot.totals.expense.format(),
                            incomeTotal = snapshot.totals.income.format(),
                            groups = groupByDay(snapshot.items),
                            miniChart = buildMiniChart(snapshot.daily),
                            budgetCards = buildBudgetCards(snapshot.budgets),
                            isLoading = false,
                        )
                    }
                }
        }
    }

    private data class MonthSnapshot(
        val anchorMillis: Long,
        val items: List<TransactionListItem>,
        val totals: PeriodTotals,
        val daily: Map<String, PeriodTotals>,
        val budgets: List<BudgetProgress>,
    )

    /**
     * Seven daily bars ending today.
     *
     * Days without activity are included as zero-height bars so the chart always reads as a week; a
     * gap would look like missing data rather than a day without spending.
     */
    private fun buildMiniChart(daily: Map<String, PeriodTotals>): List<MiniBarUi> {
        val today = dateProvider.toLocalDate(clock.nowMillis())
        val days = (6 downTo 0).map { offset -> today.minusDays(offset.toLong()) }
        val values = days.map { date -> daily[date.toString()]?.expense?.minorUnits ?: 0L }
        val max = values.maxOrNull() ?: 0L

        return days.mapIndexed { index, date ->
            val value = values[index]
            MiniBarUi(
                // Weekday abbreviations are already short in Chinese ("周一" -> "一").
                label = WeekdayLabels.narrow(date.dayOfWeek),
                amountLabel = Money(value, CurrencyCode.CNY).format(),
                fraction = if (max <= 0L) 0f else (value.toFloat() / max.toFloat()).coerceIn(0f, 1f),
                isToday = date == today,
            )
        }
    }

    /**
     * Two summary rows: the monthly and the weekly budget.
     *
     * Multiple budgets of the same period are summed, because the home screen answers "how much room
     * do I have left overall", not "which of my three budgets is worst".
     */
    private fun buildBudgetCards(budgets: List<BudgetProgress>): List<BudgetCardUi> {
        fun card(period: BudgetPeriod, title: String): BudgetCardUi? {
            val ofPeriod = budgets.filter { it.period == period }
            if (ofPeriod.isEmpty()) return null
            // Category budgets overlap by design. Summing their limits invents a total that the user
            // never configured, so show the most at-risk budget and an aggregate overrun count.
            val critical = ofPeriod.maxWith(
                compareBy<BudgetProgress> { it.isOverBudget }.thenBy { it.progress },
            )
            val remaining = critical.remaining
            val over = remaining.isNegative
            return BudgetCardUi(
                title = if (ofPeriod.size == 1) title else "$title · ${critical.name}",
                subtitle = if (ofPeriod.size == 1) {
                    null
                } else {
                    "${ofPeriod.size} 个预算 · ${ofPeriod.count { it.isOverBudget }} 个超支"
                },
                // Worded here so a negative number never leaks into the UI as "-¥30".
                amountLabel = if (over) "超支 ${remaining.negate().format()}" else "还剩 ${remaining.format()}",
                progress = critical.progress,
                isOverBudget = over,
            )
        }

        return listOfNotNull(
            card(BudgetPeriod.MONTHLY, "本月预算"),
            card(BudgetPeriod.WEEKLY, "本周预算"),
        )
    }

    fun showPreviousMonth() {
        anchor.update { dateProvider.startOfMonth(it) - 1 }
    }

    fun showNextMonth() {
        anchor.update { dateProvider.endOfMonthExclusive(it) }
    }

    fun showCurrentMonth() {
        anchor.value = clock.nowMillis()
    }

    fun delete(id: String) {
        viewModelScope.launch {
            transactionRepository.delete(id)
            _uiState.update {
                it.copy(undoMessage = "已删除这笔记录", undoTransactionId = id)
            }
        }
    }

    fun undoDelete() {
        val id = _uiState.value.undoTransactionId ?: return
        viewModelScope.launch {
            transactionRepository.restore(id)
            _uiState.update {
                it.copy(
                    undoMessage = null,
                    undoTransactionId = null,
                    scrollToTopTick = it.scrollToTopTick + 1,
                )
            }
        }
    }

    fun onSnackbarShown() {
        _uiState.update { it.copy(undoMessage = null, undoTransactionId = null) }
    }

    private fun groupByDay(items: List<TransactionListItem>): List<DayGroupUi> =
        items.groupBy { dateProvider.toLocalDate(it.transaction.occurredAt) }
            .toSortedMap(compareByDescending { it })
            .map { (date, dayItems) ->
                val expense = dayItems.fold(Money.ZERO_CNY) { acc, item ->
                    val transaction = item.transaction
                    when {
                        transaction.kind == TransactionKind.TRANSFER -> acc
                        transaction.type == TransactionType.EXPENSE -> acc.plus(transaction.amount)
                        transaction.refundOfId != null -> acc.minus(transaction.amount)
                        else -> acc
                    }
                }
                DayGroupUi(
                    dateKey = date.toString(),
                    dayLabel = dayLabel(date),
                    expenseLabel = if (expense.isZero) "" else "支出 ${expense.format()}",
                    items = dayItems.map { it.toTransactionRowUi() },
                )
            }

    private fun dayLabel(date: LocalDate): String {
        val weekday = WeekdayLabels.short(date.dayOfWeek)
        val today = dateProvider.toLocalDate(clock.nowMillis())
        val relative = when (date) {
            today -> "今天 · "
            today.minusDays(1) -> "昨天 · "
            else -> ""
        }
        return "$relative${date.monthValue}月${date.dayOfMonth}日 $weekday"
    }

    private companion object {
        val MONTH_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月", Locale.getDefault())
    }
}
