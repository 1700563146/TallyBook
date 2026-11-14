package com.tallybook.core.data.wechat

import com.tallybook.core.model.transaction.TransactionType
import java.nio.charset.StandardCharsets
import java.util.UUID

data class WeChatCategory(
    val name: String,
    val type: TransactionType,
    val iconKey: String,
    val colorArgb: Long,
)

data class PlannedWeChatTransaction(
    val id: String,
    val source: WeChatBillRow,
    val type: TransactionType,
    val amountMinor: Long,
    val category: WeChatCategory,
    val refundOfId: String? = null,
)

data class WeChatImportPlan(
    val transactions: List<PlannedWeChatTransaction>,
    val neutralRows: List<WeChatBillRow>,
    val unmatchedRefunds: List<WeChatBillRow>,
    val duplicateIds: Set<String>,
)

object WeChatImportPlanner {

    const val IMPORT_ACCOUNT_NAME = "微信支付"

    fun plan(document: WeChatBillDocument): WeChatImportPlan {
        val duplicateIds = document.rows
            .groupingBy(WeChatBillRow::transactionId)
            .eachCount()
            .filterValues { it > 1 }
            .keys
        val uniqueRows = document.rows.distinctBy(WeChatBillRow::transactionId)
        val neutral = uniqueRows.filter { it.direction == WeChatDirection.NEUTRAL }
        val candidates = mutableListOf<RefundCandidate>()
        val planned = mutableListOf<PlannedWeChatTransaction>()
        val unmatchedRefunds = mutableListOf<WeChatBillRow>()

        uniqueRows
            .filter { it.direction != WeChatDirection.NEUTRAL }
            .sortedWith(compareBy(WeChatBillRow::occurredAt, WeChatBillRow::sourceRow))
            .forEach { row ->
                when (row.direction) {
                    WeChatDirection.EXPENSE -> {
                        val transaction = plannedTransaction(row, TransactionType.EXPENSE)
                        planned += transaction
                        candidates += RefundCandidate(transaction.id, row, normalizeMerchant(row.merchant))
                    }

                    WeChatDirection.INCOME -> {
                        if (row.isRefund) {
                            val original = findOriginalRefund(row, candidates)
                            if (original == null) {
                                unmatchedRefunds += row
                            } else {
                                original.matched = true
                                planned += PlannedWeChatTransaction(
                                    id = stableTransactionId(row),
                                    source = row,
                                    type = TransactionType.INCOME,
                                    amountMinor = row.amountMinor,
                                    category = categoryFor(original.row, TransactionType.EXPENSE),
                                    refundOfId = original.id,
                                )
                            }
                        } else {
                            planned += plannedTransaction(row, TransactionType.INCOME)
                        }
                    }

                    WeChatDirection.NEUTRAL -> Unit
                }
            }

        return WeChatImportPlan(
            transactions = planned.sortedBy { it.source.occurredAt },
            neutralRows = neutral,
            unmatchedRefunds = unmatchedRefunds,
            duplicateIds = duplicateIds,
        )
    }

    fun stableTransactionId(row: WeChatBillRow): String =
        UUID.nameUUIDFromBytes(
            "tallybook-wechat:${row.transactionId}".toByteArray(StandardCharsets.UTF_8),
        ).toString()

    private fun plannedTransaction(
        row: WeChatBillRow,
        type: TransactionType,
    ): PlannedWeChatTransaction = PlannedWeChatTransaction(
        id = stableTransactionId(row),
        source = row,
        type = type,
        amountMinor = row.amountMinor,
        category = categoryFor(row, type),
    )

    private fun findOriginalRefund(
        refund: WeChatBillRow,
        candidates: List<RefundCandidate>,
    ): RefundCandidate? {
        val refundMerchant = normalizeMerchant(refund.merchant)
        val byMerchant = candidates
            .asSequence()
            .filter { !it.matched }
            .filter { it.row.amountMinor == refund.amountMinor }
            .filter { it.row.occurredAt <= refund.occurredAt }
            .filter {
                refundMerchant.isNotBlank() &&
                    it.merchant.isNotBlank() &&
                    (
                        it.merchant.contains(refundMerchant) ||
                            refundMerchant.contains(it.merchant)
                        )
            }
            .maxByOrNull { it.row.occurredAt }
        if (byMerchant != null) return byMerchant

        return candidates
            .asSequence()
            .filter { !it.matched }
            .filter { it.row.amountMinor == refund.amountMinor }
            .filter { it.row.occurredAt <= refund.occurredAt }
            .filter { refund.occurredAt - it.row.occurredAt <= THIRTY_DAYS_MILLIS }
            .toList()
            .singleOrNull()
    }

