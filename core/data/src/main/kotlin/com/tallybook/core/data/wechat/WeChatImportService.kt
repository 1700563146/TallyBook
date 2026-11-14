package com.tallybook.core.data.wechat

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.tallybook.core.common.clock.Clock
import com.tallybook.core.database.TallyDatabase
import com.tallybook.core.database.dao.AccountDao
import com.tallybook.core.database.dao.CategoryDao
import com.tallybook.core.database.dao.TransactionDao
import com.tallybook.core.database.entity.AccountEntity
import com.tallybook.core.database.entity.CategoryEntity
import com.tallybook.core.database.entity.TransactionEntity
import com.tallybook.core.database.entity.TransactionTagEntity
import com.tallybook.core.model.money.CurrencyCode
import com.tallybook.core.model.transaction.AccountType
import com.tallybook.core.model.transaction.TransactionKind
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class WeChatImportPreview(
    val totalRows: Int,
    val expenseCount: Int,
    val incomeCount: Int,
    val refundCount: Int,
    val matchedRefundCount: Int,
    val neutralCount: Int,
    val unmatchedRefundCount: Int,
    val unmatchedRefundMinor: Long,
    val duplicateIdCount: Int,
    val invalidRowCount: Int,
    val existingCount: Int,
    val importableCount: Int,
    val declaredExpenseMinor: Long?,
    val declaredIncomeMinor: Long?,
    val parsedExpenseMinor: Long,
    val parsedIncomeMinor: Long,
    val targetAccountName: String,
) {
    val declaredTotalsMatch: Boolean
        get() {
            val expenseMatches = declaredExpenseMinor == null ||
                declaredExpenseMinor == parsedExpenseMinor
            val incomeMatches = declaredIncomeMinor == null ||
                declaredIncomeMinor == parsedIncomeMinor + unmatchedRefundMinor
            return expenseMatches && incomeMatches
        }
}

data class WeChatImportEnvelope(
    val document: WeChatBillDocument,
    val plan: WeChatImportPlan,
    val existingIds: Set<String>,
    val preview: WeChatImportPreview,
)

data class WeChatImportResult(
    val inserted: Int,
    val skippedExisting: Int,
    val skippedNeutral: Int,
    val skippedUnmatchedRefunds: Int,
    val accountCreated: Boolean,
    val categoriesCreated: Int,
)

