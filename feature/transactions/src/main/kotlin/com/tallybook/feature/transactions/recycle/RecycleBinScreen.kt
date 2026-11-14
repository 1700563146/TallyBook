package com.tallybook.feature.transactions.recycle

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tallybook.feature.transactions.categoryIcon

/**
 * Recycle bin.
 *
 * Deliberately plain: the only two actions are "恢复" and "彻底删除". A recycle bin that offers
 * editing is really just a second ledger, and it invites the user to fix a record they meant to
 * throw away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecycleBinScreen(
    onBack: () -> Unit,
    viewModel: RecycleBinViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    RecycleBinContent(
        state = state,
        onBack = onBack,
        onRestore = viewModel::restore,
        onPurge = viewModel::purge,
        onPurgeAll = viewModel::purgeAll,
        onMessageShown = viewModel::onMessageShown,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecycleBinContent(
    state: RecycleBinUiState,
    onBack: () -> Unit,
    onRestore: (String) -> Unit,
    onPurge: (String) -> Unit,
    onPurgeAll: () -> Unit,
    onMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmPurgeAll by remember { mutableStateOf(false) }
    var purgeTarget by remember { mutableStateOf<DeletedRowUi?>(null) }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onMessageShown()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (state.count > 0) "回收站 · ${state.count}" else "回收站") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (state.count > 0) {
                        TextButton(onClick = { confirmPurgeAll = true }) {
                            Text("清空", color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
            )
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
                Text("回收站是空的", style = MaterialTheme.typography.titleMedium)
                Text(
                    "删除的记录会先放在这里，随时可以恢复。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            return@Scaffold
        }

        LazyColumn(
            // innerPadding must be applied to the LIST, not only to the empty state: without it the
            // first card renders underneath the TopAppBar and only its action buttons peek out.
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.rows, key = { it.id }) { row ->
                DeletedRow(
                    row = row,
                    onRestore = { onRestore(row.id) },
                    onPurge = { purgeTarget = row },
                )
            }
        }
    }

    if (confirmPurgeAll) {
        AlertDialog(
            onDismissRequest = { confirmPurgeAll = false },
            title = { Text("清空回收站？") },
            text = { Text("这 ${state.count} 条记录会被永久删除，无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmPurgeAll = false
                    onPurgeAll()
                }) { Text("永久删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmPurgeAll = false }) { Text("取消") }
            },
        )
    }

    purgeTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { purgeTarget = null },
            title = { Text("永久删除这条记录？") },
            text = { Text("${target.categoryName} ${target.amountLabel}，删除后无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    val id = target.id
                    purgeTarget = null
                    onPurge(id)
                }) { Text("永久删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { purgeTarget = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun DeletedRow(
    row: DeletedRowUi,
    onRestore: () -> Unit,
    onPurge: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            color = when (val argb = row.colorArgb) {
                                null -> MaterialTheme.colorScheme.secondaryContainer
                                else -> Color(argb.toInt()).copy(alpha = 0.18f)
                            },
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = categoryIcon(row.iconKey),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color(0xFF2E4A42),
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(row.categoryName, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        listOf(row.note, row.accountName)
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        row.deletedAtLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(
                    row.amountLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onRestore) {
                    Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("恢复", modifier = Modifier.padding(start = 4.dp))
                }
                TextButton(onClick = onPurge) {
                    Icon(
                        Icons.Filled.DeleteForever,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        "彻底删除",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RecycleBinPreview() {
    RecycleBinContent(
        state = RecycleBinUiState(
            isLoading = false,
            rows = listOf(
                DeletedRowUi(
                    id = "1",
                    categoryName = "餐饮",
                    note = "午饭",
                    accountName = "现金",
                    amountLabel = "-¥32.00",
                    deletedAtLabel = "11月6日 14:20 删除",
                    isExpense = true,
                    iconKey = "restaurant",
                    colorArgb = 0xFFE8734A,
                ),
            ),
        ),
        onBack = {},
        onRestore = {},
        onPurge = {},
        onPurgeAll = {},
        onMessageShown = {},
    )
}
