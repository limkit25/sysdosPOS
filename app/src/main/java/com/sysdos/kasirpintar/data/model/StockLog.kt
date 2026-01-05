package com.sysdos.kasirpintar.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stock_logs")
data class StockLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val purchaseId: Long,     // 🔥 BARU: Nomor Unik Pembelian (bisa pakai timestamp waktu klik simpan)
    val timestamp: Long,
    val productName: String,
    val supplierName: String,
    val quantity: Int,
    val costPrice: Double,    // Harga satuan
    val totalCost: Double,    // quantity * costPrice
    val type: String          // "IN" (Masuk) atau "OUT" (Keluar/Koreksi)
)