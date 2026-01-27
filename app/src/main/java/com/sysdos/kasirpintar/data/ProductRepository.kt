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
import com.sysdos.kasirpintar.data.model.ShiftLog
import kotlinx.coroutines.flow.first

class ProductRepository(
    private val context: Context,
    private val productDao: ProductDao,
    private val shiftDao: ShiftDao,
    private val stockLogDao: StockLogDao,
    private val storeConfigDao: StoreConfigDao,
    private val transactionDao: TransactionDao
) {
    // --- STORE CONFIG ---
    val storeConfig = storeConfigDao.getStoreConfig()
    suspend fun saveStoreConfig(config: StoreConfig) = storeConfigDao.saveConfig(config)
    suspend fun getStoreConfigDirect(): StoreConfig? = storeConfigDao.getStoreConfigOneShot()

    // =================================================================
    // 📦 1. PRODUK & VARIAN
    // =================================================================

    val allProducts: Flow<List<Product>> = productDao.getAllProducts()

    suspend fun insert(product: Product) = productDao.insertProduct(product)
    suspend fun insertProductReturnId(product: Product): Long = productDao.insertProduct(product)
    suspend fun insertVariants(variants: List<ProductVariant>) = productDao.insertVariants(variants)
    suspend fun update(product: Product) = productDao.update(product)
    suspend fun delete(product: Product) = productDao.delete(product)

    fun getVariantsByProductId(productId: Int): Flow<List<ProductVariant>> = productDao.getVariantsByProductId(productId)

    suspend fun replaceVariants(productId: Int, variants: List<ProductVariant>) {
        productDao.deleteVariantsByProductId(productId)
        productDao.insertVariants(variants)
    }

    suspend fun decreaseStock(productId: Int, quantity: Int) = productDao.decreaseStock(productId, quantity)

    // 🔥 TAMBAHAN UNTUK VOID: AMBIL PRODUK BY NAME 🔥
    suspend fun getProductByName(name: String): Product? {
        return productDao.getProductByName(name)
    }

    suspend fun getProductById(id: Int): Product? = productDao.getProductById(id)
    suspend fun getProductByBarcode(barcode: String): Product? = productDao.getProductByBarcode(barcode)

    // =================================================================
    // 🛒 TRANSAKSI
    // =================================================================

    // 🔥🔥 WAJIB TAMBAH BARIS INI (SUPAYA VIEWMODEL TIDAK ERROR) 🔥🔥
    val allTransactions: Flow<List<Transaction>> = transactionDao.getAllTransactionsGlobal()

    // Kode lama di bawahnya biarkan saja:
    fun getTransactionsByUser(userId: Int): Flow<List<Transaction>> = transactionDao.getAllTransactions(userId)
    suspend fun insertTransaction(transaction: Transaction): Long = transactionDao.insertTransaction(transaction)
    suspend fun getTransactionsByDateRange(uid: Int, start: Long, end: Long): List<Transaction> = transactionDao.getTransactionsByDateRange(uid, start, end)

    // 🔥 TAMBAHAN UNTUK VOID: HAPUS TRANSAKSI 🔥
    suspend fun deleteTransaction(transaction: Transaction) {
        transactionDao.delete(transaction)
    }

    // =================================================================
    // DATA LAINNYA (Biarkan Tetap Sama)
    // =================================================================
    val allCategories = productDao.getAllCategories()
    suspend fun insertCategory(c: Category) = productDao.insertCategory(c)
    suspend fun deleteCategory(c: Category) = productDao.deleteCategory(c)

    val allSuppliers = productDao.getAllSuppliers()
    suspend fun insertSupplier(s: Supplier) = productDao.insertSupplier(s)
    suspend fun deleteSupplier(s: Supplier) = productDao.deleteSupplier(s)
    suspend fun updateSupplier(s: Supplier) = productDao.updateSupplier(s)

    val allUsers = productDao.getAllUsers()
    suspend fun insertUser(u: User) = productDao.insertUser(u)
    suspend fun updateUser(u: User) = productDao.updateUser(u)
    suspend fun deleteUser(u: User) = productDao.deleteUser(u)
    suspend fun login(u: String, p: String) = productDao.login(u, p)
    suspend fun getUserByEmail(email: String) = productDao.getUserByEmail(email)

    fun getShiftLogsByUser(userId: Int) = shiftDao.getAllLogs(userId)
    suspend fun insertShiftLog(log: ShiftLog) = shiftDao.insertLog(log)

    val purchaseHistory = stockLogDao.getPurchaseHistoryGroups()
    val allStockLogs = stockLogDao.getAllStockLogs()
    suspend fun recordPurchase(log: StockLog) = stockLogDao.insertLog(log)
    suspend fun getPurchaseDetails(pId: Long) = stockLogDao.getPurchaseDetails(pId)

    // SERVER SYNC
    suspend fun uploadCsvFile(file: File) {
        withContext(Dispatchers.IO) {
            try {
                val requestFile = file.asRequestBody("text/csv".toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
                val api = ApiClient.getLocalClient(context)
                val response = api.importCsv(body).execute()
                if (response.isSuccessful) refreshProductsFromApi()
            } catch (e: Exception) { Log.e("SysdosPOS", "Error Upload: ${e.message}") }
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

                // 1. Ambil Produk dari Server
                val responseProd = api.getProducts(currentUser.id).execute()

                if (responseProd.isSuccessful) {
                    val serverProducts = responseProd.body()
                    if (serverProducts != null) {
                        // Hapus data lama di HP biar bersih & sama dengan server
                        productDao.deleteAllProducts()

                        serverProducts.forEach { apiItem ->
                            // Logic URL Gambar
                            val fullImagePath = if (!apiItem.image_path.isNullOrEmpty()) dynamicBaseUrl + apiItem.image_path else null
                            val serverCategory = if (apiItem.category.isNullOrEmpty()) "Umum" else apiItem.category

                            // 🔥 PERBAIKAN FINAL (androidId DIHAPUS) 🔥
                            val newProd = Product(
                                id = apiItem.id, // Pakai ID dari server sebagai ID lokal
                                name = apiItem.name,

                                // Konversi Double (Server) ke Double (Lokal)
                                price = apiItem.price.toDouble(),
                                costPrice = apiItem.cost_price.toDouble(),

                                stock = apiItem.stock,
                                category = serverCategory,
                                barcode = "", // Default kosong
                                imagePath = fullImagePath
                                // ❌ androidId = ... (DIBUANG KARENA TIDAK ADA DI MODEL)
                            )
                            productDao.insertProduct(newProd)
                        }
                    }
                }

                // 2. Ambil Kategori dari Server
                val responseCat = api.getCategories(currentUser.id).execute()
                if (responseCat.isSuccessful) {
                    // Opsional: Hapus kategori lama jika perlu
                    // productDao.deleteAllCategories()
                    responseCat.body()?.forEach {
                        productDao.insertCategory(Category(id = it.id, name = it.name))
                    }
                }
                val responseUser = api.getAllUsers().execute()
                if (responseUser.isSuccessful && responseUser.body() != null) {
                    val serverUsers = responseUser.body()!!
                    serverUsers.forEach { sUser ->
                        // Cek apakah user sudah ada di HP?
                        val localUser = productDao.getUserByEmail(sUser.username)
                        if (localUser == null) {
                            // Jika belum ada, masukkan!
                            // Password dari server sudah ter-enkripsi (aman), jadi langsung simpan saja.
                            productDao.insertUser(sUser)
                        } else {
                            // Jika sudah ada, update datanya (termasuk jika password diganti di web)
                            // Pastikan ID-nya sama agar tidak duplikat
                            val updatedUser = sUser.copy(id = localUser.id)
                            productDao.updateUser(updatedUser)
                        }
                    }
                }
                // 4. KIRIM DATA LOG STOK
// Ambil semua log saat ini (gunakan .first() untuk ambil snapshot dari Flow)
                val logs = stockLogDao.getAllStockLogs().first()

                if (logs.isNotEmpty()) {
                    // Gunakan variable 'api' yang SUDAH ADA di atas (jangan pakai val lagi)
                    api.syncStockLogs(logs).execute()
                }
                // 5. KIRIM DATA SHIFT (Log Buka/Tutup Kasir)
// Ambil semua shift log dari DB Local
// (Pastikan DAO shift ada fungsi getAllShiftLogs(), jika belum ada, buat dulu di ShiftDao!)
// Contoh: @Query("SELECT * FROM shift_logs") fun getAllShiftLogs(): Flow<List<ShiftLog>>

                val shiftLogs = shiftDao.getAllShiftLogs().first() // Pakai .first() karena Flow
                if (shiftLogs.isNotEmpty()) {
                    api.syncShiftLogs(shiftLogs).execute()
                }

            } catch (e: Exception) {
                Log.e("SysdosRepo", "Error Sync: ${e.message}")
            }
        }
    }

    suspend fun uploadTransactionToServer(trx: Transaction, cartItems: List<Product>) {
        withContext(Dispatchers.IO) {
            try {
                val prefs = context.getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
                val email = prefs.getString("username", "admin") ?: "admin" // Default admin jika kosong

                // 🔥 PERBAIKAN: Jika user tidak ketemu di DB Lokal, pakai ID 1 (Admin) atau ID dari Transaksi
                // Jangan langsung return/berhenti!
                val currentUser = productDao.getUserByEmail(email)
                val userIdToSend = currentUser?.id ?: trx.userId

                val itemList = cartItems.map { com.sysdos.kasirpintar.api.TransactionItemRequest(it.id, it.stock) }

                val requestData = com.sysdos.kasirpintar.api.TransactionUploadRequest(
                    trx.totalAmount,
                    trx.profit,
                    trx.itemsSummary,
                    userIdToSend,
                    // 🔥 MASUKKAN ANGKA DARI TRANSAKSI DISINI:
                    trx.tax,       // Pajak
                    trx.discount,  // Diskon
                    trx.note,      // Catatan
                    itemList
                )

                Log.d("SysdosPOS", "Mencoba Upload Transaksi: $requestData") // Cek Logcat

                val api = ApiClient.getLocalClient(context)
                val response = api.uploadTransaction(requestData).execute()

                if (response.isSuccessful) {
                    Log.d("SysdosPOS", "✅ Upload Berhasil: ${response.body()?.string()}")
                } else {
                    Log.e("SysdosPOS", "❌ Gagal Server: ${response.code()} - ${response.errorBody()?.string()}")
                }

            } catch (e: Exception) {
                Log.e("SysdosPOS", "❌ Error Koneksi: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    suspend fun updateProductToServer(product: Product) {
        withContext(Dispatchers.IO) {
            try {
                // 🔥 PERBAIKAN DI SINI (Ubah ke .toDouble())
                // Karena ProductResponse (ApiService) sekarang mintanya Double
                val dataKirim = ProductResponse(
                    id = product.id,
                    name = product.name,
                    category = product.category,

                    price = product.price.toDouble(),       // Ganti .toInt() jadi .toDouble()
                    cost_price = product.costPrice.toDouble(), // Ganti .toInt() jadi .toDouble()

                    stock = product.stock,
                    image_path = null
                )
                ApiClient.getLocalClient(context).updateProduct(dataKirim).execute()
            } catch (e: Exception) {}
        }
    }

    suspend fun syncUserToServer(user: User) {
        withContext(Dispatchers.IO) { try { ApiClient.getLocalClient(context).registerLocalUser(user).execute() } catch (e: Exception) {} }
    }

    suspend fun clearAllData() = AppDatabase.getDatabase(context).clearAllTables()
}