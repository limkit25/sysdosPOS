package com.sysdos.kasirpintar.data.model

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "products")
data class Product(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val price: Double,
    val costPrice: Double = 0.0,    // Tambahan: Harga Modal
    val stock: Int,
    val category: String = "Lainnya", // Tambahan: Kategori
    val barcode: String? = null,
    val imagePath: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) : Parcelable