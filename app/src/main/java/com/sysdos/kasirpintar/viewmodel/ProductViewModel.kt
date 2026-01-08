package com.sysdos.kasirpintar.viewmodel

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.*
import com.sysdos.kasirpintar.data.AppDatabase
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

        // ⚠️ PENTING: Sync Otomatis bisa menimpa data lokal jika internet lambat.
        // Jika stok sering "muncul lagi", matikan baris ini dan gunakan tombol refresh manual.
//        syncData()
        startServerHealthCheck()
    }
    // 🔥 PERBAIKAN: Tambahkan (Dispatchers.IO) agar jalan di background
    private fun startServerHealthCheck() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            while (true) {
                try {
                    val api = com.sysdos.kasirpintar.api.ApiClient.getInstance(getApplication())
                    val response = api.getCategories().execute()

                    if (response.isSuccessful) {
                        _isOnline.postValue(true) // KONEK (Hijau)
                    } else {
                        _isOnline.postValue(false) // GAGAL (Merah)
                    }
                } catch (e: Exception) {
                    _isOnline.postValue(false) // ERROR (Merah)
                    // Log.e("Ping", "Server putus: ${e.message}")
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

    fun scanAndAddToCart(barcode: String, onResult: (Product?) -> Unit, onError: (String) -> Unit) {
        val list = allProducts.value ?: emptyList()
        val product = list.find { it.barcode == barcode }

        if (product != null) {
            // Panggil fungsi addToCart yg sudah ada SATPAM-nya
            addToCart(product) { msg ->
                if (msg.contains("Habis") || msg.contains("Kosong")) {
                    onError(msg)
                }
            }
            onResult(product)
        } else {
            onResult(null)
        }
    }

    private fun calculateTotal() {
        var total = 0.0
        _cart.value?.forEach { total += it.price * it.stock }
        _totalPrice.value = total
    }

    // =================================================================
    // 💰 CHECKOUT (BAYAR) + LAPOR SERVER
    // =================================================================
    fun checkout(
        subtotal: Double, discount: Double, tax: Double, paymentMethod: String,
        cashReceived: Double, changeAmount: Double, onResult: (Transaction?) -> Unit
    ) = viewModelScope.launch {

        val cartItems = _cart.value ?: emptyList()
        if (cartItems.isEmpty()) { onResult(null); return@launch }

        // Simpan data untuk upload
        val itemsForUpload = ArrayList(cartItems)

        // Generate Rincian (Format: Nama|Qty|Harga|Total;)
        val summaryBuilder = StringBuilder()
        var totalProfit = 0.0

        for (item in cartItems) {
            val totalItemPrice = item.price * item.stock
            summaryBuilder.append("${item.name}|${item.stock}|${item.price.toLong()}|${totalItemPrice.toLong()};")

            // Hitung Profit
            val profitPerItem = item.price - item.costPrice
            totalProfit += (profitPerItem * item.stock)
        }
        val itemsSummary = summaryBuilder.toString().removeSuffix(";")

        // 1. Buat Objek Transaksi
        val trx = Transaction(
            timestamp = System.currentTimeMillis(),
            itemsSummary = itemsSummary, // Format String yg benar
            totalAmount = subtotal + tax - discount,
            subtotal = subtotal,
            discount = discount,
            tax = tax,
            profit = totalProfit,
            paymentMethod = paymentMethod,
            cashReceived = cashReceived,
            changeAmount = changeAmount
        )

        // 2. Simpan Transaksi ke Database HP
        val trxId = repository.insertTransaction(trx)

        // 3. Kurangi Stok di Database HP (Lokal)
        cartItems.forEach { cartItem ->
            // Gunakan getProductById agar aman (pastikan repo punya fungsi ini)
            // Jika error merah disini, tambahkan fungsi getProductById di Repo seperti panduan sebelumnya
            try {
                val masterItem = allProducts.value?.find { it.id == cartItem.id }
                if (masterItem != null) {
                    val newStock = masterItem.stock - cartItem.stock
                    repository.update(masterItem.copy(stock = newStock))
                }
            } catch (e: Exception) { Log.e("CheckOut", "Error potong stok lokal: ${e.message}") }
        }

        // 4. Bersihkan UI
        _cart.value = emptyList()
        _totalPrice.value = 0.0

        val finalTrx = trx.copy(id = trxId.toInt())
        onResult(finalTrx)

        // 5. 🔥 UPLOAD KE SERVER (DENGAN PENANGANAN ERROR) 🔥
        viewModelScope.launch {
            try {
                repository.uploadTransactionToServer(finalTrx, itemsForUpload)
                // Jika sukses, biarkan data server update sendiri.
                // JANGAN langsung syncData() disini karena bisa jadi server butuh waktu.
                Log.d("SysdosVM", "✅ Upload Sukses. Stok Server aman.")
            } catch (e: Exception) {
                // Beritahu user jika internet mati
                Log.e("SysdosVM", "⚠️ Gagal Upload: ${e.message}")
                Toast.makeText(getApplication(), "⚠️ Offline: Transaksi tersimpan di HP, tapi stok Web belum berkurang.", Toast.LENGTH_LONG).show()
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

    // =================================================================
    // 🔄 SYNC DATA
    // =================================================================
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