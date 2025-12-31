package com.sysdos.kasirpintar.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "transaction_items",
    foreignKeys = [
        ForeignKey(
            entity = Transaction::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE // Hapus item jika struk dihapus
        )
    ]
)
data class TransactionItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val transactionId: Long, // Nempel ke struk mana?
    val productId: Int,
    val productName: String, // Simpan nama saat beli (antisipasi nama produk berubah)
    val price: Double,
    val quantity: Int
)