package com.sysdos.kasirpintar.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "store_config")
data class StoreConfig(
    @PrimaryKey(autoGenerate = false)
    val id: Int = 1,

    val storeName: String,
    val storeAddress: String,
    val storePhone: String,
    val storeEmail: String = "",
    val printerMac: String? = null,
    val strukFooter: String = "Terima Kasih",

    // 🔥 TAMBAHAN KOLOM BARU
    @ColumnInfo(name = "tax_percentage") val taxPercentage: Double = 0.0
)