package com.sysdos.kasirpintar.data

import android.content.Context
import android.util.Log
import com.sysdos.kasirpintar.api.ApiClient
import com.sysdos.kasirpintar.api.ProductResponse
import com.sysdos.kasirpintar.data.dao.*
import com.sysdos.kasirpintar.data.model.*
import com.sysdos.kasirpintar.utils.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class ProductRepository(
    private val context: Context,
    private val productDao: ProductDao,
    private val shiftDao: ShiftDao,
    private val stockLogDao: StockLogDao,
    private val storeConfigDao: StoreConfigDao
) {
    // --- STORE CONFIG ---
    val storeConfig = storeConfigDao.getStoreConfig()

    suspend fun saveStoreConfig(config: StoreConfig) {
        storeConfigDao.saveConfig(config)
    }

    suspend fun getStoreConfigDirect(): StoreConfig? {
        return storeConfigDao.getStoreConfigOneShot()
    }

    // =================================================================
    // 📦 1. PRODUK & VARIAN
    // =================================================================

    val allProducts: Flow<List<Product>> = productDao.getAllProducts()

    // Insert Standar
    suspend fun insert(product: Product) = productDao.insertProduct(product)

    // Insert Return ID (PENTING BUAT VARIAN)
    suspend fun insertProductReturnId(product: Product): Long {
        return productDao.insertProduct(product)
    }

    // Insert Varian
    suspend fun insertVariants(variants: List<ProductVariant>) {
        productDao.insertVariants(variants)
    }

    suspend fun update(product: Product) = productDao.update(product)
    suspend fun delete(product: Product) = productDao.delete(product)

    // 🔥 FUNGSI BARU: Ambil Varian berdasarkan ID Produk
    fun getVariantsByProductId(productId: Int): Flow<List<ProductVariant>> {
        return productDao.getVariantsByProductId(productId)
    }
    // 🔥 FUNGSI BARU: Update Varian (Hapus Lama -> Simpan Baru)
    suspend fun replaceVariants(productId: Int, variants: List<ProductVariant>) {
        productDao.deleteVariantsByProductId(productId) // 1. Hapus varian lama
        productDao.insertVariants(variants)             // 2. Masukkan varian baru dari layar
    }

    // =================================================================
    // 📂 2. DATA PENDUKUNG (User, Kategori, Supplier)
    // =================================================================

    val allCategories: Flow<List<Category>> = productDao.getAllCategories()
    suspend fun insertCategory(category: Category) = productDao.insertCategory(category)
    suspend fun deleteCategory(category: Category) = productDao.deleteCategory(category)

    val allSuppliers: Flow<List<Supplier>> = productDao.getAllSuppliers()
    suspend fun insertSupplier(supplier: Supplier) = productDao.insertSupplier(supplier)
    suspend fun deleteSupplier(supplier: Supplier) = productDao.deleteSupplier(supplier)
    suspend fun updateSupplier(supplier: Supplier) = productDao.updateSupplier(supplier)

    val allUsers: Flow<List<User>> = productDao.getAllUsers()
    suspend fun insertUser(user: User) = productDao.insertUser(user)
    suspend fun updateUser(user: User) = productDao.updateUser(user)
    suspend fun deleteUser(user: User) = productDao.deleteUser(user)
    suspend fun login(u: String, p: String): User? = productDao.login(u, p)
    suspend fun getUserByEmail(email: String): User? = productDao.getUserByEmail(email)

    // =================================================================
    // 🛒 3. TRANSAKSI
    // =================================================================
    fun getTransactionsByUser(userId: Int): Flow<List<Transaction>> {
        return productDao.getAllTransactions(userId)
    }
    suspend fun insertTransaction(transaction: Transaction): Long {
        return productDao.insertTransaction(transaction)
    }

    // =================================================================
    // 📅 4. SHIFT (LOG KASIR)
    // =================================================================
    fun getShiftLogsByUser(userId: Int): Flow<List<ShiftLog>> {
        return shiftDao.getAllLogs(userId)
    }
    suspend fun insertShiftLog(log: ShiftLog) {
        shiftDao.insertLog(log)
    }

    // =================================================================
    // 📦 5. STOCK & PURCHASE (SUDAH DIPERBAIKI AGAR TIDAK DOUBLE)
    // =================================================================

    val purchaseHistory: Flow<List<StockLog>> = stockLogDao.getPurchaseHistoryGroups()
    val allStockLogs: Flow<List<StockLog>> = stockLogDao.getAllStockLogs()

    // 🔥 PERBAIKAN DISINI: Hapus logika update produk, cukup simpan log saja.
    // Karena PurchaseActivity sudah melakukan update stok ke produk.
    suspend fun recordPurchase(log: StockLog) {
        stockLogDao.insertLog(log)
    }

    suspend fun getPurchaseDetails(pId: Long): List<StockLog> {
        return stockLogDao.getPurchaseDetails(pId)
    }

    suspend fun getProductById(id: Int): Product? = productDao.getProductById(id)
    suspend fun getProductByBarcode(barcode: String): Product? = productDao.getProductByBarcode(barcode)

    // =================================================================
    // 🌐 6. SERVER SYNC & UPLOAD (LENGKAP SEPERTI SEMULA)
    // =================================================================

    suspend fun uploadCsvFile(file: File) {
        withContext(Dispatchers.IO) {
            try {
                Log.d("SysdosPOS", "Mulai Upload CSV: ${file.name}")
                val requestFile = file.asRequestBody("text/csv".toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
                val api = ApiClient.getLocalClient(context)
                val response = api.importCsv(body).execute()
                if (response.isSuccessful) refreshProductsFromApi()
            } catch (e: Exception) {
                Log.e("SysdosPOS", "Error Upload: ${e.message}")
            }
        }
    }

    suspend fun refreshProductsFromApi() {
        withContext(Dispatchers.IO) {
            try {
                val prefs = context.getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
                val email = prefs.getString("username", "") ?: ""
                val currentUser = productDao.getUserByEmail(email) ?: return@withContext

                val api = ApiClient.getLocalClient(context)
                val session = SessionManager(context)
                val dynamicBaseUrl = session.getServerUrl()

                val responseProd = api.getProducts(currentUser.id).execute()
                if (responseProd.isSuccessful) {
                    val serverProducts = responseProd.body()
                    if (serverProducts != null) {
                        productDao.deleteAllProducts()
                        serverProducts.forEach { apiItem ->
                            val fullImagePath = if (!apiItem.image_path.isNullOrEmpty()) dynamicBaseUrl + apiItem.image_path else null
                            val serverCategory = if (apiItem.category.isNullOrEmpty()) "Umum" else apiItem.category
                            val newProd = Product(
                                id = apiItem.id, name = apiItem.name, price = apiItem.price.toDouble(),
                                costPrice = apiItem.cost_price.toDouble(), stock = apiItem.stock,
                                category = serverCategory, barcode = "", imagePath = fullImagePath
                            )
                            productDao.insertProduct(newProd)
                        }
                    }
                }

                // Sync Kategori
                val responseCat = api.getCategories(currentUser.id).execute()
                if (responseCat.isSuccessful) {
                    responseCat.body()?.forEach {
                        productDao.insertCategory(Category(id = it.id, name = it.name))
                    }
                }
            } catch (e: Exception) { Log.e("SysdosRepo", "Error Sync: ${e.message}") }
        }
    }

    suspend fun uploadTransactionToServer(trx: Transaction, cartItems: List<Product>) {
        withContext(Dispatchers.IO) {
            try {
                val prefs = context.getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
                val email = prefs.getString("username", "") ?: ""
                val currentUser = productDao.getUserByEmail(email) ?: return@withContext

                val itemList = cartItems.map {
                    com.sysdos.kasirpintar.api.TransactionItemRequest(it.id, it.stock)
                }
                val requestData = com.sysdos.kasirpintar.api.TransactionUploadRequest(
                    trx.totalAmount, trx.profit, trx.itemsSummary, currentUser.id, itemList
                )
                ApiClient.getLocalClient(context).uploadTransaction(requestData).execute()
            } catch (e: Exception) { Log.e("SysdosPOS", "Gagal Upload: ${e.message}") }
        }
    }

    suspend fun updateProductToServer(product: Product) {
        withContext(Dispatchers.IO) {
            try {
                val dataKirim = ProductResponse(
                    id = product.id, name = product.name, category = product.category,
                    price = product.price.toInt(), cost_price = product.costPrice.toInt(),
                    stock = product.stock, image_path = null
                )
                ApiClient.getLocalClient(context).updateProduct(dataKirim).execute()
            } catch (e: Exception) {}
        }
    }

    suspend fun syncUserToServer(user: User) {
        withContext(Dispatchers.IO) {
            try {
                ApiClient.getLocalClient(context).registerLocalUser(user).execute()
            } catch (e: Exception) {}
        }
    }

    suspend fun clearAllData() {
        AppDatabase.getDatabase(context).clearAllTables()
    }
}