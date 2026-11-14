package com.tallybook.core.ui.preview

/**
 * Shared preview fixtures.
 *
 * Kept in :core:ui so every feature previews with the same realistic data instead of "Lorem" rows.
 * This is where long Chinese text, zero amounts and deep hierarchies get caught before a designer
 * ever opens the screen.
 */
object PreviewData {

    /** Simple structural preview fixture; features map their own UI types onto this. */
    data class Row(
        val id: String,
        val categoryName: String,
        val note: String,
        val amountLabel: String,
        val isExpense: Boolean,
    )

    val rows: List<Row> = listOf(
        Row("1", "餐饮", "午饭 · 兰州拉面", "-¥32.00", true),
        Row("2", "交通", "地铁通勤", "-¥6.00", true),
        Row("3", "工资", "11月薪资", "+¥18,000.00", false),
        Row("4", "购物", "双十一 · 清洁用品", "-¥268.50", true),
    )
}
