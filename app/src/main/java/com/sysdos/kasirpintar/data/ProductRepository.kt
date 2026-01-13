package com.sysdos.kasirpintar.data

import android.content.Context
import android.util.Log
import com.sysdos.kasirpintar.data.dao.ProductDao
import com.sysdos.kasirpintar.data.dao.ShiftDao
import com.sysdos.kasirpintar.data.dao.StockLogDao
import com.sysdos.kasirpintar.data.model.*

import com.sysdos.kasirpintar.api.ApiClient // <--- SUDAH HYBRID
import com.sysdos.kasirpintar.api.ProductResponse
import com.sysdos.kasirpintar.data.dao.StoreConfigDao
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
    // 📦 1. PRODUK (PAKAI JALUR LOKAL / TOKO)
    // =================================================================

    val allProducts: Flow<List<Product>> = productDao.getAllProducts()

    suspend fun insert(product: Product) = productDao.insert(product)
    suspend fun update(product: Product) = productDao.update(product)
    suspend fun delete(product: Product) = productDao.delete(product)

    suspend fun uploadCsvFile(file: File) {
        withContext(Dispatchers.IO) {
            try {
                Log.d("SysdosPOS", "Mulai Upload CSV: ${file.name}")
                val requestFile = file.asRequestBody("text/csv".toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData("file", file.name, requestFile)

                // 🔥 JALUR TOKO (LOCAL)
                val api = ApiClient.getLocalClient(context)
                val response = api.importCsv(body).execute()

                if (response.isSuccessful) {
                    Log.d("SysdosPOS", "✅ Sukses Import CSV!")
                    refreshProductsFromApi()
                } else {
                    Log.e("SysdosPOS", "❌ Gagal Import: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("SysdosPOS", "Error Upload: ${e.message}")
            }
        }
    }

    suspend fun refreshProductsFromApi() {
        withContext(Dispatchers.IO) {
            try {
                Log.d("SysdosRepo", "🔄 Sinkronisasi dari Server Toko...")

                // 1. 🔥 AMBIL USER ID DULU (SIAPA YANG LOGIN?)
                val prefs = context.getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
                val email = prefs.getString("username", "") ?: ""
                val currentUser = productDao.getUserByEmail(email)

                if (currentUser == null) {
                    Log.e("SysdosRepo", "❌ User belum login, tidak bisa sync produk.")
                    return@withContext
                }

                // 2. Setup API
                val api = ApiClient.getLocalClient(context)
                val session = SessionManager(context)
                val dynamicBaseUrl = session.getServerUrl()

                // 3. 🔥 PANGGIL API DENGAN USER ID
                val responseProd = api.getProducts(currentUser.id).execute()

                if (responseProd.isSuccessful) {
                    val serverProducts = responseProd.body()
                    if (serverProducts != null) {
                        productDao.deleteAllProducts()

                        serverProducts.forEach { apiItem ->
                            val fullImagePath = if (!apiItem.image_path.isNullOrEmpty()) {
                                dynamicBaseUrl + apiItem.image_path
                            } else {
                                null
                            }
                            val serverCategory = if (apiItem.category.isNullOrEmpty()) "Umum" else apiItem.category

                            val newProd = Product(
                                id = apiItem.id,
                                name = apiItem.name,
                                price = apiItem.price.toDouble(),
                                costPrice = apiItem.cost_price.toDouble(),
                                stock = apiItem.stock,
                                category = serverCategory,
                                barcode = "",
                                imagePath = fullImagePath
                            )
                            productDao.insert(newProd)
                        }
                        Log.d("SysdosRepo", "✅ Sukses Refresh Produk User ${currentUser.username}!")
                    }
                }

                // 4. 🔥 PANGGIL KATEGORI DENGAN USER ID
                val responseCat = api.getCategories(currentUser.id).execute()
                if (responseCat.isSuccessful) {
                    val serverCats = responseCat.body()
                    if (serverCats != null) {
                        productDao.deleteAllCategories()
                        serverCats.forEach { catRes ->
                            val newCat = Category(id = catRes.id, name = catRes.name)
                            productDao.insertCategory(newCat)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SysdosRepo", "Error Koneksi Toko: ${e.message}")
            }
        }
    }

    // =================================================================
    // 📂 2. KATEGORI & LAIN-LAIN
    // =================================================================
    val allCategories: Flow<List<Category>> = productDao.getAllCategories()
    suspend fun insertCategory(category: Category) = productDao.insertCategory(category)
    suspend fun deleteCategory(category: Category) = productDao.deleteCategory(category)

    val allSuppliers: Flow<List<Supplier>> = productDao.getAllSuppliers()
    suspend fun insertSupplier(supplier: Supplier) = productDao.insertSupplier(supplier)
    suspend fun deleteSupplier(supplier: Supplier) = productDao.deleteSupplier(supplier)
    suspend fun updateSupplier(supplier: Supplier) = productDao.updateSupplier(supplier)

    // ✅ GANTI DENGAN FUNGSI INI:
    fun getTransactionsByUser(userId: Int): Flow<List<Transaction>> {
        return productDao.getAllTransactions(userId)
    }
    suspend fun insertTransaction(transaction: Transaction): Long = productDao.insertTransaction(transaction)

    val allUsers: Flow<List<User>> = productDao.getAllUsers()
    suspend fun insertUser(user: User) = productDao.insertUser(user)
    suspend fun updateUser(user: User) = productDao.updateUser(user)
    suspend fun deleteUser(user: User) = productDao.deleteUser(user)
    suspend fun login(u: String, p: String): User? = productDao.login(u, p)

    val allStockLogs: Flow<List<StockLog>> = productDao.getAllStockLogs()
    val purchaseHistory: Flow<List<StockLog>> = stockLogDao.getPurchaseHistoryGroups()
    suspend fun recordPurchase(log: StockLog) { stockLogDao.insertLog(log) }
    suspend fun getPurchaseDetails(pId: Long): List<StockLog> { return stockLogDao.getPurchaseDetails(pId) }

    fun getShiftLogsByUser(userId: Int): Flow<List<ShiftLog>> {
        return shiftDao.getAllLogs(userId)
    }
    suspend fun insertShiftLog(log: ShiftLog) { shiftDao.insertLog(log) }

    suspend fun getProductById(id: Int): Product? = productDao.getProductById(id)
    suspend fun getProductByBarcode(barcode: String): Product? = productDao.getProductByBarcode(barcode)
    suspend fun getUserByEmail(email: String): User? {
        return productDao.getUserByEmail(email)
    }
    // =================================================================
    // 🚀 FITUR UPLOAD TRANSAKSI KE SERVER (JALUR TOKO / LOKAL)
    // =================================================================
    suspend fun uploadTransactionToServer(trx: Transaction, cartItems: List<Product>) {
        withContext(Dispatchers.IO) {
            try {
                // 1. 🔥 AMBIL USER ID
                val prefs = context.getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
                val email = prefs.getString("username", "") ?: ""
                val currentUser = productDao.getUserByEmail(email)

                if (currentUser == null) return@withContext

                Log.d("SysdosPOS", "Mengupload transaksi User ${currentUser.id}...")

                val itemList = cartItems.map { product ->
                    com.sysdos.kasirpintar.api.TransactionItemRequest(
                        product_id = product.id,
                        quantity = product.stock
                    )
                }

                // 2. 🔥 MASUKKAN user_id KE REQUEST
                val requestData = com.sysdos.kasirpintar.api.TransactionUploadRequest(
                    total_amount = trx.totalAmount,
                    profit = trx.profit,
                    items_summary = trx.itemsSummary,
                    user_id = currentUser.id, // <--- TAMBAHAN WAJIB
                    items = itemList
                )

                val api = ApiClient.getLocalClient(context)
                val response = api.uploadTransaction(requestData).execute()

                if (response.isSuccessful) {
                    Log.d("SysdosPOS", "✅ Laporan Terkirim ke Server Toko!")
                } else {
                    Log.e("SysdosPOS", "❌ Gagal Upload: ${response.code()}")
                }

            } catch (e: Exception) {
                Log.e("SysdosPOS", "⚠️ Gagal Upload: ${e.message}")
            }
        }
    }

    // =================================================================
    // 🔄 REVERSE SYNC (JALUR TOKO / LOKAL)
    // =================================================================
    suspend fun updateProductToServer(product: Product) {
        withContext(Dispatchers.IO) {
            try {
                // 1. 🔥 AMBIL USER ID LAGI
                val prefs = context.getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
                val email = prefs.getString("username", "") ?: ""
                val currentUser = productDao.getUserByEmail(email)

                // Default ke 0 jika user tidak ketemu (tapi harusnya ketemu)
                val userId = currentUser?.id ?: 0

                // Note: Struktur ProductResponse di ApiClient sebaiknya ditambah field user_id juga
                // Tapi jika belum, backend Go biasanya membaca ID dari body atau session.
                // Untuk amannya, pastikan Backend Go bagian PUT menerima user_id atau kita kirim lewat query/body.

                val dataKirim = ProductResponse(
                    id = product.id,
                    name = product.name,
                    category = product.category,
                    price = product.price.toInt(),
                    cost_price = product.costPrice.toInt(),
                    stock = product.stock,
                    image_path = null
                    // user_id = userId // Idealnya disini jika ProductResponse diupdate
                )

                val api = ApiClient.getLocalClient(context)
                val response = api.updateProduct(dataKirim).execute()

                if (response.isSuccessful) {
                    Log.d("SysdosPOS", "✅ Sukses Update Data ke Server Toko!")
                } else {
                    Log.e("SysdosPOS", "❌ Gagal Update: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("SysdosPOS", "Error Update: ${e.message}")
            }
        }
    }

    // =================================================================
    // 🔥 SYNC USER KE SERVER PUSAT (JALUR WEB / CLOUD) 🔥
    // =================================================================
    suspend fun syncUserToServer(user: User) {
        withContext(Dispatchers.IO) {
            try {
                // 🔥 JALUR CLOUD (WEB) - Inilah bedanya!
                val api = ApiClient.webClient

                val response = api.registerUser(user).execute()

                if (response.isSuccessful) {
                    Log.d("SysdosPOS", "✅ User ${user.username} sync ke WEB PUSAT!")
                } else {
                    Log.e("SysdosPOS", "❌ Gagal Sync User ke WEB: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("SysdosPOS", "⚠️ Gagal Konek Web Pusat: ${e.message}")
            }
        }
    }
    suspend fun clearAllData() {
        // Menghapus semua isi tabel di database Room
        AppDatabase.getDatabase(context).clearAllTables()
    }
}