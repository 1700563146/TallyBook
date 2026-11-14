package com.tallybook.feature.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.tallybook.feature.settings.manage.ManageAccountsScreen
import com.tallybook.feature.settings.manage.ManageCategoriesScreen
import com.tallybook.feature.settings.ui.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable
sealed interface SettingsRoute {
    @Serializable
    data object Settings : SettingsRoute

    /** Add / rename / archive categories. */
    @Serializable
    data object ManageCategories : SettingsRoute

    /** Add / edit / archive accounts. */
    @Serializable
    data object ManageAccounts : SettingsRoute
}

fun NavGraphBuilder.settingsGraph(
    navController: NavController,
    onOpenRecycleBin: () -> Unit,
) {
    composable<SettingsRoute.Settings> {
        SettingsScreen(
            onOpenRecycleBin = onOpenRecycleBin,
            onOpenCategories = { navController.navigate(SettingsRoute.ManageCategories) },
            onOpenAccounts = { navController.navigate(SettingsRoute.ManageAccounts) },
        )
    }

    composable<SettingsRoute.ManageCategories> {
        ManageCategoriesScreen(onBack = { navController.popBackStack() })
    }

    composable<SettingsRoute.ManageAccounts> {
        ManageAccountsScreen(onBack = { navController.popBackStack() })
    }
}
