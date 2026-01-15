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
    val userId: Int = 0,

    // 🔥 TAMBAHAN BARU (WAJIB ADA BIAR GAK ERROR MERAH)
    // Default value "" agar data lama tidak error
    val note: String = ""
) : Parcelable