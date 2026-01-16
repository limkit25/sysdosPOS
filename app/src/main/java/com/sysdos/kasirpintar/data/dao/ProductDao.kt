package com.sysdos.kasirpintar.data.dao

import androidx.room.*
import com.sysdos.kasirpintar.data.model.*
import com.sysdos.kasirpintar.data.model.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {

    // --- PRODUK ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: Product): Long // 🔥 Wajib return Long

    @Update
    suspend fun update(product: Product)

    @Delete
    suspend fun delete(product: Product)

    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAllProducts(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getProductRaw(id: Int): Product?

    // 🔥 --- VARIAN (YANG TADI HILANG) --- 🔥
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVariant(variant: ProductVariant)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVariants(variants: List<ProductVariant>)

    @Query("SELECT * FROM product_variants WHERE productId = :productId")
    fun getVariantsByProductId(productId: Int): Flow<List<ProductVariant>>

    @Query("DELETE FROM product_variants WHERE productId = :productId")
    suspend fun deleteVariantsByProductId(productId: Int)

    // --- KATEGORI ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: Category)

    @Delete
    suspend fun deleteCategory(category: Category)

    @Query("SELECT * FROM categories ORDER BY id DESC")
    fun getAllCategories(): Flow<List<Category>>

    @Query("DELETE FROM categories")
    suspend fun deleteAllCategories()

    // --- SUPPLIER ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSupplier(supplier: Supplier)

    @Update
    suspend fun updateSupplier(supplier: Supplier)

    @Delete
    suspend fun deleteSupplier(supplier: Supplier)

    @Query("SELECT * FROM suppliers ORDER BY name ASC")
    fun getAllSuppliers(): Flow<List<Supplier>>

    // --- TRANSAKSI ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction): Long

    @Query("SELECT * FROM transaction_table WHERE userId = :uid ORDER BY timestamp DESC")
    fun getAllTransactions(uid: Int): Flow<List<Transaction>>

    // --- USER ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    @Update
    suspend fun updateUser(user: User)

    @Delete
    suspend fun deleteUser(user: User)

    @Query("SELECT * FROM user_table ORDER BY username ASC")
    fun getAllUsers(): Flow<List<User>>

    @Query("UPDATE user_table SET password = :newPass WHERE id = :userId")
    suspend fun updatePassword(userId: Int, newPass: String)

    @Query("SELECT * FROM user_table WHERE username = :username AND password = :password")
    suspend fun login(username: String, password: String): User?

    @Query("SELECT * FROM user_table WHERE username = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): User?

    // --- LOG STOK ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: StockLog)

    @Query("SELECT * FROM stock_logs ORDER BY timestamp DESC")
    fun getAllStockLogs(): Flow<List<StockLog>>

    // --- UTILS LAINNYA ---
    @Query("SELECT * FROM products WHERE name = :productName LIMIT 1")
    suspend fun getProductByName(productName: String): Product?

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getProductById(id: Int): Product?

    @Query("SELECT * FROM products WHERE barcode = :barcode LIMIT 1")
    suspend fun getProductByBarcode(barcode: String): Product?

    @Query("DELETE FROM products")
    suspend fun deleteAllProducts()

    // 🔥 PERINTAH POTONG STOK
    @Query("UPDATE products SET stock = stock - :quantity WHERE id = :productId")
    suspend fun decreaseStock(productId: Int, quantity: Int)

}