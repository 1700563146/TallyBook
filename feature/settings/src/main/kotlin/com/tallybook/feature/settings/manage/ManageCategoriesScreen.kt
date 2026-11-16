package com.tallybook.feature.settings.manage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tallybook.core.model.transaction.TransactionType

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ManageCategoriesScreen(
    onBack: () -> Unit,
    viewModel: ManageCategoriesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onMessageShown()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text("分类管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::startAdd) {
                        Icon(Icons.Filled.Add, contentDescription = "添加分类")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                listOf(
                    TransactionType.EXPENSE to "支出分类",
                    TransactionType.INCOME to "收入分类",
                ).forEachIndexed { index, (type, label) ->
                    SegmentedButton(
                        selected = state.type == type,
                        onClick = { viewModel.selectType(type) },
                        shape = SegmentedButtonDefaults.itemShape(index, 2),
                    ) { Text(label) }
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(state.categories, key = { it.id }) { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.startRename(row) }
                            .padding(
                                start = (16 + row.depth * 24).dp,
                                end = 8.dp,
                                top = 12.dp,
                                bottom = 12.dp,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (row.depth > 0) "↳ ${row.name}" else row.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (row.isArchived) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                            Text(
                                buildString {
                                    if (row.depth > 0) append("子分类 · ")
                                    append(if (row.isArchived) "已归档" else row.usageLabel)
                                    if (row.isArchived) append(" · ${row.usageLabel}")
                                    if (row.childCount > 0) append(" · ${row.childCount} 个子分类")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (row.depth == 0 && !row.isArchived) {
                            IconButton(onClick = { viewModel.startAdd(row.id) }) {
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = "在「${row.name}」下添加子分类",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.toggleArchive(row) }) {
                            Icon(
                                if (row.isArchived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                                contentDescription = if (row.isArchived) "恢复" else "归档",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }

    state.editing?.let { dialog ->
        var name by remember(dialog) { mutableStateOf(dialog.initialName) }
        var parentId by remember(dialog) { mutableStateOf(dialog.parentId) }
        val editingRow = dialog.categoryId?.let { id ->
            state.categories.firstOrNull { it.id == id }
        }
        val canChooseParent = editingRow?.childCount?.let { it == 0 } ?: true
        val parentOptions = state.categories.filter {
            it.depth == 0 &&
                it.id != dialog.categoryId &&
                (!it.isArchived || it.id == dialog.parentId)
        }
        AlertDialog(
            onDismissRequest = viewModel::dismissDialog,
            title = {
                Text(
                    when {
                        dialog.isNew && parentId != null -> "新建子分类"
                        dialog.isNew -> "新建分类"
                        else -> "编辑分类"
                    },
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("名称") },
                        singleLine = true,
                    )
                    if (canChooseParent) {
                        Text("上级分类", style = MaterialTheme.typography.labelMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = parentId == null,
                                onClick = { parentId = null },
                                label = { Text("顶级分类") },
                            )
                            parentOptions.forEach { parent ->
                                FilterChip(
                                    selected = parentId == parent.id,
                                    onClick = { parentId = parent.id },
                                    label = {
                                        Text(
                                            parent.name +
                                                if (parent.isArchived) "（已归档）" else "",
                                        )
                                    },
                                )
                            }
                        }
                    } else {
                        Text(
                            "这个分类已有子分类，需保留为顶级分类。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.submit(name, parentId) },
                    enabled = name.isNotBlank(),
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDialog) { Text("取消") }
            },
        )
    }
}
