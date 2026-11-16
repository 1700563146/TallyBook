package com.tallybook.feature.budget.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tallybook.core.model.transaction.BudgetPeriod
import androidx.compose.foundation.text.KeyboardOptions

/**
 * Budget editor.
 *
 * A plain form with the system keyboard, deliberately unlike the transaction editor's custom
 * keypad: a budget limit is typed once or twice ever, so arithmetic support would be dead weight,
 * while a normal text field gets autofill, cursor movement and accessibility for free.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetEditorScreen(
    onClose: () -> Unit,
    viewModel: BudgetEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val finished by viewModel.finished.collectAsStateWithLifecycle()

    LaunchedEffect(finished) {
        if (finished) onClose()
    }

    BudgetEditorContent(
        state = state,
        onBack = onClose,
        onNameChange = viewModel::onNameChange,
        onLimitChange = viewModel::onLimitChange,
        onPeriodChange = viewModel::onPeriodChange,
        onCategorySelected = viewModel::onCategorySelected,
        onIncludeSubcategoriesChange = viewModel::onIncludeSubcategoriesChange,
        onOpenSubcategoryDialog = viewModel::openSubcategoryDialog,
        onDismissSubcategoryDialog = viewModel::dismissSubcategoryDialog,
        onCreateSubcategory = viewModel::createSubcategory,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BudgetEditorContent(
    state: BudgetEditorUiState,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onLimitChange: (String) -> Unit,
    onPeriodChange: (BudgetPeriod) -> Unit,
    onCategorySelected: (String?) -> Unit,
    onIncludeSubcategoriesChange: (Boolean) -> Unit,
    onOpenSubcategoryDialog: () -> Unit,
    onDismissSubcategoryDialog: () -> Unit,
    onCreateSubcategory: (String, String?) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text(if (state.isEditing) "编辑预算" else "新建预算") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (state.isEditing) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = onNameChange,
                label = { Text("名称") },
                placeholder = { Text("例如：餐饮") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Column {
                Text("周期", style = MaterialTheme.typography.labelMedium)
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                ) {
                    listOf(
                        BudgetPeriod.WEEKLY to "每周",
                        BudgetPeriod.MONTHLY to "每月",
                    ).forEachIndexed { index, (period, label) ->
                        SegmentedButton(
                            selected = state.period == period,
                            onClick = { onPeriodChange(period) },
                            shape = SegmentedButtonDefaults.itemShape(index, 2),
                        ) {
                            Text(label)
                        }
                    }
                }
            }

            OutlinedTextField(
                value = state.limitText,
                onValueChange = onLimitChange,
                label = { Text("限额（元）") },
                prefix = { Text("¥") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            Column {
                Text("适用分类", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = state.isAllCategories,
                        onClick = { onCategorySelected(null) },
                        label = { Text("全部分类") },
                    )
                    state.categories.forEach { category ->
                        FilterChip(
                            selected = state.categoryId == category.id,
                            onClick = { onCategorySelected(category.id) },
                            label = {
                                Text(
                                    category.displayName +
                                        if (category.childCount > 0) {
                                            " · ${category.childCount} 个子分类"
                                        } else {
                                            ""
                                        },
                                )
                            },
                        )
                    }
                }
                if (state.categories.isEmpty()) {
                    Text(
                        "还没有支出分类，先去记一笔或到设置里添加。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(
                    onClick = onOpenSubcategoryDialog,
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                    Text(if (state.isAllCategories) "新建分类" else "新建子分类")
                }
            }

            if (state.selectedCategory?.isParent == true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("汇总子分类支出", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "开启后，${state.selectedCategory?.childCount ?: 0} 个子分类的支出也算进这个预算。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.includeSubcategories,
                        onCheckedChange = onIncludeSubcategoriesChange,
                    )
                }
            } else {
                Text(
                    if (state.isAllCategories) {
                        "全部分类预算已包含所有支出。"
                    } else {
                        "这是独立分类预算；新建子分类后可另行设置汇总预算。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            state.error?.let { message ->
                Text(
                    message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Button(
                onClick = onSave,
                enabled = state.canSave && !state.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(if (state.isSaving) "保存中…" else "保存")
            }

            if (state.isEditing) {
                AssistChip(
                    onClick = { confirmDelete = true },
                    label = { Text("删除这个预算") },
                    colors = AssistChipDefaults.assistChipColors(
                        labelColor = MaterialTheme.colorScheme.error,
                    ),
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除预算？") },
            text = { Text("删除后不再统计这个预算的执行情况，已有的记账记录不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
        )
    }

    state.subcategoryDialog?.let { dialog ->
        var name by remember(dialog) { mutableStateOf("") }
        var parentId by remember(dialog) { mutableStateOf(dialog.defaultParentId) }
        val parentOptions = state.categories.filter { it.depth == 0 }

        AlertDialog(
            onDismissRequest = onDismissSubcategoryDialog,
            title = { Text(if (parentId == null) "新建顶级分类" else "新建子分类") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                        },
                        label = { Text("分类名称") },
                        placeholder = { Text("例如：早餐") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("上级分类", style = MaterialTheme.typography.labelMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = parentId == null,
                            onClick = { parentId = null },
                            label = { Text("顶级分类") },
                        )
                        parentOptions.forEach { parent ->
                            FilterChip(
                                selected = parentId == parent.id,
                                onClick = { parentId = parent.id },
                                label = { Text(parent.name) },
                            )
                        }
                    }
                    state.categoryError?.let { error ->
                        Text(
                            error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(
                        "创建后会自动选中这个分类；填写限额并保存即可为它设置预算。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onCreateSubcategory(name, parentId) },
                    enabled = name.isNotBlank() && !state.isCreatingCategory,
                ) {
                    Text(if (state.isCreatingCategory) "创建中…" else "创建并设置预算")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissSubcategoryDialog) { Text("取消") }
            },
        )
    }
}
