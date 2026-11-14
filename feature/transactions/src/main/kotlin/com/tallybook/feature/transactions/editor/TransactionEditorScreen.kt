package com.tallybook.feature.transactions.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tallybook.core.model.money.AmountKey
import com.tallybook.feature.transactions.categoryIcon
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Full-page editor, used for **editing** an existing row and for refunds.
 *
 * New entries are created in [TransactionEditorSheet] instead (bottom sheet from the ledger), because
 * recording a purchase should not move the user to a different screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditorScreen(
    onClose: () -> Unit,
    onRefund: (String) -> Unit,
    viewModel: TransactionEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val finished by viewModel.finished.collectAsStateWithLifecycle()

    LaunchedEffect(finished) {
        if (finished) onClose()
    }

    TransactionEditorContent(
        state = state,
        onBack = onClose,
        onKey = viewModel::onKey,
        onModeChange = viewModel::onModeChange,
        onCategorySelected = viewModel::onCategorySelected,
        onAccountSelected = viewModel::onAccountSelected,
        onCounterAccountSelected = viewModel::onCounterAccountSelected,
        onDateSelected = viewModel::onDateSelected,
        onNoteChange = viewModel::onNoteChange,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
        onRefund = onRefund,
    )
}

/**
 * Bottom-sheet editor for a new transaction.
 *
 * The keyboard-styled layout sits in a sheet that covers most of the screen: the ledger stays visible
 * behind it, so the user keeps their context (which month, which rows) while typing an amount.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditorSheet(
    onDismiss: () -> Unit,
    viewModel: TransactionEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val finished by viewModel.finished.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(finished) {
        if (finished) onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            // A tall sheet instead of a short one: the form (categories, account, date, note) needs
            // room, and a hard height cap cut the category chips in half.
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .imePadding()
                .padding(bottom = 8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "记一笔",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭")
                }
            }

            TransactionEditorBody(
                state = state,
                onKey = viewModel::onKey,
                onModeChange = viewModel::onModeChange,
                onCategorySelected = viewModel::onCategorySelected,
                onAccountSelected = viewModel::onAccountSelected,
                onCounterAccountSelected = viewModel::onCounterAccountSelected,
                onNoteChange = viewModel::onNoteChange,
                onSave = viewModel::save,
                onPickDate = { showDatePicker = true },
                modifier = Modifier.weight(1f),
                // The form takes the space above the keypad and scrolls; the keypad and save button
                // stay pinned, so the thumb never has to chase them.
                fillHeight = true,
            )
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = state.occurredAt)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { utcMillis ->
                        // DatePicker reports UTC midnight; rebuild it in the device zone so the day
                        // does not shift by one.
                        val localDate = Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()
                        val localMillis = localDate.atStartOfDay(ZoneId.systemDefault())
                            .toInstant().toEpochMilli()
                        viewModel.onDateSelected(localMillis)
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            },
        ) { DatePicker(state = pickerState) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditorContent(
    state: TransactionEditorUiState,
    onBack: () -> Unit,
    onKey: (AmountKey) -> Unit,
    onModeChange: (EditorType) -> Unit,
    onCategorySelected: (String) -> Unit,
    onAccountSelected: (String) -> Unit,
    onCounterAccountSelected: (String) -> Unit,
    onDateSelected: (Long) -> Unit,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onRefund: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            state.isRefund -> "记退款"
                            state.isEditing -> "编辑"
                            else -> "记一笔"
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (state.canRefund) {
                        val id = state.transactionId
                        if (id != null) {
                            TextButton(onClick = { onRefund(id) }) { Text("退款") }
                        }
                    }
                    if (state.isEditing && !state.isRefund) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        TransactionEditorBody(
            state = state,
            onKey = onKey,
            onModeChange = onModeChange,
            onCategorySelected = onCategorySelected,
            onAccountSelected = onAccountSelected,
            onCounterAccountSelected = onCounterAccountSelected,
            onNoteChange = onNoteChange,
            onSave = onSave,
            onPickDate = { showDatePicker = true },
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .imePadding(),
            fillHeight = true,
        )
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = state.occurredAt)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { utcMillis ->
                        val localDate = Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()
                        val localMillis = localDate.atStartOfDay(ZoneId.systemDefault())
                            .toInstant().toEpochMilli()
                        onDateSelected(localMillis)
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            },
        ) { DatePicker(state = pickerState) }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除这笔记录？") },
            text = { Text("删除后可在明细列表的提示里撤销。") },
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
}

/**
 * The shared form: fields on top, keypad and save pinned at the bottom.
 *
 * [formModifier] is how the two hosts differ — a weighted (scrollable) area on the full page, a
 * height-capped area inside the sheet. Everything else is literally the same composable, so the two
 * entry points cannot drift apart.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionEditorBody(
    state: TransactionEditorUiState,
    onKey: (AmountKey) -> Unit,
    onModeChange: (EditorType) -> Unit,
    onCategorySelected: (String) -> Unit,
    onAccountSelected: (String) -> Unit,
    onCounterAccountSelected: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onPickDate: () -> Unit,
    modifier: Modifier = Modifier,
    formModifier: Modifier = Modifier,
    /** True on the full page: the form takes the remaining height instead of a fixed ceiling. */
    fillHeight: Boolean = false,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // `weight` is a ColumnScope modifier, so it has to be built in here rather than passed in.
        val formBase = if (fillHeight) Modifier.weight(1f) else formModifier
        Column(
            modifier = formBase
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            if (state.isRefund) {
                RefundBanner(state.refundSourceLabel)
            } else {
                ModeSelector(mode = state.mode, onModeChange = onModeChange)
            }

            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                AmountDisplay(state)
            }

            if (!state.isTransfer) {
                SectionTitle("分类")
                CategoryChips(
                    categories = state.categories,
                    selectedId = state.selectedCategoryId,
                    onSelect = onCategorySelected,
                )
            }

            SectionTitle(if (state.isTransfer) "从账户" else "账户")
            AccountChips(
                accounts = state.accounts,
                selectedId = state.selectedAccountId,
                excludeId = if (state.isTransfer) state.counterAccountId else null,
                onSelect = onAccountSelected,
            )

            if (state.isTransfer) {
                SectionTitle("转入账户")
                AccountChips(
                    accounts = state.accounts,
                    selectedId = state.counterAccountId,
                    excludeId = state.selectedAccountId,
                    onSelect = onCounterAccountSelected,
                )
            }

            SectionTitle("日期")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onPickDate)
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(state.dateLabel, style = MaterialTheme.typography.bodyLarge)
                Text("修改", style = MaterialTheme.typography.labelMedium)
            }

            OutlinedTextField(
                value = state.note,
                onValueChange = onNoteChange,
                label = { Text("备注") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            state.error?.let { message ->
                Text(
                    message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Spacer(Modifier.height(12.dp))
        }

        Column(modifier = Modifier.padding(horizontal = 12.dp)) {
            AmountKeypad(onKey = onKey)
            Button(
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 10.dp)
                    .height(50.dp),
                enabled = state.canSave && !state.isSaving,
            ) {
                Text(if (state.isSaving) "保存中…" else "保存")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSelector(mode: EditorType, onModeChange: (EditorType) -> Unit) {
    val options = listOf(
        EditorType.EXPENSE to "支出",
        EditorType.INCOME to "收入",
        EditorType.TRANSFER to "转账",
    )
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = mode == value,
                onClick = { onModeChange(value) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) {
                Text(label)
            }
        }
    }
}

@Composable
private fun AmountDisplay(state: TransactionEditorUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.medium)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            "¥",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = state.amount.display,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Shown instead of the direction selector when recording a refund.
 *
 * The selector is hidden rather than disabled: a refund's direction is not a choice, and offering
 * three disabled-looking options would imply otherwise.
 */
@Composable
private fun RefundBanner(sourceLabel: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.medium)
            .padding(12.dp),
    ) {
        Text("退款", style = MaterialTheme.typography.labelLarge)
        Text(
            if (sourceLabel != null) {
                "冲抵：$sourceLabel。这笔金额会从支出里扣掉，不计入收入。"
            } else {
                "这笔金额会从支出里扣掉，不计入收入。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryChips(
    categories: List<CategoryOption>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    if (categories.isEmpty()) {
        Text(
            "没有可用分类，请到设置里添加",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        categories.forEach { category ->
            val selected = category.id == selectedId
            AssistChip(
                onClick = { onSelect(category.id) },
                label = { Text(category.name) },
                leadingIcon = {
                    Icon(
                        imageVector = categoryIcon(category.iconKey),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = when (val argb = category.colorArgb) {
                            null -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> Color(argb.toInt())
                        },
                    )
                },
                colors = if (selected) {
                    AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    )
                } else {
                    AssistChipDefaults.assistChipColors()
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountChips(
    accounts: List<AccountOption>,
    selectedId: String?,
    excludeId: String?,
    onSelect: (String) -> Unit,
) {
    val selectable = accounts.filter { it.id != excludeId }
    if (selectable.isEmpty()) {
        Text(
            "至少需要两个账户才能转账",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        selectable.forEach { account ->
            AssistChip(
                onClick = { onSelect(account.id) },
                label = { Text(account.name) },
                colors = if (account.id == selectedId) {
                    AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    )
                } else {
                    AssistChipDefaults.assistChipColors()
                },
            )
        }
    }
}
