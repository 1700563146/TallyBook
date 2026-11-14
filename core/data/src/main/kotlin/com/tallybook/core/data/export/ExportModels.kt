package com.tallybook.core.data.export

import kotlinx.serialization.Serializable

/**
 * Versioned export envelope.
 *
 * `schemaVersion` is not decoration: import will have to read files produced by older builds, and
 * without a version the only options are "guess" or "refuse". Bumping it is cheap now and impossible
 * to retrofit onto files that were already written without it.
 */
@Serializable
data class ExportEnvelope(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val exportedAt: Long,
    val currency: String,
    val accounts: List<ExportAccount> = emptyList(),
    val categories: List<ExportCategory> = emptyList(),
    val transactions: List<ExportTransaction> = emptyList(),
    val budgets: List<ExportBudget> = emptyList(),
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 2
    }
}

@Serializable
data class ExportAccount(
    val id: String,
    val name: String,
    val type: String,
    val currency: String,
    /** Minor units; the reader must apply the currency's exponent. */
    val initialBalanceMinor: Long,
    val isArchived: Boolean,
    val sortOrder: Int = 0,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

@Serializable
data class ExportCategory(
    val id: String,
    val name: String,
    val type: String,
    val parentId: String? = null,
    val iconKey: String? = null,
    val colorArgb: Long? = null,
    val sortOrder: Int = 0,
    val isArchived: Boolean = false,
)

@Serializable
data class ExportTransaction(
    val id: String,
    val type: String,
    val kind: String,
    val amountMinor: Long,
    val currency: String,
    val accountId: String,
    val categoryId: String? = null,
    /** Epoch millis. Kept as an instant, not a date string, so the reader can re-zone it. */
    val occurredAt: Long,
    val note: String? = null,
    val counterAccountId: String? = null,
    val refundOfId: String? = null,
    val deletedAt: Long? = null,
    val tags: List<String> = emptyList(),
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

@Serializable
data class ExportBudget(
    val id: String,
    val name: String,
    val categoryId: String? = null,
    val period: String,
    val limitMinor: Long,
    val currency: String,
    val includeSubcategories: Boolean,
    val isActive: Boolean = true,
    val createdAt: Long = 0,
)
