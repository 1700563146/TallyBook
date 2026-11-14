package com.tallybook.feature.budget.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.tallybook.feature.budget.editor.BudgetEditorScreen
import com.tallybook.feature.budget.ui.BudgetScreen
import kotlinx.serialization.Serializable

@Serializable
sealed interface BudgetRoute {

    @Serializable
    data object Budget : BudgetRoute

    /** Null id = 新建预算; non-null = edit that budget. */
    @Serializable
    data class Editor(val budgetId: String? = null) : BudgetRoute
}

fun NavGraphBuilder.budgetGraph(navController: NavController) {
    composable<BudgetRoute.Budget> {
        BudgetScreen(
            onAddClick = { navController.navigate(BudgetRoute.Editor()) },
            onBudgetClick = { id -> navController.navigate(BudgetRoute.Editor(budgetId = id)) },
        )
    }

    composable<BudgetRoute.Editor> {
        BudgetEditorScreen(onClose = { navController.popBackStack() })
    }
}
