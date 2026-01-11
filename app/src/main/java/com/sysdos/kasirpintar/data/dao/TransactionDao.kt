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

    // 🔥 PERBAIKAN DI SINI:
    // 1. Tambahkan parameter (uid: Int) di dalam kurung fungsi
    // 2. Tambahkan WHERE userId = :uid di dalam Query SQL
    @Query("SELECT * FROM transaction_table WHERE userId = :uid ORDER BY timestamp DESC")
    fun getAllTransactions(uid: Int): Flow<List<Transaction>>
}