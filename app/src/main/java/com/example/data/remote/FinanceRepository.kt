package com.example.data.remote

import android.content.Context
import com.example.R
import com.example.data.model.CategoryBudget
import com.example.data.model.FinanceTransaction
import com.example.data.model.TransactionType
import com.example.data.model.Wallet
import com.example.data.model.WalletColorKey
import com.example.data.model.WalletKind
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FinanceRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireMatchingUserId(expectedUserId: String): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("Pengguna harus masuk dengan Google sebelum mengakses data.")
        check(uid == expectedUserId) { "Sesi pengguna berubah; operasi lintas akun ditolak." }
        return uid
    }

    fun observeWallets(userId: String): Flow<List<Wallet>> = flow {
        val uid = requireMatchingUserId(userId)
        val path = "users/$uid/wallets"
        emitAll(
            db.collection("users").document(uid).collection("wallets")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Wallet::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.createdAt?.toDate()?.time ?: Long.MAX_VALUE }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeTransactions(userId: String): Flow<List<FinanceTransaction>> = flow {
        val uid = requireMatchingUserId(userId)
        val path = "users/$uid/transactions"
        emitAll(
            db.collection("users").document(uid).collection("transactions")
                .orderBy("dateEpochMillis", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        FinanceTransaction::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeBudgets(userId: String): Flow<List<CategoryBudget>> = flow {
        val uid = requireMatchingUserId(userId)
        val path = "users/$uid/budgets"
        emitAll(
            db.collection("users").document(uid).collection("budgets")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CategoryBudget::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun seedStarterWalletsIfEmpty(userId: String): Result<Unit> {
        val path = "users/$userId/wallets"
        return try {
            val uid = requireMatchingUserId(userId)
            val walletsRef = db.collection("users").document(uid).collection("wallets")
            val existing = walletsRef.limit(1).get().await()
            if (!existing.isEmpty) {
                return Result.success(Unit)
            }
            val starters = listOf(
                Triple("wallet_tunai", "Dompet Tunai", WalletKind.CASH to WalletColorKey.EMERALD),
                Triple("wallet_bank", "Rekening Bank", WalletKind.BANK to WalletColorKey.COBALT),
                Triple("wallet_ewallet", "E-Wallet", WalletKind.EWALLET to WalletColorKey.AMBER)
            )
            val batch = db.batch()
            starters.forEach { (docId, name, style) ->
                val ref = walletsRef.document(docId)
                batch.set(
                    ref,
                    mapOf(
                        "userId" to uid,
                        "name" to name,
                        "kind" to style.first.name,
                        "initialBalance" to 0L,
                        "colorKey" to style.second.name,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun addTransaction(
        userId: String,
        type: TransactionType,
        amount: Long,
        categoryId: String,
        walletId: String,
        note: String,
        dateEpochMillis: Long
    ): Result<Unit> {
        var docPath = "users/$userId/transactions"
        return try {
            val uid = requireMatchingUserId(userId)
            val trimmedNote = note.trim().take(120)
            require(amount in 1L..MAX_AMOUNT) { "Nominal transaksi harus antara Rp 1 dan Rp 1.000 M." }
            require(categoryId.isNotBlank() && categoryId.length <= 40) { "Kategori tidak valid." }
            require(walletId.isNotBlank() && walletId.length <= 128) { "Dompet harus dipilih." }
            require(dateEpochMillis in 1L..MAX_EPOCH_MILLIS) { "Tanggal tidak valid." }

            val docRef = db.collection("users").document(uid).collection("transactions").document()
            docPath = docRef.path
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "type" to type.name,
                    "amount" to amount,
                    "categoryId" to categoryId,
                    "walletId" to walletId,
                    "note" to trimmedNote,
                    "dateEpochMillis" to dateEpochMillis,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateTransaction(
        userId: String,
        transactionId: String,
        type: TransactionType,
        amount: Long,
        categoryId: String,
        walletId: String,
        note: String,
        dateEpochMillis: Long
    ): Result<Unit> {
        val docPath = "users/$userId/transactions/$transactionId"
        return try {
            val uid = requireMatchingUserId(userId)
            require(isValidDocumentId(transactionId)) { "ID transaksi tidak valid." }
            val trimmedNote = note.trim().take(120)
            require(amount in 1L..MAX_AMOUNT) { "Nominal transaksi harus antara Rp 1 dan Rp 1.000 M." }
            require(categoryId.isNotBlank() && categoryId.length <= 40) { "Kategori tidak valid." }
            require(walletId.isNotBlank() && walletId.length <= 128) { "Dompet harus dipilih." }
            require(dateEpochMillis in 1L..MAX_EPOCH_MILLIS) { "Tanggal tidak valid." }

            db.collection("users").document(uid).collection("transactions").document(transactionId)
                .update(
                    mapOf(
                        "type" to type.name,
                        "amount" to amount,
                        "categoryId" to categoryId,
                        "walletId" to walletId,
                        "note" to trimmedNote,
                        "dateEpochMillis" to dateEpochMillis,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteTransaction(userId: String, transactionId: String): Result<Unit> {
        val docPath = "users/$userId/transactions/$transactionId"
        return try {
            val uid = requireMatchingUserId(userId)
            require(isValidDocumentId(transactionId)) { "ID transaksi tidak valid." }
            db.collection("users").document(uid).collection("transactions").document(transactionId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addWallet(
        userId: String,
        name: String,
        kind: WalletKind,
        initialBalance: Long,
        colorKey: WalletColorKey
    ): Result<Unit> {
        var docPath = "users/$userId/wallets"
        return try {
            val uid = requireMatchingUserId(userId)
            val trimmedName = name.trim()
            require(trimmedName.length in 1..40) { "Nama dompet harus 1–40 karakter." }
            require(initialBalance in -MAX_AMOUNT..MAX_AMOUNT) { "Saldo awal di luar batas." }

            val docRef = db.collection("users").document(uid).collection("wallets").document()
            docPath = docRef.path
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "name" to trimmedName,
                    "kind" to kind.name,
                    "initialBalance" to initialBalance,
                    "colorKey" to colorKey.name,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateWallet(
        userId: String,
        walletId: String,
        name: String,
        kind: WalletKind,
        initialBalance: Long,
        colorKey: WalletColorKey
    ): Result<Unit> {
        val docPath = "users/$userId/wallets/$walletId"
        return try {
            val uid = requireMatchingUserId(userId)
            require(isValidDocumentId(walletId)) { "ID dompet tidak valid." }
            val trimmedName = name.trim()
            require(trimmedName.length in 1..40) { "Nama dompet harus 1–40 karakter." }
            require(initialBalance in -MAX_AMOUNT..MAX_AMOUNT) { "Saldo awal di luar batas." }

            db.collection("users").document(uid).collection("wallets").document(walletId)
                .update(
                    mapOf(
                        "name" to trimmedName,
                        "kind" to kind.name,
                        "initialBalance" to initialBalance,
                        "colorKey" to colorKey.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteWallet(userId: String, walletId: String): Result<Unit> {
        val docPath = "users/$userId/wallets/$walletId"
        return try {
            val uid = requireMatchingUserId(userId)
            require(isValidDocumentId(walletId)) { "ID dompet tidak valid." }
            db.collection("users").document(uid).collection("wallets").document(walletId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun upsertBudget(
        userId: String,
        categoryId: String,
        monthKey: String,
        limitAmount: Long,
        isExisting: Boolean
    ): Result<Unit> {
        val budgetId = "${monthKey}_${categoryId}"
        val docPath = "users/$userId/budgets/$budgetId"
        return try {
            val uid = requireMatchingUserId(userId)
            require(isValidDocumentId(budgetId)) { "ID anggaran tidak valid." }
            require(categoryId.isNotBlank() && categoryId.length <= 40) { "Kategori tidak valid." }
            require(MONTH_REGEX.matches(monthKey)) { "Format bulan tidak valid." }
            require(limitAmount in 1L..MAX_AMOUNT) { "Batas anggaran harus lebih dari Rp 0." }

            val docRef = db.collection("users").document(uid).collection("budgets").document(budgetId)
            if (isExisting) {
                docRef.update(
                    mapOf(
                        "limitAmount" to limitAmount,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "categoryId" to categoryId,
                        "monthKey" to monthKey,
                        "limitAmount" to limitAmount,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (isExisting) OperationType.UPDATE else OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteBudget(userId: String, budgetId: String): Result<Unit> {
        val docPath = "users/$userId/budgets/$budgetId"
        return try {
            val uid = requireMatchingUserId(userId)
            require(isValidDocumentId(budgetId)) { "ID anggaran tidak valid." }
            db.collection("users").document(uid).collection("budgets").document(budgetId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    private fun isValidDocumentId(id: String): Boolean =
        id.length in 1..128 && ID_REGEX.matches(id)

    private companion object {
        const val MAX_AMOUNT = 1_000_000_000_000L
        const val MAX_EPOCH_MILLIS = 4_102_444_800_000L
        val ID_REGEX = Regex("^[a-zA-Z0-9_\\-]+$")
        val MONTH_REGEX = Regex("^[0-9]{4}-(0[1-9]|1[0-2])$")
    }
}
