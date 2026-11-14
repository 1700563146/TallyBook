package com.tallybook.feature.settings.manage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tallybook.core.data.repository.AccountRepository
import com.tallybook.core.model.money.AmountParser
import com.tallybook.core.model.money.CurrencyCode
import com.tallybook.core.model.money.Money
import com.tallybook.core.model.transaction.Account
import com.tallybook.core.model.transaction.AccountType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.UUID
import javax.inject.Inject

data class AccountRowUi(
    val id: String,
    val name: String,
    val typeLabel: String,
    val balanceLabel: String,
    val isArchived: Boolean,
)

data class ManageAccountsUiState(
    val accounts: List<AccountRowUi> = emptyList(),
    val editing: AccountEditDialog? = null,
    val message: String? = null,
)

data class AccountEditDialog(
    val accountId: String?,
    val name: String,
    val type: AccountType,
    val initialBalanceText: String,
)

/**
 * Account management.
 *
 * Same rule as categories: used accounts are archived, not deleted. Deleting one would take every
 * transaction recorded against it out of the ledger.
 */
@HiltViewModel
class ManageAccountsViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ManageAccountsUiState())
    val uiState: StateFlow<ManageAccountsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            accountRepository.observeAll().collect { accounts ->
                _uiState.update { state ->
                    state.copy(
                        accounts = accounts.sortedWith(
                            compareBy({ it.isArchived }, { it.sortOrder }, { it.name }),
                        ).map { account ->
                            AccountRowUi(
                                id = account.id,
                                name = account.name,
                                typeLabel = typeLabelOf(account.type),
                                balanceLabel = "初始 ${account.initialBalance.format()}",
                                isArchived = account.isArchived,
                            )
                        },
                    )
                }
            }
        }
    }

    fun startAdd() {
        _uiState.update {
            it.copy(
                editing = AccountEditDialog(
                    accountId = null,
                    name = "",
                    type = AccountType.CASH,
                    initialBalanceText = "0",
                ),
            )
        }
    }

    fun startEdit(row: AccountRowUi) {
        viewModelScope.launch {
            val account = accountRepository.observeAll().first().firstOrNull { it.id == row.id }
                ?: return@launch
            _uiState.update {
                it.copy(
                    editing = AccountEditDialog(
                        accountId = account.id,
                        name = account.name,
                        type = account.type,
                        initialBalanceText = majorUnits(account) ,
                    ),
                )
            }
        }
    }

    fun dismissDialog() {
        _uiState.update { it.copy(editing = null) }
    }

    fun updateDraft(name: String? = null, type: AccountType? = null, balance: String? = null) {
        _uiState.update { state ->
            val draft = state.editing ?: return@update state
            state.copy(
                editing = draft.copy(
                    name = name ?: draft.name,
                    type = type ?: draft.type,
                    initialBalanceText = balance ?: draft.initialBalanceText,
                ),
            )
        }
    }

    fun submit() {
        val draft = _uiState.value.editing ?: return
        val trimmed = draft.name.trim()
        if (trimmed.isEmpty()) return

        val minor = parseMinor(draft.initialBalanceText)
        if (minor == null) {
            _uiState.update { it.copy(message = "初始余额请填数字，例如 1200.50") }
            return
        }

        viewModelScope.launch {
            val all = accountRepository.observeAll().first()
            if (all.any { it.id != draft.accountId && it.name == trimmed }) {
                _uiState.update { it.copy(message = "已经有同名账户了") }
                return@launch
            }
            val original = all.firstOrNull { it.id == draft.accountId }
            accountRepository.upsert(
                Account(
                    id = draft.accountId ?: UUID.randomUUID().toString(),
                    name = trimmed,
                    type = draft.type,
                    currency = original?.currency ?: CurrencyCode.CNY,
                    initialBalance = Money(minor, original?.currency ?: CurrencyCode.CNY),
                    isArchived = original?.isArchived ?: false,
                    sortOrder = original?.sortOrder ?: 0,
                ),
            )
            _uiState.update { it.copy(editing = null, message = "已保存「$trimmed」") }
        }
    }

    fun toggleArchive(row: AccountRowUi) {
        viewModelScope.launch {
            val account = accountRepository.observeAll().first().firstOrNull { it.id == row.id }
                ?: return@launch
            accountRepository.upsert(account.copy(isArchived = !account.isArchived))
            _uiState.update {
                it.copy(
                    message = if (account.isArchived) {
                        "已恢复「${account.name}」"
                    } else {
                        "已归档「${account.name}」"
                    },
                )
            }
        }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(message = null) }
    }

    private fun typeLabelOf(type: AccountType): String = when (type) {
        AccountType.CASH -> "现金"
        AccountType.DEBIT_CARD -> "储蓄卡"
        AccountType.CREDIT_CARD -> "信用卡"
        AccountType.E_WALLET -> "电子钱包"
        AccountType.INVESTMENT -> "投资"
        AccountType.OTHER -> "其他"
    }

    private fun majorUnits(account: Account): String =
        BigDecimal.valueOf(account.initialBalance.minorUnits).movePointLeft(2).toPlainString()

    /**
     * Parses "1200", "1200.5", "-30.00" into minor units.
     *
     * Returns null for anything that is not a plain number so the dialog can say so, rather than
     * silently writing 0 — a wrong initial balance skews every balance in the app.
     */
    // Delegated to a tested pure function: a silent mis-parse here would skew every balance.
    private fun parseMinor(text: String): Long? = AmountParser.parseMinorUnits(text)
}
