package com.sysdos.kasirpintar.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sysdos.kasirpintar.data.model.StockLog
import kotlinx.coroutines.flow.Flow

@Dao
interface StockLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: StockLog)

    // 1. QUERY UTAMA: Mengelompokkan berdasarkan purchaseId
    // Menggunakan Flow agar konsisten dengan Repository
    @Query("SELECT * FROM stock_logs WHERE type = 'IN' GROUP BY purchaseId ORDER BY timestamp DESC")
    fun getPurchaseHistoryGroups(): Flow<List<StockLog>>

    // 2. QUERY DETAIL: Mengambil semua barang dalam satu pembelian
    @Query("SELECT * FROM stock_logs WHERE purchaseId = :pId")
    suspend fun getPurchaseDetails(pId: Long): List<StockLog>

    // 3. Query Log biasa (Semua history) - Opsional jika masih dipakai
    @Query("SELECT * FROM stock_logs ORDER BY timestamp DESC")
    fun getAllStockLogs(): Flow<List<StockLog>>
}