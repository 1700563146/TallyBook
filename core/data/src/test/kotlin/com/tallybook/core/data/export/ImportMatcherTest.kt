package com.tallybook.core.data.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Natural-key matching tests.
 *
 * The failure this guards against is the one seen on a device: clear data, reinstall, restore the
 * backup — and every category appears twice, because the fresh install re-seeded them under new ids.
 * Nothing crashes; the ledger just quietly gains a second "餐饮".
 */
class ImportMatcherTest {

    private fun account(id: String, name: String = "现金", type: String = "CASH") =
        ExportAccount(id, name, type, "CNY", 0, false)

    private fun category(id: String, name: String = "餐饮", type: String = "EXPENSE") =
        ExportCategory(id, name, type)

    private fun envelope(
        accounts: List<ExportAccount> = listOf(account("a-file")),
        categories: List<ExportCategory> = listOf(category("c-file")),
    ) = ExportEnvelope(
        exportedAt = 0L,
        currency = "CNY",
        accounts = accounts,
        categories = categories,
    )

    @Test
    fun `same name and type maps the file id onto the local one`() {
        val remap = ImportMatcher.plan(
            envelope(accounts = listOf(account("a-file", "现金")), categories = emptyList()),
            existingAccounts = listOf("a-local" to NaturalKey("现金", "CASH")),
            existingCategories = emptyList(),
        )
        assertEquals("a-local", remap.account("a-file"))
    }

    @Test
    fun `a different type is a different account`() {
        val remap = ImportMatcher.plan(
            envelope(accounts = listOf(account("a-file", "现金", "BANK")), categories = emptyList()),
            existingAccounts = listOf("a-local" to NaturalKey("现金", "CASH")),
            existingCategories = emptyList(),
        )
        assertTrue("同名的不同类型不能被合并", remap.isEmpty)
        assertEquals("a-file", remap.account("a-file"))
    }

    @Test
    fun `expense and income category with the same name stay separate`() {
        val remap = ImportMatcher.plan(
            envelope(
                accounts = emptyList(),
                categories = listOf(category("c-file", "其他", "INCOME")),
            ),
            existingAccounts = emptyList(),
            existingCategories = listOf("c-local" to NaturalKey("其他", "EXPENSE")),
        )
        assertTrue(remap.isEmpty)
    }

    @Test
    fun `already matching id needs no remap`() {
        val remap = ImportMatcher.plan(
            envelope(accounts = listOf(account("same-id"))),
            existingAccounts = listOf("same-id" to NaturalKey("现金", "CASH")),
            existingCategories = emptyList(),
        )
        assertFalse(remap.accounts.containsKey("same-id"))
    }

    @Test
    fun `new accounts are only the unmatched ones`() {
        val file = envelope(
            accounts = listOf(account("a-file", "现金"), account("a-new", "招行", "BANK")),
            categories = emptyList(),
        )
        val remap = ImportMatcher.plan(
            file,
            existingAccounts = listOf("a-local" to NaturalKey("现金", "CASH")),
            existingCategories = emptyList(),
        )
        val fresh = ImportMatcher.newAccounts(file, remap)
        assertEquals(listOf("a-new"), fresh.map { it.id })
    }

    @Test
    fun `duplicate-looking file against itself inserts nothing`() {
        val file = envelope(
            accounts = listOf(account("a1", "现金")),
            categories = listOf(category("c1", "餐饮")),
        )
        val remap = ImportMatcher.plan(
            file,
            existingAccounts = listOf("a2" to NaturalKey("现金", "CASH")),
            existingCategories = listOf("c2" to NaturalKey("餐饮", "EXPENSE")),
        )
        assertTrue(ImportMatcher.newAccounts(file, remap).isEmpty())
        assertTrue(ImportMatcher.newCategories(file, remap).isEmpty())
    }

    @Test
    fun `unknown category ids pass through untouched`() {
        val remap = ImportMatcher.plan(envelope(), emptyList(), emptyList())
        assertEquals("c-unknown", remap.category("c-unknown"))
        assertEquals(null, remap.category(null))
    }
}
