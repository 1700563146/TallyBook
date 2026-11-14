package com.tallybook.core.data.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * CSV export tests.
 *
 * A ledger export is read by spreadsheets, which are hostile environments: they interpret leading
 * `=`, they split on commas inside notes, and they misread UTF-8 without a BOM. Each of those turns a
 * "successful export" into data the user cannot use — and none of it is visible in the app.
 */
class CsvExportTest {

    @Test
    fun `plain values are not quoted`() {
        assertEquals("午饭", CsvExport.escape("午饭"))
    }

    @Test
    fun `commas force quoting`() {
        assertEquals("\"午饭, 拉面\"", CsvExport.escape("午饭, 拉面"))
    }

    @Test
    fun `embedded quotes are doubled`() {
        assertEquals("\"他说\"\"好\"\"\"", CsvExport.escape("他说\"好\""))
    }

    @Test
    fun `newlines force quoting`() {
        assertEquals("\"第一行\n第二行\"", CsvExport.escape("第一行\n第二行"))
    }

    @Test
    fun `formula prefixes are neutralised`() {
        assertEquals("'=1+1", CsvExport.guardFormula("=1+1"))
        assertEquals("'+1", CsvExport.guardFormula("+1"))
        assertEquals("'-1", CsvExport.guardFormula("-1"))
        assertEquals("'@SUM(A1)", CsvExport.guardFormula("@SUM(A1)"))
    }

    @Test
    fun `a guarded formula is still quoted when it contains a comma`() {
        assertEquals("\"'=A1,B1\"", CsvExport.escape("=A1,B1"))
    }

    @Test
    fun `normal text is never guarded`() {
        assertEquals("午饭 32 元", CsvExport.guardFormula("午饭 32 元"))
        assertEquals("", CsvExport.guardFormula(""))
    }

    @Test
    fun `document starts with a UTF-8 BOM so Excel reads Chinese`() {
        val csv = CsvExport.write(envelope(), ZoneId.of("Asia/Shanghai"))
        assertTrue("export must begin with the BOM", csv.startsWith("\uFEFF"))
        assertTrue(csv.startsWith("\uFEFF日期,"))
    }

    @Test
    fun `header and one row per transaction`() {
        val csv = CsvExport.write(envelope(), ZoneId.of("Asia/Shanghai"))
        val lines = csv.removePrefix("\uFEFF").trim().split("\r\n")
        assertEquals("header + 1 row", 2, lines.size)
        assertTrue(lines[0].startsWith("日期,时间,类型,分类,账户"))
    }

    @Test
    fun `amounts are plain decimals without symbols or separators`() {
        val csv = CsvExport.write(
            envelope(amountMinor = 123_456),
            ZoneId.of("Asia/Shanghai"),
        )
        assertTrue("expected 1234.56 in: $csv", csv.contains(",1234.56,"))
        assertFalse(csv.contains("¥"))
    }

    @Test
    fun `deleted rows are kept and marked`() {
        val csv = CsvExport.write(
            envelope(note = "午饭", deletedAt = 1L),
            ZoneId.of("Asia/Shanghai"),
        )
        assertTrue("a backup must not silently drop the recycle bin", csv.contains("[已删除]"))
    }

    @Test
    fun `notes from an imported file cannot smuggle a formula`() {
        val csv = CsvExport.write(
            envelope(note = "=HYPERLINK(\"http://evil\",\"click\")"),
            ZoneId.of("Asia/Shanghai"),
        )
        assertTrue("the `=` must be neutralised", csv.contains("'=HYPERLINK"))
    }

    private fun envelope(
        amountMinor: Long = 100,
        note: String = "午饭",
        deletedAt: Long? = null,
    ) = ExportEnvelope(
        exportedAt = 0L,
        currency = "CNY",
        accounts = listOf(
            ExportAccount("a1", "现金", "CASH", "CNY", 0, false),
        ),
        categories = listOf(
            ExportCategory("c1", "餐饮", "EXPENSE", null, "restaurant", null),
        ),
        transactions = listOf(
            ExportTransaction(
                id = "t1",
                type = "EXPENSE",
                kind = "NORMAL",
                amountMinor = amountMinor,
                currency = "CNY",
                accountId = "a1",
                categoryId = "c1",
                occurredAt = 1_760_000_000_000L,
                note = note,
                deletedAt = deletedAt,
            ),
        ),
    )
}
