package com.tallybook.core.data.export

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * CSV writer for the export.
 *
 * Three details that separate a CSV people can actually open from one they can't:
 *
 * 1. **UTF-8 BOM.** Excel on Windows treats a BOM-less UTF-8 file as the local ANSI codepage, so
 *    every Chinese character in the ledger turns into mojibake. The BOM costs three bytes.
 * 2. **RFC 4180 quoting.** Values containing `,`, `"` or a newline are wrapped in quotes with inner
 *    quotes doubled. A note like `午饭, 拉面` would otherwise shift every following column.
 * 3. **Formula-injection guard.** A note starting with `=`, `+`, `-`, `@`, tab or CR is executed as a
 *    formula by Excel/Numbers/Sheets. Prefixing with `'` neutralises it. This matters because notes
 *    can come from an imported file written by someone else.
 *
 * Amounts are written as plain major-unit decimals (`1234.56`) with **no currency symbol and no
 * thousands separators**, so spreadsheets parse the column as numbers instead of text.
 */
object CsvExport {

    const val BOM = "\uFEFF"

    val HEADER: List<String> = listOf(
        "日期",
        "时间",
        "类型",
        "分类",
        "账户",
        "对手账户",
        "金额",
        "币种",
        "备注",
        "记录ID",
        "退款关联",
    )

    private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT)
    private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

    /**
     * Renders the whole document, including the BOM.
     *
     * Soft-deleted rows are included with their `deletedAt` reflected only in `记录ID`/nothing: the
     * export is a backup, and a backup that silently drops the recycle bin cannot restore the user's
     * state. Re-importing therefore needs to know about deletions — hence the JSON file keeps
     * `deletedAt`, and this CSV keeps the row but marks it in the note column.
     */
    fun write(envelope: ExportEnvelope, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val categoryNames = envelope.categories.associate { it.id to it.name }
        val accountNames = envelope.accounts.associate { it.id to it.name }

        val sb = StringBuilder(1024 + envelope.transactions.size * 96)
        sb.append(BOM)
        sb.append(HEADER.joinToString(",") { escape(it) }).append("\r\n")

        for (transaction in envelope.transactions) {
            val instant = Instant.ofEpochMilli(transaction.occurredAt).atZone(zoneId)
            val amount = majorUnits(transaction.amountMinor, transaction.currency)
            val note = buildString {
                append(transaction.note.orEmpty())
                if (transaction.deletedAt != null) {
                    if (isNotEmpty()) append(" ")
                    append("[已删除]")
                }
            }

            sb.append(escape(DATE_FORMAT.format(instant))).append(',')
                .append(escape(TIME_FORMAT.format(instant))).append(',')
                .append(escape(typeLabel(transaction))).append(',')
                .append(escape(transaction.categoryId?.let { categoryNames[it] } ?: "未分类")).append(',')
                .append(escape(accountNames[transaction.accountId] ?: "")).append(',')
                .append(escape(transaction.counterAccountId?.let { accountNames[it] } ?: "")).append(',')
                .append(escape(amount)).append(',')
                .append(escape(transaction.currency)).append(',')
                .append(escape(note)).append(',')
                .append(escape(transaction.id)).append(',')
                .append(escape(transaction.refundOfId ?: "")).append("\r\n")
        }
        return sb.toString()
    }

    /**
     * Escapes one field.
     *
     * Order matters: the formula guard runs on the *raw* value, then quoting is applied. Doing it the
     * other way round would put the `'` outside the quotes and break the parse.
     */
    fun escape(value: String): String {
        val guarded = guardFormula(value)
        val needsQuotes = guarded.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) {
            "\"" + guarded.replace("\"", "\"\"") + "\""
        } else {
            guarded
        }
    }

    /** Prefixes values that a spreadsheet would evaluate as a formula. */
    fun guardFormula(value: String): String {
        if (value.isEmpty()) return value
        return when (value[0]) {
            '=', '+', '-', '@', '\t', '\r' -> "'" + value
            else -> value
        }
    }

    private fun typeLabel(transaction: ExportTransaction): String = when {
        transaction.kind == "TRANSFER" -> "转账"
        transaction.type == "INCOME" -> "收入"
        else -> "支出"
    }

    /**
     * Converts minor units to a plain decimal string.
     *
     * Uses `BigDecimal` rather than dividing doubles: `12345 / 100.0` is exactly `123.45` today but the
     * pattern invites float error the moment the exponent is not 2.
     */
    private fun majorUnits(minorUnits: Long, currencyCode: String): String {
        val fractionDigits = runCatching {
            java.util.Currency.getInstance(currencyCode).defaultFractionDigits
        }.getOrDefault(2).coerceAtLeast(0)
        return java.math.BigDecimal.valueOf(minorUnits)
            .movePointLeft(fractionDigits)
            .toPlainString()
    }
}
