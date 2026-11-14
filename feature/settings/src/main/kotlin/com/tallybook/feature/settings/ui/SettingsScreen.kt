package com.tallybook.feature.settings.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tallybook.core.model.settings.ThemeMode
import com.tallybook.core.model.settings.UiStyle
import com.tallybook.core.ui.components.TallyCard
import com.tallybook.core.ui.components.tallyCardColors
import com.tallybook.core.ui.components.tallyCardElevation

@Composable
fun SettingsScreen(
    onOpenAppearance: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenBackupRestore: () -> Unit,
    onOpenLedger: () -> Unit,
    onOpenLocalData: () -> Unit,
) {
    Scaffold(containerColor = Color.Transparent) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("设置", style = MaterialTheme.typography.headlineSmall)
            Text(
                "按类别进入对应设置，本机偏好会立即生效。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))

            SettingsEntryCard(
                title = "界面",
                description = "显示模式、亮色/暗色与简约/扁平风格",
                icon = Icons.Filled.Palette,
                onClick = onOpenAppearance,
            )
            SettingsEntryCard(
                title = "提醒",
                description = "预算接近或超支时的本机通知",
                icon = Icons.Filled.Notifications,
                onClick = onOpenNotifications,
            )
            SettingsEntryCard(
                title = "备份与恢复",
                description = "导出 CSV/JSON，预览并导入备份",
                icon = Icons.Filled.Backup,
                onClick = onOpenBackupRestore,
            )
            SettingsEntryCard(
                title = "账本管理",
                description = "分类、账户和回收站",
                icon = Icons.Filled.AccountBalanceWallet,
                onClick = onOpenLedger,
            )
            SettingsEntryCard(
                title = "本地数据",
                description = "载入示例数据或清空当前账本",
                icon = Icons.Filled.Folder,
                onClick = onOpenLocalData,
            )
            Text(
                "数据只保存在本机，不会自动上传。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
fun AppearanceSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsPage(title = "界面", onBack = onBack) {
        IntroCard(
            title = "按你的习惯显示",
            description = "主题模式和界面风格分别保存，切换后全局立即生效。",
        )
        SettingsPanel(
            title = "显示模式",
            subtitle = "跟随系统会随设备深浅色设置自动变化。",
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        label = { Text(mode.label()) },
                    )
                }
            }
        }
        SettingsPanel(
            title = "界面风格",
            subtitle = state.uiStyle.description(),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UiStyle.entries.forEach { style ->
                    FilterChip(
                        selected = state.uiStyle == style,
                        onClick = { viewModel.setUiStyle(style) },
                        label = { Text(style.label()) },
                    )
                }
            }
        }
    }
}

@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.refreshPermissionState() }

    SettingsPage(title = "提醒", onBack = onBack) {
        SettingsPanel(
            title = "预算超支提醒",
            subtitle = "提醒完全由本机计算，不会上传预算或流水。",
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("启用提醒", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (state.notificationsPermitted) {
                            "接近或超出预算时通知，每个周期最多两次。"
                        } else {
                            "系统尚未授权通知，提醒不会显示。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.notificationsPermitted) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
                Switch(
                    checked = state.notificationsEnabled,
                    onCheckedChange = viewModel::setNotificationsEnabled,
                )
            }
            if (!state.notificationsPermitted) {
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        runCatching { context.startActivity(intent) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("打开系统通知设置")
                }
            }
        }
    }
}

