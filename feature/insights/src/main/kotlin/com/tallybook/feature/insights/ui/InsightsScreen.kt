package com.tallybook.feature.insights.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.tallybook.core.model.stats.SeriesMode
import com.tallybook.core.model.stats.ReportPeriod
import com.tallybook.core.model.stats.TrendSeries
import com.tallybook.core.ui.components.TallyCard
import com.tallybook.core.ui.components.tallyCardColors
import com.tallybook.core.ui.components.tallyCardDecoration
import com.tallybook.core.ui.components.tallyCardElevation
import androidx.compose.material3.Card

/**
 * Statistics screen.
 *
 * Two deliberate UX decisions:
 * - **Granularity is segmented (周/月/年)**, because it changes what the chart *means* and must be
 *   obvious at a glance. Series mode (支出/收入/两者) is a filter chip row, because it only hides
 *   data rather than reinterpreting it.
 * - **Chart type follows the series mode**: bar chart for a single series (readable magnitudes),
 *   line chart for both (trend comparison without 11 overlapping bars per bucket).
 *
 * The interactive chart state lives in the ViewModel so rotation and process death preserve it.
 */
@Composable
fun InsightsScreen(
    onCategoryClick: (categoryId: String?, fromInclusive: Long, toExclusive: Long, name: String) -> Unit,
    viewModel: InsightsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    InsightsContent(
        state = state,
        onAction = viewModel::onAction,
        onCategoryClick = onCategoryClick,
    )
}

