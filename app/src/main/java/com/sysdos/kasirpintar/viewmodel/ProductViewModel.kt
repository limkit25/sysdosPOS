package com.sysdos.kasirpintar.viewmodel

import android.app.Application
import android.util.Log
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

    // Keranjang Belanja (State Sementara)
    private val _cart = MutableLiveData<List<Product>>(emptyList())
    val cart: LiveData<List<Product>> = _cart

    private val _totalPrice = MutableLiveData(0.0)
    val totalPrice: LiveData<Double> = _totalPrice

    // --- INIT (Jalan Pertama Kali Aplikasi Dibuka) ---
    init {
        val database = AppDatabase.getDatabase(application)
        val productDao = database.productDao()
        val shiftDao = database.shiftDao()
        val stockLogDao = database.stockLogDao()

        // 🔥 PERBAIKAN DI SINI: Tambahkan 'application' di depan 🔥
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

        // 🔥 FITUR 1: AUTO SYNC SAAT BUKA APLIKASI
        syncData()
    }

    // =================================================================
    // 🛠️ CRUD DATA MASTER
    // =================================================================

    fun insert(product: Product) = viewModelScope.launch { repository.insert(product) }

    // 🔥 FITUR 2: UPDATE BARANG (LOKAL + SERVER)
    // Dipanggil saat Anda edit harga/nama barang di HP
    fun update(product: Product) = viewModelScope.launch {
        // 1. Update Database HP (Instan)
        repository.update(product)

        // 2. Coba Update ke Server (Background)
        // Agar perubahan harga di HP juga tersimpan di Server
        try {
            repository.updateProductToServer(product)
        } catch (e: Exception) {
            Log.e("SysdosVM", "Gagal update ke server (Mungkin Offline): ${e.message}")
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
    // 🛒 LOGIKA KERANJANG (CART)
    // =================================================================

    fun addToCart(product: Product, onResult: (String) -> Unit) {
        val currentList = _cart.value?.toMutableList() ?: mutableListOf()
        val index = currentList.indexOfFirst { it.id == product.id }

        if (index != -1) {
            val existingItem = currentList[index]
            // Cek stok (gunakan stok asli produk, bukan stok di item keranjang)
            if (existingItem.stock + 1 <= product.stock) {
                currentList[index] = existingItem.copy(stock = existingItem.stock + 1)
                onResult("Qty ditambah")
            } else {
                onResult("Stok habis!")
                return
            }
        } else {
            if (product.stock > 0) {
                // Saat masuk keranjang, field 'stock' kita bajak jadi 'qty beli'
                currentList.add(product.copy(stock = 1))
                onResult("Masuk keranjang")
            } else {
                onResult("Stok habis!")
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
            addToCart(product) { msg -> onError(msg) }
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
    // 💰 FITUR 3: CHECKOUT (BAYAR) + UPLOAD SERVER
    // =================================================================
    fun checkout(
        subtotal: Double, discount: Double, tax: Double, paymentMethod: String,
        cashReceived: Double, changeAmount: Double, onResult: (Transaction?) -> Unit
    ) = viewModelScope.launch {

        val cartItems = _cart.value ?: emptyList()
        if (cartItems.isEmpty()) { onResult(null); return@launch }

        // PENTING: Simpan salinan barang belanjaan untuk dikirim ke server nanti
        // (karena variabel _cart akan kita kosongkan sebentar lagi)
        val itemsForUpload = ArrayList(cartItems)

        val itemsSummary = cartItems.joinToString(", ") { "${it.name} (${it.stock})" }

        // Hitung Profit (Opsional)
        var totalProfit = 0.0
        cartItems.forEach { item ->
            val profitPerItem = item.price - item.costPrice
            totalProfit += (profitPerItem * item.stock)
        }

        // 1. Buat Objek Transaksi
        val trx = Transaction(
            timestamp = System.currentTimeMillis(),
            itemsSummary = itemsSummary,
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

        // 3. Kurangi Stok di Database HP
        cartItems.forEach { cartItem ->
            val masterItem = allProducts.value?.find { it.id == cartItem.id }
            if (masterItem != null) {
                val newStock = masterItem.stock - cartItem.stock
                repository.update(masterItem.copy(stock = newStock))
            }
        }

        // 4. Bersihkan UI (Reset Keranjang)
        _cart.value = emptyList()
        _totalPrice.value = 0.0

        // Kembalikan hasil (struk) ke Activity
        val finalTrx = trx.copy(id = trxId.toInt())
        onResult(finalTrx)

        // 5. 🔥 UPLOAD KE SERVER BOSS (BACKGROUND PROCESS) 🔥
        // Kirim data transaksi + Detail Barang agar Stok Server terpotong
        viewModelScope.launch {
            try {
                repository.uploadTransactionToServer(finalTrx, itemsForUpload)
            } catch (e: Exception) {
                Log.e("SysdosVM", "Checkout Offline? Gagal lapor server: ${e.message}")
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
    // 🔄 FUNGSI SYNC MANUAL
    // =================================================================
    fun syncData() {
        viewModelScope.launch {
            // Memanggil Repository untuk tarik data terbaru (termasuk Gambar)
            repository.refreshProductsFromApi()
        }
    }
}