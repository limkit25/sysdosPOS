package com.sysdos.kasirpintar.data

import android.content.Context
import android.util.Log
import com.sysdos.kasirpintar.data.dao.ProductDao
import com.sysdos.kasirpintar.data.dao.ShiftDao
import com.sysdos.kasirpintar.data.dao.StockLogDao
import com.sysdos.kasirpintar.data.model.*

// --- PENTING: Import Fitur Koneksi & Session ---
import com.sysdos.kasirpintar.api.ApiClient
import com.sysdos.kasirpintar.api.ProductResponse
import com.sysdos.kasirpintar.utils.SessionManager
// -----------------------------------------------

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ProductRepository(
    private val context: Context, // <--- TAMBAHAN WAJIB (Biar bisa baca IP)
    private val productDao: ProductDao,
    private val shiftDao: ShiftDao,
    private val stockLogDao: StockLogDao
) {

    // =================================================================
    // 📦 1. PRODUK (LOKAL & SERVER)
    // =================================================================

    val allProducts: Flow<List<Product>> = productDao.getAllProducts()

    suspend fun insert(product: Product) = productDao.insert(product)
    suspend fun update(product: Product) = productDao.update(product)
    suspend fun delete(product: Product) = productDao.delete(product)

    /**
     * 🔥 FUNGSI SYNC BARU: RESET TOTAL & PAKSA ID SAMA DENGAN SERVER
     */
    suspend fun refreshProductsFromApi() {
        withContext(Dispatchers.IO) {
            try {
                Log.d("SysdosRepo", "🔄 Mulai Sinkronisasi Total...")

                val api = ApiClient.getInstance(context)
                val session = SessionManager(context)
                val dynamicBaseUrl = session.getServerUrl()

                // 1. AMBIL PRODUK DARI SERVER
                val responseProd = api.getProducts().execute()

                if (responseProd.isSuccessful) {
                    val serverProducts = responseProd.body()
                    if (serverProducts != null) {

                        // 🔥 LANGKAH KRUSIAL: HAPUS DATA LAMA BIAR BERSIH
                        // Ini mencegah duplikat barang (ID 1 vs ID 5)
                        productDao.deleteAllProducts()

                        serverProducts.forEach { apiItem ->

                            val fullImagePath = if (!apiItem.image_path.isNullOrEmpty()) {
                                dynamicBaseUrl + apiItem.image_path
                            } else {
                                null
                            }

                            val serverCategory = if (apiItem.category.isNullOrEmpty()) "Umum" else apiItem.category

                            // 🔥 INSERT DENGAN MEMAKSA ID DARI SERVER (apiItem.id)
                            val newProd = Product(
                                id = apiItem.id, // <--- KUNCINYA DISINI! JANGAN BIARKAN AUTO-GENERATE
                                name = apiItem.name,
                                price = apiItem.price.toDouble(),
                                costPrice = apiItem.cost_price.toDouble(),
                                stock = apiItem.stock,
                                category = serverCategory,
                                barcode = "", // Atau apiItem.barcode jika ada
                                imagePath = fullImagePath
                            )
                            productDao.insert(newProd)
                        }
                        Log.d("SysdosRepo", "✅ Sukses Refresh: ID HP sekarang sama dengan Server!")
                    }
                }

                // 2. AMBIL KATEGORI (SAMA SEPERTI SEBELUMNYA)
                val responseCat = api.getCategories().execute()
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
                Log.e("SysdosRepo", "Error Koneksi: ${e.message}")
            }
        }
    }

    // =================================================================
    // 📂 2. KATEGORI, SUPPLIER, USER, DLL
    // =================================================================
    val allCategories: Flow<List<Category>> = productDao.getAllCategories()
    suspend fun insertCategory(category: Category) = productDao.insertCategory(category)
    suspend fun deleteCategory(category: Category) = productDao.deleteCategory(category)

    val allSuppliers: Flow<List<Supplier>> = productDao.getAllSuppliers()
    suspend fun insertSupplier(supplier: Supplier) = productDao.insertSupplier(supplier)
    suspend fun deleteSupplier(supplier: Supplier) = productDao.deleteSupplier(supplier)
    suspend fun updateSupplier(supplier: Supplier) = productDao.updateSupplier(supplier)

    val allTransactions: Flow<List<Transaction>> = productDao.getAllTransactions()
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

    val allShiftLogs: Flow<List<ShiftLog>> = shiftDao.getAllLogs()
    suspend fun insertShiftLog(log: ShiftLog) { shiftDao.insertLog(log) }

    // 🔥 TAMBAHKAN FUNGSI INI 🔥
    suspend fun getProductById(id: Int): Product? {
        return productDao.getProductById(id)
    }
    // 🔥 TAMBAHKAN INI 🔥
    suspend fun getProductByBarcode(barcode: String): Product? {
        return productDao.getProductByBarcode(barcode)
    }

    // =================================================================
    // 🚀 FITUR UPLOAD TRANSAKSI KE SERVER
    // =================================================================
    suspend fun uploadTransactionToServer(trx: Transaction, cartItems: List<Product>) {
        withContext(Dispatchers.IO) {
            try {
                Log.d("SysdosPOS", "Mengupload transaksi lengkap...")

                val itemList = cartItems.map { product ->
                    com.sysdos.kasirpintar.api.TransactionItemRequest(
                        product_id = product.id,
                        quantity = product.stock
                    )
                }

                val requestData = com.sysdos.kasirpintar.api.TransactionUploadRequest(
                    total_amount = trx.totalAmount,
                    items_summary = trx.itemsSummary,
                    items = itemList
                )

                // 🔥 PAKE INSTANCE DINAMIS
                val api = ApiClient.getInstance(context)
                val response = api.uploadTransaction(requestData).execute()

                if (response.isSuccessful) {
                    Log.d("SysdosPOS", "✅ Laporan Terkirim & Stok Server Terpotong!")
                } else {
                    Log.e("SysdosPOS", "❌ Gagal Upload: ${response.code()}")
                }

            } catch (e: Exception) {
                Log.e("SysdosPOS", "⚠️ Gagal Upload: ${e.message}")
            }
        }
    }

    // =================================================================
    // 🔄 REVERSE SYNC: UPDATE DARI HP KE SERVER
    // =================================================================
    suspend fun updateProductToServer(product: Product) {
        withContext(Dispatchers.IO) {
            try {
                val dataKirim = ProductResponse(
                    id = product.id,
                    name = product.name,
                    category = product.category,
                    price = product.price.toInt(),
                    cost_price = product.costPrice.toInt(),
                    stock = product.stock,
                    image_path = null
                )

                // 🔥 PAKE INSTANCE DINAMIS
                val api = ApiClient.getInstance(context)
                val response = api.updateProduct(dataKirim).execute()

                if (response.isSuccessful) {
                    Log.d("SysdosPOS", "✅ Sukses Update Data ke Server!")
                } else {
                    Log.e("SysdosPOS", "❌ Gagal Update: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("SysdosPOS", "Error Update: ${e.message}")
            }
        }
    }
}