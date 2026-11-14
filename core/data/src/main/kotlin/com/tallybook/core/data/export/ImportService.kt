package com.tallybook.core.data.export

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.tallybook.core.common.clock.Clock
import com.tallybook.core.database.TallyDatabase
import com.tallybook.core.database.dao.AccountDao
import com.tallybook.core.database.dao.BudgetDao
import com.tallybook.core.database.dao.CategoryDao
import com.tallybook.core.database.dao.TransactionDao
import com.tallybook.core.database.entity.AccountEntity
import com.tallybook.core.database.entity.BudgetEntity
import com.tallybook.core.database.entity.CategoryEntity
import com.tallybook.core.database.entity.TransactionEntity
import com.tallybook.core.database.entity.TransactionTagEntity
import com.tallybook.core.model.transaction.AccountType
import com.tallybook.core.model.transaction.BudgetPeriod
import com.tallybook.core.model.transaction.TransactionKind
import com.tallybook.core.model.transaction.TransactionType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Raised for files that cannot be imported at all; the message is shown to the user. */
class ImportException(message: String) : Exception(message)

/**
 * Reads a backup produced by [ExportService] and merges it in.
 *
 * Three guarantees, in order of importance:
 *
 * 1. **Atomic.** Everything happens inside one Room transaction, so a failure (bad enum, foreign key,
 *    disk full) leaves the database exactly as it was. A half-imported ledger is worse than none.
 * 2. **Idempotent.** Rows whose id already exists are skipped, so restoring the same file twice — or
 *    importing on top of existing data — never doubles the user's spending.
 * 3. **Refuses before writing.** [preview] runs the same validation the apply step relies on, so the
 *    confirmation dialog can show exactly what will happen and say "no" when it must.
 */
