package com.sysdos.kasirpintar.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val amount: Double,
    val category: String, // e.g., "Keamanan", "Sampah", "Operasional", "Lainnya"
    val note: String,
    val timestamp: Long,
    val userId: Int, // Who input this expense
    val branchId: Int = 0 // Future proofing for multi-branch
)
