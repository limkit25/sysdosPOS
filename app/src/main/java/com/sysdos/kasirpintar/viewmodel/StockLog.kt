package com.sysdos.kasirpintar.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stock_logs")
data class StockLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,       // Waktu Pembelian
    val productName: String,   // Nama Barang
    val supplierName: String,  // Nama Supplier
    val quantity: Int,         // Jumlah Masuk
    val costPrice: Double,     // Harga Beli per pcs
    val totalCost: Double,     // Total Uang Keluar (Qty * Cost)
    val type: String = "IN"    // "IN" = Masuk (Pembelian), "OUT" = Keluar (Rusak/Hilang)
)