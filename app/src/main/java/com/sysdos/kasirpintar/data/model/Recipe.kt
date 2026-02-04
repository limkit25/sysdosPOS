package com.sysdos.kasirpintar.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "recipe",
    foreignKeys = [
        ForeignKey(
            entity = Product::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Product::class,
            parentColumns = ["id"],
            childColumns = ["ingredientId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Recipe(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Int,      // ID Produk Utama (Int match Product.id)
    val ingredientId: Int,   // ID Bahan Baku (Int match Product.id)
    val quantity: Double,    // Jumlah yang dipakai
    val unit: String         // Satuan
)
