package com.sysdos.kasirpintar.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sysdos.kasirpintar.data.model.Product
import com.sysdos.kasirpintar.data.model.Transaction
import com.sysdos.kasirpintar.data.model.User
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {

    // --- BAGIAN PRODUK (NAMA TABEL: products) ---
    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAllProducts(): Flow<List<Product>>

    // Fungsi untuk LiveData (Async)
    @Query("SELECT * FROM products WHERE id = :id")
    fun getProductById(id: Int): Flow<Product>

    // [BARU] Fungsi untuk ambil data MENTAH saat Checkout (Synchronous/Suspend)
    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getProductRaw(id: Int): Product?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProduct(product: Product)

    @Update
    suspend fun updateProduct(product: Product)

    @Delete
    suspend fun deleteProduct(product: Product)

    // Update stok langsung (Opsional, tapi kita pakai logika updateProduct di VM biar aman)
    @Query("UPDATE products SET stock = stock - :quantity WHERE id = :productId")
    suspend fun decreaseStock(productId: Int, quantity: Int)


    // --- BAGIAN TRANSAKSI ---
    @Query("SELECT * FROM transaction_table ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction): Long


    // --- BAGIAN USER ---
    @Query("SELECT * FROM user_table")
    fun getAllUsers(): Flow<List<User>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertUser(user: User)

    @Update
    suspend fun updateUser(user: User)

    @Delete
    suspend fun deleteUser(user: User)

    @Query("UPDATE user_table SET password = :newPass WHERE id = :userId")
    suspend fun updatePassword(userId: Int, newPass: String)

    // Login
    @Query("SELECT * FROM user_table WHERE username = :username AND password = :password")
    suspend fun login(username: String, password: String): User?

    // 🔥 TAMBAHKAN INI 🔥
    @Query("SELECT * FROM stock_logs ORDER BY timestamp DESC")
    fun getAllStockLogs(): kotlinx.coroutines.flow.Flow<List<com.sysdos.kasirpintar.data.model.StockLog>>
}