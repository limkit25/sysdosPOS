package com.sysdos.kasirpintar.data.model

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "transaction_table")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val itemsSummary: String, // Ringkasan barang yg dibeli
    val totalAmount: Double,
    val profit: Double,
    val timestamp: Long,      // Tanggal transaksi (millisecond)
    val paymentMethod: String, // Tunai / QRIS
    val cashReceived: Double,
    val changeAmount: Double,
    val subtotal: Double,
    val discount: Double,
    val tax: Double,

    // 🔥 TAMBAHKAN KOLOM INI AGAR MULTI-TOKO BERJALAN 🔥
    val userId: Int = 0
) : Parcelable