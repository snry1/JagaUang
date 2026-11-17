package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.FinanceTransaction
import com.example.data.model.TransactionType
import com.example.ui.components.TransactionRowCard
import com.example.ui.state.KasKuDashboardData
import com.example.ui.state.PeriodFilter
import com.example.ui.state.TypeFilter
import com.example.ui.theme.ExpenseCoral
import com.example.ui.theme.IncomeGreen
import com.example.util.dayStartMillis
import com.example.util.formatDateHeader
import com.example.util.formatRupiah

@Composable
fun TransactionsScreen(
    data: KasKuDashboardData,
    onSearchChange: (String) -> Unit,
    onSelectTypeFilter: (TypeFilter) -> Unit,
    onSelectWalletFilter: (String?) -> Unit,
    onSelectPeriod: (PeriodFilter) -> Unit,
    onEditTransaction: (FinanceTransaction) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val walletNamesById = remember(data.wallets) {
        data.wallets.associate { it.wallet.id to it.wallet.name }
    }

    val groupedByDay = remember(data.filteredTransactions) {
        data.filteredTransactions
            .groupBy { dayStartMillis(it.dateEpochMillis) }
            .toSortedMap(compareByDescending { it })
    }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Riwayat Transaksi",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            OutlinedTextField(
                value = data.searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Cari catatan atau kategori...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Cari transaksi"
                    )
                },
                trailingIcon = {
                    if (data.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(
                                imageVector = Icons.Rounded.Clear,
                                contentDescription = "Bersihkan pencarian"
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_transactions_input")
            )

            // Type & Period Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TypeFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = data.selectedTypeFilter == filter,
                        onClick = { onSelectTypeFilter(filter) },
                        label = { Text(filter.labelId) },
                        modifier = Modifier.testTag("type_filter_${filter.name.lowercase()}")
                    )
                }
                PeriodFilter.entries.forEach { period ->
                    FilterChip(
                        selected = data.selectedPeriod == period,
                        onClick = { onSelectPeriod(period) },
                        label = { Text(period.labelId) }
                    )
                }
            }

            // Wallet Filter Chips
            if (data.wallets.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = data.selectedWalletFilterId == null,
                        onClick = { onSelectWalletFilter(null) },
                        label = { Text("Semua Dompet") }
                    )
                    data.wallets.forEach { summary ->
                        FilterChip(
                            selected = data.selectedWalletFilterId == summary.wallet.id,
                            onClick = { onSelectWalletFilter(summary.wallet.id) },
                            label = { Text(summary.wallet.name) }
                        )
                    }
                }
            }
        }

        if (groupedByDay.isEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
            ) {
                item {
                    EmptyLedgerCard(onAddClick = onAddClick)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                groupedByDay.forEach { (dayMillis, dayTransactions) ->
                    val dailyNet = dayTransactions.sumOf { tx ->
                        if (TransactionType.fromRaw(tx.type) == TransactionType.INCOME) tx.amount else -tx.amount
                    }
                    item(key = "header_$dayMillis") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp, bottom = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatDateHeader(dayMillis),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatRupiah(dailyNet, showPlusSign = true),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (dailyNet >= 0) IncomeGreen else ExpenseCoral
                            )
                        }
                    }

                    items(
                        items = dayTransactions,
                        key = { it.id }
                    ) { tx ->
                        TransactionRowCard(
                            transaction = tx,
                            walletName = walletNamesById[tx.walletId] ?: "Dompet",
                            onClick = { onEditTransaction(tx) }
                        )
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}
