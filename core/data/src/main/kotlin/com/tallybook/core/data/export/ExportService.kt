package com.tallybook.core.data.export

import android.content.Context
import android.net.Uri
import com.tallybook.core.common.clock.Clock
import com.tallybook.core.data.repository.AccountRepository
import com.tallybook.core.data.repository.BudgetRepository
import com.tallybook.core.data.repository.CategoryRepository
import com.tallybook.core.data.repository.TransactionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds and writes backups.
 *
 * The file is written through the `ContentResolver` of a user-chosen SAF URI: the app never needs
 * storage permissions, and the user keeps control of where the data lands (device, SD card, cloud
 * provider). That is the whole reason export is the first Phase 4 feature — it is the cheapest way to
 * give a user real ownership of their ledger.
 */
@Singleton
class ExportService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val budgetRepository: BudgetRepository,
    private val clock: Clock,
) {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        // Unknown keys are tolerated so a file written by a *newer* build can still be read by an
        // older one where the difference does not matter.
        ignoreUnknownKeys = true
    }

    /** Collects everything into the versioned envelope. */
    suspend fun buildEnvelope(): ExportEnvelope = withContext(Dispatchers.IO) {
        val transactions = transactionRepository.getAllForBackup()
        val accounts = accountRepository.observeAll().first()
        val categories = categoryRepository.observeAll().first()
        val budgets = budgetRepository.observeActive().first()

        ExportEnvelope(
            exportedAt = clock.nowMillis(),
            currency = transactions.firstOrNull()?.amount?.currency?.value ?: "CNY",
            accounts = accounts.map {
                ExportAccount(
                    id = it.id,
                    name = it.name,
                    type = it.type.name,
                    currency = it.currency.value,
                    initialBalanceMinor = it.initialBalance.minorUnits,
                    isArchived = it.isArchived,
                    sortOrder = it.sortOrder,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt,
                )
            },
            categories = categories.map {
                ExportCategory(
                    id = it.id,
                    name = it.name,
                    type = it.type.name,
                    parentId = it.parentId,
                    iconKey = it.iconKey,
                    colorArgb = it.colorArgb,
                    sortOrder = it.sortOrder,
                    isArchived = it.isArchived,
                )
            },
            transactions = transactions.map {
                ExportTransaction(
                    id = it.id.value,
                    type = it.type.name,
                    kind = it.kind.name,
                    amountMinor = it.amount.minorUnits,
                    currency = it.amount.currency.value,
                    accountId = it.accountId,
                    categoryId = it.categoryId,
                    occurredAt = it.occurredAt,
                    note = it.note,
                    counterAccountId = it.counterAccountId,
                    refundOfId = it.refundOfId,
                    deletedAt = it.deletedAt,
                    tags = it.tags,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt,
                )
            },
            budgets = budgets.map {
                ExportBudget(
                    id = it.id,
                    name = it.name,
                    categoryId = it.categoryId,
                    period = it.period.name,
                    limitMinor = it.limit.minorUnits,
                    currency = it.limit.currency.value,
                    includeSubcategories = it.includeSubcategories,
                    isActive = it.isActive,
                    createdAt = it.createdAt,
                )
            },
        )
    }

    /** @return number of transactions written. */
    suspend fun exportJson(uri: Uri): Int = withContext(Dispatchers.IO) {
        val envelope = buildEnvelope()
        writeText(uri, json.encodeToString(ExportEnvelope.serializer(), envelope))
        envelope.transactions.size
    }

    /** @return number of transactions written. */
    suspend fun exportCsv(uri: Uri): Int = withContext(Dispatchers.IO) {
        val envelope = buildEnvelope()
        writeText(uri, CsvExport.write(envelope))
        envelope.transactions.size
    }

    /**
     * Writes with `"wt"` (truncate), not the default append mode.
     *
     * The default openOutputStream mode on some providers appends, which would silently corrupt a
     * second export into the same file.
     */
    private fun writeText(uri: Uri, text: String) {
        val resolver = context.contentResolver
        resolver.openOutputStream(uri, "wt")?.use { stream ->
            stream.write(text.toByteArray(Charsets.UTF_8))
            stream.flush()
        } ?: error("无法写入所选位置")
    }
}