@Composable
fun InsightsContent(
    state: InsightsUiState,
    onAction: (InsightsAction) -> Unit,
    onCategoryClick: (categoryId: String?, fromInclusive: Long, toExclusive: Long, name: String) -> Unit =
        { _, _, _, _ -> },
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            PeriodSelector(
                selected = state.period,
                onSelect = { onAction(InsightsAction.SetPeriod(it)) },
            )
        }

        item {
            PeriodNavigator(
                label = state.rangeLabel,
                bucketHint = state.bucketHint,
                canGoNext = state.canGoNext,
                onPrevious = { onAction(InsightsAction.PreviousPeriod) },
                onNext = { onAction(InsightsAction.NextPeriod) },
            )
        }

        item {
            TotalsRow(state)
        }

        item {
            SeriesModeSelector(
                selected = state.mode,
                onSelect = { onAction(InsightsAction.SetMode(it)) },
            )
        }

        item {
            TrendChartCard(state = state)
        }

        item {
            Text(
                "分类占比（点一行看明细）",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        if (state.breakdown.isEmpty() && !state.isLoading) {
            item {
                Text(
                    "这个时间段还没有支出记录",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        } else {
            items(state.breakdown, key = { it.categoryId ?: "uncategorized" }) { slice ->
                CategoryBreakdownRow(
                    name = slice.categoryName,
                    amountLabel = slice.amount.format(),
                    share = slice.share,
                    count = slice.transactionCount,
                    onClick = {
                        onCategoryClick(
                            slice.categoryId,
                            state.rangeStartInclusive,
                            state.rangeEndExclusive,
                            slice.categoryName,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun PeriodSelector(
    selected: ReportPeriod,
    onSelect: (ReportPeriod) -> Unit,
) {
    val options = listOf(
        ReportPeriod.WEEK to "周",
        ReportPeriod.MONTH to "月",
        ReportPeriod.YEAR to "年",
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = selected == value,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) {
                Text(label)
            }
        }
    }
}

/**
 * Period navigation plus the bucket hint.
 *
 * The hint ("按日 / 按周 / 按月") is not decoration: without it, switching 周→月 changes both the range
 * and what a bar means, and the chart silently changes meaning.
 */
@Composable
private fun PeriodNavigator(
    label: String,
    bucketHint: String,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "上一期")
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            if (bucketHint.isNotBlank()) {
                Text(
                    bucketHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onNext, enabled = canGoNext) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "下一期")
        }
    }
}

@Composable
private fun SeriesModeSelector(
    selected: SeriesMode,
    onSelect: (SeriesMode) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            SeriesMode.EXPENSE to "支出",
            SeriesMode.INCOME to "收入",
            SeriesMode.BOTH to "两者",
        ).forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun TotalsRow(state: InsightsUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // No date here: the navigator above already says which period this is, and repeating it
        // made the header read "2026年11月" twice.
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column {
                Text("支出", style = MaterialTheme.typography.labelSmall)
                Text(
                    state.expenseTotalLabel,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Column {
                Text("收入", style = MaterialTheme.typography.labelSmall)
                Text(
                    state.incomeTotalLabel,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Column {
                Text("结余", style = MaterialTheme.typography.labelSmall)
                Text(state.netTotalLabel, style = MaterialTheme.typography.titleLarge)
            }
        }

        state.comparisonLabel?.let { label ->
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                // Rising spending is the thing to notice, so it uses the error role; a drop reads as
                // good news. "No baseline" stays neutral because it says nothing about direction.
                color = when (state.comparisonDirection) {
                    com.tallybook.core.model.stats.ComparisonDirection.UP -> MaterialTheme.colorScheme.error
                    com.tallybook.core.model.stats.ComparisonDirection.DOWN -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun TrendChartCard(state: InsightsUiState) {
    val trend = state.trend

    TallyCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            if (trend == null || trend.isEmpty) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("暂无数据", style = MaterialTheme.typography.bodyMedium)
                }
                return@Column
            }

            if (state.isEmpty) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("这段时间没有收支记录", style = MaterialTheme.typography.bodyMedium)
                }
                return@Column
            }

            TrendChart(
                trend = trend,
                mode = state.mode,
                modifier = Modifier.fillMaxWidth().height(200.dp),
            )
            TrendChartLabels(trend)
        }
    }
}

/**
 * The chart itself.
 *
 * Bar layer for a single series (readable magnitudes), line layer when both are shown (a month of
 * doubled bars is unreadable). Axis labels are drawn by [TrendChartLabels] instead of by Vico, because
 * the library clips the extreme label at the plot edge.
 */
@Composable
private fun TrendChart(
    trend: TrendSeries,
    mode: SeriesMode,
    modifier: Modifier = Modifier,
) {
    val modelProducer = remember { CartesianChartModelProducer() }
    var lastSignature by remember { mutableStateOf<String?>(null) }

    val signature = remember(trend, mode) {
        buildString {
            append(trend.granularity.name)
            append(mode.name)
            trend.points.forEach { append(it.expense.minorUnits).append(',').append(it.income.minorUnits) }
        }
    }

    androidx.compose.runtime.LaunchedEffect(signature) {
        if (signature == lastSignature) return@LaunchedEffect
        lastSignature = signature

        if (mode == SeriesMode.BOTH) {
            modelProducer.runTransaction {
                lineSeries {
                    series(trend.points.map { it.expense.minorUnits.toDouble() / 100.0 })
                    series(trend.points.map { it.income.minorUnits.toDouble() / 100.0 })
                }
            }
        } else {
            val values = trend.points.map { it.valueFor(mode).minorUnits.toDouble() / 100.0 }
            modelProducer.runTransaction {
                columnSeries { series(values) }
            }
        }
    }

    val chart = if (mode == SeriesMode.BOTH) {
        rememberCartesianChart(
            rememberLineCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = null,
        )
    } else {
        rememberCartesianChart(
            rememberColumnCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = null,
        )
    }

    CartesianChartHost(
        chart = chart,
        modelProducer = modelProducer,
        modifier = modifier,
    )
}

@Composable
private fun CategoryBreakdownRow(
    name: String,
    amountLabel: String,
    share: Float,
    count: Int,
    onClick: () -> Unit = {},
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .tallyCardDecoration(),
        shape = MaterialTheme.shapes.medium,
        colors = tallyCardColors(),
        elevation = tallyCardElevation(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(name, style = MaterialTheme.typography.bodyLarge)
                Text(amountLabel, style = MaterialTheme.typography.bodyLarge)
            }
            androidx.compose.material3.LinearProgressIndicator(
                progress = { share.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            )
            Text(
                "${(share * 100).toInt()}% · $count 笔",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun InsightsContentPreview() {
    InsightsContent(
        state = InsightsUiState(
            period = ReportPeriod.MONTH,
            mode = SeriesMode.BOTH,
            rangeLabel = "2024年12月 – 2025年11月",
            expenseTotalLabel = "¥12,345.00",
            incomeTotalLabel = "¥28,000.00",
            netTotalLabel = "¥15,655.00",
            isLoading = false,
            isEmpty = false,
        ),
        onAction = {},
    )
}

/** Show roughly six x-axis labels: enough to read the trend, few enough to never collide. */
private const val LABEL_SPACING = 2

/**
 * X-axis labels rendered outside the chart.
 *
 * Each label gets an equal share of the width, which lines it up with its bucket regardless of the
 * chart library's internal padding — and, unlike an axis drawn at the plot edge, the last label can
 * never be clipped. Labels are thinned to every [LABEL_SPACING]-th bucket so they cannot collide.
 */
@Composable
private fun TrendChartLabels(trend: TrendSeries) {
    Row(modifier = Modifier.fillMaxWidth()) {
        val lastIndex = trend.points.lastIndex
        trend.points.forEachIndexed { index, point ->
            // Always show the newest bucket: it is the one users look at first, and it is dropped by
            // the modulo thinning in odd-length series.
            val visible = index % LABEL_SPACING == 0 || index == lastIndex
            Text(
                text = if (visible) point.label else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
