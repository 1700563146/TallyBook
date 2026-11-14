package com.tallybook.feature.transactions.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.tallybook.feature.transactions.editor.TransactionEditorScreen
import com.tallybook.feature.transactions.recycle.RecycleBinScreen
import com.tallybook.feature.transactions.search.TransactionSearchScreen
import com.tallybook.feature.transactions.ui.TransactionsScreen
import kotlinx.serialization.Serializable

/**
 * Type-safe routes.
 *
 * Serializable route objects instead of string routes: a typo or a missing argument becomes a compile
 * error rather than a crash at navigation time, and the argument type is checked where it is read
 * (`toRoute<Editor>().transactionId`).
 */
@Serializable
sealed interface TransactionsRoute {

    @Serializable
    data object Transactions : TransactionsRoute

    /** Soft-deleted rows: restore or purge. Reached from Settings. */
    @Serializable
    data object RecycleBin : TransactionsRoute

    /** Cross-month text search and filters. */
    @Serializable
    data object Search : TransactionsRoute

    /**
     * One editor route for three jobs, distinguished by which argument is set:
     * - neither → 记一笔
     * - [transactionId] → 编辑
     * - [refundOfId] → 记退款（指向原支出）
     */
    @Serializable
    data class Editor(
        val transactionId: String? = null,
        val refundOfId: String? = null,
    ) : TransactionsRoute
}

fun NavGraphBuilder.transactionsGraph(
    navController: NavController,
    /**
     * Opens the statistics tab. Passed in rather than imported: the transactions feature must not
     * depend on the insights feature just to navigate to it.
     */
    onOpenInsights: () -> Unit = {},
) {
    composable<TransactionsRoute.Transactions> {
        TransactionsScreen(
            onEditClick = { id -> navController.navigate(TransactionsRoute.Editor(transactionId = id)) },
            onOpenInsights = onOpenInsights,
            onOpenSearch = { navController.navigate(TransactionsRoute.Search) },
        )
    }

    composable<TransactionsRoute.Search> {
        TransactionSearchScreen(
            onBack = { navController.popBackStack() },
            onEditClick = { id ->
                navController.navigate(TransactionsRoute.Editor(transactionId = id))
            },
        )
    }

    composable<TransactionsRoute.Editor> { backStackEntry ->
        val route = backStackEntry.toRoute<TransactionsRoute.Editor>()
        TransactionEditorScreen(
            // The refund editor sits on top of the expense editor it was launched from, so a plain
            // popBackStack() would drop the user back into the original expense — confusing after a
            // successful save. Pop to the list instead; the ids are read by the ViewModel.
            onClose = {
                if (route.refundOfId != null) {
                    navController.popBackStack(TransactionsRoute.Transactions, inclusive = false)
                } else {
                    navController.popBackStack()
                }
            },
            onRefund = { expenseId ->
                navController.navigate(TransactionsRoute.Editor(refundOfId = expenseId))
            },
        )
    }
}

/**
 * Exposed separately so Settings can navigate here without depending on this feature module.
 * The app wires the two together.
 */
fun NavGraphBuilder.recycleBinGraph(navController: NavController) {
    composable<TransactionsRoute.RecycleBin> {
        RecycleBinScreen(onBack = { navController.popBackStack() })
    }
}
