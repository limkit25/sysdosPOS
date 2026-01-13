package com.sysdos.kasirpintar.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "store_config")
data class StoreConfig(
    @PrimaryKey(autoGenerate = false)
    val id: Int = 1, // ID Selalu 1 (Single Row)

    val storeName: String,
    val storeAddress: String,
    val storePhone: String,
    val storeEmail: String = "",
    val printerMac: String? = null, // Simpan Mac Address Printer disini juga
    val strukFooter: String = "Terima Kasih"
)