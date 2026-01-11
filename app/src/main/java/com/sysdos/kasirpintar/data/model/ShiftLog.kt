package com.sysdos.kasirpintar.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shift_logs")
data class ShiftLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val type: String,        // "OPEN" atau "CLOSE"
    val timestamp: Long,     // Waktu kejadian
    val kasirName: String,   // Siapa yang jaga
    val expectedAmount: Double, // Uang yang SEHARUSNYA ada (Sistem)
    val actualAmount: Double,   // Uang FISIK yang diinput kasir
    val difference: Double,      // Selisih (Plus/Minus)
    val userId: Int = 0
)