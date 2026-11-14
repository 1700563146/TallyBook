package com.tallybook.feature.insights.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tallybook.core.common.clock.DateProvider
import com.tallybook.core.data.repository.TransactionListItem
import com.tallybook.core.data.repository.TransactionRepository
import com.tallybook.core.model.money.Money
import com.tallybook.core.model.transaction.TransactionKind
import com.tallybook.core.model.transaction.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

/** One day of the drilled-down list. */
data class DetailDayGroup(
    val dateKey: String,
    val dayLabel: String,
    val dayTotalLabel: String,
    val rows: List<DetailRow>,
)

data class DetailRow(
    val id: String,
    val note: String,
    val accountName: String,
    val amountLabel: String,
    val isExpense: Boolean,
)

data class CategoryDetailUiState(
    val categoryName: String = "",
    val rangeLabel: String = "",
    val totalLabel: String = "",
    val count: Int = 0,
    val groups: List<DetailDayGroup> = emptyList(),
    val isLoading: Boolean = true,
)

/**
 * Category drill-down: "where did these 9,016 元 of 居住 actually go?"
 *
 * The window is passed in from the statistics screen rather than recomputed here, so the numbers can
 * never disagree with the row the user tapped.
 */
@HiltViewModel
class CategoryDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transactionRepository: TransactionRepository,
    private val dateProvider: DateProvider,
) : ViewModel() {

    private val categoryId: String? = savedStateHandle.get<String>("categoryId")
    private val fromInclusive: Long = savedStateHandle.get<Long>("fromInclusive") ?: 0L
    private val toExclusive: Long = savedStateHandle.get<Long>("toExclusive") ?: 0L
    private val categoryName: String = savedStateHandle.get<String>("categoryName") ?: "明细"

    private val _uiState = MutableStateFlow(CategoryDetailUiState(categoryName = categoryName))
    val uiState: StateFlow<CategoryDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            transactionRepository
                .observeCategoryItems(categoryId, fromInclusive, toExclusive)
                .collect { items ->
                    _uiState.update { current ->
                        current.copy(
                            totalLabel = totalOf(items).format(),
                            count = items.size,
                            groups = groupByDay(items),
                            rangeLabel = rangeLabel(),
                            isLoading = false,
                        )
                    }
                }
        }
    }

    /**
     * Net expense of the drilled-down rows.
     *
     * Uses the same rule as every other total in the app (expenses add, refunds subtract, transfers
     * ignored) so the headline here matches the slice the user tapped.
     */
    private fun totalOf(items: List<TransactionListItem>): Money =
        items.fold(Money.ZERO_CNY) { acc, item ->
            val transaction = item.transaction
            when {
                transaction.kind == TransactionKind.TRANSFER -> acc
                transaction.type == TransactionType.EXPENSE -> acc.plus(transaction.amount)
                transaction.refundOfId != null -> acc.minus(transaction.amount)
                else -> acc
            }
        }

    private fun groupByDay(items: List<TransactionListItem>): List<DetailDayGroup> =
        items.groupBy { dateProvider.toLocalDate(it.transaction.occurredAt) }
            .toSortedMap(compareByDescending { it })
            .map { (date, dayItems) ->
                val dayTotal = totalOf(dayItems)
                DetailDayGroup(
                    dateKey = date.toString(),
                    dayLabel = dayLabelOf(date),
                    dayTotalLabel = if (dayTotal.isZero) "" else dayTotal.format(),
                    rows = dayItems.map { item ->
                        val transaction = item.transaction
                        val isTransfer = transaction.kind == TransactionKind.TRANSFER
                        val isExpense = transaction.type == TransactionType.EXPENSE
                        DetailRow(
                            id = transaction.id.value,
                            note = transaction.note.orEmpty(),
                            accountName = item.accountName,
                            amountLabel = when {
                                isTransfer -> transaction.amount.format()
                                isExpense -> "-${transaction.amount.format()}"
                                else -> "+${transaction.amount.format()}"
                            },
                            isExpense = isExpense && !isTransfer,
                        )
                    },
                )
            }

    private fun dayLabelOf(date: LocalDate): String {
        val weekday = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        return "${date.monthValue}月${date.dayOfMonth}日 $weekday"
    }

    private fun rangeLabel(): String {
        if (fromInclusive == 0L || toExclusive <= fromInclusive) return ""
        val formatter = DateTimeFormatter.ofPattern("yyyy/M/d", Locale.getDefault())
        val start = dateProvider.toLocalDate(fromInclusive).format(formatter)
        val end = dateProvider.toLocalDate(toExclusive - 1).format(formatter)
        return "$start – $end"
    }
}
