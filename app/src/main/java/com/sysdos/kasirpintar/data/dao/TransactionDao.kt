package com.sysdos.kasirpintar.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update // <--- JANGAN LUPA IMPORT INI
import kotlinx.coroutines.flow.Flow
import com.sysdos.kasirpintar.data.model.Transaction

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction): Long

    // Ambil Semua Data (Untuk List di Layar HP)
    @Query("SELECT * FROM transaction_table WHERE userId = :uid ORDER BY timestamp DESC")
    fun getAllTransactions(uid: Int): Flow<List<Transaction>>

    // Untuk Export Laporan
    @Query("SELECT * FROM transaction_table WHERE userId = :uid AND timestamp BETWEEN :startDate AND :endDate ORDER BY timestamp DESC")
    suspend fun getTransactionsByDateRange(uid: Int, startDate: Long, endDate: Long): List<Transaction>

    // 🔥 WAJIB DITAMBAHKAN UNTUK FITUR PELUNASAN:
    @Update
    fun update(transaction: Transaction)

}