package com.tallybook.core.data.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Import planning tests.
 *
 * Two rules carry all the risk here: re-importing must be idempotent (or a user restoring twice ends
 * up with doubled spending), and dangling account references must be caught before writing (or the
 * import dies halfway through a transaction).
 */
class ImportPlannerTest {

    private fun tx(id: String, accountId: String = "a1") = ExportTransaction(
        id = id,
        type = "EXPENSE",
        kind = "NORMAL",
        amountMinor = 100,
        currency = "CNY",
        accountId = accountId,
        categoryId = null,
        occurredAt = 1L,
    )

    private fun envelope(
        transactions: List<ExportTransaction>,
        accounts: List<ExportAccount> = listOf(ExportAccount("a1", "现金", "CASH", "CNY", 0, false)),
        categories: List<ExportCategory> = emptyList(),
    ) = ExportEnvelope(
        exportedAt = 0L,
        currency = "CNY",
        accounts = accounts,
        categories = categories,
        transactions = transactions,
    )

    @Test
    fun `counting a fresh file`() {
        val preview = ImportPlanner.plan(
            envelope(listOf(tx("t1"), tx("t2"))),
            existingTransactionIds = emptySet(),
            existingAccountIds = emptySet(),
        )
        assertEquals(2, preview.transactions)
        assertEquals(0, preview.duplicates)
        assertFalse(preview.isNoOp)
        assertFalse(preview.hasBlockingProblems)
    }

    @Test
    fun `rows already present count as duplicates and are skipped`() {
        val preview = ImportPlanner.plan(
            envelope(listOf(tx("t1"), tx("t2"))),
            existingTransactionIds = setOf("t1"),
            existingAccountIds = setOf("a1"),
        )
        assertEquals(1, preview.duplicates)
        assertEquals(
            listOf("t2"),
            ImportPlanner.rowsToInsert(envelope(listOf(tx("t1"), tx("t2"))), setOf("t1")).map { it.id },
        )
    }

    @Test
    fun `importing an already imported file is a no-op`() {
        val file = envelope(listOf(tx("t1"), tx("t2")))
        val preview = ImportPlanner.plan(file, setOf("t1", "t2"), setOf("a1"))
        assertTrue("re-importing twice must not duplicate", preview.isNoOp)
    }

    @Test
    fun `an account defined in the file satisfies the reference`() {
        val preview = ImportPlanner.plan(
            envelope(listOf(tx("t1", accountId = "a-new")), accounts = listOf(
                ExportAccount("a-new", "新账户", "CASH", "CNY", 0, false),
            )),
            existingTransactionIds = emptySet(),
            existingAccountIds = emptySet(),
        )
        assertFalse(preview.hasBlockingProblems)
    }

    @Test
    fun `a dangling account reference blocks the import`() {
        val preview = ImportPlanner.plan(
            envelope(listOf(tx("t1", accountId = "a-missing"))),
            existingTransactionIds = emptySet(),
            existingAccountIds = emptySet(),
        )
        assertTrue(preview.hasBlockingProblems)
        assertEquals(setOf("a-missing"), preview.missingAccountIds)
    }

    @Test
    fun `accounts present locally are enough`() {
        val preview = ImportPlanner.plan(
            envelope(listOf(tx("t1", accountId = "a-existing"))),
            existingTransactionIds = emptySet(),
            existingAccountIds = setOf("a-existing"),
        )
        assertFalse(preview.hasBlockingProblems)
    }

    @Test
    fun `schema version is surfaced so the caller can reject the future`() {
        val preview = ImportPlanner.plan(
            envelope(listOf(tx("t1"))).copy(schemaVersion = 99),
            existingTransactionIds = emptySet(),
            existingAccountIds = setOf("a1"),
        )
        assertEquals(99, preview.schemaVersion)
    }

    @Test
    fun `a transfer counter account is validated too`() {
        val preview = ImportPlanner.plan(
            envelope(
                listOf(
                    tx("t1").copy(
                        kind = "TRANSFER",
                        counterAccountId = "a-missing",
                    ),
                ),
            ),
            existingTransactionIds = emptySet(),
            existingAccountIds = emptySet(),
        )

        assertEquals(setOf("a-missing"), preview.missingAccountIds)
        assertTrue(preview.hasBlockingProblems)
    }

    @Test
    fun `a transaction referencing an undeclared category is blocked`() {
        val preview = ImportPlanner.plan(
            envelope(listOf(tx("t1").copy(categoryId = "c-missing"))),
            existingTransactionIds = emptySet(),
            existingAccountIds = setOf("a1"),
        )

        assertEquals(setOf("c-missing"), preview.missingCategoryIds)
        assertTrue(preview.hasBlockingProblems)
    }

    @Test
    fun `an archived category is valid when declared in the backup`() {
        val category = ExportCategory(
            id = "c-old",
            name = "旧分类",
            type = "EXPENSE",
            isArchived = true,
        )
        val preview = ImportPlanner.plan(
            envelope(
                listOf(tx("t1").copy(categoryId = category.id)),
                categories = listOf(category),
            ),
            existingTransactionIds = emptySet(),
            existingAccountIds = setOf("a1"),
        )

        assertFalse(preview.hasBlockingProblems)
    }

    @Test
    fun `duplicate ids in one file are blocking`() {
        val preview = ImportPlanner.plan(
            envelope(listOf(tx("same"), tx("same"))),
            existingTransactionIds = emptySet(),
            existingAccountIds = setOf("a1"),
        )

        assertEquals(setOf("same"), preview.duplicateIds)
    }

    @Test
    fun `invalid amounts and currencies are rejected before writing`() {
        val preview = ImportPlanner.plan(
            envelope(
                listOf(
                    tx("too-large").copy(amountMinor = Long.MAX_VALUE),
                    tx("wrong-currency").copy(currency = "USD"),
                ),
            ),
            existingTransactionIds = emptySet(),
            existingAccountIds = setOf("a1"),
        )

        assertEquals(setOf("too-large"), preview.invalidRecordIds)
        assertTrue(preview.currencyMismatch)
    }

    @Test
    fun `a refund must point at a transaction present in the file or locally`() {
        val preview = ImportPlanner.plan(
            envelope(
                listOf(
                    tx("refund").copy(
                        type = "INCOME",
                        refundOfId = "missing-expense",
                    ),
                ),
            ),
            existingTransactionIds = emptySet(),
            existingAccountIds = setOf("a1"),
        )

        assertEquals(setOf("missing-expense"), preview.missingRefundIds)
    }
}
