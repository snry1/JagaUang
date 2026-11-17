package com.example.data.model

import androidx.annotation.Keep
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class TransactionType(val labelId: String) {
    EXPENSE("Pengeluaran"),
    INCOME("Pemasukan");

    companion object {
        fun fromRaw(raw: String?): TransactionType =
            entries.firstOrNull { it.name == raw } ?: EXPENSE
    }
}

enum class WalletKind(val labelId: String, val icon: ImageVector) {
    CASH("Tunai", Icons.Rounded.AccountBalanceWallet),
    BANK("Rekening Bank", Icons.Rounded.AccountBalance),
    EWALLET("E-Wallet", Icons.Rounded.Smartphone),
    SAVINGS("Tabungan", Icons.Rounded.Savings);

    companion object {
        fun fromRaw(raw: String?): WalletKind =
            entries.firstOrNull { it.name == raw } ?: CASH
    }
}

enum class WalletColorKey(val labelId: String, val primaryColor: Color) {
    EMERALD("Zamrud", Color(0xFF0D5C46)),
    COBALT("Biru", Color(0xFF275DAD)),
    AMBER("Emas", Color(0xFFC58B1E)),
    TERRACOTTA("Terakota", Color(0xFFC94A32)),
    PLUM("Plum", Color(0xFF6B4E71)),
    SLATE("Grafit", Color(0xFF3F4E4F));

    companion object {
        fun fromRaw(raw: String?): WalletColorKey =
            entries.firstOrNull { it.name == raw } ?: EMERALD
    }
}

@Keep
@IgnoreExtraProperties
data class Wallet(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val kind: String = WalletKind.CASH.name,
    val initialBalance: Long = 0L,
    val colorKey: String = WalletColorKey.EMERALD.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class FinanceTransaction(
    @DocumentId val id: String = "",
    val userId: String = "",
    val type: String = TransactionType.EXPENSE.name,
    val amount: Long = 0L,
    val categoryId: String = "makan",
    val walletId: String = "wallet_tunai",
    val note: String = "",
    val dateEpochMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CategoryBudget(
    @DocumentId val id: String = "",
    val userId: String = "",
    val categoryId: String = "makan",
    val monthKey: String = "",
    val limitAmount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class FinanceCategory(
    val id: String,
    val name: String,
    val type: TransactionType,
    val icon: ImageVector,
    val accentColor: Color
)

object CategoryCatalog {
    val expenseCategories: List<FinanceCategory> = listOf(
        FinanceCategory("makan", "Makan & Minum", TransactionType.EXPENSE, Icons.Rounded.Restaurant, Color(0xFFC94A32)),
        FinanceCategory("transport", "Transportasi", TransactionType.EXPENSE, Icons.Rounded.DirectionsCar, Color(0xFF275DAD)),
        FinanceCategory("belanja", "Belanja", TransactionType.EXPENSE, Icons.Rounded.ShoppingBag, Color(0xFF9C4E8B)),
        FinanceCategory("tagihan", "Tagihan", TransactionType.EXPENSE, Icons.Rounded.ReceiptLong, Color(0xFFC58B1E)),
        FinanceCategory("hiburan", "Hiburan", TransactionType.EXPENSE, Icons.Rounded.Movie, Color(0xFF3E7B9E)),
        FinanceCategory("kesehatan", "Kesehatan", TransactionType.EXPENSE, Icons.Rounded.LocalHospital, Color(0xFF2E8B6E)),
        FinanceCategory("pendidikan", "Pendidikan", TransactionType.EXPENSE, Icons.Rounded.School, Color(0xFF5D5C8A)),
        FinanceCategory("lainnya_keluar", "Lainnya", TransactionType.EXPENSE, Icons.Rounded.Category, Color(0xFF565F5A))
    )

    val incomeCategories: List<FinanceCategory> = listOf(
        FinanceCategory("gaji", "Gaji", TransactionType.INCOME, Icons.Rounded.Work, Color(0xFF0D5C46)),
        FinanceCategory("bonus", "Bonus", TransactionType.INCOME, Icons.Rounded.Payments, Color(0xFF1A8767)),
        FinanceCategory("usaha", "Usaha", TransactionType.INCOME, Icons.Rounded.Storefront, Color(0xFFC58B1E)),
        FinanceCategory("investasi", "Investasi", TransactionType.INCOME, Icons.Rounded.TrendingUp, Color(0xFF275DAD)),
        FinanceCategory("hadiah", "Hadiah", TransactionType.INCOME, Icons.Rounded.CardGiftcard, Color(0xFF8C5383)),
        FinanceCategory("lainnya_masuk", "Lainnya", TransactionType.INCOME, Icons.Rounded.Category, Color(0xFF565F5A))
    )

    private val byId: Map<String, FinanceCategory> =
        (expenseCategories + incomeCategories).associateBy { it.id }

    fun find(categoryId: String, type: TransactionType = TransactionType.EXPENSE): FinanceCategory {
        return byId[categoryId] ?: if (type == TransactionType.INCOME) {
            incomeCategories.last()
        } else {
            expenseCategories.last()
        }
    }

    fun forType(type: TransactionType): List<FinanceCategory> {
        return if (type == TransactionType.INCOME) incomeCategories else expenseCategories
    }
}
