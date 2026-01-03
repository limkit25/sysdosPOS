package com.sysdos.kasirpintar.data.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.sysdos.kasirpintar.data.model.StockLog

@Dao
interface StockLogDao {
    @Insert
    suspend fun insert(log: StockLog)

    // Ambil riwayat pembelian (terbaru paling atas)
    @Query("SELECT * FROM stock_logs WHERE type = 'IN' ORDER BY timestamp DESC")
    fun getAllPurchases(): LiveData<List<StockLog>>

    // Hitung Total Pengeluaran (Uang Belanja)
    @Query("SELECT SUM(totalCost) FROM stock_logs WHERE type = 'IN'")
    fun getTotalPurchaseExpense(): LiveData<Double>
}