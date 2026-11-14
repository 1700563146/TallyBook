package com.tallybook

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tallybook.feature.budget.navigation.BudgetRoute
import com.tallybook.feature.budget.navigation.budgetGraph
import com.tallybook.feature.insights.navigation.InsightsRoute
import com.tallybook.feature.insights.navigation.insightsGraph
import com.tallybook.feature.settings.navigation.SettingsRoute
import com.tallybook.feature.settings.navigation.settingsGraph
import com.tallybook.feature.transactions.navigation.TransactionsRoute
import com.tallybook.feature.transactions.navigation.recycleBinGraph
import com.tallybook.feature.transactions.navigation.transactionsGraph
import com.tallybook.ui.theme.TallyBookTheme

/**
 * One description of a top-level destination, so the bottom bar cannot disagree with the graph.
 */
private data class TopLevelDestination(
    val route: Any,
    val routeClass: kotlin.reflect.KClass<*>,
    val icon: ImageVector,
    val labelRes: Int,
)

/**
 * Single-activity Compose host.
 *
 * Each feature contributes a `NavGraphBuilder` extension, so adding a feature touches this file by
 * exactly one `include` line plus one destination entry — there is no central route table that can
 * drift from the features that actually exist.
 *
 * Four bottom-bar destinations is the practical ceiling before labels truncate on a 360dp phone;
 * anything beyond that belongs in Settings or a "More" tab.
 */
@Composable
fun TallyBookApp() {
    TallyBookTheme {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = backStackEntry?.destination

        val topLevelDestinations = listOf(
            TopLevelDestination(
                route = TransactionsRoute.Transactions,
                routeClass = TransactionsRoute.Transactions::class,
                icon = Icons.AutoMirrored.Filled.List,
                labelRes = R.string.nav_transactions,
            ),
            TopLevelDestination(
                route = InsightsRoute.Insights,
                routeClass = InsightsRoute.Insights::class,
                icon = Icons.Filled.PieChart,
                labelRes = R.string.nav_statistics,
            ),
            TopLevelDestination(
                route = BudgetRoute.Budget,
                routeClass = BudgetRoute.Budget::class,
                icon = Icons.Filled.Savings,
                labelRes = R.string.nav_budget,
            ),
            TopLevelDestination(
                route = SettingsRoute.Settings,
                routeClass = SettingsRoute.Settings::class,
                icon = Icons.Filled.Settings,
                labelRes = R.string.nav_settings,
            ),
        )

        Scaffold(
            bottomBar = {
                NavigationBar {
                    topLevelDestinations.forEach { destination ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.hasRoute(destination.routeClass)
                        } == true

                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    // Single copy of each top-level screen, restoring scroll state on
                                    // return: the standard back-stack contract for a bottom-nav app.
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(stringResource(destination.labelRes)) },
                        )
                    }
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = TransactionsRoute.Transactions,
                modifier = Modifier.padding(innerPadding),
            ) {
                transactionsGraph(
                    navController = navController,
                    onOpenInsights = {
                        navController.navigate(InsightsRoute.Insights) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
                recycleBinGraph(navController)
                insightsGraph(navController)
                budgetGraph(navController)
                settingsGraph(
                    navController = navController,
                    onOpenRecycleBin = { navController.navigate(TransactionsRoute.RecycleBin) },
                )
            }
        }
    }
}