@Singleton
class ImportService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: TallyDatabase,
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val budgetDao: BudgetDao,
    private val clock: Clock,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Reads and validates the file without writing anything. */
    suspend fun preview(uri: Uri): ImportEnvelope = withContext(Dispatchers.IO) {
        val envelope = read(uri)

        if (envelope.schemaVersion > ExportEnvelope.CURRENT_SCHEMA_VERSION) {
            throw ImportException(
                "这个备份来自更新版本的 App（v${envelope.schemaVersion}），当前版本无法读取。",
            )
        }

        val ids = envelope.transactions.map { it.id }
        // Chunked to stay under SQLite's bound-variable limit on large backups.
        val existingIds = ids.chunked(SQL_CHUNK).flatMap { transactionDao.existingIds(it) }.toSet()
        val existingAccounts = accountDao.observeAll().first().map { it.id }.toSet()
        val existingCategories = categoryDao.observeAll().first().map { it.id }.toSet()

        val preview = ImportPlanner.plan(
            envelope = envelope,
            existingTransactionIds = existingIds,
            existingAccountIds = existingAccounts,
            existingCategoryIds = existingCategories,
            ledgerCurrency = LEDGER_CURRENCY,
        )
        val remap = buildRemap(envelope)
        ImportEnvelope(envelope, existingIds, preview, remap)
    }

    /** Applies a previously previewed file. */
    suspend fun apply(loaded: ImportEnvelope): ImportResult = withContext(Dispatchers.IO) {
        val envelope = loaded.envelope
        val preview = loaded.preview
        if (preview.hasBlockingProblems) {
            throw ImportException(
                "备份不完整或存在冲突（账户 ${preview.missingAccountIds.size}、" +
                    "分类 ${preview.missingCategoryIds.size}、重复 ${preview.duplicateIds.size}、" +
                    "无效 ${preview.invalidRecordIds.size}），无法导入。",
            )
        }

        val now = clock.nowMillis()
        val remap = loaded.remap
        val newAccounts = ImportMatcher.newAccounts(envelope, remap)
        val newCategories = ImportMatcher.newCategories(envelope, remap)

        database.withTransaction {
            orderCategoriesForInsert(newCategories).forEach {
                categoryDao.upsert(it.toEntity(remap))
            }
            newAccounts.forEach { accountDao.upsert(it.toEntity(now)) }
            envelope.budgets.forEach {
                budgetDao.upsertPreservingCreatedAt(it.toEntity(now, remap))
            }

            val exportRows = ImportPlanner.rowsToInsert(envelope, loaded.existingIds)
            val rows = exportRows.map { it.toEntity(now, remap) }
            rows.chunked(SQL_CHUNK).forEach { transactionDao.upsertAll(it) }
            val tags = exportRows.flatMap { row ->
                row.tags
                    .asSequence()
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .distinct()
                    .map { tag -> TransactionTagEntity(row.id, tag) }
                    .toList()
            }
            if (tags.isNotEmpty()) {
                tags.chunked(SQL_CHUNK).forEach { transactionDao.upsertTags(it) }
            }

            ImportResult(
                inserted = rows.size,
                skipped = preview.duplicates,
                accountsUpserted = newAccounts.size,
                categoriesUpserted = newCategories.size,
                budgetsUpserted = envelope.budgets.size,
            )
        }
    }

    /**
     * Matches incoming accounts/categories against local rows by name + type.
     *
     * Without this, restoring a backup after a reinstall doubles every category, because the fresh
     * install seeded them under different ids.
     */
    private suspend fun buildRemap(envelope: ExportEnvelope): ImportRemap {
        val existingAccounts = accountDao.observeAll().first().map {
            it.id to NaturalKey(it.name, it.type.name)
        }
        val existingCategories = categoryDao.observeAll().first().map {
            it.id to NaturalKey(it.name, it.type.name)
        }
        return ImportMatcher.plan(envelope, existingAccounts, existingCategories)
    }

    private fun read(uri: Uri): ExportEnvelope {
        val text = context.contentResolver.openInputStream(uri)?.use {
            it.readBytes().toString(Charsets.UTF_8)
        } ?: throw ImportException("无法读取所选文件")

        return runCatching { json.decodeFromString(ExportEnvelope.serializer(), text.removePrefix("\uFEFF")) }
            .getOrElse { throw ImportException("这不是 TallyBook 的 JSON 备份，或文件已损坏。") }
    }

    private fun ExportCategory.toEntity(remap: ImportRemap) = CategoryEntity(
        id = id,
        name = name,
        type = enumValueOf(type),
        parentId = remap.category(parentId),
        iconKey = iconKey,
        colorArgb = colorArgb,
        sortOrder = sortOrder,
        isArchived = isArchived,
    )

    private fun ExportAccount.toEntity(now: Long) = AccountEntity(
        id = id,
        name = name,
        type = runCatching { AccountType.valueOf(type) }.getOrDefault(AccountType.OTHER),
        currency = currency,
        initialBalanceMinor = initialBalanceMinor,
        isArchived = isArchived,
        sortOrder = sortOrder,
        createdAt = createdAt.takeIf { it > 0 } ?: now,
        updatedAt = updatedAt.takeIf { it > 0 } ?: now,
    )

    private fun ExportBudget.toEntity(now: Long, remap: ImportRemap) = BudgetEntity(
        id = id,
        name = name,
        categoryId = remap.category(categoryId),
        period = runCatching { BudgetPeriod.valueOf(period) }.getOrDefault(BudgetPeriod.MONTHLY),
        limitMinor = limitMinor,
        currency = currency,
        includeSubcategories = includeSubcategories,
        isActive = isActive,
        createdAt = createdAt.takeIf { it > 0 } ?: now,
    )

    private fun ExportTransaction.toEntity(now: Long, remap: ImportRemap) = TransactionEntity(
        id = id,
        type = runCatching { TransactionType.valueOf(type) }
            .getOrElse { throw ImportException("记录 $id 的类型无法识别：$type") },
        kind = runCatching { TransactionKind.valueOf(kind) }
            .getOrElse { throw ImportException("记录 $id 的类型无法识别：$kind") },
        amountMinor = amountMinor,
        currency = currency,
        accountId = remap.account(accountId),
        categoryId = remap.category(categoryId),
        occurredAt = occurredAt,
        note = note,
        counterAccountId = counterAccountId?.let { remap.account(it) },
        refundOfId = refundOfId,
        createdAt = createdAt.takeIf { it > 0 } ?: occurredAt,
        updatedAt = updatedAt.takeIf { it > 0 } ?: now,
        deletedAt = deletedAt,
    )

    private fun orderCategoriesForInsert(
        categories: List<ExportCategory>,
    ): List<ExportCategory> {
        val byId = categories.associateBy { it.id }
        val result = ArrayList<ExportCategory>(categories.size)
        val visiting = mutableSetOf<String>()
        val visited = mutableSetOf<String>()

        fun visit(category: ExportCategory) {
            if (category.id in visited) return
            if (!visiting.add(category.id)) {
                throw ImportException("分类层级存在循环引用，无法导入。")
            }
            category.parentId?.let(byId::get)?.let(::visit)
            visiting.remove(category.id)
            visited += category.id
            result += category
        }

        categories.forEach(::visit)
        return result
    }

    private companion object {
        /** SQLite's default limit is 999 bound variables; stay well under it. */
        const val SQL_CHUNK = 400
        const val LEDGER_CURRENCY = "CNY"

        fun enumValueOf(value: String): TransactionType =
            runCatching { TransactionType.valueOf(value) }.getOrDefault(TransactionType.EXPENSE)
    }
}

/** A parsed file plus what it would change — carried from preview to apply so parsing happens once. */
data class ImportEnvelope(
    val envelope: ExportEnvelope,
    val existingIds: Set<String>,
    val preview: ImportPreview,
    val remap: ImportRemap = ImportRemap(),
)
