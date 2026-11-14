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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("提醒", style = MaterialTheme.typography.titleMedium)

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
                // The switch alone cannot fix this, so offer the actual remedy.
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

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text("数据", style = MaterialTheme.typography.titleMedium)
            Text(
                "账本只保存在这台设备上，不会上传到任何服务器。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = onLoadDemo,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.busy) "处理中…" else "载入示例数据")
            }
            Text(
                "仅在你还没有任何记录时才会写入，用于预览图表与预算效果。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedButton(
                onClick = { confirmClear = true },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("清空全部数据")
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text("导出备份", style = MaterialTheme.typography.titleMedium)
            Text(
                "CSV 给表格软件看，JSON 是完整备份（含已删除记录与预算）。文件保存在你选的位置。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("导入 JSON 备份")
            }
            Text(
                "导入前会先显示将写入多少条记录，已存在的记录会自动跳过。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text("记账", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(
                onClick = onOpenCategories,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("分类管理") }
            Text(
                "增改名、归档不用的分类。归档只影响以后记账，历史记录还在。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = onOpenAccounts,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("账户管理") }
            Text(
                "现金、支付宝、银行卡等；可设置初始余额。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = onOpenRecycleBin,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("回收站")
            }
            Text(
                "删除的记录会先放在这里，可以恢复或彻底删除。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text("即将支持", style = MaterialTheme.typography.titleMedium)
            Text(
                """
                · 标签
                · 多账本
                · 定期记账（房租、订阅自动生成）
                """.trimIndent(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

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

/** `tallybook-20261107.csv` — a dated name makes repeated exports distinguishable in the picker. */
private fun suggestedFileName(extension: String): String {
    val date = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
    return "tallybook-$date.$extension"
}
