package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.CategoryCatalog
import com.example.ui.state.BudgetProgress
import com.example.ui.state.KasKuDashboardData
import com.example.ui.theme.AntiqueGold
import com.example.ui.theme.ExpenseCoral
import com.example.ui.theme.IncomeGreen
import com.example.util.formatGroupedDigits
import com.example.util.formatMonthLabel
import com.example.util.formatRupiah

@Composable
fun BudgetsScreen(
    data: KasKuDashboardData,
    isMutating: Boolean,
    onShiftMonth: (Int) -> Unit,
    onSaveBudget: (categoryId: String, limitAmount: Long, existingBudgetIds: Set<String>, onDone: () -> Unit) -> Unit,
    onDeleteBudget: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var editingBudget by remember { mutableStateOf<BudgetProgress?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    val existingIds = remember(data.budgetProgressList) {
        data.budgetProgressList.map { it.budget.id }.toSet()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Anggaran Bulanan",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                OutlinedButton(
                    onClick = { showAddDialog = true },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("add_budget_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Atur")
                }
            }
        }

        // Month Selector Card
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onShiftMonth(-1) },
                        modifier = Modifier.testTag("prev_month_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                            contentDescription = "Bulan sebelumnya"
                        )
                    }
                    Text(
                        text = formatMonthLabel(data.selectedBudgetMonth),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = { onShiftMonth(1) },
                        modifier = Modifier.testTag("next_month_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = "Bulan berikutnya"
                        )
                    }
                }
            }
        }

        if (data.budgetProgressList.isNotEmpty()) {
            item {
                val totalRatio = if (data.totalBudgetLimit > 0L) {
                    (data.totalBudgetSpent.toFloat() / data.totalBudgetLimit.toFloat()).coerceIn(0f, 1f)
                } else 0f
                val animatedTotal by animateFloatAsState(
                    targetValue = totalRatio,
                    animationSpec = tween(450),
                    label = "total_budget_ratio"
                )
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Total Terpakai Bulan Ini",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Text(
                                text = formatRupiah(data.totalBudgetSpent),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "dari ${formatRupiah(data.totalBudgetLimit)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        LinearProgressIndicator(
                            progress = { animatedTotal },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                            strokeCap = StrokeCap.Round
                        )
                    }
                }
            }

            items(data.budgetProgressList, key = { it.budget.id }) { item ->
                BudgetProgressCard(
                    item = item,
                    onClick = { editingBudget = item },
                    onDelete = { onDeleteBudget(item.budget.id) }
                )
            }
        } else {
            item {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Belum ada batas anggaran",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Tetapkan batas pengeluaran per kategori agar keuangan bulananmu tetap terkendali.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = { showAddDialog = true },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Buat Anggaran Pertama")
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }

    if (showAddDialog) {
        BudgetEditorDialog(
            initialCategoryId = CategoryCatalog.expenseCategories.first().id,
            initialLimit = 500_000L,
            isEditing = false,
            isSaving = isMutating,
            onDismiss = { showAddDialog = false },
            onSave = { categoryId, limit ->
                onSaveBudget(categoryId, limit, existingIds) {
                    showAddDialog = false
                }
            }
        )
    }

    editingBudget?.let { current ->
        BudgetEditorDialog(
            initialCategoryId = current.budget.categoryId,
            initialLimit = current.budget.limitAmount,
            isEditing = true,
            isSaving = isMutating,
            onDismiss = { editingBudget = null },
            onSave = { categoryId, limit ->
                onSaveBudget(categoryId, limit, existingIds) {
                    editingBudget = null
                }
            }
        )
    }
}

@Composable
private fun BudgetProgressCard(
    item: BudgetProgress,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val barColor = when {
        item.ratio >= 1f -> ExpenseCoral
        item.ratio >= 0.75f -> AntiqueGold
        else -> IncomeGreen
    }
    val statusText = when {
        item.ratio >= 1f -> "Melebihi batas ${formatRupiah(-item.remainingAmount)}"
        else -> "Sisa ${formatRupiah(item.remainingAmount)}"
    }
    val animatedProgress by animateFloatAsState(
        targetValue = item.ratio.coerceIn(0f, 1f),
        animationSpec = tween(450),
        label = "budget_bar"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(item.category.accentColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = item.category.icon,
                        contentDescription = item.category.name,
                        tint = item.category.accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.category.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${formatRupiah(item.spentAmount)} / ${formatRupiah(item.budget.limitAmount)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${(item.ratio * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = barColor
                )
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Hapus anggaran",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(9.dp)
                    .clip(RoundedCornerShape(8.dp)),
                color = barColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round
            )

            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = if (item.ratio >= 1f) ExpenseCoral else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BudgetEditorDialog(
    initialCategoryId: String,
    initialLimit: Long,
    isEditing: Boolean,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (categoryId: String, limitAmount: Long) -> Unit
) {
    var selectedCategoryId by remember(initialCategoryId) { mutableStateOf(initialCategoryId) }
    var limitDigits by remember(initialLimit) { mutableStateOf(initialLimit.toString()) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Ubah Batas Anggaran" else "Atur Anggaran Kategori") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!isEditing) {
                    Text("Kategori Pengeluaran", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CategoryCatalog.expenseCategories.forEach { cat ->
                            FilterChip(
                                selected = selectedCategoryId == cat.id,
                                onClick = { selectedCategoryId = cat.id },
                                label = { Text(cat.name) }
                            )
                        }
                    }
                } else {
                    Text(
                        text = CategoryCatalog.find(selectedCategoryId).name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                OutlinedTextField(
                    value = limitDigits,
                    onValueChange = { raw ->
                        limitDigits = raw.filter { it.isDigit() }.take(12)
                        errorText = null
                    },
                    label = { Text("Batas Bulanan (Rp)") },
                    prefix = { Text("Rp ") },
                    supportingText = {
                        val grouped = formatGroupedDigits(limitDigits)
                        if (grouped.isNotEmpty()) Text("Rp $grouped")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("budget_limit_input")
                )

                if (errorText != null) {
                    Text(
                        text = errorText!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = limitDigits.toLongOrNull() ?: 0L
                    if (amount <= 0L) {
                        errorText = "Masukkan batas anggaran lebih dari Rp 0."
                        return@Button
                    }
                    onSave(selectedCategoryId, amount)
                },
                enabled = !isSaving,
                modifier = Modifier.testTag("save_budget_button")
            ) {
                Text("Simpan")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}
