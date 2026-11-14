package com.tallybook.feature.transactions.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.tallybook.feature.transactions.editor.TransactionEditorSheet
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tallybook.feature.transactions.categoryIcon
import com.tallybook.core.ui.components.tallyCardColors
import com.tallybook.core.ui.components.tallyCardDecoration
import com.tallybook.core.ui.components.tallyCardElevation

/**
 * Ledger list.
 *
 * Interaction contract:
 * - tap a row -> edit
 * - long-press a row -> delete, with an undo snackbar
 *
 * Long-press rather than swipe: swipe-to-delete needs a visible affordance to be discoverable, and
 * on a ledger row it fights with horizontal month navigation gestures.
 */
@Composable
fun TransactionsScreen(
    onEditClick: (String) -> Unit,
    onOpenInsights: () -> Unit,
    onOpenSearch: () -> Unit,
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // New entries are recorded in a bottom sheet so the ledger stays visible behind it; editing still
    // opens the full page, because that is a deliberate "go and fix this" action.
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var editorKey by rememberSaveable { mutableIntStateOf(0) }

    TransactionsContent(
        state = state,
        onAddClick = {
            editorKey += 1
            showEditor = true
        },
        onEditClick = onEditClick,
        onOpenInsights = onOpenInsights,
        onOpenSearch = onOpenSearch,
        onDeleteClick = viewModel::delete,
        onPreviousMonth = viewModel::showPreviousMonth,
        onNextMonth = viewModel::showNextMonth,
        onUndo = viewModel::undoDelete,
        onSnackbarShown = viewModel::onSnackbarShown,
    )

    if (showEditor) {
        // A fresh ViewModel per opening: reusing one would show the previous entry's amount.
        TransactionEditorSheet(
            onDismiss = { showEditor = false },
            viewModel = hiltViewModel(key = "new-entry-$editorKey"),
        )
    }
}

@Composable
fun TransactionsContent(
    state: TransactionsUiState,
    onAddClick: () -> Unit,
    onEditClick: (String) -> Unit,
    onDeleteClick: (String) -> Unit,
    onOpenInsights: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onUndo: () -> Unit,
    onSnackbarShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    LaunchedEffect(state.scrollToTopTick) {
        if (state.scrollToTopTick > 0) listState.animateScrollToItem(0)
    }

    LaunchedEffect(state.undoMessage) {
        val message = state.undoMessage ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = "撤销",
            withDismissAction = true,
        )
        when (result) {
            SnackbarResult.ActionPerformed -> onUndo()
            SnackbarResult.Dismissed -> onSnackbarShown()
        }
        onSnackbarShown()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(Icons.Filled.Add, contentDescription = "记一笔")
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            MonthHeader(
                monthLabel = state.monthLabel,
                expenseTotal = state.expenseTotal,
                incomeTotal = state.incomeTotal,
                onPrevious = onPreviousMonth,
                onNext = onNextMonth,
                onSearch = onOpenSearch,
            )

            if (state.budgetCards.isNotEmpty()) {
                BudgetSummaryRow(cards = state.budgetCards)
            }

            if (state.miniChart.isNotEmpty()) {
                MiniChartCard(bars = state.miniChart, onClick = onOpenInsights)
            }

            when {
                state.isLoading -> LoadingBox()
                state.isEmpty -> EmptyState()
                else -> DayGroupList(
                    groups = state.groups,
                    listState = listState,
                    onEditClick = onEditClick,
                    onDeleteClick = onDeleteClick,
                )
            }
        }
    }
}

/**
 * Budget summaries above the chart.
 *
 * Placed on the home screen rather than only inside the budget tab because "how much is left" is the
 * question a user opens the app to answer most often; making them switch tabs for it is friction.
 */
