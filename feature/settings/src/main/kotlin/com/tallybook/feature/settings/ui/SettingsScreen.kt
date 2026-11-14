package com.tallybook.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Switch
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tallybook.core.model.settings.ThemeMode
import com.tallybook.core.model.settings.UiStyle
import com.tallybook.core.ui.components.TallyCard
import com.tallybook.core.ui.components.tallyCardColors

/**
 * Settings.
 *
 * The destructive action ("清空全部数据") is an outlined button behind a confirmation dialog, while
 * the additive one ("载入示例数据") is a single tap. Matching destructive weight to the actual risk is
 * the whole of good settings design.
 */
@Composable
fun SettingsScreen(
    onOpenRecycleBin: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenAccounts: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsContent(
        state = state,
        onLoadDemo = viewModel::loadDemoData,
        onClearAll = viewModel::clearAllData,
        onMessageShown = viewModel::onMessageShown,
        onOpenRecycleBin = onOpenRecycleBin,
        onNotificationsEnabledChange = viewModel::setNotificationsEnabled,
        onThemeModeChange = viewModel::setThemeMode,
        onUiStyleChange = viewModel::setUiStyle,
        onExportCsv = viewModel::exportCsv,
        onExportJson = viewModel::exportJson,
        onOpenCategories = onOpenCategories,
        onOpenAccounts = onOpenAccounts,
        onImportSelected = viewModel::onImportSelected,
        onConfirmImport = viewModel::confirmImport,
        onDismissImport = viewModel::dismissImport,
    )
}

