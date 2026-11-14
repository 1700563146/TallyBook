package com.tallybook.core.data.export

/**
 * Maps ids from a backup file onto the ids that already exist locally.
 *
 * Why this is needed: a backup carries the ids it was exported with, but a *fresh install* seeds its
 * accounts and categories with brand-new ids. Importing by id alone therefore duplicated every
 * category ("餐饮" appearing twice) whenever the user restored after reinstalling — the exact flow a
 * backup exists for.
 *
 * Matching by natural key (name + type) instead keeps the user's ledger single-copy, and the mapping
 * table is then applied to the transactions so their foreign keys follow.
 */
data class ImportRemap(
    val accounts: Map<String, String> = emptyMap(),
    val categories: Map<String, String> = emptyMap(),
) {
    val isEmpty: Boolean get() = accounts.isEmpty() && categories.isEmpty()

    fun account(id: String): String = accounts[id] ?: id
    fun category(id: String?): String? = id?.let { categories[it] ?: it }
}

/** A candidate row for natural-key matching. */
data class NaturalKey(val name: String, val type: String)

/** Builds the remap from what the file declares and what already exists. */
object ImportMatcher {

    fun plan(
        envelope: ExportEnvelope,
        existingAccounts: List<Pair<String, NaturalKey>>,
        existingCategories: List<Pair<String, NaturalKey>>,
    ): ImportRemap {
        val accountByKey = existingAccounts.associate { (id, key) -> key to id }
        val categoryByKey = existingCategories.associate { (id, key) -> key to id }

        val accountRemap = mutableMapOf<String, String>()
        envelope.accounts.forEach { incoming ->
            accountByKey[NaturalKey(incoming.name, incoming.type)]?.let { existingId ->
                if (existingId != incoming.id) accountRemap[incoming.id] = existingId
            }
        }

        val categoryRemap = mutableMapOf<String, String>()
        envelope.categories.forEach { incoming ->
            categoryByKey[NaturalKey(incoming.name, incoming.type)]?.let { existingId ->
                if (existingId != incoming.id) categoryRemap[incoming.id] = existingId
            }
        }

        return ImportRemap(accountRemap, categoryRemap)
    }

    /** Accounts from the file that are genuinely new (no local row with the same name and type). */
    fun newAccounts(envelope: ExportEnvelope, remap: ImportRemap): List<ExportAccount> =
        envelope.accounts.filter { it.id !in remap.accounts }

    fun newCategories(envelope: ExportEnvelope, remap: ImportRemap): List<ExportCategory> =
        envelope.categories.filter { it.id !in remap.categories }
}
