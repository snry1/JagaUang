package com.example.ui.state

import androidx.compose.ui.graphics.Color
import com.example.data.model.CategoryBudget
import com.example.data.model.FinanceCategory
import com.example.data.model.FinanceTransaction
import com.example.data.model.TransactionType
import com.example.data.model.Wallet
import com.example.util.currentMonthKey

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class PeriodFilter(val labelId: String) {
    TODAY("Hari Ini"),
    THIS_WEEK("Minggu Ini"),
    THIS_MONTH("Bulan Ini"),
    ALL("Semua")
}

enum class TypeFilter(val labelId: String) {
    ALL("Semua"),
    EXPENSE("Pengeluaran"),
    INCOME("Pemasukan")
}

data class WalletSummary(
    val wallet: Wallet,
    val currentBalance: Long,
    val totalIncome: Long,
    val totalExpense: Long
)

data class CategoryBreakdown(
    val category: FinanceCategory,
    val amount: Long,
    val percentage: Float
)

data class DailyCashflowPoint(
    val dayLabel: String,
    val income: Long,
    val expense: Long
)

data class BudgetProgress(
    val budget: CategoryBudget,
    val category: FinanceCategory,
    val spentAmount: Long,
    val remainingAmount: Long,
    val ratio: Float
)

data class KasKuDashboardData(
    val wallets: List<WalletSummary>,
    val allTransactions: List<FinanceTransaction>,
    val periodTransactions: List<FinanceTransaction>,
    val filteredTransactions: List<FinanceTransaction>,
    val totalNetBalance: Long,
    val periodIncome: Long,
    val periodExpense: Long,
    val periodNetCashflow: Long,
    val expenseBreakdowns: List<CategoryBreakdown>,
    val last7DaysCashflow: List<DailyCashflowPoint>,
    val budgetProgressList: List<BudgetProgress>,
    val totalBudgetLimit: Long,
    val totalBudgetSpent: Long,
    val selectedPeriod: PeriodFilter,
    val selectedBudgetMonth: String = currentMonthKey(),
    val searchQuery: String = "",
    val selectedTypeFilter: TypeFilter = TypeFilter.ALL,
    val selectedWalletFilterId: String? = null
)

data class FeedbackMessage(
    val text: String,
    val isError: Boolean = false
)
