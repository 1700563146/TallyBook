package com.tallybook.feature.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.tallybook.feature.settings.manage.ManageAccountsScreen
import com.tallybook.feature.settings.manage.ManageCategoriesScreen
import com.tallybook.feature.settings.ui.AppearanceSettingsScreen
import com.tallybook.feature.settings.ui.BackupRestoreSettingsScreen
import com.tallybook.feature.settings.ui.LedgerSettingsScreen
import com.tallybook.feature.settings.ui.LocalDataSettingsScreen
import com.tallybook.feature.settings.ui.NotificationSettingsScreen
import com.tallybook.feature.settings.ui.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable
sealed interface SettingsRoute {
    @Serializable
    data object Settings : SettingsRoute

    @Serializable
    data object Appearance : SettingsRoute

    @Serializable
    data object Notifications : SettingsRoute

    @Serializable
    data object BackupRestore : SettingsRoute

    @Serializable
    data object Ledger : SettingsRoute

    @Serializable
    data object LocalData : SettingsRoute

    @Serializable
    data object ManageCategories : SettingsRoute

    @Serializable
    data object ManageAccounts : SettingsRoute
}

fun NavGraphBuilder.settingsGraph(
    navController: NavController,
    onOpenRecycleBin: () -> Unit,
) {
    composable<SettingsRoute.Settings> {
        SettingsScreen(
            onOpenAppearance = { navController.navigate(SettingsRoute.Appearance) },
            onOpenNotifications = { navController.navigate(SettingsRoute.Notifications) },
            onOpenBackupRestore = { navController.navigate(SettingsRoute.BackupRestore) },
            onOpenLedger = { navController.navigate(SettingsRoute.Ledger) },
            onOpenLocalData = { navController.navigate(SettingsRoute.LocalData) },
        )
    }

    composable<SettingsRoute.Appearance> {
        AppearanceSettingsScreen(onBack = { navController.popBackStack() })
    }

    composable<SettingsRoute.Notifications> {
        NotificationSettingsScreen(onBack = { navController.popBackStack() })
    }

    composable<SettingsRoute.BackupRestore> {
        BackupRestoreSettingsScreen(onBack = { navController.popBackStack() })
    }

    composable<SettingsRoute.Ledger> {
        LedgerSettingsScreen(
            onBack = { navController.popBackStack() },
            onOpenCategories = { navController.navigate(SettingsRoute.ManageCategories) },
            onOpenAccounts = { navController.navigate(SettingsRoute.ManageAccounts) },
            onOpenRecycleBin = onOpenRecycleBin,
        )
    }

    composable<SettingsRoute.LocalData> {
        LocalDataSettingsScreen(onBack = { navController.popBackStack() })
    }

    composable<SettingsRoute.ManageCategories> {
        ManageCategoriesScreen(onBack = { navController.popBackStack() })
    }

    composable<SettingsRoute.ManageAccounts> {
        ManageAccountsScreen(onBack = { navController.popBackStack() })
    }
}