@Composable
fun BackupRestoreSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri: Uri? -> uri?.let(viewModel::exportCsv) }
    val exportJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? -> uri?.let(viewModel::exportJson) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? -> uri?.let(viewModel::onImportSelected) }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onMessageShown()
    }

    SettingsPage(
        title = "备份与恢复",
        onBack = onBack,
        snackbarHostState = snackbarHostState,
    ) {
        IntroCard(
            title = "把数据掌握在自己手里",
            description = "文件通过系统文件选择器保存，账本不会上传到服务器。",
        )
        SettingsPanel(
            title = "导出",
            subtitle = "CSV 便于表格查看；JSON 是包含账户、分类和预算的完整备份。",
        ) {
            Button(
                onClick = { exportCsvLauncher.launch(suggestedFileName("csv")) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("导出 CSV") }
            OutlinedButton(
                onClick = { exportJsonLauncher.launch(suggestedFileName("json")) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("导出 JSON") }
        }
        SettingsPanel(
            title = "恢复",
            subtitle = "导入前会展示记录数量，已存在的记录自动跳过。",
        ) {
            OutlinedButton(
                onClick = {
                    importLauncher.launch(
                        arrayOf("application/json", "text/plain", "application/octet-stream"),
                    )
                },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("导入 JSON 备份") }
        }
    }

    state.importPreview?.let { preview ->
        ImportPreviewDialog(
            preview = preview,
            onConfirm = viewModel::confirmImport,
            onDismiss = viewModel::dismissImport,
        )
    }
}

@Composable
fun LedgerSettingsScreen(
    onBack: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenRecycleBin: () -> Unit,
) {
    SettingsPage(title = "账本管理", onBack = onBack) {
        SettingsPanel(
            title = "账本结构",
            subtitle = "归档或整理账本项不会删除历史流水。",
        ) {
            OutlinedButton(
                onClick = onOpenCategories,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("分类管理") }
            OutlinedButton(
                onClick = onOpenAccounts,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("账户管理") }
            OutlinedButton(
                onClick = onOpenRecycleBin,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("回收站") }
        }
    }
}

@Composable
fun LocalDataSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onMessageShown()
    }

    SettingsPage(
        title = "本地数据",
        onBack = onBack,
        snackbarHostState = snackbarHostState,
    ) {
        SettingsPanel(
            title = "示例数据",
            subtitle = "仅在没有记录时写入，用于预览图表与预算效果。",
        ) {
            Button(
                onClick = viewModel::loadDemoData,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.busy) "处理中…" else "载入示例数据")
            }
        }
        SettingsPanel(
            title = "清空账本",
            subtitle = "所有记录、账户、分类和预算都会被删除，无法恢复。",
        ) {
            OutlinedButton(
                onClick = { confirmClear = true },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("清空全部数据", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空全部数据？") },
            text = { Text("此操作无法撤销，建议先在“备份与恢复”中导出 JSON。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        viewModel.clearAllData()
                    },
                ) { Text("确认清空") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SettingsEntryCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = tallyCardColors(),
        elevation = tallyCardElevation(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.size(44.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Card(
                    colors = tallyCardColors(MaterialTheme.colorScheme.primaryContainer),
                    shape = CircleShape,
                    elevation = tallyCardElevation(),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsPage(
    title: String,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState? = null,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        snackbarHost = {
            snackbarHostState?.let { SnackbarHost(it) }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

@Composable
private fun IntroCard(title: String, description: String) {
    TallyCard(
        modifier = Modifier.fillMaxWidth(),
        colors = tallyCardColors(MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsPanel(
    title: String,
    subtitle: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    TallyCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}

@Composable
private fun ImportPreviewDialog(
    preview: com.tallybook.core.data.export.ImportPreview,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认导入？") },
        text = {
            Column {
                Text("· 流水 ${preview.transactions} 条（重复 ${preview.duplicates} 条）")
                Text("· 账户 ${preview.accounts} / 分类 ${preview.categories} / 预算 ${preview.budgets}")
                if (preview.hasBlockingProblems) {
                    Text(
                        "备份存在问题，导入会被拒绝。",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    if (preview.missingAccountIds.isNotEmpty()) {
                        Text("缺失账户 ${preview.missingAccountIds.size} 个")
                    }
                    if (preview.missingCategoryIds.isNotEmpty()) {
                        Text("缺失分类 ${preview.missingCategoryIds.size} 个")
                    }
                    if (preview.missingRefundIds.isNotEmpty()) {
                        Text("缺失退款原记录 ${preview.missingRefundIds.size} 条")
                    }
                    if (preview.duplicateIds.isNotEmpty()) {
                        Text("文件内重复 ID ${preview.duplicateIds.size} 个")
                    }
                    if (preview.invalidRecordIds.isNotEmpty()) {
                        Text("无效记录 ${preview.invalidRecordIds.size} 条")
                    }
                    if (preview.currencyMismatch) {
                        Text("备份币种与当前账本不一致")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !preview.hasBlockingProblems) {
                Text("导入")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "跟随系统"
    ThemeMode.LIGHT -> "亮色"
    ThemeMode.DARK -> "暗色"
}

private fun UiStyle.label(): String = when (this) {
    UiStyle.SIMPLE -> "简约"
    UiStyle.FLAT -> "扁平"
}

private fun UiStyle.description(): String = when (this) {
    UiStyle.SIMPLE -> "柔和圆角、无描边、暖色留白，长时间阅读更轻松。"
    UiStyle.FLAT -> "紧凑圆角、无投影、无描边，信息密度更高。"
}

private fun suggestedFileName(extension: String): String {
    val date = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
    return "tallybook-$date.$extension"
}