    internal fun categoryFor(row: WeChatBillRow, type: TransactionType): WeChatCategory {
        val text = "${row.tradeType} ${row.merchant} ${row.product} ${row.note.orEmpty()}"
        if (type == TransactionType.INCOME) {
            return when {
                text.containsAny("工资", "薪资", "薪金", "公司", "兼职") -> INCOME_SALARY
                text.containsAny("红包") -> INCOME_RED_PACKET
                text.containsAny("理财", "收益", "返利") -> INCOME_INVESTMENT
                else -> INCOME_OTHER
            }
        }
        return when {
            text.containsAny("美团", "饿了么", "餐饮", "食堂", "饭店", "汉堡", "拉面", "小炒", "咖啡", "奶茶")
                -> EXPENSE_FOOD
            text.containsAny("地铁", "公交", "打车", "滴滴", "出租", "铁路", "12306", "加油", "停车", "出行")
                -> EXPENSE_TRANSPORT
            text.containsAny("淘宝", "拼多多", "京东", "购物", "商城", "服饰") -> EXPENSE_SHOPPING
            text.containsAny("医院", "医疗", "药房", "药店", "诊所") -> EXPENSE_MEDICAL
            text.containsAny("电影", "游戏", "演出", "娱乐", "KTV") -> EXPENSE_ENTERTAINMENT
            text.containsAny("学校", "课程", "教育", "书店", "学习") -> EXPENSE_EDUCATION
            text.containsAny("话费", "宽带", "流量", "通讯", "通信") -> EXPENSE_COMMUNICATION
            text.containsAny("房租", "物业", "水费", "电费", "燃气", "居住") -> EXPENSE_HOUSING
            text.containsAny("红包", "礼金", "随礼", "人情") -> EXPENSE_SOCIAL
            text.containsAny("超市", "便利店") -> EXPENSE_SHOPPING
            else -> EXPENSE_OTHER
        }
    }

    private fun normalizeMerchant(value: String): String =
        value.lowercase()
            .replace("平台商户", "")
            .replace("平台", "")
            .replace("商户", "")
            .replace("有限公司", "")
            .trim()

    private fun String.containsAny(vararg values: String): Boolean =
        values.any { contains(it, ignoreCase = true) }

    private data class RefundCandidate(
        val id: String,
        val row: WeChatBillRow,
        val merchant: String,
        var matched: Boolean = false,
    )

    private const val THIRTY_DAYS_MILLIS = 30L * 86_400_000L

    private val EXPENSE_FOOD = WeChatCategory("餐饮", TransactionType.EXPENSE, "restaurant", 0xFFE8734A)
    private val EXPENSE_TRANSPORT =
        WeChatCategory("交通", TransactionType.EXPENSE, "directions_bus", 0xFF4A90D9)
    private val EXPENSE_SHOPPING =
        WeChatCategory("购物", TransactionType.EXPENSE, "shopping_bag", 0xFFD94A8C)
    private val EXPENSE_HOUSING = WeChatCategory("居住", TransactionType.EXPENSE, "home", 0xFF8C6FD9)
    private val EXPENSE_ENTERTAINMENT =
        WeChatCategory("娱乐", TransactionType.EXPENSE, "celebration", 0xFFD9A64A)
    private val EXPENSE_MEDICAL =
        WeChatCategory("医疗", TransactionType.EXPENSE, "local_hospital", 0xFF4AD9A0)
    private val EXPENSE_EDUCATION = WeChatCategory("学习", TransactionType.EXPENSE, "school", 0xFF4AB8D9)
    private val EXPENSE_SOCIAL = WeChatCategory("人情", TransactionType.EXPENSE, "redeem", 0xFFD94A4A)
    private val EXPENSE_COMMUNICATION =
        WeChatCategory("通讯", TransactionType.EXPENSE, "phone", 0xFF6F8CD9)
    private val EXPENSE_OTHER =
        WeChatCategory("其他", TransactionType.EXPENSE, "more_horiz", 0xFF8A8A8A)
    private val INCOME_SALARY =
        WeChatCategory("工资", TransactionType.INCOME, "payments", 0xFF3BA55D)
    private val INCOME_RED_PACKET =
        WeChatCategory("红包", TransactionType.INCOME, "redeem", 0xFF3BA55D)
    private val INCOME_INVESTMENT =
        WeChatCategory("理财", TransactionType.INCOME, "trending_up", 0xFF3BA55D)
    private val INCOME_OTHER =
        WeChatCategory("其他", TransactionType.INCOME, "more_horiz", 0xFF8A8A8A)
}
