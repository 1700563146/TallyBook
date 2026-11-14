package com.tallybook.core.data.wechat

import com.tallybook.core.model.money.CurrencyCode
import com.tallybook.core.model.money.Money
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

class WeChatBillFormatException(message: String) : Exception(message)

enum class WeChatDirection {
    EXPENSE,
    INCOME,
    NEUTRAL,
}

data class WeChatBillDeclaredSummary(
    val totalCount: Int? = null,
    val incomeCount: Int? = null,
    val incomeMinor: Long? = null,
    val expenseCount: Int? = null,
    val expenseMinor: Long? = null,
    val neutralCount: Int? = null,
    val neutralMinor: Long? = null,
)

data class WeChatBillRow(
    val sourceRow: Int,
    val occurredAt: Long,
    val tradeType: String,
    val merchant: String,
    val product: String,
    val direction: WeChatDirection,
    val amountMinor: Long,
    val paymentMethod: String,
    val status: String,
    val transactionId: String,
    val merchantOrderId: String?,
    val note: String?,
) {
    val isRefund: Boolean
        get() = direction == WeChatDirection.INCOME &&
            (tradeType.contains("退款") || note.orEmpty().contains("退款"))
}

data class WeChatBillDocument(
    val rows: List<WeChatBillRow>,
    val declared: WeChatBillDeclaredSummary,
    val invalidRowCount: Int,
)

@Singleton
class WeChatBillParser @Inject constructor() {

    /** WeChat exports explicitly state that all bill timestamps use UTC+08:00. */
    private val zoneId: ZoneId = ZoneId.of("Asia/Shanghai")

    fun parse(input: InputStream): WeChatBillDocument {
        val entries = readRelevantZipEntries(input)
        val sharedStrings = entries["xl/sharedStrings.xml"]
            ?.let(::parseSharedStrings)
            .orEmpty()
        val sheetPath = resolveFirstWorksheetPath(entries)
        val sheetXml = entries[sheetPath]
            ?: throw WeChatBillFormatException("Excel 中找不到账单工作表")
        val sheetRows = parseWorksheet(sheetXml, sharedStrings)
        return parseBillRows(sheetRows)
    }

    private fun parseBillRows(rows: Map<Int, Map<Int, String>>): WeChatBillDocument {
        val requiredHeaders = setOf(
            "交易时间",
            "交易类型",
            "交易对方",
            "商品",
            "收/支",
            "金额(元)",
            "支付方式",
            "当前状态",
            "交易单号",
        )
        val headerEntry = rows.entries.firstOrNull { (_, values) ->
            values.values.map(String::trim).count { it in requiredHeaders } >= 8
        } ?: throw WeChatBillFormatException("无法识别微信支付账单表头")

        val headerRowNumber = headerEntry.key
        val columnByHeader = headerEntry.value.entries.associate { (column, value) ->
            value.trim() to column
        }
        fun column(name: String): Int? = columnByHeader[name]

        val parsedRows = mutableListOf<WeChatBillRow>()
        var invalidRows = 0
        rows.entries
            .filter { it.key > headerRowNumber }
            .sortedBy { it.key }
            .forEach { (rowNumber, values) ->
                fun value(header: String): String =
                    column(header)?.let { values[it]?.trim().orEmpty() }.orEmpty()

                val transactionId = value("交易单号")
                if (transactionId.isBlank()) return@forEach
                val occurredAt = parseDate(value("交易时间"))
                val amountMinor = parseAmountMinor(value("金额(元)"))
                val direction = when (value("收/支")) {
                    "支出" -> WeChatDirection.EXPENSE
                    "收入" -> WeChatDirection.INCOME
                    "/", "" -> WeChatDirection.NEUTRAL
                    else -> null
                }
                if (occurredAt == null || amountMinor == null || direction == null) {
                    invalidRows++
                    return@forEach
                }

                parsedRows += WeChatBillRow(
                    sourceRow = rowNumber,
                    occurredAt = occurredAt,
                    tradeType = value("交易类型"),
                    merchant = value("交易对方"),
                    product = value("商品"),
                    direction = direction,
                    amountMinor = amountMinor,
                    paymentMethod = value("支付方式"),
                    status = value("当前状态"),
                    transactionId = transactionId,
                    merchantOrderId = value("商户单号").takeUnless { it.isBlank() || it == "/" },
                    note = value("备注").takeUnless { it.isBlank() || it == "/" },
                )
            }

        val metadata = rows.entries
            .filter { it.key < headerRowNumber }
            .flatMap { it.value.values }
        val declared = parseDeclaredSummary(metadata)
        return WeChatBillDocument(
            rows = parsedRows,
            declared = declared,
            invalidRowCount = invalidRows,
        )
    }

