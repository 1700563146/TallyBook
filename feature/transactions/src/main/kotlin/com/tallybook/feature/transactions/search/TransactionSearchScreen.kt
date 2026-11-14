package com.tallybook.feature.transactions.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.tallybook.core.data.repository.SearchEntryType
import com.tallybook.feature.transactions.categoryIcon
import com.tallybook.core.ui.components.tallyCardColors
import com.tallybook.core.ui.components.tallyCardDecoration
import com.tallybook.core.ui.components.tallyCardElevation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionSearchScreen(
    onBack: () -> Unit,
    onEditClick: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val entryType by viewModel.entryType.collectAsStateWithLifecycle()
    val accountId by viewModel.accountId.collectAsStateWithLifecycle()
    val categoryId by viewModel.categoryId.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val results = viewModel.results.collectAsLazyPagingItems()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text("搜索与筛选") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                label = { Text("搜索备注、分类或账户") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = "清空搜索")
                        }
                    }
                },
                singleLine = true,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SearchEntryType.entries.forEach { type ->
                    FilterChip(
                        selected = entryType == type,
                        onClick = { viewModel.setEntryType(type) },
                        label = { Text(type.label()) },
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterPicker(
                    selectedName = accounts.firstOrNull { it.id == accountId }?.name ?: "全部账户",
                    options = accounts,
                    onSelected = viewModel::setAccount,
                    modifier = Modifier.weight(1f),
                )
                FilterPicker(
                    selectedName = categories.firstOrNull { it.id == categoryId }?.name ?: "全部分类",
                    options = categories,
                    onSelected = viewModel::setCategory,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "结果按时间倒序排列",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = viewModel::clearFilters) {
                    Icon(Icons.Filled.FilterList, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("清除筛选")
                }
            }

            when {
                results.loadState.refresh is LoadState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                results.loadState.refresh is LoadState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("搜索失败，请重试")
                            Button(
                                onClick = { results.retry() },
                                modifier = Modifier.padding(top = 12.dp),
                            ) { Text("重试") }
                        }
                    }
                }

                results.itemCount == 0 -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "没有找到匹配记录",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                else -> SearchResults(
                    results = results,
                    onEditClick = onEditClick,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SearchResults(
    results: androidx.paging.compose.LazyPagingItems<SearchResultUi>,
    onEditClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            count = results.itemCount,
            key = results.itemKey { it.row.id },
        ) { index ->
            val item = results[index] ?: return@items
            val previous = if (index > 0) results[index - 1] else null
            if (previous?.dayKey != item.dayKey) {
                Text(
                    item.dayLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            SearchResultCard(item = item, onClick = { onEditClick(item.row.id) })
        }

        if (results.loadState.append is LoadState.Loading) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    item: SearchResultUi,
    onClick: () -> Unit,
) {
    val row = item.row
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
                        color = row.colorArgb?.let { Color(it.toInt()).copy(alpha = 0.18f) }
                            ?: MaterialTheme.colorScheme.secondaryContainer,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = categoryIcon(row.iconKey),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color(0xFF2E4A42),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(row.categoryName, style = MaterialTheme.typography.bodyLarge)
                val accountText = if (row.isTransfer && row.counterAccountName != null) {
                    "${row.accountName} → ${row.counterAccountName}"
                } else {
                    row.accountName
                }
                val subtitle = listOf(row.note, accountText)
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
                color = if (row.isExpense) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }
    }
}

@Composable
private fun FilterPicker(
    selectedName: String,
    options: List<SearchOption>,
    onSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(selectedName, maxLines = 1)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.name) },
                    onClick = {
                        expanded = false
                        onSelected(option.id)
                    },
                )
            }
        }
    }
}

private fun SearchEntryType.label(): String = when (this) {
    SearchEntryType.ALL -> "全部"
    SearchEntryType.EXPENSE -> "支出"
    SearchEntryType.INCOME -> "收入"
    SearchEntryType.TRANSFER -> "转账"
}
