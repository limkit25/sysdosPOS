package com.sysdos.kasirpintar.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import com.sysdos.kasirpintar.data.model.Transaction

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction): Long

    // Ambil Semua Data (Untuk List di Layar HP)
    @Query("SELECT * FROM transaction_table WHERE userId = :uid ORDER BY timestamp DESC")
    fun getAllTransactions(uid: Int): Flow<List<Transaction>>

    // 🔥 TAMBAHAN BARU: UNTUK EXPORT LAPORAN
    // Mengambil data berdasarkan User ID DAN Rentang Tanggal
    @Query("SELECT * FROM transaction_table WHERE userId = :uid AND timestamp BETWEEN :startDate AND :endDate ORDER BY timestamp DESC")
    suspend fun getTransactionsByDateRange(uid: Int, startDate: Long, endDate: Long): List<Transaction>

}