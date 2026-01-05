package com.sysdos.kasirpintar.data

import com.sysdos.kasirpintar.data.dao.ProductDao
import com.sysdos.kasirpintar.data.dao.ShiftDao
import com.sysdos.kasirpintar.data.dao.StockLogDao
import com.sysdos.kasirpintar.data.model.*
import kotlinx.coroutines.flow.Flow

// [PERUBAHAN 1] Tambahkan 3 DAO di constructor
class ProductRepository(
    private val productDao: ProductDao,
    private val shiftDao: ShiftDao,
    private val stockLogDao: StockLogDao
) {

    // --- PRODUK ---
    val allProducts: Flow<List<Product>> = productDao.getAllProducts()

    suspend fun insert(product: Product) = productDao.insert(product)
    suspend fun update(product: Product) = productDao.update(product)
    suspend fun delete(product: Product) = productDao.delete(product)

    // --- KATEGORI ---
    val allCategories: Flow<List<Category>> = productDao.getAllCategories()
    suspend fun insertCategory(category: Category) = productDao.insertCategory(category)
    suspend fun deleteCategory(category: Category) = productDao.deleteCategory(category)

    // --- SUPPLIER ---
    val allSuppliers: Flow<List<Supplier>> = productDao.getAllSuppliers()
    suspend fun insertSupplier(supplier: Supplier) = productDao.insertSupplier(supplier)
    suspend fun deleteSupplier(supplier: Supplier) = productDao.deleteSupplier(supplier)
    // Tambahkan update jika error di fragment supplier
    suspend fun updateSupplier(supplier: Supplier) = productDao.updateSupplier(supplier)

    // --- TRANSAKSI ---
    val allTransactions: Flow<List<Transaction>> = productDao.getAllTransactions()
    suspend fun insertTransaction(transaction: Transaction): Long = productDao.insertTransaction(transaction)

    // --- USERS (Login) ---
    val allUsers: Flow<List<User>> = productDao.getAllUsers()
    suspend fun insertUser(user: User) = productDao.insertUser(user)
    suspend fun updateUser(user: User) = productDao.updateUser(user)
    suspend fun deleteUser(user: User) = productDao.deleteUser(user)

    suspend fun login(u: String, p: String): User? = productDao.login(u, p)

    // --- LOG STOK & PEMBELIAN (Panggil stockLogDao) ---
    val allStockLogs: Flow<List<StockLog>> = productDao.getAllStockLogs()

    // 🔥 [PERBAIKAN] Menggunakan stockLogDao untuk Riwayat Pembelian (Grouping)
    // Pastikan di StockLogDao return type-nya Flow agar konsisten, atau LiveData jika mau
    val purchaseHistory: Flow<List<StockLog>> = stockLogDao.getPurchaseHistoryGroups()

    suspend fun recordPurchase(log: StockLog) {
        // Simpan ke tabel log via DAO yang benar
        stockLogDao.insertLog(log)
    }

    // Ambil detail pembelian untuk dialog
    suspend fun getPurchaseDetails(pId: Long): List<StockLog> {
        return stockLogDao.getPurchaseDetails(pId)
    }

    // --- SHIFT (Panggil shiftDao) ---
    val allShiftLogs: Flow<List<ShiftLog>> = shiftDao.getAllLogs()

    suspend fun insertShiftLog(log: ShiftLog) {
        shiftDao.insertLog(log)
    }
}