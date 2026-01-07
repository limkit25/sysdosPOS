package com.sysdos.kasirpintar.data

import android.util.Log
import com.sysdos.kasirpintar.data.dao.ProductDao
import com.sysdos.kasirpintar.data.dao.ShiftDao
import com.sysdos.kasirpintar.data.dao.StockLogDao
import com.sysdos.kasirpintar.data.model.*

// --- PENTING: Import untuk Fitur Koneksi Server ---
// Pastikan package ini sesuai dengan lokasi file ApiClient.kt Anda
import com.sysdos.kasirpintar.api.ApiClient
import com.sysdos.kasirpintar.api.ProductResponse
// -------------------------------------------------

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ProductRepository(
    private val productDao: ProductDao,
    private val shiftDao: ShiftDao,
    private val stockLogDao: StockLogDao
) {

    // =================================================================
    // 📦 1. PRODUK (LOKAL & SERVER)
    // =================================================================

    // Live Data / Flow untuk ditampilkan di RecyclerView
    val allProducts: Flow<List<Product>> = productDao.getAllProducts()

    suspend fun insert(product: Product) = productDao.insert(product)
    suspend fun update(product: Product) = productDao.update(product)
    suspend fun delete(product: Product) = productDao.delete(product)

    /**
     * 🔥 FUNGSI BARU: Tarik data barang dari Server Go (Laptop)
     * Dipanggil lewat ViewModel saat tombol sync ditekan atau saat aplikasi mulai.
     */
    // ... di dalam class ProductRepository ...

    suspend fun refreshProductsFromApi() {
        withContext(Dispatchers.IO) {
            try {
                Log.d("SysdosRepo", "Mulai mengambil data dari server...")
                val response = ApiClient.instance.getProducts().execute()

                if (response.isSuccessful) {
                    val serverProducts = response.body()

                    if (serverProducts != null) {
                        serverProducts.forEach { apiItem ->

                            // 1. CEK DULU: Apakah barang ini sudah ada di HP?
                            val existingProduct = productDao.getProductByName(apiItem.name)

                            if (existingProduct != null) {
                                // === SKENARIO A: BARANG SUDAH ADA (UPDATE) ===
                                // Kita update harga & stoknya mengikuti server, TAPI ID TETAP SAMA
                                val updatedProduct = existingProduct.copy(
                                    price = apiItem.price.toDouble(),
                                    stock = apiItem.stock // Atau apiItem.stock + existingProduct.stock (tergantung kebijakan)
                                )
                                productDao.update(updatedProduct)
                                Log.d("SysdosRepo", "Update barang: ${apiItem.name}")

                            } else {
                                // === SKENARIO B: BARANG BELUM ADA (INSERT BARU) ===
                                val newProduct = Product(
                                    name = apiItem.name,
                                    price = apiItem.price.toDouble(),
                                    stock = apiItem.stock,
                                    category = "Umum",
                                    barcode = "",
                                    imagePath = ""
                                )
                                productDao.insert(newProduct)
                                Log.d("SysdosRepo", "Insert barang baru: ${apiItem.name}")
                            }
                        }
                        Log.d("SysdosRepo", "Sinkronisasi Selesai!")
                    }
                } else {
                    Log.e("SysdosRepo", "Gagal ambil data. Kode: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("SysdosRepo", "Error Koneksi: ${e.message}")
            }
        }
    }

    // =================================================================
    // 📂 2. KATEGORI
    // =================================================================
    // Sesuaikan tipe List<Category> atau List<String> dengan DAO Anda
    val allCategories: Flow<List<Category>> = productDao.getAllCategories()

    suspend fun insertCategory(category: Category) = productDao.insertCategory(category)
    suspend fun deleteCategory(category: Category) = productDao.deleteCategory(category)

    // =================================================================
    // 🚚 3. SUPPLIER
    // =================================================================
    val allSuppliers: Flow<List<Supplier>> = productDao.getAllSuppliers()

    suspend fun insertSupplier(supplier: Supplier) = productDao.insertSupplier(supplier)
    suspend fun deleteSupplier(supplier: Supplier) = productDao.deleteSupplier(supplier)
    suspend fun updateSupplier(supplier: Supplier) = productDao.updateSupplier(supplier)

    // =================================================================
    // 🧾 4. TRANSAKSI
    // =================================================================
    val allTransactions: Flow<List<Transaction>> = productDao.getAllTransactions()

    suspend fun insertTransaction(transaction: Transaction): Long = productDao.insertTransaction(transaction)

    // =================================================================
    // 👤 5. USER / KASIR (LOGIN)
    // =================================================================
    val allUsers: Flow<List<User>> = productDao.getAllUsers()

    suspend fun insertUser(user: User) = productDao.insertUser(user)
    suspend fun updateUser(user: User) = productDao.updateUser(user)
    suspend fun deleteUser(user: User) = productDao.deleteUser(user)

    // Fungsi Login Database Lokal
    suspend fun login(u: String, p: String): User? = productDao.login(u, p)

    // =================================================================
    // 📊 6. LOG STOK & PEMBELIAN
    // =================================================================
    val allStockLogs: Flow<List<StockLog>> = productDao.getAllStockLogs()

    // History pembelian (Grouping) dari DAO StockLog
    val purchaseHistory: Flow<List<StockLog>> = stockLogDao.getPurchaseHistoryGroups()

    suspend fun recordPurchase(log: StockLog) {
        stockLogDao.insertLog(log)
    }

    suspend fun getPurchaseDetails(pId: Long): List<StockLog> {
        return stockLogDao.getPurchaseDetails(pId)
    }

    // =================================================================
    // ⏱️ 7. SHIFT
    // =================================================================
    val allShiftLogs: Flow<List<ShiftLog>> = shiftDao.getAllLogs()

    suspend fun insertShiftLog(log: ShiftLog) {
        shiftDao.insertLog(log)
    }
    // =================================================================
    // 🚀 FITUR UPLOAD TRANSAKSI KE SERVER
    // =================================================================
    suspend fun uploadTransactionToServer(trx: Transaction, cartItems: List<Product>) {
        withContext(Dispatchers.IO) {
            try {
                Log.d("SysdosPOS", "Mengupload transaksi lengkap...")

                // 1. Ubah List Product Menjadi List Request Server
                val itemList = cartItems.map { product ->
                    com.sysdos.kasirpintar.api.TransactionItemRequest(
                        product_id = product.id, // ID ini akan dipakai Server untuk potong stok
                        quantity = product.stock     // Perhatikan: di Keranjang, field 'stock' kita pakai utk simpan Qty Belanja
                    )
                }

                // 2. Siapkan Data Paket Lengkap
                val requestData = com.sysdos.kasirpintar.api.TransactionUploadRequest(
                    total_amount = trx.totalAmount,
                    items_summary = trx.itemsSummary,
                    items = itemList // Data untuk potong stok
                )

                // 3. Kirim!
                val response = ApiClient.instance.uploadTransaction(requestData).execute()

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
}