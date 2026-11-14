package com.tallybook.feature.transactions.recycle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tallybook.core.data.repository.TransactionListItem
import com.tallybook.core.data.repository.TransactionRepository
import com.tallybook.core.model.transaction.TransactionKind
import com.tallybook.core.model.transaction.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/**
 * Recycle bin.
 *
 * Two destructive levels, deliberately named differently in the UI:
 * - **恢复** (restore) is cheap and reversible.
 * - **彻底删除 / 清空** (purge) is irreversible, so it always sits behind a confirmation dialog.
 *
 * The screen never purges automatically: a recycle bin that quietly empties itself after N days
 * would defeat the reason it exists.
 */
@HiltViewModel
class RecycleBinViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val zoneId: ZoneId,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecycleBinUiState())
    val uiState: StateFlow<RecycleBinUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            transactionRepository.observeDeletedItems().collect { items ->
                _uiState.update {
                    it.copy(rows = items.map { item -> item.toRow() }, isLoading = false)
                }
            }
        }
    }

    fun restore(id: String) {
        viewModelScope.launch {
            runCatching { transactionRepository.restore(id) }
                .onSuccess { _uiState.update { state -> state.copy(message = "已恢复") } }
                .onFailure { error ->
                    _uiState.update { state -> state.copy(message = error.message ?: "恢复失败") }
                }
        }
    }

    fun purge(id: String) {
        viewModelScope.launch {
            runCatching { transactionRepository.purge(id) }
                .onSuccess { _uiState.update { state -> state.copy(message = "已彻底删除") } }
                .onFailure { error ->
                    _uiState.update { state -> state.copy(message = error.message ?: "删除失败") }
                }
        }
    }

    fun purgeAll() {
        viewModelScope.launch {
            runCatching { transactionRepository.purgeAll() }
                .onSuccess { count ->
                    _uiState.update { state ->
                        state.copy(message = if (count > 0) "已清空 $count 条记录" else "回收站已经是空的")
                    }
                }
                .onFailure { error ->
                    _uiState.update { state -> state.copy(message = error.message ?: "清空失败") }
                }
        }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(message = null) }
    }

    private fun TransactionListItem.toRow(): DeletedRowUi {
        val transaction = transaction
        val isTransfer = transaction.kind == TransactionKind.TRANSFER
        val isExpense = transaction.type == TransactionType.EXPENSE
        return DeletedRowUi(
            id = transaction.id.value,
            categoryName = if (isTransfer) "转账" else categoryName,
            note = transaction.note.orEmpty(),
            accountName = accountName,
            amountLabel = when {
                isTransfer -> transaction.amount.format()
                isExpense -> "-${transaction.amount.format()}"
                else -> "+${transaction.amount.format()}"
            },
            deletedAtLabel = DELETED_AT_FORMAT.format(
                Instant.ofEpochMilli(transaction.updatedAt).atZone(zoneId),
            ),
            isExpense = isExpense && !isTransfer,
            iconKey = categoryIconKey,
            colorArgb = categoryColorArgb,
        )
    }

    private companion object {
        val DELETED_AT_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("M月d日 HH:mm 删除", Locale.getDefault())
    }
}
