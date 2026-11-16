package com.tallybook.core.model.transaction

/**
 * One category in display order, with the information the UI needs to render a tree.
 *
 * Keeping this as a pure model helper makes category ordering identical in settings and the budget
 * editor. Without a shared contract each screen is free to invent a different parent/child order,
 * which is especially confusing when a user has just created a subcategory in one place.
 */
data class CategoryTreeNode(
    val category: Category,
    val depth: Int,
    val childCount: Int,
)

/**
 * Flattens categories into parent-first pre-order.
 *
 * Archived siblings sort after active siblings, but they remain attached to their parent so the
 * management screen can still explain historical structure. Orphans and malformed cycles are
 * surfaced as roots rather than silently disappearing.
 */
fun List<Category>.toCategoryTree(): List<CategoryTreeNode> {
    if (isEmpty()) return emptyList()

    val byId = associateBy { it.id }
    val childrenByParent = groupBy { it.parentId }
    val visited = mutableSetOf<String>()
    val result = mutableListOf<CategoryTreeNode>()

    fun append(category: Category, depth: Int) {
        if (!visited.add(category.id)) return
        val children = childrenByParent[category.id]
            .orEmpty()
            .sortedWith(CATEGORY_SORT)
        result += CategoryTreeNode(
            category = category,
            depth = depth,
            childCount = children.size,
        )
        children.forEach { child -> append(child, depth + 1) }
    }

    val roots = filter { category ->
        category.parentId == null || byId[category.parentId] == null
    }.sortedWith(CATEGORY_SORT)
    roots.forEach { root -> append(root, 0) }

    // A cycle has no natural root. Surface any remaining rows (sorted) instead of hiding them.
    filterNot { it.id in visited }
        .sortedWith(CATEGORY_SORT)
        .forEach { orphan -> append(orphan, 0) }

    return result
}

private val CATEGORY_SORT = compareBy<Category>(
    { it.isArchived },
    { it.sortOrder },
    { it.name },
)
