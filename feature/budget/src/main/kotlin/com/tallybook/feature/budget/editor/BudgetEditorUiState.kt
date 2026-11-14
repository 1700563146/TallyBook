package com.tallybook.feature.budget.editor

import com.tallybook.core.model.transaction.BudgetPeriod

/**
 * Budget editor state.
 *
 * The limit is kept as a String rather than a `Money` because it is bound directly to a text field:
 * keeping a parsed value alongside the raw text makes "user typed `12.`" unsaveable for no reason.
 * Parsing happens once, on save, with `Money.parse` (BigDecimal based).
 */
data class BudgetEditorUiState(
    val budgetId: String? = null,
    val name: String = "",
    val limitText: String = "",
    val period: BudgetPeriod = BudgetPeriod.MONTHLY,
    /** null = applies to all categories. */
    val categoryId: String? = null,
    val categories: List<BudgetCategoryOption> = emptyList(),
    val includeSubcategories: Boolean = true,
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
) {
    val canSave: Boolean
        get() = name.isNotBlank() && (limitText.toBigDecimalOrNull()?.signum() ?: 0) > 0

    val isAllCategories: Boolean get() = categoryId == null
}

data class BudgetCategoryOption(
    val id: String,
    val name: String,
)

private fun String.toBigDecimalOrNull(): java.math.BigDecimal? =
    runCatching { java.math.BigDecimal(trim()) }.getOrNull()
