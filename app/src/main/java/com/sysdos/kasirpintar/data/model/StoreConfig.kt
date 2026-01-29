package com.sysdos.kasirpintar.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import com.google.gson.annotations.SerializedName

@Entity(tableName = "store_config")
data class StoreConfig(
    @PrimaryKey(autoGenerate = false)
    val id: Int = 1,

    @SerializedName("store_name")
    val storeName: String,

    @SerializedName("address") // Server: address
    val storeAddress: String,

    @SerializedName("phone") // Server: phone
    val storePhone: String,

    @SerializedName("email") // Server: email (KOSONG DI SERVER, JADI NULLABLE AJA)
    val storeEmail: String? = "",

    val printerMac: String? = null,

    @SerializedName("footer_note") // Server: footer_note
    val strukFooter: String = "Terima Kasih",

    // 🔥 TAMBAHAN KOLOM BARU
    @ColumnInfo(name = "tax_percentage") 
    @SerializedName("tax_percentage") // Server: tax_percentage? (Check model)
    val taxPercentage: Double = 0.0
)