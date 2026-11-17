package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.CategoryBudget
import com.example.data.model.CategoryCatalog
import com.example.data.model.FinanceTransaction
import com.example.data.model.TransactionType
import com.example.data.model.Wallet
import com.example.data.model.WalletColorKey
import com.example.data.model.WalletKind
import com.example.data.remote.FinanceRepository
import com.example.ui.state.BudgetProgress
import com.example.ui.state.CategoryBreakdown
import com.example.ui.state.DailyCashflowPoint
import com.example.ui.state.FeedbackMessage
import com.example.ui.state.KasKuDashboardData
import com.example.ui.state.PeriodFilter
import com.example.ui.state.TypeFilter
import com.example.ui.state.UiState
import com.example.ui.state.WalletSummary
import com.example.util.currentMonthKey
import com.example.util.dayStartMillis
import com.example.util.formatDayOfWeekShort
import com.example.util.monthStartMillis
import com.example.util.shiftMonthKey
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class KasKuViewModel(
    private val repository: FinanceRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _selectedPeriod = MutableStateFlow(PeriodFilter.THIS_MONTH)
    private val _selectedBudgetMonth = MutableStateFlow(currentMonthKey())
    private val _searchQuery = MutableStateFlow("")
    private val _selectedTypeFilter = MutableStateFlow(TypeFilter.ALL)
    private val _selectedWalletFilterId = MutableStateFlow<String?>(null)

    private val _isMutating = MutableStateFlow(false)
    val isMutating: StateFlow<Boolean> = _isMutating.asStateFlow()

    private val _feedback = MutableStateFlow<FeedbackMessage?>(null)
    val feedback: StateFlow<FeedbackMessage?> = _feedback.asStateFlow()

    private var hasCheckedStarterWallets = false

    private data class FilterInputs(
        val period: PeriodFilter,
        val budgetMonth: String,
        val search: String,
        val typeFilter: TypeFilter,
        val walletFilterId: String?
    )

    private val filtersFlow = combine(
        _selectedPeriod,
        _selectedBudgetMonth,
        _searchQuery,
        _selectedTypeFilter,
        _selectedWalletFilterId
    ) { period, budgetMonth, search, typeFilter, walletFilterId ->
        FilterInputs(period, budgetMonth, search, typeFilter, walletFilterId)
    }

    private val walletsFlow = repository.observeWallets(currentUserId)
        .onEach { wallets ->
            if (wallets.isEmpty() && !hasCheckedStarterWallets) {
                hasCheckedStarterWallets = true
                viewModelScope.launch {
                    repository.seedStarterWalletsIfEmpty(currentUserId)
                }
            } else if (wallets.isNotEmpty()) {
                hasCheckedStarterWallets = true
            }
        }

    val dashboardState: StateFlow<UiState<KasKuDashboardData>> = combine(
        walletsFlow,
        repository.observeTransactions(currentUserId),
        repository.observeBudgets(currentUserId),
        filtersFlow
    ) { wallets, transactions, budgets, filters ->
        buildDashboardData(wallets, transactions, budgets, filters)
    }
        .combine(MutableStateFlow(Unit)) { data, _ ->
            UiState.Success(data) as UiState<KasKuDashboardData>
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing finance streams", rootError)
            emit(UiState.Error(rootError.localizedMessage ?: "Gagal memuat data keuangan."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    private fun buildDashboardData(
        wallets: List<Wallet>,
        transactions: List<FinanceTransaction>,
        budgets: List<CategoryBudget>,
        filters: FilterInputs
    ): KasKuDashboardData {
        val walletSummaries = wallets.map { wallet ->
            val walletTx = transactions.filter { it.walletId == wallet.id }
            val income = walletTx
                .filter { TransactionType.fromRaw(it.type) == TransactionType.INCOME }
                .sumOf { it.amount }
            val expense = walletTx
                .filter { TransactionType.fromRaw(it.type) == TransactionType.EXPENSE }
                .sumOf { it.amount }
            WalletSummary(
                wallet = wallet,
                currentBalance = wallet.initialBalance + income - expense,
                totalIncome = income,
                totalExpense = expense
            )
        }

        val totalNetBalance = walletSummaries.sumOf { it.currentBalance }

        val periodStart = computePeriodStart(filters.period)
        val periodTransactions = if (periodStart == null) {
            transactions
        } else {
            transactions.filter { it.dateEpochMillis >= periodStart }
        }

        val periodIncome = periodTransactions
            .filter { TransactionType.fromRaw(it.type) == TransactionType.INCOME }
            .sumOf { it.amount }
        val periodExpense = periodTransactions
            .filter { TransactionType.fromRaw(it.type) == TransactionType.EXPENSE }
            .sumOf { it.amount }

        val queryLower = filters.search.trim().lowercase()
        val filteredTransactions = periodTransactions.filter { tx ->
            val txType = TransactionType.fromRaw(tx.type)
            val matchesType = when (filters.typeFilter) {
                TypeFilter.ALL -> true
                TypeFilter.EXPENSE -> txType == TransactionType.EXPENSE
                TypeFilter.INCOME -> txType == TransactionType.INCOME
            }
            val matchesWallet = filters.walletFilterId == null || tx.walletId == filters.walletFilterId
            val categoryName = CategoryCatalog.find(tx.categoryId, txType).name.lowercase()
            val matchesSearch = queryLower.isEmpty() ||
                tx.note.lowercase().contains(queryLower) ||
                categoryName.contains(queryLower)
            matchesType && matchesWallet && matchesSearch
        }

        val expenseBreakdowns = if (periodExpense <= 0L) {
            emptyList()
        } else {
            periodTransactions
                .filter { TransactionType.fromRaw(it.type) == TransactionType.EXPENSE }
                .groupBy { it.categoryId }
                .map { (catId, list) ->
                    val catSum = list.sumOf { it.amount }
                    CategoryBreakdown(
                        category = CategoryCatalog.find(catId, TransactionType.EXPENSE),
                        amount = catSum,
                        percentage = (catSum.toDouble() / periodExpense.toDouble()).toFloat()
                    )
                }
                .sortedByDescending { it.amount }
        }

        val last7DaysCashflow = buildLast7DaysCashflow(transactions)

        val monthBudgets = budgets.filter { it.monthKey == filters.budgetMonth }
        val monthExpenseByCategory = transactions
            .filter {
                TransactionType.fromRaw(it.type) == TransactionType.EXPENSE &&
                    currentMonthKey(it.dateEpochMillis) == filters.budgetMonth
            }
            .groupBy { it.categoryId }
            .mapValues { (_, list) -> list.sumOf { it.amount } }

        val budgetProgressList = monthBudgets.map { budget ->
            val spent = monthExpenseByCategory[budget.categoryId] ?: 0L
            val ratio = if (budget.limitAmount > 0L) {
                (spent.toDouble() / budget.limitAmount.toDouble()).toFloat()
            } else 0f
            BudgetProgress(
                budget = budget,
                category = CategoryCatalog.find(budget.categoryId, TransactionType.EXPENSE),
                spentAmount = spent,
                remainingAmount = budget.limitAmount - spent,
                ratio = ratio
            )
        }.sortedByDescending { it.ratio }

        return KasKuDashboardData(
            wallets = walletSummaries,
            allTransactions = transactions,
            periodTransactions = periodTransactions,
            filteredTransactions = filteredTransactions,
            totalNetBalance = totalNetBalance,
            periodIncome = periodIncome,
            periodExpense = periodExpense,
            periodNetCashflow = periodIncome - periodExpense,
            expenseBreakdowns = expenseBreakdowns,
            last7DaysCashflow = last7DaysCashflow,
            budgetProgressList = budgetProgressList,
            totalBudgetLimit = monthBudgets.sumOf { it.limitAmount },
            totalBudgetSpent = budgetProgressList.sumOf { it.spentAmount },
            selectedPeriod = filters.period,
            selectedBudgetMonth = filters.budgetMonth,
            searchQuery = filters.search,
            selectedTypeFilter = filters.typeFilter,
            selectedWalletFilterId = filters.walletFilterId
        )
    }

    private fun computePeriodStart(period: PeriodFilter): Long? {
        val now = System.currentTimeMillis()
        return when (period) {
            PeriodFilter.TODAY -> dayStartMillis(now)
            PeriodFilter.THIS_WEEK -> {
                val cal = Calendar.getInstance().apply {
                    timeInMillis = dayStartMillis(now)
                    add(Calendar.DAY_OF_YEAR, -6)
                }
                cal.timeInMillis
            }
            PeriodFilter.THIS_MONTH -> monthStartMillis(currentMonthKey(now))
            PeriodFilter.ALL -> null
        }
    }

    private fun buildLast7DaysCashflow(transactions: List<FinanceTransaction>): List<DailyCashflowPoint> {
        val todayStart = dayStartMillis(System.currentTimeMillis())
        return (6 downTo 0).map { daysAgo ->
            val start = todayStart - daysAgo * DAY_MILLIS
            val end = start + DAY_MILLIS
            val dayTxs = transactions.filter { it.dateEpochMillis in start until end }
            DailyCashflowPoint(
                dayLabel = formatDayOfWeekShort(start),
                income = dayTxs.filter { TransactionType.fromRaw(it.type) == TransactionType.INCOME }
                    .sumOf { it.amount },
                expense = dayTxs.filter { TransactionType.fromRaw(it.type) == TransactionType.EXPENSE }
                    .sumOf { it.amount }
            )
        }
    }

    fun selectPeriod(period: PeriodFilter) {
        _selectedPeriod.value = period
    }

    fun shiftBudgetMonth(delta: Int) {
        _selectedBudgetMonth.value = shiftMonthKey(_selectedBudgetMonth.value, delta)
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectTypeFilter(filter: TypeFilter) {
        _selectedTypeFilter.value = filter
    }

    fun selectWalletFilter(walletId: String?) {
        _selectedWalletFilterId.value = walletId
    }

    fun clearFeedback() {
        _feedback.value = null
    }

    fun saveTransaction(
        existingId: String?,
        type: TransactionType,
        amount: Long,
        categoryId: String,
        walletId: String,
        note: String,
        dateEpochMillis: Long,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isMutating.value = true
            val result = if (existingId.isNullOrBlank()) {
                repository.addTransaction(
                    userId = currentUserId,
                    type = type,
                    amount = amount,
                    categoryId = categoryId,
                    walletId = walletId,
                    note = note,
                    dateEpochMillis = dateEpochMillis
                )
            } else {
                repository.updateTransaction(
                    userId = currentUserId,
                    transactionId = existingId,
                    type = type,
                    amount = amount,
                    categoryId = categoryId,
                    walletId = walletId,
                    note = note,
                    dateEpochMillis = dateEpochMillis
                )
            }
            _isMutating.value = false
            result.fold(
                onSuccess = {
                    _feedback.value = FeedbackMessage(
                        if (existingId.isNullOrBlank()) "Transaksi tercatat" else "Transaksi diperbarui"
                    )
                    onSuccess()
                },
                onFailure = { e ->
                    _feedback.value = FeedbackMessage(
                        e.localizedMessage ?: "Gagal menyimpan transaksi.",
                        isError = true
                    )
                }
            )
        }
    }

    fun deleteTransaction(transactionId: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _isMutating.value = true
            val result = repository.deleteTransaction(currentUserId, transactionId)
            _isMutating.value = false
            result.fold(
                onSuccess = {
                    _feedback.value = FeedbackMessage("Transaksi dihapus")
                    onSuccess()
                },
                onFailure = { e ->
                    _feedback.value = FeedbackMessage(
                        e.localizedMessage ?: "Gagal menghapus transaksi.",
                        isError = true
                    )
                }
            )
        }
    }

    fun saveWallet(
        existingId: String?,
        name: String,
        kind: WalletKind,
        targetCurrentBalance: Long,
        currentNetTransactions: Long,
        colorKey: WalletColorKey,
        onSuccess: () -> Unit
    ) {
        val computedInitialBalance = targetCurrentBalance - currentNetTransactions
        viewModelScope.launch {
            _isMutating.value = true
            val result = if (existingId.isNullOrBlank()) {
                repository.addWallet(
                    userId = currentUserId,
                    name = name,
                    kind = kind,
                    initialBalance = targetCurrentBalance,
                    colorKey = colorKey
                )
            } else {
                repository.updateWallet(
                    userId = currentUserId,
                    walletId = existingId,
                    name = name,
                    kind = kind,
                    initialBalance = computedInitialBalance,
                    colorKey = colorKey
                )
            }
            _isMutating.value = false
            result.fold(
                onSuccess = {
                    _feedback.value = FeedbackMessage(
                        if (existingId.isNullOrBlank()) "Dompet ditambahkan" else "Dompet diperbarui"
                    )
                    onSuccess()
                },
                onFailure = { e ->
                    _feedback.value = FeedbackMessage(
                        e.localizedMessage ?: "Gagal menyimpan dompet.",
                        isError = true
                    )
                }
            )
        }
    }

    fun deleteWallet(walletId: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _isMutating.value = true
            val result = repository.deleteWallet(currentUserId, walletId)
            _isMutating.value = false
            result.fold(
                onSuccess = {
                    if (_selectedWalletFilterId.value == walletId) {
                        _selectedWalletFilterId.value = null
                    }
                    _feedback.value = FeedbackMessage("Dompet dihapus")
                    onSuccess()
                },
                onFailure = { e ->
                    _feedback.value = FeedbackMessage(
                        e.localizedMessage ?: "Gagal menghapus dompet.",
                        isError = true
                    )
                }
            )
        }
    }

    fun saveBudget(
        categoryId: String,
        limitAmount: Long,
        existingBudgetIds: Set<String>,
        onSuccess: () -> Unit
    ) {
        val monthKey = _selectedBudgetMonth.value
        val budgetId = "${monthKey}_${categoryId}"
        val isExisting = budgetId in existingBudgetIds
        viewModelScope.launch {
            _isMutating.value = true
            val result = repository.upsertBudget(
                userId = currentUserId,
                categoryId = categoryId,
                monthKey = monthKey,
                limitAmount = limitAmount,
                isExisting = isExisting
            )
            _isMutating.value = false
            result.fold(
                onSuccess = {
                    _feedback.value = FeedbackMessage("Anggaran disimpan")
                    onSuccess()
                },
                onFailure = { e ->
                    _feedback.value = FeedbackMessage(
                        e.localizedMessage ?: "Gagal menyimpan anggaran.",
                        isError = true
                    )
                }
            )
        }
    }

    fun deleteBudget(budgetId: String) {
        viewModelScope.launch {
            _isMutating.value = true
            val result = repository.deleteBudget(currentUserId, budgetId)
            _isMutating.value = false
            result.fold(
                onSuccess = { _feedback.value = FeedbackMessage("Anggaran dihapus") },
                onFailure = { e ->
                    _feedback.value = FeedbackMessage(
                        e.localizedMessage ?: "Gagal menghapus anggaran.",
                        isError = true
                    )
                }
            )
        }
    }

    private companion object {
        const val TAG = "KasKuVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
        const val DAY_MILLIS = 86_400_000L
    }
}
