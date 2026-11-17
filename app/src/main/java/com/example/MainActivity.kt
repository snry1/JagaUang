package com.example

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.auth.authStateFlow
import com.example.auth.signOut
import com.example.data.model.FinanceTransaction
import com.example.data.model.TransactionType
import com.example.data.remote.FinanceRepository
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.BudgetsScreen
import com.example.ui.screens.OverviewScreen
import com.example.ui.screens.TransactionEditorSheet
import com.example.ui.screens.TransactionsScreen
import com.example.ui.screens.WalletsScreen
import com.example.ui.state.UiState
import com.example.ui.theme.KasKuTheme
import com.example.ui.viewmodel.KasKuViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

object KasKuRoutes {
    const val OVERVIEW = "ringkasan"
    const val TRANSACTIONS = "transaksi"
    const val WALLETS = "dompet"
    const val BUDGETS = "anggaran"
}

private data class NavDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val kasKuDestinations = listOf(
    NavDestination(
        route = KasKuRoutes.OVERVIEW,
        label = "Ringkasan",
        selectedIcon = Icons.Rounded.Home,
        unselectedIcon = Icons.Outlined.Home
    ),
    NavDestination(
        route = KasKuRoutes.TRANSACTIONS,
        label = "Transaksi",
        selectedIcon = Icons.Rounded.ReceiptLong,
        unselectedIcon = Icons.Outlined.ReceiptLong
    ),
    NavDestination(
        route = KasKuRoutes.WALLETS,
        label = "Dompet",
        selectedIcon = Icons.Rounded.AccountBalanceWallet,
        unselectedIcon = Icons.Outlined.AccountBalanceWallet
    ),
    NavDestination(
        route = KasKuRoutes.BUDGETS,
        label = "Anggaran",
        selectedIcon = Icons.Rounded.PieChart,
        unselectedIcon = Icons.Outlined.PieChart
    )
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KasKuTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        key(user.uid) {
            KasKuAppShell(currentUser = user)
        }
    }
}

@Composable
private fun KasKuAppShell(currentUser: FirebaseUser) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: KasKuViewModel = viewModel(
        key = currentUser.uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) as Application
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                KasKuViewModel(FinanceRepository(db), currentUser.uid)
            }
        }
    )

    val dashboardState by viewModel.dashboardState.collectAsStateWithLifecycle()
    val isMutating by viewModel.isMutating.collectAsStateWithLifecycle()
    val feedback by viewModel.feedback.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(feedback) {
        val msg = feedback
        if (msg != null) {
            snackbarHostState.showSnackbar(msg.text)
            viewModel.clearFeedback()
        }
    }

    var showTransactionSheet by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<FinanceTransaction?>(null) }
    var sheetInitialType by remember { mutableStateOf(TransactionType.EXPENSE) }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: KasKuRoutes.OVERVIEW

    fun navigateTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        editingTransaction = null
                        sheetInitialType = TransactionType.EXPENSE
                        showTransactionSheet = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.testTag("fab_add_transaction")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Catat transaksi baru"
                    )
                }
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp
                    ) {
                        kasKuDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTab(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag("nav_${dest.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(Modifier.height(16.dp))
                        kasKuDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTab(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag("nav_${dest.route}")
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    when (val state = dashboardState) {
                        is UiState.Loading -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = "Menyiapkan buku kasmu...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        is UiState.Error -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.errorContainer
                                ) {
                                    Text(
                                        text = state.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }
                        }

                        is UiState.Success -> {
                            val data = state.data
                            NavHost(
                                navController = navController,
                                startDestination = KasKuRoutes.OVERVIEW,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .widthIn(max = 720.dp)
                            ) {
                                composable(KasKuRoutes.OVERVIEW) {
                                    OverviewScreen(
                                        userName = currentUser.displayName,
                                        data = data,
                                        onSelectPeriod = viewModel::selectPeriod,
                                        onQuickAdd = { type ->
                                            editingTransaction = null
                                            sheetInitialType = type
                                            showTransactionSheet = true
                                        },
                                        onEditTransaction = { tx ->
                                            editingTransaction = tx
                                            showTransactionSheet = true
                                        },
                                        onSeeAllTransactions = { navigateTab(KasKuRoutes.TRANSACTIONS) },
                                        onNavigateWallets = { navigateTab(KasKuRoutes.WALLETS) },
                                        onSignOut = {
                                            signOut(credentialManager = credentialManager, scope = scope)
                                        }
                                    )
                                }

                                composable(KasKuRoutes.TRANSACTIONS) {
                                    TransactionsScreen(
                                        data = data,
                                        onSearchChange = viewModel::updateSearchQuery,
                                        onSelectTypeFilter = viewModel::selectTypeFilter,
                                        onSelectWalletFilter = viewModel::selectWalletFilter,
                                        onSelectPeriod = viewModel::selectPeriod,
                                        onEditTransaction = { tx ->
                                            editingTransaction = tx
                                            showTransactionSheet = true
                                        },
                                        onAddClick = {
                                            editingTransaction = null
                                            sheetInitialType = TransactionType.EXPENSE
                                            showTransactionSheet = true
                                        }
                                    )
                                }

                                composable(KasKuRoutes.WALLETS) {
                                    WalletsScreen(
                                        data = data,
                                        isMutating = isMutating,
                                        onSaveWallet = viewModel::saveWallet,
                                        onDeleteWallet = viewModel::deleteWallet
                                    )
                                }

                                composable(KasKuRoutes.BUDGETS) {
                                    BudgetsScreen(
                                        data = data,
                                        isMutating = isMutating,
                                        onShiftMonth = viewModel::shiftBudgetMonth,
                                        onSaveBudget = viewModel::saveBudget,
                                        onDeleteBudget = viewModel::deleteBudget
                                    )
                                }
                            }

                            if (showTransactionSheet) {
                                TransactionEditorSheet(
                                    existingTransaction = editingTransaction,
                                    initialType = sheetInitialType,
                                    wallets = data.wallets,
                                    isSaving = isMutating,
                                    onDismiss = {
                                        showTransactionSheet = false
                                        editingTransaction = null
                                    },
                                    onSave = { existingId, type, amount, categoryId, walletId, note, dateMillis ->
                                        viewModel.saveTransaction(
                                            existingId = existingId,
                                            type = type,
                                            amount = amount,
                                            categoryId = categoryId,
                                            walletId = walletId,
                                            note = note,
                                            dateEpochMillis = dateMillis,
                                            onSuccess = {
                                                showTransactionSheet = false
                                                editingTransaction = null
                                            }
                                        )
                                    },
                                    onDelete = { txId ->
                                        viewModel.deleteTransaction(txId) {
                                            showTransactionSheet = false
                                            editingTransaction = null
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