@Composable
fun SettingsContent(
    state: SettingsUiState,
    onLoadDemo: () -> Unit,
    onClearAll: () -> Unit,
    onMessageShown: () -> Unit,
    onOpenRecycleBin: () -> Unit = {},
    onNotificationsEnabledChange: (Boolean) -> Unit = {},
    onThemeModeChange: (ThemeMode) -> Unit = {},
    onUiStyleChange: (UiStyle) -> Unit = {},
    onExportCsv: (Uri) -> Unit = {},
    onExportJson: (Uri) -> Unit = {},
    onOpenCategories: () -> Unit = {},
    onOpenAccounts: () -> Unit = {},
    onImportSelected: (Uri) -> Unit = {},
    onConfirmImport: () -> Unit = {},
    onDismissImport: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmClear by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // SAF: the user picks the destination, so no storage permission is needed and the file can land
    // in Drive / a NAS / the SD card.
    val exportCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> uri?.let(onExportCsv) }
    val exportJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(onExportJson) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onImportSelected) }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onMessageShown()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TallyCard(
                colors = tallyCardColors(MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "让账本更像你的账本",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        "切换明暗模式与界面质感，偏好会保存在本机并立即生效。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SettingsPanel(
                title = "外观与质感",
                subtitle = "显示模式和界面风格可分别选择。",
            ) {
                SettingsSubLabel("显示模式")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.themeMode == mode,
                            onClick = { onThemeModeChange(mode) },
                            label = { Text(mode.label()) },
                        )
                    }
                }

                SettingsSubLabel("界面风格")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    UiStyle.entries.forEach { style ->
                        FilterChip(
                            selected = state.uiStyle == style,
                            onClick = { onUiStyleChange(style) },
                            label = { Text(style.label()) },
                        )
                    }
                }
                Text(
                    state.uiStyle.description(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SettingsPanel(
                title = "提醒",
                subtitle = "预算提醒完全在本机计算。",
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("预算超支提醒", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (state.notificationsPermitted) {
                                "接近或超出预算时通知你，每个周期最多两次。"
                            } else {
                                "系统未授权通知，提醒不会显示。"
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
                        onCheckedChange = onNotificationsEnabledChange,
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
                        Text("去系统设置开启通知权限")
                    }
                }
            }

            SettingsPanel(
                title = "本地数据",
                subtitle = "账本只保存在这台设备上，不会上传到服务器。",
            ) {
                Button(
                    onClick = onLoadDemo,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.busy) "处理中…" else "载入示例数据")
                }
                SettingsHint("仅在没有记录时写入，用于预览图表与预算。")
                OutlinedButton(
                    onClick = { confirmClear = true },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("清空全部数据", color = MaterialTheme.colorScheme.error)
                }
            }

            SettingsPanel(
                title = "备份与恢复",
                subtitle = "JSON 是完整备份；CSV 适合交给表格软件查看。",
            ) {
                Button(
                    onClick = { exportCsvLauncher.launch(suggestedFileName("csv")) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("导出 CSV")
                }
                OutlinedButton(
                    onClick = { exportJsonLauncher.launch(suggestedFileName("json")) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("导出 JSON")
                }
                OutlinedButton(
                    onClick = {
                        importLauncher.launch(
                            arrayOf("application/json", "text/plain", "application/octet-stream"),
                        )
                    },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("导入 JSON 备份")
                }
                SettingsHint("导入前会预览数量，已有记录自动跳过。")
            }

            SettingsPanel(
                title = "账本结构",
                subtitle = "管理记账时使用的分类、账户与已删除记录。",
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

            SettingsPanel(
                title = "即将支持",
                subtitle = "标签 · 多账本 · 定期记账",
            ) {}

            Spacer(Modifier.height(24.dp))
        }
    }

    state.importPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = onDismissImport,
            title = { Text("确认导入？") },
            text = {
                Column {
                    Text("备份文件包含：")
                    Text("· 流水 ${preview.transactions} 条（其中 ${preview.duplicates} 条已存在会被跳过）")
                    Text("· 账户 ${preview.accounts} 个 / 分类 ${preview.categories} 个 / 预算 ${preview.budgets} 个")
                    if (preview.hasBlockingProblems) {
                        Text(
                            "备份存在问题，导入会被拒绝：",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        if (preview.missingAccountIds.isNotEmpty()) {
                            Text("· 缺失账户 ${preview.missingAccountIds.size} 个")
                        }
                        if (preview.missingCategoryIds.isNotEmpty()) {
                            Text("· 缺失分类 ${preview.missingCategoryIds.size} 个")
                        }
                        if (preview.missingRefundIds.isNotEmpty()) {
                            Text("· 缺失退款原记录 ${preview.missingRefundIds.size} 条")
                        }
                        if (preview.duplicateIds.isNotEmpty()) {
                            Text("· 文件内重复 ID ${preview.duplicateIds.size} 个")
                        }
                        if (preview.invalidRecordIds.isNotEmpty()) {
                            Text("· 无效记录 ${preview.invalidRecordIds.size} 条")
                        }
                        if (preview.currencyMismatch) {
                            Text("· 备份币种与当前账本不一致")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = onConfirmImport,
                    enabled = !preview.hasBlockingProblems,
                ) { Text("导入") }
            },
            dismissButton = {
                TextButton(onClick = onDismissImport) { Text("取消") }
            },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空全部数据？") },
            text = { Text("所有记录、账户与分类都会被删除，无法恢复。建议先导出 JSON 备份。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClearAll()
                }) { Text("确认清空") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            },
        )
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
private fun SettingsSubLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
private fun SettingsHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "跟随系统"
    ThemeMode.LIGHT -> "亮色"
    ThemeMode.DARK -> "暗色"
}

private fun UiStyle.label(): String = when (this) {
    UiStyle.GLASS -> "毛玻璃"
    UiStyle.SIMPLE -> "简约"
    UiStyle.FLAT -> "扁平"
}

private fun UiStyle.description(): String = when (this) {
    UiStyle.GLASS -> "半透明面板、渐变氛围与细描边，层次更丰富。"
    UiStyle.SIMPLE -> "柔和圆角、克制阴影与暖色留白，长时间阅读更轻松。"
    UiStyle.FLAT -> "紧凑圆角、无投影、细边界，信息密度更高。"
}

/** `tallybook-20261107.csv` — a dated name makes repeated exports distinguishable in the picker. */
private fun suggestedFileName(extension: String): String {
    val date = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
    return "tallybook-$date.$extension"
}
