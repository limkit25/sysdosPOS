package com.sysdos.kasirpintar.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stock_logs")
data class StockLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val purchaseId: Long = 0,      // ID Unik Pembelian/Void
    val timestamp: Long,           // Waktu kejadian
    val productName: String,       // Nama Barang
    val supplierName: String = "", // Supplier (atau Keterangan Void/Retur)
    val quantity: Int,             // Jumlah Barang
    val costPrice: Double = 0.0,   // Harga Beli Satuan
    val totalCost: Double = 0.0,   // Total Harga (Qty * Cost)

    // 🔥 KOLOM PENTING BUAT LAPORAN VOID & RETUR
    // Isi bisa: "IN" (Beli), "OUT" (Retur), "VOID" (Batal Transaksi)
    val type: String = "IN"
)