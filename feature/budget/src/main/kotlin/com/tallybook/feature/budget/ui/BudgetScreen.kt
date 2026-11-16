package com.tallybook.feature.budget.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tallybook.core.model.money.CurrencyCode
import com.tallybook.core.model.money.Money
import com.tallybook.core.model.stats.BudgetProgress
import com.tallybook.core.model.transaction.BudgetPeriod
import com.tallybook.core.ui.components.tallyCardColors
import com.tallybook.core.ui.components.tallyCardDecoration
import com.tallybook.core.ui.components.tallyCardElevation

/**
 * Budget screen.
 *
 * Layout rationale: each budget is a card whose *progress bar is the primary element*, with the
 * numbers secondary. Users glance at this screen to answer one question — "am I okay?" — and a bar
 * with a color state answers it faster than ¥1,234.56 / ¥3,000.00.
 *
 * The pace hint ("花得比时间快") is what makes this more than a percentage: 60% spent on day 10 of 30
 * is comfortable, on day 28 it is a warning. Raw percentages cannot express that.
 */
@Composable
fun BudgetScreen(
    onAddClick: () -> Unit,
    onBudgetClick: (String) -> Unit,
    viewModel: BudgetViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BudgetContent(
        state = state,
        onAddClick = onAddClick,
        onBudgetClick = onBudgetClick,
    )
}

@Composable
fun BudgetContent(
    state: BudgetUiState,
    onAddClick: () -> Unit = {},
    onBudgetClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(Icons.Filled.Add, contentDescription = "新建预算")
            }
        },
    ) { innerPadding ->
        if (state.isEmpty && !state.isLoading) {
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("还没有预算", style = MaterialTheme.typography.titleMedium)
                Text(
                    "给每周或每月设一个支出上限，随时查看执行进度。",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    "点右下角 + 新建",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // Bottom padding clears the FAB so the last card is never half-covered.
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.monthProgress.isNotEmpty()) {
                item {
                    BudgetGroupHeader(
                        title = "每月预算",
                        limit = state.totalLimit(state.monthProgress),
                        spent = state.totalSpent(state.monthProgress),
                    )
                }
                items(state.monthProgress, key = { it.budgetId }) { progress ->
                    BudgetCard(progress = progress, onClick = { onBudgetClick(progress.budgetId) })
                }
            }

            if (state.weekProgress.isNotEmpty()) {
                item {
                    BudgetGroupHeader(
                        title = "每周预算",
                        limit = state.totalLimit(state.weekProgress),
                        spent = state.totalSpent(state.weekProgress),
                    )
                }
                items(state.weekProgress, key = { it.budgetId }) { progress ->
                    BudgetCard(progress = progress, onClick = { onBudgetClick(progress.budgetId) })
                }
            }
        }
    }
}

@Composable
private fun BudgetGroupHeader(title: String, limit: Money, spent: Money) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "${spent.format()} / ${limit.format()}",
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun BudgetCard(progress: BudgetProgress, onClick: () -> Unit = {}) {
    // Color communicates state, and it is semantic (theme roles), never a raw hex literal:
    // a hardcoded red fails contrast checks in dark mode and in high-contrast themes.
    val barColor = when {
        progress.isOverBudget -> MaterialTheme.colorScheme.error
        progress.progress > 0.85f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .tallyCardDecoration(),
        shape = MaterialTheme.shapes.large,
        colors = tallyCardColors(),
        elevation = tallyCardElevation(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(progress.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${(progress.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = barColor,
                )
            }

            if (progress.categoryId != null && progress.includeSubcategories) {
                Text(
                    "含子分类",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            LinearProgressIndicator(
                progress = { progress.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                color = barColor,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${progress.spent.format()} / ${progress.limit.format()}",
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    BudgetFormat.paceLabel(progress),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (progress.isOverBudget) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            Text(
                BudgetFormat.paceHint(progress),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp),
            )

            BudgetFormat.projectedLabel(progress)?.let { hint ->
                Text(
                    hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BudgetContentPreview() {
    val cny = CurrencyCode.CNY
    BudgetContent(
        state = BudgetUiState(
            monthProgress = listOf(
                BudgetProgress(
                    budgetId = "1",
                    name = "餐饮",
                    period = BudgetPeriod.MONTHLY,
                    limit = Money(300000, cny),
                    spent = Money(185000, cny),
                    periodStartMillis = 0,
                    periodEndExclusiveMillis = 1,
                    daysElapsed = 10,
                    daysTotal = 30,
                ),
                BudgetProgress(
                    budgetId = "2",
                    name = "购物",
                    period = BudgetPeriod.MONTHLY,
                    limit = Money(100000, cny),
                    spent = Money(132000, cny),
                    periodStartMillis = 0,
                    periodEndExclusiveMillis = 1,
                    daysElapsed = 18,
                    daysTotal = 30,
                ),
            ),
            nowMillis = 0,
            isLoading = false,
        ),
    )
}
