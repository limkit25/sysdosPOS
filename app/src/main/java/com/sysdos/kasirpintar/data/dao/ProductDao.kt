package com.sysdos.kasirpintar.data.dao

import androidx.room.*
import com.sysdos.kasirpintar.data.model.*
import com.sysdos.kasirpintar.data.model.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {

    // --- PRODUK (Tabel: products) ---
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(product: Product)

    @Update
    suspend fun update(product: Product)

    @Delete
    suspend fun delete(product: Product)

    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAllProducts(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getProductRaw(id: Int): Product?

    // --- KATEGORI (Tabel: categories) ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: Category)

    @Delete
    suspend fun deleteCategory(category: Category)

    @Query("SELECT * FROM categories ORDER BY id DESC")
    fun getAllCategories(): Flow<List<Category>>

    // --- SUPPLIER (Tabel: suppliers) ---
    // --- SUPPLIER (Tabel: suppliers) ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSupplier(supplier: Supplier)

    // 🔥 TAMBAHKAN INI 🔥
    @Update
    suspend fun updateSupplier(supplier: Supplier)

    @Delete
    suspend fun deleteSupplier(supplier: Supplier)

    @Query("SELECT * FROM suppliers ORDER BY name ASC")
    fun getAllSuppliers(): Flow<List<Supplier>>

    // --- TRANSAKSI (Tabel: transaction_table) ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction): Long

    @Query("SELECT * FROM transaction_table ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    // --- USER (Tabel: user_table) ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    @Update
    suspend fun updateUser(user: User)

    @Delete
    suspend fun deleteUser(user: User)

    @Query("SELECT * FROM user_table ORDER BY username ASC")
    fun getAllUsers(): Flow<List<User>>

    // 🔥 TAMBAHAN YANG HILANG (Dibutuhkan oleh ProductViewModel) 🔥
    @Query("UPDATE user_table SET password = :newPass WHERE id = :userId")
    suspend fun updatePassword(userId: Int, newPass: String)

    @Query("SELECT * FROM user_table WHERE username = :username AND password = :password")
    suspend fun login(username: String, password: String): User?

    // --- LOG STOK (Tabel: stock_logs) ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: StockLog)

    @Query("SELECT * FROM stock_logs ORDER BY timestamp DESC")
    fun getAllStockLogs(): Flow<List<StockLog>>

    // 🔥 TAMBAHKAN INI 🔥
    // Cari barang berdasarkan nama (limit 1 saja biar cepat)
    @Query("SELECT * FROM products WHERE name = :productName LIMIT 1")
    suspend fun getProductByName(productName: String): Product?

    // 🔥 TAMBAHKAN INI: Fungsi untuk menghapus semua kategori 🔥
    @Query("DELETE FROM categories")
    suspend fun deleteAllCategories()


}