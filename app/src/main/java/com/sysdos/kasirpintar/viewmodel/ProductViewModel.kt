package com.sysdos.kasirpintar.viewmodel

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.*
import com.sysdos.kasirpintar.api.ApiClient // Import ApiClient
import com.sysdos.kasirpintar.data.AppDatabase // Pastikan nama DB Anda sesuai (AppDatabase atau ProductDatabase)
import com.sysdos.kasirpintar.data.ProductRepository
import com.sysdos.kasirpintar.data.model.*
import kotlinx.coroutines.launch

class ProductViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ProductRepository

    // --- LIVE DATA (Untuk UI) ---
    val allProducts: LiveData<List<Product>>
    val allCategories: LiveData<List<Category>>
    val allSuppliers: LiveData<List<Supplier>>
    val allTransactions: LiveData<List<Transaction>>
    val allUsers: LiveData<List<User>>

    // Log Data & Shift
    val stockLogs: LiveData<List<StockLog>>
    val allShiftLogs: LiveData<List<ShiftLog>>
    val purchaseHistory: LiveData<List<StockLog>>

    // Keranjang Belanja
    private val _cart = MutableLiveData<List<Product>>(emptyList())
    val cart: LiveData<List<Product>> = _cart

    private val _totalPrice = MutableLiveData(0.0)
    val totalPrice: LiveData<Double> = _totalPrice
    private val _isOnline = MutableLiveData<Boolean>(false)
    val isOnline: LiveData<Boolean> = _isOnline

    // --- INIT ---
    init {
        // Sesuaikan dengan nama class Database Anda (AppDatabase atau ProductDatabase)
        val database = AppDatabase.getDatabase(application)
        val productDao = database.productDao()
        val shiftDao = database.shiftDao()
        val stockLogDao = database.stockLogDao()

        repository = ProductRepository(application, productDao, shiftDao, stockLogDao)

        // Hubungkan LiveData
        allProducts = repository.allProducts.asLiveData()
        allCategories = repository.allCategories.asLiveData()
        allSuppliers = repository.allSuppliers.asLiveData()
        allTransactions = repository.allTransactions.asLiveData()
        allUsers = repository.allUsers.asLiveData()
        stockLogs = repository.allStockLogs.asLiveData()
        allShiftLogs = repository.allShiftLogs.asLiveData()
        purchaseHistory = repository.purchaseHistory.asLiveData()

        syncData()
        startServerHealthCheck()
    }

    // 🔥 PERBAIKAN UTAMA DI SINI 🔥
    private fun startServerHealthCheck() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            while (true) {
                try {
                    // GANTI 'getInstance' JADI 'getLocalClient'
                    // Kita cek koneksi ke Server Toko (Local)
                    val api = ApiClient.getLocalClient(getApplication())
                    val response = api.getCategories().execute()

                    if (response.isSuccessful) {
                        _isOnline.postValue(true) // KONEK (Hijau)
                    } else {
                        _isOnline.postValue(false) // GAGAL (Merah)
                    }
                } catch (e: Exception) {
                    _isOnline.postValue(false) // ERROR (Merah)
                }
                kotlinx.coroutines.delay(5000) // Cek lagi setiap 5 detik
            }
        }
    }

    // =================================================================
    // 🛠️ CRUD DATA MASTER
    // =================================================================

    fun insert(product: Product) = viewModelScope.launch { repository.insert(product) }

    fun update(product: Product) = viewModelScope.launch {
        repository.update(product)
        try {
            repository.updateProductToServer(product)
        } catch (e: Exception) {
            Log.e("SysdosVM", "Gagal update ke server: ${e.message}")
        }
    }

    fun delete(product: Product) = viewModelScope.launch { repository.delete(product) }

    // --- Kategori, Supplier, User ---
    fun insertCategory(category: Category) = viewModelScope.launch { repository.insertCategory(category) }
    fun deleteCategory(category: Category) = viewModelScope.launch { repository.deleteCategory(category) }

    fun insertSupplier(supplier: Supplier) = viewModelScope.launch { repository.insertSupplier(supplier) }
    fun deleteSupplier(supplier: Supplier) = viewModelScope.launch { repository.deleteSupplier(supplier) }
    fun updateSupplier(supplier: Supplier) = viewModelScope.launch { repository.updateSupplier(supplier) }

    fun insertUser(user: User) = viewModelScope.launch { repository.insertUser(user) }
    fun updateUser(user: User) = viewModelScope.launch { repository.updateUser(user) }
    fun deleteUser(user: User) = viewModelScope.launch { repository.deleteUser(user) }

    fun login(u: String, p: String, onResult: (User?) -> Unit) = viewModelScope.launch {
        val user = repository.login(u, p)
        onResult(user)
    }

    // =================================================================
    // 🛡️ SATPAM KERANJANG (ANTI STOK MINUS)
    // =================================================================

    fun addToCart(product: Product, onResult: (String) -> Unit) {
        val currentList = _cart.value?.toMutableList() ?: mutableListOf()
        val index = currentList.indexOfFirst { it.id == product.id }

        // Cek Stok Asli (Dari Database Master)
        val stokGudang = product.stock

        if (index != -1) {
            val existingItem = currentList[index]
            val qtyBaru = existingItem.stock + 1

            // 🔥 PENJAGA: Cek apakah melebihi stok?
            if (qtyBaru > stokGudang) {
                onResult("❌ Stok Habis! Sisa: $stokGudang") // Tolak
                return
            }

            currentList[index] = existingItem.copy(stock = qtyBaru)
            onResult("Qty ditambah")
        } else {
            // 🔥 PENJAGA: Cek apakah stok kosong?
            if (stokGudang > 0) {
                currentList.add(product.copy(stock = 1))
                onResult("Masuk keranjang")
            } else {
                onResult("❌ Stok Kosong!") // Tolak
                return
            }
        }
        _cart.value = currentList
        calculateTotal()
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

    // File: ProductViewModel.kt
    fun checkGoogleUser(email: String, onResult: (User?) -> Unit) {
        viewModelScope.launch {
            val user = repository.getUserByEmail(email)
            onResult(user)
        }
    }
    fun scanAndAddToCart(barcode: String, onResult: (Product?) -> Unit, onError: (String) -> Unit) {
        val list = allProducts.value ?: emptyList()
        // 1. Cek di Database HP dulu (Cepat)
        val product = list.find { it.barcode == barcode }

        if (product != null) {
            addToCart(product) { msg ->
                if (msg.contains("Habis") || msg.contains("Kosong")) {
                    onError(msg)
                }
            }
            onResult(product)
        } else {
            // 2. Jika tidak ada di HP, cari ke Server Toko
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val api = ApiClient.getLocalClient(getApplication())

                    // Panggil API
                    val res = api.getProductByBarcode(barcode).execute()

                    // Cek apakah sukses DAN ada isinya?
                    if (res.isSuccessful && !res.body().isNullOrEmpty()) {

                        // 🔥 AMBIL ITEM PERTAMA DARI LIST
                        val serverItem = res.body()!!.first()

                        // Perbaikan Argument Type Mismatch (Category)
                        val catName: String = serverItem.category ?: "Umum"

                        val newProduct = Product(
                            id = serverItem.id,
                            name = serverItem.name,
                            price = serverItem.price.toDouble(),
                            costPrice = serverItem.cost_price.toDouble(),
                            stock = serverItem.stock,
                            category = catName, // Pakai variabel string yang sudah aman
                            barcode = barcode, // Simpan barcode yang discan
                            imagePath = null
                        )

                        // Kembali ke Main Thread (UI)
                        launch(kotlinx.coroutines.Dispatchers.Main) {
                            addToCart(newProduct) { msg -> onError(msg) }
                            onResult(newProduct)
                        }
                    } else {
                        // Gagal / Kosong
                        launch(kotlinx.coroutines.Dispatchers.Main) {
                            onError("Barang tidak ditemukan di Server")
                            onResult(null)
                        }
                    }
                } catch(e: Exception) {
                    launch(kotlinx.coroutines.Dispatchers.Main) {
                        onError("Gagal koneksi server")
                        onResult(null)
                    }
                }
            }
        }
    }

    private fun calculateTotal() {
        var total = 0.0
        _cart.value?.forEach { total += it.price * it.stock }
        _totalPrice.value = total
    }

    fun importCsv(file: java.io.File) = viewModelScope.launch {
        repository.uploadCsvFile(file)
    }

    fun syncUser(user: User) = viewModelScope.launch {
        repository.syncUserToServer(user)
    }

    // =================================================================
    // 💰 CHECKOUT (BAYAR) + LAPOR SERVER
    // =================================================================
    fun checkout(
        subtotal: Double,
        discount: Double,
        tax: Double,
        paymentMethod: String,
        cashReceived: Double,
        changeAmount: Double,
        note: String = "",
        userId: Int = 0, // 🔥 1. TAMBAHKAN PARAMETER INI (Default 0 biar gak error)
        onResult: (Transaction?) -> Unit
    ) = viewModelScope.launch {

        val cartItems = _cart.value ?: emptyList()
        if (cartItems.isEmpty()) { onResult(null); return@launch }

        val itemsForUpload = ArrayList(cartItems)
        val summaryBuilder = StringBuilder()
        var totalProfit = 0.0

        for (item in cartItems) {
            val totalItemPrice = item.price * item.stock
            summaryBuilder.append("${item.name}|${item.stock}|${item.price.toLong()}|${totalItemPrice.toLong()};")
            val profitPerItem = item.price - item.costPrice
            totalProfit += (profitPerItem * item.stock)
        }
        val itemsSummary = summaryBuilder.toString().removeSuffix(";")

        // Gabungkan Note jika ada
        val finalSummary = itemsSummary + (if(note.isNotEmpty()) " || $note" else "")

        val trx = Transaction(
            timestamp = System.currentTimeMillis(),
            itemsSummary = finalSummary,
            totalAmount = subtotal + tax - discount,
            subtotal = subtotal,
            discount = discount,
            tax = tax,
            profit = totalProfit,
            paymentMethod = paymentMethod,
            cashReceived = cashReceived,
            changeAmount = changeAmount,
            userId = userId // 🔥 2. MASUKKAN KE MODEL TRANSACTION
        )

        // Simpan Lokal
        val trxId = repository.insertTransaction(trx)

        // Potong Stok Lokal
        cartItems.forEach { cartItem ->
            try {
                val masterItem = allProducts.value?.find { it.id == cartItem.id }
                if (masterItem != null) {
                    val newStock = masterItem.stock - cartItem.stock
                    repository.update(masterItem.copy(stock = newStock))
                }
            } catch (e: Exception) { Log.e("CheckOut", "Error potong stok lokal: ${e.message}") }
        }

        _cart.value = emptyList()
        _totalPrice.value = 0.0

        val finalTrx = trx.copy(id = trxId.toInt())
        onResult(finalTrx)

        // Upload Server
        viewModelScope.launch {
            try {
                // Repository nanti akan mencari User ID asli dari session database
                // sebelum upload, jadi aman dikirim userId 0 dari sini.
                repository.uploadTransactionToServer(finalTrx, itemsForUpload)
                Log.d("SysdosVM", "✅ Upload Sukses. Stok Server aman.")
            } catch (e: Exception) {
                Log.e("SysdosVM", "⚠️ Gagal Upload: ${e.message}")
                // Hapus Toast disini karena ViewModel tidak punya context Application langsung
                // Atau gunakan getApplication<Application>() jika AndroidViewModel
            }
        }
    }

    // =================================================================
    // ⏱️ SHIFT & LOGS
    // =================================================================
    fun openShift(user: String, modal: Double) = viewModelScope.launch {
        val log = ShiftLog(
            timestamp = System.currentTimeMillis(), type = "OPEN", kasirName = user,
            expectedAmount = 0.0, actualAmount = modal, difference = 0.0
        )
        repository.insertShiftLog(log)
    }

    fun closeShift(user: String, expected: Double, actual: Double) = viewModelScope.launch {
        val diff = actual - expected
        val log = ShiftLog(
            timestamp = System.currentTimeMillis(), type = "CLOSE", kasirName = user,
            expectedAmount = expected, actualAmount = actual, difference = diff
        )
        repository.insertShiftLog(log)
    }

    fun recordPurchase(log: StockLog) = viewModelScope.launch {
        repository.recordPurchase(log)
    }

    fun getPurchaseDetails(purchaseId: Long, onResult: (List<StockLog>) -> Unit) = viewModelScope.launch {
        val details = repository.getPurchaseDetails(purchaseId)
        onResult(details)
    }

    fun syncData() {
        viewModelScope.launch {
            try {
                repository.refreshProductsFromApi()
            } catch (e: Exception) {
                Log.e("Sync", "Gagal Sync: ${e.message}")
            }
        }
    }
}