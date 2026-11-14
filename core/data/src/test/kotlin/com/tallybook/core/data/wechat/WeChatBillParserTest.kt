package com.tallybook.core.data.wechat

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeChatBillParserTest {

    private val parser = WeChatBillParser()

    @Test
    fun parses_wechat_metadata_rows_strings_dates_and_amounts() {
        val document = parser.parse(ByteArrayInputStream(workbookBytes()))

        assertEquals(2, document.rows.size)
        assertEquals(0, document.invalidRowCount)
        assertEquals(2, document.declared.totalCount)
        assertEquals(1, document.declared.expenseCount)
        assertEquals(1, document.declared.incomeCount)

        val expense = document.rows.first()
        assertEquals(WeChatDirection.EXPENSE, expense.direction)
        assertEquals("食堂消费", expense.product)
        assertEquals(1_250L, expense.amountMinor)
        assertEquals("零钱", expense.paymentMethod)
        assertEquals("2026-11-14T22:39:51+08:00[Asia/Shanghai]", expense.occurredAt.toZonedString())

        val refund = document.rows.last()
        assertTrue(refund.isRefund)
        assertEquals(WeChatDirection.INCOME, refund.direction)
    }

    @Test
    fun planner_links_refund_to_original_expense() {
        val plan = WeChatImportPlanner.plan(
            parser.parse(ByteArrayInputStream(workbookBytes())),
        )

        assertEquals(2, plan.transactions.size)
        assertEquals(0, plan.unmatchedRefunds.size)
        val refund = plan.transactions.single { it.refundOfId != null }
        val expense = plan.transactions.single { it.refundOfId == null }
        assertEquals(expense.id, refund.refundOfId)
        assertEquals("餐饮", expense.category.name)
        assertEquals(expense.category, refund.category)
    }

    @Test
    fun stable_id_does_not_depend_on_import_time_or_row_order() {
        val row = WeChatBillRow(
            sourceRow = 19,
            occurredAt = 1,
            tradeType = "商户消费",
            merchant = "美团",
            product = "午餐",
            direction = WeChatDirection.EXPENSE,
            amountMinor = 100,
            paymentMethod = "零钱",
            status = "支付成功",
            transactionId = "tx-1",
            merchantOrderId = null,
            note = null,
        )

        assertEquals(
            WeChatImportPlanner.stableTransactionId(row),
            WeChatImportPlanner.stableTransactionId(row.copy(sourceRow = 99, occurredAt = 2)),
        )
    }

    private fun Long.toZonedString(): String =
        java.time.Instant.ofEpochMilli(this)
            .atZone(ZoneId.of("Asia/Shanghai"))
            .toString()

    private fun workbookBytes(): ByteArray {
        val strings = listOf(
            "微信支付账单明细",
            "共2笔记录",
            "收入：1笔 12.50元",
            "支出：1笔 12.50元",
            "----------------------微信支付账单明细列表--------------------",
            "交易时间",
            "交易类型",
            "交易对方",
            "商品",
            "收/支",
            "金额(元)",
            "支付方式",
            "当前状态",
            "交易单号",
            "商户单号",
            "备注",
            "商户消费",
            "师大食堂",
            "食堂消费",
            "支出",
            "零钱",
            "支付成功",
            "tx-expense",
            "订单-1",
            "/",
            "美团平台商户-退款",
            "美团平台商户",
            "收入",
            "已全额退款",
            "tx-refund",
            "美团平台商户-退款",
        )
        val rows = listOf(
            listOf(0),
            listOf(1),
            listOf(2),
            listOf(3),
            listOf(4),
            listOf(5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15),
            listOf("46340.94434027778", 16, 17, 18, 19, 12.5, 20, 21, 22, 23, 24),
            listOf("46340.95000000000", 25, 26, 26, 27, 12.5, 20, 28, 29, null, 30),
        )
        val sheet = buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""")
            append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
            rows.forEachIndexed { rowIndex, values ->
                val rowNumber = rowIndex + 1
                append("""<row r="$rowNumber">""")
                values.forEachIndexed { columnIndex, value ->
                    if (value == null) return@forEachIndexed
                    val reference = "${columnName(columnIndex)}$rowNumber"
                    when (value) {
                        is Int -> append("""<c r="$reference" t="s"><v>$value</v></c>""")
                        is Double -> append("""<c r="$reference"><v>$value</v></c>""")
                        is String -> {
                            val numeric = value.toDoubleOrNull()
                            if (numeric != null) {
                                append("""<c r="$reference"><v>$value</v></c>""")
                            } else {
                                append(
                                    """<c r="$reference" t="inlineStr"><is><t>${xmlEscape(value)}</t></is></c>""",
                                )
                            }
                        }
                    }
                }
                append("</row>")
            }
            append("</sheetData></worksheet>")
        }

        return zipOf(
            "xl/workbook.xml" to """
                <?xml version="1.0" encoding="UTF-8"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                    xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets><sheet name="Sheet1" sheetId="1" r:id="rId1"/></sheets>
                </workbook>
            """.trimIndent(),
            "xl/_rels/workbook.xml.rels" to """
                <?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1"
                    Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet"
                    Target="worksheets/sheet1.xml"/>
                </Relationships>
            """.trimIndent(),
            "xl/sharedStrings.xml" to buildString {
                append("""<?xml version="1.0" encoding="UTF-8"?>""")
                append("""<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
                strings.forEach { append("<si><t>${xmlEscape(it)}</t></si>") }
                append("</sst>")
            },
            "xl/worksheets/sheet1.xml" to sheet,
        )
    }

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun columnName(index: Int): String {
        var value = index + 1
        val result = StringBuilder()
        while (value > 0) {
            val remainder = (value - 1) % 26
            result.append(('A'.code + remainder).toChar())
            value = (value - 1) / 26
        }
        return result.reverse().toString()
    }

    private fun xmlEscape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}