    private fun parseDeclaredSummary(values: Collection<String>): WeChatBillDeclaredSummary {
        val total = values.firstNotNullOfOrNull {
            Regex("共\\s*(\\d+)\\s*笔记录").find(it)?.groupValues?.getOrNull(1)?.toIntOrNull()
        }
        fun direction(name: String): Pair<Int?, Long?> {
            val match = values.firstNotNullOfOrNull {
                Regex("$name[：:]\\s*(\\d+)\\s*笔\\s*([\\d,.]+)\\s*元").find(it)
            } ?: return null to null
            return match.groupValues[1].toIntOrNull() to parseAmountMinor(match.groupValues[2])
        }
        val income = direction("收入")
        val expense = direction("支出")
        val neutral = direction("中性交易")
        return WeChatBillDeclaredSummary(
            totalCount = total,
            incomeCount = income.first,
            incomeMinor = income.second,
            expenseCount = expense.first,
            expenseMinor = expense.second,
            neutralCount = neutral.first,
            neutralMinor = neutral.second,
        )
    }

    internal fun parseDate(value: String): Long? {
        if (value.isBlank()) return null
        val numeric = value.replace(",", "").toBigDecimalOrNull()
        if (numeric != null) return excelSerialToEpochMillis(numeric)
        return DATE_FORMATS.firstNotNullOfOrNull { formatter ->
            runCatching {
                LocalDateTime.parse(value, formatter).atZone(zoneId).toInstant().toEpochMilli()
            }.getOrNull()
        }
    }

