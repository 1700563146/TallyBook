package com.tallybook.feature.transactions.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.tallybook.core.common.clock.Clock
import com.tallybook.core.common.clock.DateProvider
import com.tallybook.core.data.repository.AccountRepository
import com.tallybook.core.data.repository.CategoryRepository
import com.tallybook.core.data.repository.SearchEntryType
import com.tallybook.core.data.repository.TransactionListItem
import com.tallybook.core.data.repository.TransactionRepository
import com.tallybook.core.data.repository.TransactionSearchFilter
import com.tallybook.core.model.stats.WeekdayLabels
import com.tallybook.core.model.transaction.Category
import com.tallybook.core.model.transaction.Transaction
import com.tallybook.feature.transactions.ui.TransactionRowUi
import com.tallybook.feature.transactions.ui.toTransactionRowUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

data class SearchOption(val id: String?, val name: String)

data class SearchResultUi(
    val dayKey: String,
    val dayLabel: String,
    val row: TransactionRowUi,
)

/**
 * Cross-month ledger search.
 *
 * Query text is debounced, but filter selections reuse the same pipeline. Every filter is stored in
 * [SavedStateHandle] so returning after process death does not silently widen the result set.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val transactionRepository: TransactionRepository,
    accountRepository: AccountRepository,
    categoryRepository: CategoryRepository,
    private val dateProvider: DateProvider,
    private val clock: Clock,
) : ViewModel() {

    val query = savedStateHandle.getStateFlow(KEY_QUERY, "")
    val entryType = savedStateHandle.getStateFlow(KEY_ENTRY_TYPE, SearchEntryType.ALL.name)
        .map { runCatching { SearchEntryType.valueOf(it) }.getOrDefault(SearchEntryType.ALL) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SearchEntryType.ALL)
    val accountId = savedStateHandle.getStateFlow<String?>(KEY_ACCOUNT, null)
    val categoryId = savedStateHandle.getStateFlow<String?>(KEY_CATEGORY, null)

    val accounts = accountRepository.observeAll()
        .map { accounts ->
            listOf(SearchOption(null, "全部账户")) + accounts.map {
                SearchOption(it.id, it.name + if (it.isArchived) "（已归档）" else "")
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), listOf(SearchOption(null, "全部账户")))

    val categories = categoryRepository.observeAll()
        .map { categories ->
            listOf(SearchOption(null, "全部分类")) + categories.sortedWith(
                compareBy<Category>({ it.isArchived }, { it.sortOrder }, { it.name }),
            ).map {
                SearchOption(it.id, it.name + if (it.isArchived) "（已归档）" else "")
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), listOf(SearchOption(null, "全部分类")))

    val results: Flow<PagingData<SearchResultUi>> =
        combine(query.debounce(QUERY_DEBOUNCE_MS), entryType, accountId, categoryId) {
                text, type, account, category ->
            TransactionSearchFilter(
                query = text,
                entryType = type,
                accountId = account,
                categoryId = category,
            )
        }
            .flatMapLatest(transactionRepository::observeSearchResults)
            .map { page -> page.map { it.toSearchResultUi() } }
            .cachedIn(viewModelScope)

    fun setQuery(value: String) {
        savedStateHandle[KEY_QUERY] = value
    }

    fun setEntryType(value: SearchEntryType) {
        savedStateHandle[KEY_ENTRY_TYPE] = value.name
    }

    fun setAccount(id: String?) {
        savedStateHandle[KEY_ACCOUNT] = id
    }

    fun setCategory(id: String?) {
        savedStateHandle[KEY_CATEGORY] = id
    }

    fun clearFilters() {
        savedStateHandle[KEY_ENTRY_TYPE] = SearchEntryType.ALL.name
        savedStateHandle[KEY_ACCOUNT] = null
        savedStateHandle[KEY_CATEGORY] = null
    }

    private fun TransactionListItem.toSearchResultUi(): SearchResultUi {
        val date = dateProvider.toLocalDate(transaction.occurredAt)
        return SearchResultUi(
            dayKey = date.toString(),
            dayLabel = dayLabel(date),
            row = toTransactionRowUi(),
        )
    }

    private fun dayLabel(date: LocalDate): String {
        val today = dateProvider.toLocalDate(clock.nowMillis())
        val relative = when (date) {
            today -> "今天 · "
            today.minusDays(1) -> "昨天 · "
            else -> ""
        }
        return "$relative${date.monthValue}月${date.dayOfMonth}日 ${WeekdayLabels.short(date.dayOfWeek)}"
    }

    private companion object {
        const val QUERY_DEBOUNCE_MS = 300L
        const val KEY_QUERY = "search.query"
        const val KEY_ENTRY_TYPE = "search.entryType"
        const val KEY_ACCOUNT = "search.accountId"
        const val KEY_CATEGORY = "search.categoryId"
    }
}
