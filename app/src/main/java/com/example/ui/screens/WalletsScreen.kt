package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.WalletColorKey
import com.example.data.model.WalletKind
import com.example.ui.state.KasKuDashboardData
import com.example.ui.state.WalletSummary
import com.example.ui.theme.ExpenseCoral
import com.example.ui.theme.IncomeGreen
import com.example.util.formatGroupedDigits
import com.example.util.formatRupiah

@Composable
fun WalletsScreen(
    data: KasKuDashboardData,
    isMutating: Boolean,
    onSaveWallet: (
        existingId: String?,
        name: String,
        kind: WalletKind,
        targetBalance: Long,
        currentNetTransactions: Long,
        colorKey: WalletColorKey,
        onDone: () -> Unit
    ) -> Unit,
    onDeleteWallet: (String, () -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    var editingWallet by remember { mutableStateOf<WalletSummary?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

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
                Column {
                    Text(
                        text = "Dompet & Rekening",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${data.wallets.size} akun aktif",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = { showCreateDialog = true },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("add_wallet_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Tambah")
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Total Kekayaan Tersimpan",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = formatRupiah(data.totalNetBalance),
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        items(data.wallets, key = { it.wallet.id }) { summary ->
            val kind = WalletKind.fromRaw(summary.wallet.kind)
            val color = WalletColorKey.fromRaw(summary.wallet.colorKey).primaryColor
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .clickable { editingWallet = summary }
                    .testTag("wallet_card_${summary.wallet.id}"),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(color.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = kind.icon,
                                contentDescription = kind.labelId,
                                tint = color,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = summary.wallet.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = kind.labelId,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { editingWallet = summary }) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = "Ubah dompet",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = formatRupiah(summary.currentBalance),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Total Masuk",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatRupiah(summary.totalIncome),
                                style = MaterialTheme.typography.labelLarge,
                                color = IncomeGreen
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Total Keluar",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatRupiah(summary.totalExpense),
                                style = MaterialTheme.typography.labelLarge,
                                color = ExpenseCoral
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(80.dp)) }
    }

    if (showCreateDialog) {
        WalletEditorDialog(
            existing = null,
            canDelete = false,
            isSaving = isMutating,
            onDismiss = { showCreateDialog = false },
            onSave = { name, kind, balance, colorKey ->
                onSaveWallet(null, name, kind, balance, 0L, colorKey) {
                    showCreateDialog = false
                }
            },
            onDelete = {}
        )
    }

    editingWallet?.let { summary ->
        val netTransactions = summary.totalIncome - summary.totalExpense
        WalletEditorDialog(
            existing = summary,
            canDelete = data.wallets.size > 1,
            isSaving = isMutating,
            onDismiss = { editingWallet = null },
            onSave = { name, kind, targetBalance, colorKey ->
                onSaveWallet(
                    summary.wallet.id,
                    name,
                    kind,
                    targetBalance,
                    netTransactions,
                    colorKey
                ) {
                    editingWallet = null
                }
            },
            onDelete = {
                onDeleteWallet(summary.wallet.id) {
                    editingWallet = null
                }
            }
        )
    }
}

@Composable
private fun WalletEditorDialog(
    existing: WalletSummary?,
    canDelete: Boolean,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, WalletKind, Long, WalletColorKey) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember(existing) { mutableStateOf(existing?.wallet?.name ?: "") }
    var kind by remember(existing) {
        mutableStateOf(WalletKind.fromRaw(existing?.wallet?.kind))
    }
    var balanceDigits by remember(existing) {
        mutableStateOf(existing?.currentBalance?.takeIf { it >= 0 }?.toString() ?: "0")
    }
    var colorKey by remember(existing) {
        mutableStateOf(WalletColorKey.fromRaw(existing?.wallet?.colorKey))
    }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (existing == null) "Tambah Dompet" else "Ubah Dompet")
                if (existing != null && canDelete) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Hapus dompet",
                            tint = ExpenseCoral
                        )
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it.take(40)
                        errorText = null
                    },
                    label = { Text("Nama Dompet") },
                    placeholder = { Text("Misal: BCA, GoPay, Dompet") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("wallet_name_input")
                )

                OutlinedTextField(
                    value = balanceDigits,
                    onValueChange = { raw ->
                        balanceDigits = raw.filter { it.isDigit() }.take(12)
                        errorText = null
                    },
                    label = { Text(if (existing == null) "Saldo Awal (Rp)" else "Saldo Saat Ini (Rp)") },
                    prefix = { Text("Rp ") },
                    supportingText = {
                        val formatted = formatGroupedDigits(balanceDigits).ifEmpty { "0" }
                        Text("Rp $formatted")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("wallet_balance_input")
                )

                Text("Jenis Akun", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    WalletKind.entries.forEach { item ->
                        FilterChip(
                            selected = kind == item,
                            onClick = { kind = item },
                            label = { Text(item.labelId) }
                        )
                    }
                }

                Text("Warna Kartu", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    WalletColorKey.entries.forEach { key ->
                        val selected = colorKey == key
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(key.primaryColor)
                                .then(
                                    if (selected) {
                                        Modifier.border(
                                            width = 3.dp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            shape = CircleShape
                                        )
                                    } else Modifier
                                )
                                .clickable { colorKey = key }
                        )
                    }
                }

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
                    val trimmed = name.trim()
                    if (trimmed.isEmpty()) {
                        errorText = "Nama dompet tidak boleh kosong."
                        return@Button
                    }
                    val balance = balanceDigits.toLongOrNull() ?: 0L
                    onSave(trimmed, kind, balance, colorKey)
                },
                enabled = !isSaving,
                modifier = Modifier.testTag("save_wallet_button")
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
