package com.tallybook.core.data.export

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCompatibilityTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `v1 backups decode with safe defaults for v2 fields`() {
        val v1 = """
            {
              "schemaVersion": 1,
              "exportedAt": 123,
              "currency": "CNY",
              "accounts": [{
                "id": "a1",
                "name": "现金",
                "type": "CASH",
                "currency": "CNY",
                "initialBalanceMinor": 0,
                "isArchived": false
              }],
              "categories": [{
                "id": "c1",
                "name": "餐饮",
                "type": "EXPENSE"
              }],
              "transactions": [{
                "id": "t1",
                "type": "EXPENSE",
                "kind": "NORMAL",
                "amountMinor": 1200,
                "currency": "CNY",
                "accountId": "a1",
                "categoryId": "c1",
                "occurredAt": 123
              }]
            }
        """.trimIndent()

        val decoded = json.decodeFromString(ExportEnvelope.serializer(), v1)

        assertEquals(1, decoded.schemaVersion)
        assertEquals(0, decoded.categories.single().sortOrder)
        assertFalse(decoded.categories.single().isArchived)
        assertTrue(decoded.transactions.single().tags.isEmpty())
    }

    @Test
    fun `v2 backups round trip archived category and transaction metadata`() {
        val envelope = ExportEnvelope(
            exportedAt = 456,
            currency = "CNY",
            categories = listOf(
                ExportCategory(
                    id = "c1",
                    name = "旧分类",
                    type = "EXPENSE",
                    sortOrder = 7,
                    isArchived = true,
                ),
            ),
            transactions = listOf(
                ExportTransaction(
                    id = "t1",
                    type = "EXPENSE",
                    kind = "NORMAL",
                    amountMinor = 1200,
                    currency = "CNY",
                    accountId = "a1",
                    categoryId = "c1",
                    occurredAt = 123,
                    tags = listOf("报销"),
                    createdAt = 100,
                    updatedAt = 200,
                ),
            ),
        )

        val text = json.encodeToString(ExportEnvelope.serializer(), envelope)
        val decoded = json.decodeFromString(ExportEnvelope.serializer(), text)

        assertEquals(ExportEnvelope.CURRENT_SCHEMA_VERSION, decoded.schemaVersion)
        assertTrue(decoded.categories.single().isArchived)
        assertEquals(7, decoded.categories.single().sortOrder)
        assertEquals(listOf("报销"), decoded.transactions.single().tags)
        assertEquals(100L, decoded.transactions.single().createdAt)
    }
}
