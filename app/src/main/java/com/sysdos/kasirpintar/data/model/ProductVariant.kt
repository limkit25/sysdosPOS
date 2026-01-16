package com.sysdos.kasirpintar.data.model

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(
    tableName = "product_variants",
    // Link ke Tabel Product (Induk)
    foreignKeys = [ForeignKey(
        entity = Product::class,
        parentColumns = ["id"],
        childColumns = ["productId"],
        onDelete = ForeignKey.CASCADE // Kalau Produk induk dihapus, varian ikut hilang
    )],
    indices = [Index(value = ["productId"])]
)
data class ProductVariant(
    @PrimaryKey(autoGenerate = true) val variantId: Int = 0,
    val productId: Int,          // ID Induk (misal: ID Kopi Susu)
    val variantName: String,     // Nama (misal: "Large")
    val variantPrice: Double,    // Harga (misal: 15.000)
    val variantStock: Int = 0    // Stok Varian
) : Parcelable