@Composable
private fun BudgetSummaryRow(cards: List<BudgetCardUi>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        cards.forEach { card ->
            Card(
                modifier = Modifier
                    .weight(1f)
                    .tallyCardDecoration(),
                shape = MaterialTheme.shapes.large,
                colors = tallyCardColors(
                    if (card.isOverBudget) MaterialTheme.colorScheme.errorContainer else null,
                ),
                elevation = tallyCardElevation(),
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        card.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    card.subtitle?.let { subtitle ->
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        card.amountLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (card.isOverBudget) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    LinearProgressIndicator(
                        progress = { card.progress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .height(4.dp),
                        color = if (card.isOverBudget) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            }
        }
    }
}

/**
 * Compact 7-day spending chart.
 *
 * Deliberately not the full chart: on the home screen it only has to show "is this week heavier than
 * usual" and act as a large, obvious entry point into the statistics tab.
 */
@Composable
private fun MiniChartCard(bars: List<MiniBarUi>, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .tallyCardDecoration(),
        shape = MaterialTheme.shapes.large,
        colors = tallyCardColors(),
        elevation = tallyCardElevation(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("近 7 天支出", style = MaterialTheme.typography.labelLarge)
                Text(
                    "查看统计 ›",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                bars.forEach { bar ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // Minimum height keeps a zero-spend day visible as a baseline, not a gap.
                        val barHeight = (6f + bar.fraction * 54f).dp
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(barHeight)
                                .background(
                                    color = if (bar.isToday) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                    },
                                    shape = RoundedCornerShape(4.dp),
                                ),
                        )
                        Text(
                            bar.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthHeader(
    monthLabel: String,
    expenseTotal: String,
    incomeTotal: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSearch: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .tallyCardDecoration(),
        shape = MaterialTheme.shapes.large,
        colors = tallyCardColors(),
        elevation = tallyCardElevation(),
    ) {
      Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "上个月")
            }
            Text(
                monthLabel,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IconButton(onClick = onNext) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "下个月")
            }
            IconButton(onClick = onSearch) {
                Icon(Icons.Filled.Search, contentDescription = "搜索与筛选")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column {
                Text("支出", style = MaterialTheme.typography.labelSmall)
                Text(
                    expenseTotal,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Column {
                Text("收入", style = MaterialTheme.typography.labelSmall)
                Text(
                    incomeTotal,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
      }
    }
}

@Composable
private fun DayGroupList(
    groups: List<DayGroupUi>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onEditClick: (String) -> Unit,
    onDeleteClick: (String) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        groups.forEach { group ->
            item(key = "header-${group.dateKey}") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        group.dayLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(group.expenseLabel, style = MaterialTheme.typography.labelSmall)
                }
            }
            items(group.items, key = { it.id }) { row ->
                TransactionRow(
                    row = row,
                    onClick = { onEditClick(row.id) },
                    onLongClick = { onDeleteClick(row.id) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransactionRow(
    row: TransactionRowUi,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .tallyCardDecoration(),
        shape = MaterialTheme.shapes.medium,
        colors = tallyCardColors(),
        elevation = tallyCardElevation(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = categoryTint(row.colorArgb),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = categoryIcon(row.iconKey),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = ContentOnTint,
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(row.categoryName, style = MaterialTheme.typography.bodyLarge)
                val accountsLabel = when {
                    row.isTransfer && row.counterAccountName != null ->
                        "${row.accountName} → ${row.counterAccountName}"
                    else -> row.accountName
                }
                val subtitle = listOf(row.note, accountsLabel)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                row.amountLabel,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = when {
                    row.isTransfer -> MaterialTheme.colorScheme.onSurfaceVariant
                    row.isExpense -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.primary
                },
            )
        }
    }
}

@Composable
private fun LoadingBox() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(64.dp))
        Text("这个月还没有记录", style = MaterialTheme.typography.titleMedium)
        Text(
            "点右下角 + 记第一笔",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Category colours are stored as ARGB longs; fall back to the theme when absent. */
@Composable
private fun categoryTint(argb: Long?): Color = when (argb) {
    null -> MaterialTheme.colorScheme.secondaryContainer
    else -> Color(argb.toInt()).copy(alpha = 0.18f)
}

private val ContentOnTint = Color(0xFF2E4A42)

@Preview(showBackground = true)
@Composable
private fun TransactionsContentPreview() {
    TransactionsContent(
        state = TransactionsUiState(
            monthLabel = "2025年11月",
            expenseTotal = "¥1,234.00",
            incomeTotal = "¥18,000.00",
            isLoading = false,
            groups = listOf(
                DayGroupUi(
                    dateKey = "2025-11-06",
                    dayLabel = "今天 · 11月6日 周四",
                    expenseLabel = "支出 ¥38.00",
                    items = listOf(
                        row("1", "餐饮", "午饭 · 兰州拉面", "支付宝", "-¥32.00", true, "restaurant", 0xFFE8734A),
                        row("2", "转账", "转到银行卡", "支付宝", "¥500.00", false, null, null, transfer = true),
                    ),
                ),
            ),
        ),
        onAddClick = {},
        onEditClick = {},
        onDeleteClick = {},
        onPreviousMonth = {},
        onNextMonth = {},
        onUndo = {},
        onSnackbarShown = {},
    )
}

/** Named-argument builder for preview rows: positional args broke once the model gained a field. */
private fun row(
    id: String,
    categoryName: String,
    note: String,
    accountName: String,
    amountLabel: String,
    isExpense: Boolean,
    iconKey: String?,
    colorArgb: Long?,
    transfer: Boolean = false,
) = TransactionRowUi(
    id = id,
    categoryName = categoryName,
    note = note,
    accountName = accountName,
    counterAccountName = if (transfer) "银行卡" else null,
    amountLabel = amountLabel,
    isExpense = isExpense,
    isTransfer = transfer,
    iconKey = iconKey,
    colorArgb = colorArgb,
)