@Singleton
class WeChatImportService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: TallyDatabase,
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val parser: WeChatBillParser,
    private val clock: Clock,
) {

    suspend fun preview(uri: Uri): WeChatImportEnvelope = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use(::readLimited)
            ?: throw WeChatBillFormatException("无法读取所选 Excel 文件")
        val document = parser.parse(ByteArrayInputStream(bytes))
        val plan = WeChatImportPlanner.plan(document)
        val existingIds = plan.transactions
            .map(PlannedWeChatTransaction::id)
            .chunked(SQL_CHUNK)
            .flatMap { transactionDao.existingIds(it) }
            .toSet()
        val importable = plan.transactions.filter { it.id !in existingIds }
        val expenseMinor = plan.transactions
            .filter { it.type == com.tallybook.core.model.transaction.TransactionType.EXPENSE }
            .sumOf(PlannedWeChatTransaction::amountMinor)
        val incomeMinor = plan.transactions
            .filter { it.type == com.tallybook.core.model.transaction.TransactionType.INCOME }
            .sumOf(PlannedWeChatTransaction::amountMinor)
        val preview = WeChatImportPreview(
            totalRows = document.rows.size + document.invalidRowCount,
            expenseCount = plan.transactions.count {
                it.type == com.tallybook.core.model.transaction.TransactionType.EXPENSE
            },
            incomeCount = plan.transactions.count {
                it.type == com.tallybook.core.model.transaction.TransactionType.INCOME
            },
            refundCount = plan.transactions.count { it.refundOfId != null },
            matchedRefundCount = plan.transactions.count { it.refundOfId != null },
            neutralCount = plan.neutralRows.size,
            unmatchedRefundCount = plan.unmatchedRefunds.size,
            unmatchedRefundMinor = plan.unmatchedRefunds.sumOf(WeChatBillRow::amountMinor),
            duplicateIdCount = plan.duplicateIds.size,
            invalidRowCount = document.invalidRowCount,
            existingCount = existingIds.size,
            importableCount = importable.size,
            declaredExpenseMinor = document.declared.expenseMinor,
            declaredIncomeMinor = document.declared.incomeMinor,
            parsedExpenseMinor = expenseMinor,
            parsedIncomeMinor = incomeMinor,
            targetAccountName = WeChatImportPlanner.IMPORT_ACCOUNT_NAME,
        )
        WeChatImportEnvelope(
            document = document,
            plan = plan,
            existingIds = existingIds,
            preview = preview,
        )
    }

    suspend fun apply(loaded: WeChatImportEnvelope): WeChatImportResult =
        withContext(Dispatchers.IO) {
            val now = clock.nowMillis()
            val currentIds = loaded.plan.transactions
                .map(PlannedWeChatTransaction::id)
                .chunked(SQL_CHUNK)
                .flatMap { transactionDao.existingIds(it) }
                .toSet()
            val rowsToInsert = loaded.plan.transactions.filter { it.id !in currentIds }
            if (rowsToInsert.isEmpty()) {
                return@withContext WeChatImportResult(
                    inserted = 0,
                    skippedExisting = loaded.plan.transactions.size,
                    skippedNeutral = loaded.plan.neutralRows.size,
                    skippedUnmatchedRefunds = loaded.plan.unmatchedRefunds.size,
                    accountCreated = false,
                    categoriesCreated = 0,
                )
            }

            val existingAccounts = accountDao.observeAll().first()
            val existingCategories = categoryDao.observeAll().first()
            val targetAccount = existingAccounts.firstOrNull {
                it.name == WeChatImportPlanner.IMPORT_ACCOUNT_NAME ||
                    it.name == "微信"
            }
            val accountId = targetAccount?.id ?: WECHAT_ACCOUNT_ID
            val categoryMap = existingCategories
                .groupBy { it.name to it.type }
                .mapValues { (_, categories) ->
                    categories.firstOrNull { !it.isArchived } ?: categories.first()
                }
                .toMutableMap()
            val newCategories = linkedMapOf<Pair<String, com.tallybook.core.model.transaction.TransactionType>, WeChatCategory>()
            rowsToInsert.forEach { transaction ->
                val key = transaction.category.name to transaction.category.type
                if (key !in categoryMap && key !in newCategories) {
                    newCategories[key] = transaction.category
                }
            }

            database.withTransaction {
                if (targetAccount == null) {
                    accountDao.upsert(
                        AccountEntity(
                            id = accountId,
                            name = WeChatImportPlanner.IMPORT_ACCOUNT_NAME,
                            type = AccountType.E_WALLET,
                            currency = CurrencyCode.CNY.value,
                            initialBalanceMinor = 0,
                            createdAt = now,
                            updatedAt = now,
                        ),
                    )
                } else if (targetAccount.isArchived) {
                    accountDao.upsert(targetAccount.copy(isArchived = false, updatedAt = now))
                }

                newCategories.forEach { (key, category) ->
                    val id = stableCategoryId(key.first, key.second.name)
                    categoryDao.upsert(
                        CategoryEntity(
                            id = id,
                            name = category.name,
                            type = category.type,
                            iconKey = category.iconKey,
                            colorArgb = category.colorArgb,
                        ),
                    )
                    categoryMap[key] = categoryDao.getById(id)!!
                }
                categoryMap.values
                    .filter { it.isArchived }
                    .forEach { categoryDao.upsert(it.copy(isArchived = false)) }

                val entities = rowsToInsert.map { transaction ->
                    val category = categoryMap.getValue(
                        transaction.category.name to transaction.category.type,
                    )
                    transaction.toEntity(
                        accountId = accountId,
                        categoryId = category.id,
                        now = now,
                    )
                }
                entities.chunked(SQL_CHUNK).forEach { transactionDao.upsertAll(it) }

                val tags = rowsToInsert.flatMap { transaction ->
                    transaction.tags().map { TransactionTagEntity(transaction.id, it) }
                }
                if (tags.isNotEmpty()) {
                    tags.chunked(SQL_CHUNK).forEach { transactionDao.upsertTags(it) }
                }
            }

            WeChatImportResult(
                inserted = rowsToInsert.size,
                skippedExisting = currentIds.size,
                skippedNeutral = loaded.plan.neutralRows.size,
                skippedUnmatchedRefunds = loaded.plan.unmatchedRefunds.size,
                accountCreated = targetAccount == null,
                categoriesCreated = newCategories.size,
            )
        }

    private fun PlannedWeChatTransaction.toEntity(
        accountId: String,
        categoryId: String,
        now: Long,
    ): TransactionEntity = TransactionEntity(
        id = id,
        type = type,
        kind = TransactionKind.NORMAL,
        amountMinor = amountMinor,
        currency = CurrencyCode.CNY.value,
        accountId = accountId,
        categoryId = categoryId,
        occurredAt = source.occurredAt,
        note = buildNote(source),
        refundOfId = refundOfId,
        createdAt = now,
        updatedAt = now,
    )

    private fun PlannedWeChatTransaction.tags(): List<String> = buildList {
        add("微信导入")
        add("微信类型:${source.tradeType}")
        if (source.paymentMethod.isNotBlank() && source.paymentMethod != "/") {
            add("支付方式:${source.paymentMethod}")
        }
        if (source.status.isNotBlank()) add("状态:${source.status}")
        add("微信单号:${source.transactionId}")
        source.merchantOrderId?.let { add("商户单号:$it") }
        if (source.merchant.isNotBlank()) add("商户:${source.merchant}")
    }.map { it.take(MAX_TAG_LENGTH) }.distinct()

    private fun buildNote(row: WeChatBillRow): String = buildList {
        if (row.merchant.isNotBlank() && row.merchant != "/") add(row.merchant)
        if (row.product.isNotBlank() && row.product != "/" && row.product != row.merchant) {
            add(row.product)
        }
        row.note?.let { add(it) }
        if (row.paymentMethod.isNotBlank() && row.paymentMethod != "/") {
            add(row.paymentMethod)
        }
    }.joinToString(" · ").ifBlank { "微信支付账单" }

    private fun readLimited(input: java.io.InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > WeChatBillParser.MAX_FILE_BYTES) {
                throw WeChatBillFormatException("Excel 文件超过 20 MB，已停止导入")
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun stableCategoryId(name: String, type: String): String =
        UUID.nameUUIDFromBytes(
            "tallybook-wechat-category:$type:$name".toByteArray(StandardCharsets.UTF_8),
        ).toString()

    private companion object {
        const val SQL_CHUNK = 400
        const val MAX_TAG_LENGTH = 100
        val WECHAT_ACCOUNT_ID = UUID.nameUUIDFromBytes(
            "tallybook-wechat-account".toByteArray(StandardCharsets.UTF_8),
        ).toString()
    }
}