    internal fun excelSerialToEpochMillis(serial: BigDecimal): Long? {
        if (serial < BigDecimal.ZERO) return null
        return runCatching {
            val wholeDays = serial.setScale(0, RoundingMode.DOWN).longValueExact()
            val fraction = serial.subtract(BigDecimal.valueOf(wholeDays))
            val nanos = fraction
                .multiply(BigDecimal.valueOf(86_400_000_000_000L))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
            EXCEL_EPOCH.plusDays(wholeDays).plusNanos(nanos)
                .atZone(zoneId)
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
    }

    private fun parseAmountMinor(value: String): Long? {
        val normalized = value
            .replace(",", "")
            .replace("¥", "")
            .replace("￥", "")
            .replace("元", "")
            .trim()
        if (normalized.isBlank()) return null
        return runCatching { Money.parse(normalized, CurrencyCode.CNY).minorUnits }.getOrNull()
    }

    private fun readRelevantZipEntries(input: InputStream): Map<String, ByteArray> {
        val entries = mutableMapOf<String, ByteArray>()
        var totalBytes = 0L
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.removePrefix("/")
                val relevant = name == "xl/workbook.xml" ||
                    name == "xl/_rels/workbook.xml.rels" ||
                    name == "xl/sharedStrings.xml" ||
                    (name.startsWith("xl/worksheets/") && name.endsWith(".xml"))
                if (!relevant || entry.isDirectory) {
                    zip.closeEntry()
                    continue
                }
                val bytes = readLimited(zip, MAX_ENTRY_BYTES)
                totalBytes += bytes.size
                if (totalBytes > MAX_TOTAL_XML_BYTES) {
                    throw WeChatBillFormatException("Excel 文件解压后过大，已停止导入")
                }
                entries[name] = bytes
                zip.closeEntry()
            }
        }
        return entries
    }

    private fun readLimited(input: InputStream, maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) throw WeChatBillFormatException("Excel 单个工作表过大")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun resolveFirstWorksheetPath(entries: Map<String, ByteArray>): String {
        val workbookXml = entries["xl/workbook.xml"]
        val relationshipsXml = entries["xl/_rels/workbook.xml.rels"]
        if (workbookXml != null && relationshipsXml != null) {
            val workbookDoc = parseXml(workbookXml)
            val sheet = workbookDoc.getElementsByTagNameNS("*", "sheet").item(0) as? Element
            val relationshipId = sheet?.getAttributeNS(RELATIONSHIP_NS, "id")
                ?.takeIf { it.isNotBlank() }
                ?: sheet?.getAttribute("r:id")
            if (!relationshipId.isNullOrBlank()) {
                val relationships = parseXml(relationshipsXml)
                val relNodes = relationships.getElementsByTagNameNS("*", "Relationship")
                for (index in 0 until relNodes.length) {
                    val relationship = relNodes.item(index) as? Element ?: continue
                    if (relationship.getAttribute("Id") != relationshipId) continue
                    val target = relationship.getAttribute("Target").removePrefix("/")
                    val resolved = if (target.startsWith("xl/")) target else "xl/$target"
                    if (resolved in entries) return resolved
                }
            }
        }
        return entries.keys
            .filter { it.startsWith("xl/worksheets/") && it.endsWith(".xml") }
            .sorted()
            .firstOrNull()
            ?: throw WeChatBillFormatException("这不是有效的 XLSX 文件")
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val document = parseXml(bytes)
        val values = document.getElementsByTagNameNS("*", "si")
        return buildList(values.length) {
            for (index in 0 until values.length) {
                val item = values.item(index) as? Element ?: continue
                val textNodes = item.getElementsByTagNameNS("*", "t")
                val text = buildString {
                    for (textIndex in 0 until textNodes.length) {
                        append(textNodes.item(textIndex).textContent.orEmpty())
                    }
                }
                add(text)
            }
        }
    }

    private fun parseWorksheet(
        bytes: ByteArray,
        sharedStrings: List<String>,
    ): Map<Int, Map<Int, String>> {
        val document = parseXml(bytes)
        val rowNodes = document.getElementsByTagNameNS("*", "row")
        val rows = linkedMapOf<Int, Map<Int, String>>()
        for (rowIndex in 0 until rowNodes.length) {
            val row = rowNodes.item(rowIndex) as? Element ?: continue
            val rowNumber = row.getAttribute("r").toIntOrNull() ?: continue
            val cells = linkedMapOf<Int, String>()
            val children = row.childNodes
            for (childIndex in 0 until children.length) {
                val cell = children.item(childIndex) as? Element ?: continue
                if (cell.localName != "c") continue
                val reference = cell.getAttribute("r")
                val column = columnIndex(reference) ?: continue
                val type = cell.getAttribute("t")
                val valueNode = cell.getElementsByTagNameNS("*", "v").item(0)
                val rawValue = valueNode?.textContent
                val inlineValue = if (type == "inlineStr") {
                    val textNodes = cell.getElementsByTagNameNS("*", "t")
                    buildString {
                        for (textIndex in 0 until textNodes.length) {
                            append(textNodes.item(textIndex).textContent.orEmpty())
                        }
                    }
                } else {
                    null
                }
                val value = when (type) {
                    "s" -> rawValue?.toIntOrNull()?.let(sharedStrings::getOrNull)
                    "inlineStr" -> inlineValue
                    "str", "e" -> rawValue
                    "b" -> rawValue?.let { if (it == "1") "TRUE" else "FALSE" }
                    else -> rawValue
                }
                if (value != null) cells[column] = value
            }
            if (cells.isNotEmpty()) rows[rowNumber] = cells
        }
        return rows
    }

    private fun parseXml(bytes: ByteArray) = try {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            runCatching {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    } catch (error: Exception) {
        throw WeChatBillFormatException("Excel 内部结构损坏，无法读取")
    }

    private fun columnIndex(reference: String): Int? {
        if (reference.isBlank()) return null
        var value = 0
        for (character in reference) {
            if (!character.isLetter()) break
            value = value * 26 + (character.uppercaseChar() - 'A' + 1)
        }
        return (value - 1).takeIf { it >= 0 }
    }

    private fun String.toBigDecimalOrNull(): BigDecimal? =
        runCatching { BigDecimal(this) }.getOrNull()

    companion object {
        const val MAX_FILE_BYTES = 20 * 1024 * 1024
        private const val MAX_ENTRY_BYTES = 12 * 1024 * 1024
        private const val MAX_TOTAL_XML_BYTES = 40 * 1024 * 1024
        private const val RELATIONSHIP_NS =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        private val EXCEL_EPOCH: LocalDateTime = LocalDateTime.of(1899, 12, 30, 0, 0)
        private val DATE_FORMATS = listOf(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        )
    }
}
