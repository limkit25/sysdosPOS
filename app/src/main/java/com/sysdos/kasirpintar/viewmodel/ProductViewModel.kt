package com.sysdos.kasirpintar.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.*
import com.sysdos.kasirpintar.api.ApiClient
import com.sysdos.kasirpintar.api.LeadRequest
import com.sysdos.kasirpintar.data.AppDatabase
import com.sysdos.kasirpintar.data.ProductRepository
import com.sysdos.kasirpintar.data.model.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class ProductViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ProductRepository

    init {
        val productDao = AppDatabase.getDatabase(application).productDao()
        val shiftDao = AppDatabase.getDatabase(application).shiftDao()
        val stockLogDao = AppDatabase.getDatabase(application).stockLogDao()
        val storeConfigDao = AppDatabase.getDatabase(application).storeConfigDao()

        // 🔥 TAMBAHAN BARU:
        val transactionDao = AppDatabase.getDatabase(application).transactionDao()

        repository = ProductRepository(
            application,
            productDao,
            shiftDao,
            stockLogDao,
            storeConfigDao,
            transactionDao // <--- 🔥 MASUKKAN INI DI PALING BELAKANG
        )
    }

    private val _currentUserId = MutableLiveData<Int>(0)

    // --- LIVE DATA ---
    val allProducts: LiveData<List<Product>> = repository.allProducts.asLiveData()
    val allCategories: LiveData<List<Category>> = repository.allCategories.asLiveData()
    val allSuppliers: LiveData<List<Supplier>> = repository.allSuppliers.asLiveData()
    val storeConfig: LiveData<StoreConfig?> = repository.storeConfig.asLiveData()

    val allUsers: LiveData<List<User>> = repository.allUsers.asLiveData()

    val allTransactions: LiveData<List<Transaction>> = repository.allTransactions.asLiveData()

    val allShiftLogs: LiveData<List<ShiftLog>> = _currentUserId.switchMap { uid ->
        repository.getShiftLogsByUser(uid).asLiveData()
    }

    val stockLogs: LiveData<List<StockLog>> = repository.allStockLogs.asLiveData()
    val purchaseHistory: LiveData<List<StockLog>> = repository.purchaseHistory.asLiveData()

    private val _cart = MutableLiveData<List<Product>>(emptyList())
    val cart: LiveData<List<Product>> = _cart

    private val _totalPrice = MutableLiveData(0.0)
    val totalPrice: LiveData<Double> = _totalPrice
    private val _isOnline = MutableLiveData<Boolean>(false)
    val isOnline: LiveData<Boolean> = _isOnline

    init {
        loadCurrentUser()
        syncData()
        startServerHealthCheck()
    }
    // 🔥 FUNGSI BARU: LiveData untuk Varian
    fun getVariants(productId: Int): LiveData<List<ProductVariant>> {
        return repository.getVariantsByProductId(productId).asLiveData()
    }
    fun loadCurrentUser() {
        viewModelScope.launch {
            val prefs = getApplication<Application>().getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
            val email = prefs.getString("username", "") ?: ""
            val user = repository.getUserByEmail(email)
            if (user != null) {
                _currentUserId.postValue(user.id)
            }
        }
    }

    private fun startServerHealthCheck() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            while (true) {
                try {
                    val api = ApiClient.getLocalClient(getApplication())
                    val response = api.getCategories(0).execute()
                    _isOnline.postValue(response.isSuccessful)
                } catch (e: Exception) {
                    _isOnline.postValue(false)
                }
                kotlinx.coroutines.delay(5000)
            }
        }
    }

    // --- CRUD ---
    fun insert(product: Product) = viewModelScope.launch { repository.insert(product) }

    // 🔥 PENTING: Untuk Insert Produk & Dapat ID (Buat Varian)
    fun insertProductWithCallback(product: Product, onResult: (Long) -> Unit) {
        viewModelScope.launch {
            val newId = repository.insertProductReturnId(product)
            onResult(newId)
        }
    }

    // 🔥 PENTING: Untuk Insert Varian
    fun insertVariants(variants: List<ProductVariant>) {
        viewModelScope.launch {
            repository.insertVariants(variants)
        }
    }

    fun update(product: Product) = viewModelScope.launch {
        repository.update(product)
        try { repository.updateProductToServer(product) } catch (e: Exception) {}
    }
    fun delete(product: Product) = viewModelScope.launch { repository.delete(product) }

    fun insertCategory(category: Category) = viewModelScope.launch { repository.insertCategory(category) }
    fun deleteCategory(category: Category) = viewModelScope.launch { repository.deleteCategory(category) }

    fun insertSupplier(supplier: Supplier) = viewModelScope.launch { repository.insertSupplier(supplier) }
    fun deleteSupplier(supplier: Supplier) = viewModelScope.launch { repository.deleteSupplier(supplier) }
    fun updateSupplier(supplier: Supplier) = viewModelScope.launch { repository.updateSupplier(supplier) }

    fun insertUser(user: User) = viewModelScope.launch { repository.insertUser(user) }
    fun updateUser(user: User) = viewModelScope.launch { repository.updateUser(user) }
    fun deleteUser(user: User) = viewModelScope.launch { repository.deleteUser(user) }
    // 🔥 Panggil fungsi replace dari repo
    fun updateVariants(productId: Int, variants: List<ProductVariant>) {
        viewModelScope.launch {
            repository.replaceVariants(productId, variants)
        }
    }

    // --- LOGIN MANUAL (EMAIL & PASSWORD) ---
    fun login(u: String, p: String, onResult: (User?) -> Unit) = viewModelScope.launch {
        // 🔥 PERBAIKAN: Gunakan repository.login(u, p)
        // JANGAN pakai repository.getUserByEmail(u) !!!

        val user = repository.login(u, p) // <--- Ini akan cek Email DAN Password

        if (user != null) {
            _currentUserId.postValue(user.id)
        }
        onResult(user)
    }

    // --- CART (KERANJANG) - VALIDASI DENGAN FITUR STOK LOS ---
    fun addToCart(product: Product, onResult: (String) -> Unit) {
        val prefs = getApplication<Application>().getSharedPreferences("store_prefs", Context.MODE_PRIVATE)
        val isStockSystemActive = prefs.getBoolean("use_stock_system", true)

        val currentList = _cart.value?.toMutableList() ?: mutableListOf()
        val index = currentList.indexOfFirst { it.id == product.id }
        val stokGudang = product.stock

        if (index != -1) {
            // --- LOGIKA TAMBAH QTY ---
            val existingItem = currentList[index]
            val qtyBaru = existingItem.stock + 1

            if (isStockSystemActive) {
                // Pakai '>' supaya kalau qtyBaru == stokGudang masih boleh
                if (qtyBaru > stokGudang) {
                    onResult("❌ Stok Habis! Sisa: $stokGudang")
                    return
                }
            }

            currentList[index] = existingItem.copy(stock = qtyBaru)
            // ❌ JANGAN PANGGIL onResult DISINI DULU
        } else {
            // --- LOGIKA BARANG BARU ---
            if (isStockSystemActive) {
                if (stokGudang <= 0) {
                    onResult("❌ Stok Kosong!")
                    return
                }
            }

            currentList.add(product.copy(stock = 1))
            // ❌ JANGAN PANGGIL onResult DISINI DULU
        }

        // ✅ UPDATE DATA DULU (WAJIB PERTAMA)
        _cart.value = currentList
        calculateTotal()

        // ✅ BARU LAPORAN KE MAIN ACTIVITY (TERAKHIR)
        // Dengan begini, saat MainActivity refresh, dia ngambil data yang SUDAH BARU.
        onResult("Berhasil")
    }

    fun decreaseCartItem(product: Product) {
        val currentList = _cart.value?.toMutableList() ?: return
        val index = currentList.indexOfFirst { it.id == product.id }
        if (index != -1) {
            val existingItem = currentList[index]
            if (existingItem.stock > 1) {
                currentList[index] = existingItem.copy(stock = existingItem.stock - 1)
            } else {
                currentList.removeAt(index)
            }
            _cart.value = currentList
            calculateTotal()
        }
    }

    fun removeCartItem(product: Product) {
        val currentList = _cart.value?.toMutableList() ?: return
        currentList.removeAll { it.id == product.id }
        _cart.value = currentList
        calculateTotal()
    }

    private fun calculateTotal() {
        var total = 0.0
        _cart.value?.forEach { total += it.price * it.stock }
        _totalPrice.value = total
    }

    // --- SCANNER & USER ---
    fun checkGoogleUser(email: String, onResult: (User?) -> Unit) {
        viewModelScope.launch {
            val user = repository.getUserByEmail(email)
            if(user != null) _currentUserId.postValue(user.id)
            onResult(user)
        }
    }

    fun scanAndAddToCart(barcode: String, onResult: (Product?) -> Unit, onError: (String) -> Unit) {
        val list = allProducts.value ?: emptyList()
        val product = list.find { it.barcode == barcode }

        if (product != null) {
            addToCart(product) { msg -> if (msg.contains("Habis") || msg.contains("Kosong")) onError(msg) }
            onResult(product)
        } else {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val api = ApiClient.getLocalClient(getApplication())
                    val userId = _currentUserId.value ?: 0
                    val res = api.getProductByBarcode(barcode, userId).execute()

                    if (res.isSuccessful && !res.body().isNullOrEmpty()) {
                        val serverItem = res.body()!!.first()
                        val newProduct = Product(
                            id = serverItem.id,
                            name = serverItem.name,
                            price = serverItem.price.toDouble(),
                            costPrice = serverItem.cost_price.toDouble(),
                            stock = serverItem.stock,
                            category = serverItem.category ?: "Umum",
                            barcode = barcode
                        )
                        launch(kotlinx.coroutines.Dispatchers.Main) {
                            addToCart(newProduct) { msg -> onError(msg) }
                            onResult(newProduct)
                        }
                    } else {
                        launch(kotlinx.coroutines.Dispatchers.Main) {
                            onError("Barang tidak ditemukan di Server")
                            onResult(null)
                        }
                    }
                } catch(e: Exception) {
                    launch(kotlinx.coroutines.Dispatchers.Main) { onError("Gagal koneksi server"); onResult(null) }
                }
            }
        }
    }

    fun importCsv(file: java.io.File) = viewModelScope.launch { repository.uploadCsvFile(file) }
    fun syncUser(user: User) = viewModelScope.launch { repository.syncUserToServer(user) }

    fun checkout(
        subtotal: Double, discount: Double, tax: Double, paymentMethod: String,
        cashReceived: Double, changeAmount: Double, note: String = "", userId: Int = 0,
        onResult: (Transaction?) -> Unit
    ) = viewModelScope.launch {

        // 1. Validasi Keranjang
        val cartItems = _cart.value ?: emptyList()
        if (cartItems.isEmpty()) { onResult(null); return@launch }

        // 2. Siapkan Data Transaksi (Header & Summary)
        val itemsForUpload = ArrayList(cartItems)
        val summaryBuilder = StringBuilder()
        var totalProfit = 0.0

        for (item in cartItems) {
            // Format: Nama|Qty|Harga|Total
            summaryBuilder.append("${item.name}|${item.stock}|${item.price.toLong()}|${(item.price * item.stock).toLong()};")
            totalProfit += (item.price - item.costPrice) * item.stock
        }

        val finalUserId = if (userId != 0) userId else (_currentUserId.value ?: 0)
        val finalSummary = summaryBuilder.toString().removeSuffix(";") + (if(note.isNotEmpty()) " || $note" else "")

        val trx = Transaction(
            timestamp = System.currentTimeMillis(),
            itemsSummary = finalSummary,
            totalAmount = subtotal + tax - discount,
            subtotal = subtotal, discount = discount, tax = tax,
            profit = totalProfit,
            paymentMethod = paymentMethod,
            cashReceived = cashReceived, changeAmount = changeAmount,
            userId = finalUserId,
            note = note
        )

        // 3. Simpan Transaksi ke Database
        val trxId = repository.insertTransaction(trx)

        // ============================================================
        // 🔥 LOGIKA PINTAR: CEK "SAKLAR STOK" DULU SEBELUM POTONG 🔥
        // ============================================================

        // Ambil Settingan Toko
        val prefs = getApplication<Application>().getSharedPreferences("store_prefs", Context.MODE_PRIVATE)
        val isStockSystemActive = prefs.getBoolean("use_stock_system", true) // Default: ON (Wajib Cek)

        // HANYA KURANGI STOK JIKA SAKLAR ON
        if (isStockSystemActive) {

            // Grouping Item (Agar varian & produk induk dihitung satu kesatuan)
            val stockDeductions = mutableMapOf<Int, Int>()

            cartItems.forEach { item ->
                // Jika ID item < 0 (Varian), pakai parentId. Jika positif, pakai ID sendiri.
                val realId = if (item.id < 0) item.parentId else item.id
                val currentTotal = stockDeductions.getOrDefault(realId, 0)
                stockDeductions[realId] = currentTotal + item.stock
            }

            // Eksekusi Potong Stok di Database
            stockDeductions.forEach { (productId, totalQtyToDeduct) ->
                // Ambil data produk TERBARU dari Database (Penting agar tidak minus error)
                val masterItem = repository.getProductById(productId)

                if (masterItem != null) {
                    val newStock = masterItem.stock - totalQtyToDeduct
                    repository.update(masterItem.copy(stock = newStock))
                }
            }
        }
        // JIKA SAKLAR OFF (STOK LOS) -> KITA LEWATI PROSES DI ATAS.
        // STOK DI DATABASE TIDAK BERUBAH SAMA SEKALI.

        // ============================================================

        // 4. Reset Keranjang & Selesai
        _cart.value = emptyList()
        _totalPrice.value = 0.0

        val finalTrx = trx.copy(id = trxId.toInt())
        onResult(finalTrx)

        // 5. Upload ke Server (Background)
        viewModelScope.launch {
            try { repository.uploadTransactionToServer(finalTrx, itemsForUpload) } catch (e: Exception) {}
        }
    }

    fun openShift(user: String, modal: Double) = viewModelScope.launch {
        val uid = _currentUserId.value ?: 0
        val log = ShiftLog(
            timestamp = System.currentTimeMillis(), type = "OPEN", kasirName = user,
            expectedAmount = 0.0, actualAmount = modal, difference = 0.0, userId = uid
        )
        repository.insertShiftLog(log)
    }

    fun closeShift(user: String, expected: Double, actual: Double) = viewModelScope.launch {
        val uid = _currentUserId.value ?: 0
        val log = ShiftLog(
            timestamp = System.currentTimeMillis(), type = "CLOSE", kasirName = user,
            expectedAmount = expected, actualAmount = actual, difference = actual - expected, userId = uid
        )
        repository.insertShiftLog(log)
    }

    // --- PURCHASE / STOK MASUK ---
    fun recordPurchase(log: StockLog) = viewModelScope.launch { repository.recordPurchase(log) }

    fun getPurchaseDetails(purchaseId: Long, onResult: (List<StockLog>) -> Unit) = viewModelScope.launch {
        val details = repository.getPurchaseDetails(purchaseId)
        onResult(details)
    }

    // --- TOKO ---
    // 🔥 UPDATE: Tambah Parameter 'tax' di ujung
    fun saveStoreSettings(name: String, address: String, phone: String, footer: String, printerMac: String?, tax: Double) = viewModelScope.launch {

        // Membuat objek konfigurasi baru
        val config = StoreConfig(
            id = 1, // ID Selalu 1
            storeName = name,
            storeAddress = address,
            storePhone = phone,
            storeEmail = "", // Default kosong (karena tidak ada input di UI Settings)
            strukFooter = footer, // 🔥 Pastikan ini 'strukFooter' sesuai file StoreConfig.kt Anda
            printerMac = printerMac,
            taxPercentage = tax // 🔥 Simpan Pajak
        )

        // Simpan ke Database via Repository
        repository.saveStoreConfig(config)

        // (Opsional) Kirim update ke server sales jika user sedang login
        // Ini kodingan bawaan lama, biarkan saja agar fitur sales tracking tetap jalan
        val uid = _currentUserId.value ?: 0
        if (uid > 0) {
            try {
                val allUsers = repository.allUsers.first()
                val currentUser = allUsers.find { it.id == uid }
                if (currentUser != null) {
                    sendDataToSalesSystem(currentUser)
                }
            } catch (e: Exception) {
                // Abaikan jika gagal lapor sales
            }
        }
    }

    fun syncData() {
        viewModelScope.launch { try { repository.refreshProductsFromApi() } catch (e: Exception) {} }
    }

    fun logoutAndReset(onComplete: () -> Unit) = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        try { repository.clearAllData() } catch (e: Exception) {}
        launch(kotlinx.coroutines.Dispatchers.Main) { onComplete() }
    }

    fun sendDataToSalesSystem(user: User) = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val storeConfig = repository.getStoreConfigDirect()
            val finalStoreName = storeConfig?.storeName ?: "Toko Belum Setup"
            val finalAddress = storeConfig?.storeAddress ?: "-"
            val finalStorePhone = storeConfig?.storePhone ?: "-"

            val request = LeadRequest(
                name = user.name ?: "User Baru",
                store_name = finalStoreName,
                store_address = finalAddress,
                store_phone = finalStorePhone,
                phone = user.phone ?: "-",
                email = user.username
            )

            val api = ApiClient.webClient
            api.registerLead(request).execute()
        } catch (e: Exception) {
            Log.e("SalesCRM", "⚠️ Gagal lapor sales: ${e.message}")
        }
    }

    fun checkServerLicense(email: String) = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val api = ApiClient.webClient
            val deviceId = getDeviceId(getApplication())
            val deviceModel = getDeviceName()
            val response = api.checkLicense(email, deviceId, deviceModel).execute()

            if (response.isSuccessful && response.body() != null) {
                val data = response.body()!!
                val prefs = getApplication<Application>().getSharedPreferences("app_license", Context.MODE_PRIVATE)
                val editor = prefs.edit()

                when (data.status) {
                    "BLOCKED" -> {
                        editor.putBoolean("is_full_version", false)
                        editor.putString("license_msg", "AKUN DITOLAK: Terkunci di HP Lain!")
                        launch(kotlinx.coroutines.Dispatchers.Main) {
                            Toast.makeText(getApplication(), "GAGAL: Akun ini terkunci di HP lain!", Toast.LENGTH_LONG).show()
                        }
                    }
                    "PREMIUM" -> {
                        editor.putBoolean("is_full_version", true)
                        editor.putString("license_msg", "FULL VERSION (Premium)")
                    }
                    "EXPIRED" -> {
                        editor.putBoolean("is_full_version", false)
                        editor.putBoolean("is_expired", true)
                        editor.putString("license_msg", "Masa Trial Habis! Silakan Beli.")
                    }
                    else -> {
                        editor.putBoolean("is_full_version", false)
                        editor.putBoolean("is_expired", false)
                        editor.putString("license_msg", "Trial Sisa ${data.days_left} Hari")
                    }
                }
                editor.apply()
            }
        } catch (e: Exception) {
            Log.e("License", "Gagal cek lisensi: ${e.message}")
        }
    }

    fun checkUserOnCloud(email: String, onResult: (com.sysdos.kasirpintar.api.LicenseCheckResponse?) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val api = ApiClient.webClient
                val deviceId = getDeviceId(getApplication())
                val deviceModel = getDeviceName()
                val response = api.checkLicense(email, deviceId, deviceModel).execute()
                launch(kotlinx.coroutines.Dispatchers.Main) { onResult(if (response.isSuccessful) response.body() else null) }
            } catch (e: Exception) {
                launch(kotlinx.coroutines.Dispatchers.Main) { onResult(null) }
            }
        }
    }

    private fun getDeviceId(context: Context): String {
        return android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: "UNKNOWN_DEVICE"
    }

    private fun getDeviceName(): String {
        val manufacturer = android.os.Build.MANUFACTURER
        val model = android.os.Build.MODEL
        if (model.lowercase().startsWith(manufacturer.lowercase())) {
            return capitalize(model)
        } else {
            return capitalize(manufacturer) + " " + model
        }
    }

    private fun capitalize(s: String?): String {
        if (s.isNullOrEmpty()) return ""
        val first = s[0]
        return if (Character.isUpperCase(first)) s else Character.toUpperCase(first) + s.substring(1)
    }
    // ==========================================
    // 🔥 FITUR VOID (DENGAN PENCATATAN LOG LAPORAN)
    // ==========================================
    fun voidTransaction(trx: Transaction, onSuccess: () -> Unit) = viewModelScope.launch {

        // 1. KEMBALIKAN STOK & CATAT LOG
        val rawItems = trx.itemsSummary.split(" || ")[0]
        val items = rawItems.split(";")

        for (itemStr in items) {
            val parts = itemStr.split("|")
            if (parts.size >= 2) {
                val fullName = parts[0]
                val qty = parts[1].toIntOrNull() ?: 0

                // Logika ambil nama asli (buang varian)
                val realName = if (fullName.contains(" - ")) fullName.split(" - ")[0].trim() else fullName.trim()

                var product = repository.getProductByName(realName)
                if (product == null) product = repository.getProductByName(fullName)

                if (product != null) {
                    // A. KEMBALIKAN STOK
                    val newStock = product.stock + qty
                    repository.update(product.copy(stock = newStock))

                    // B. 🔥 CATAT KE LAPORAN VOID (StockLog) 🔥
                    val log = StockLog(
                        purchaseId = System.currentTimeMillis(), // ID Unik
                        timestamp = System.currentTimeMillis(),
                        productName = "$fullName (VOID TRX #${trx.id})", // Keterangan
                        supplierName = "VOID / BATAL", // Kategori Laporan
                        quantity = qty,
                        costPrice = product.costPrice,
                        totalCost = product.costPrice * qty,
                        type = "VOID" // Tipe Khusus
                    )
                    repository.recordPurchase(log)
                }
            }
        }

        // 2. HAPUS TRANSAKSI
        repository.deleteTransaction(trx)

        launch(kotlinx.coroutines.Dispatchers.Main) {
            onSuccess()
        }
    }

    // 🔥 PERBAIKAN: GANTI FUNGSI INI AGAR TIDAK ERROR IMPORT
    fun getLogReport(targetType: String): androidx.lifecycle.LiveData<List<StockLog>> {
        val result = androidx.lifecycle.MediatorLiveData<List<StockLog>>()

        result.addSource(stockLogs) { logs ->
            // Filter manual
            if (logs != null) {
                val filtered = logs.filter { it.type == targetType }
                result.value = filtered
            }
        }
        return result
    }
    // ==========================================
    // 📦 FITUR RETUR STOK (VERSI FIX SESUAI DB ANDA)
    // ==========================================
    fun returnStockToSupplier(product: Product, qtyToReturn: Int, reason: String, supplierName: String) = viewModelScope.launch {

        // 1. Kurangi Stok Produk
        val newStock = product.stock - qtyToReturn
        repository.update(product.copy(stock = newStock))

        // 2. Catat di Log (Sesuaikan dengan format StockLog Purchase Anda)
        val log = StockLog(
            // Gunakan Timestamp sebagai Purchase ID unik
            purchaseId = System.currentTimeMillis(),
            timestamp = System.currentTimeMillis(),
            productName = product.name,

            // 🔥 Trik: Masukkan ALASAN RETUR ke nama supplier agar terbaca di laporan
            supplierName = "$supplierName (RETUR: $reason)",

            quantity = qtyToReturn,
            costPrice = product.costPrice,

            // Total Cost (Nilai barang yang diretur/dibuang)
            totalCost = product.costPrice * qtyToReturn,

            // Type "OUT" menandakan barang keluar
            type = "OUT"
        )

        // 3. Simpan Log (Pakai fungsi recordPurchase yang sudah ada di Repo)
        repository.recordPurchase(log)
    }
    // Di ProductViewModel.kt
    fun getUserByEmail(email: String, onResult: (User?) -> Unit) = viewModelScope.launch {
        val user = repository.getUserByEmail(email)
        onResult(user)
    }
}