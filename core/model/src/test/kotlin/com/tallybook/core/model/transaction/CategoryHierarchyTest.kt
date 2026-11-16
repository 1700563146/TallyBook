package com.tallybook.core.model.transaction

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryHierarchyTest {

    @Test
    fun tree_places_children_after_parents_and_records_depth() {
        val categories = listOf(
            Category(id = "lunch", name = "午餐", type = TransactionType.EXPENSE, parentId = "food", sortOrder = 1),
            Category(id = "transport", name = "交通", type = TransactionType.EXPENSE, sortOrder = 1),
            Category(id = "food", name = "餐饮", type = TransactionType.EXPENSE, sortOrder = 0),
            Category(id = "breakfast", name = "早餐", type = TransactionType.EXPENSE, parentId = "food", sortOrder = 0),
        )

        val tree = categories.toCategoryTree()

        assertEquals(
            listOf("food", "breakfast", "lunch", "transport"),
            tree.map { it.category.id },
        )
        assertEquals(listOf(0, 1, 1, 0), tree.map { it.depth })
        assertEquals(2, tree.first().childCount)
    }

    @Test
    fun orphan_is_visible_as_a_root() {
        val orphan = Category(
            id = "orphan",
            name = "孤立分类",
            type = TransactionType.EXPENSE,
            parentId = "missing",
        )

        val tree = listOf(orphan).toCategoryTree()

        assertEquals("orphan", tree.single().category.id)
        assertEquals(0, tree.single().depth)
    }
}
