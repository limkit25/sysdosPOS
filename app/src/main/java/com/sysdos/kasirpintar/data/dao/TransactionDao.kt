package com.sysdos.kasirpintar.data.dao

import androidx.room.Dao
import androidx.room.Delete // <--- IMPORT INI
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import com.sysdos.kasirpintar.data.model.Transaction

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction): Long

    // 1. BUAT KASIR (Hanya lihat punya sendiri) - BIARKAN INI
    @Query("SELECT * FROM transaction_table WHERE userId = :uid ORDER BY timestamp DESC")
    fun getAllTransactions(uid: Int): Flow<List<Transaction>>

    // 🔥 2. BUAT ADMIN (TAMBAHAN BARU - LIHAT SEMUA) 🔥
    // Perhatikan: Tidak ada "WHERE userId = ..."
    @Query("SELECT * FROM transaction_table ORDER BY timestamp DESC")
    fun getAllTransactionsGlobal(): Flow<List<Transaction>>

    // ... (Sisanya ke bawah biarkan sama) ...
    @Query("SELECT * FROM transaction_table WHERE userId = :uid AND timestamp BETWEEN :startDate AND :endDate ORDER BY timestamp DESC")
    suspend fun getTransactionsByDateRange(uid: Int, startDate: Long, endDate: Long): List<Transaction>

    // 🔥 3. QUERY EXPORT ADMIN (GLOBAL - SEMUA KASIR) 🔥
    @Query("SELECT * FROM transaction_table WHERE timestamp BETWEEN :startDate AND :endDate ORDER BY timestamp DESC")
    suspend fun getTransactionsByDateRangeGlobal(startDate: Long, endDate: Long): List<Transaction>

    @Update
    suspend fun update(transaction: Transaction)

    @Delete
    suspend fun delete(transaction: Transaction)
}