package com.sysdos.kasirpintar.data

import com.sysdos.kasirpintar.data.dao.ProductDao
import com.sysdos.kasirpintar.data.model.*
import kotlinx.coroutines.flow.Flow

class ProductRepository(private val productDao: ProductDao) {

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

    // --- TRANSAKSI ---
    val allTransactions: Flow<List<Transaction>> = productDao.getAllTransactions()
    suspend fun insertTransaction(transaction: Transaction): Long = productDao.insertTransaction(transaction)

    // --- USERS ---
    val allUsers: Flow<List<User>> = productDao.getAllUsers()
    suspend fun insertUser(user: User) = productDao.insertUser(user)
    suspend fun updateUser(user: User) = productDao.updateUser(user)
    suspend fun deleteUser(user: User) = productDao.deleteUser(user)

    // 🔥🔥🔥 SAMBUNGKAN LOG KE SINI 🔥🔥🔥
    val allStockLogs: Flow<List<StockLog>> = productDao.getAllStockLogs()

    suspend fun recordPurchase(log: StockLog) {
        productDao.insertLog(log)
    }
}