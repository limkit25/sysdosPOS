package com.sysdos.kasirpintar.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
// --- PENTING: Pastikan 3 baris import di bawah ini ada ---
import kotlinx.coroutines.flow.Flow
import com.sysdos.kasirpintar.data.model.Transaction
import com.sysdos.kasirpintar.data.model.TransactionItem

@Dao
interface TransactionDao {

    // Simpan Header Struk
    @Insert
    suspend fun insertTransaction(transaction: Transaction): Long

    // Simpan Item Belanjaan
    @Insert
    suspend fun insertTransactionItem(item: TransactionItem)

    // --- KODE ANDA TADI ---
    // Ambil semua riwayat, urutkan dari yang terbaru (DESC)
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<Transaction>>
}