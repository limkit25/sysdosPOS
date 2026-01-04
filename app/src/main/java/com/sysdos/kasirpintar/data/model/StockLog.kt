package com.sysdos.kasirpintar.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stock_logs")
data class StockLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val productName: String,
    val supplierName: String,
    val quantity: Int,
    val costPrice: Double,
    val totalCost: Double,
    val type: String = "IN"
)