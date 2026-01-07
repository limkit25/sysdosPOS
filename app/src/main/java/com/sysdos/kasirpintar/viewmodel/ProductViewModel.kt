package com.sysdos.kasirpintar.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.sysdos.kasirpintar.data.AppDatabase
import com.sysdos.kasirpintar.data.ProductRepository
import com.sysdos.kasirpintar.data.model.*
import kotlinx.coroutines.launch

class ProductViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ProductRepository

    // --- LIVE DATA ---
    val allProducts: LiveData<List<Product>>
    val allCategories: LiveData<List<Category>>
    val allSuppliers: LiveData<List<Supplier>>
    val allTransactions: LiveData<List<Transaction>>
    val allUsers: LiveData<List<User>>

    // Log Data & Shift
    val stockLogs: LiveData<List<StockLog>>
    val allShiftLogs: LiveData<List<ShiftLog>>

    // 🔥 BARU: Riwayat Pembelian (Grouped)
    val purchaseHistory: LiveData<List<StockLog>>

    // Keranjang Belanja (Penjualan)
    private val _cart = MutableLiveData<List<Product>>(emptyList())
    val cart: LiveData<List<Product>> = _cart
    private val _totalPrice = MutableLiveData(0.0)
    val totalPrice: LiveData<Double> = _totalPrice

    init {
        val database = AppDatabase.getDatabase(application)
        val dao = database.productDao()
        val shiftDao = database.shiftDao()
        val stockDao = database.stockLogDao() // Tambahan akses DAO jika perlu

        repository = ProductRepository(dao, shiftDao, stockDao) // Sesuaikan constructor Repo Anda

        allProducts = repository.allProducts.asLiveData()
        allCategories = repository.allCategories.asLiveData()
        allSuppliers = repository.allSuppliers.asLiveData()
        allTransactions = repository.allTransactions.asLiveData()
        allUsers = repository.allUsers.asLiveData()

        stockLogs = repository.allStockLogs.asLiveData()
        allShiftLogs = repository.allShiftLogs.asLiveData()

        // 🔥 Inisialisasi Riwayat Pembelian
        purchaseHistory = repository.purchaseHistory.asLiveData()
        // ============================================================
        // 🔥 TAMBAHKAN DI SINI (PALING BAWAH INIT) 🔥
        // ============================================================
        syncData()
    }

    // --- FUNGSI UTAMA ---
    fun insert(product: Product) = viewModelScope.launch { repository.insert(product) }
    fun update(product: Product) = viewModelScope.launch { repository.update(product) }
    fun delete(product: Product) = viewModelScope.launch { repository.delete(product) }

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

    // --- FUNGSI PEMBELIAN / STOK ---
    fun recordPurchase(log: StockLog) = viewModelScope.launch {
        repository.recordPurchase(log)
    }

    // 🔥 BARU: Ambil Detail Pembelian
    fun getPurchaseDetails(purchaseId: Long, onResult: (List<StockLog>) -> Unit) = viewModelScope.launch {
        val details = repository.getPurchaseDetails(purchaseId)
        onResult(details)
    }

    // --- KERANJANG PENJUALAN (POS) ---
    fun addToCart(product: Product, onResult: (String) -> Unit) {
        val currentList = _cart.value?.toMutableList() ?: mutableListOf()
        val index = currentList.indexOfFirst { it.id == product.id }

        if (index != -1) {
            val existingItem = currentList[index]
            if (existingItem.stock + 1 <= product.stock) {
                currentList[index] = existingItem.copy(stock = existingItem.stock + 1)
                onResult("Qty ditambah")
            } else {
                onResult("Stok habis!")
                return
            }
        } else {
            if (product.stock > 0) {
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

    fun checkout(
        subtotal: Double, discount: Double, tax: Double, paymentMethod: String,
        cashReceived: Double, changeAmount: Double, onResult: (Transaction?) -> Unit
    ) = viewModelScope.launch {

        val cartItems = _cart.value ?: emptyList() // 1. Ambil keranjang
        if (cartItems.isEmpty()) { onResult(null); return@launch }

        // Simpan salinan item untuk dikirim ke server nanti (karena _cart akan dihapus)
        val itemsForUpload = ArrayList(cartItems)

        val itemsSummary = cartItems.joinToString(", ") { "${it.name} (${it.stock})" }

        var totalProfit = 0.0
        cartItems.forEach { item ->
            val profitPerItem = item.price - item.costPrice
            totalProfit += (profitPerItem * item.stock)
        }

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

        val trxId = repository.insertTransaction(trx)

        cartItems.forEach { cartItem ->
            val masterItem = allProducts.value?.find { it.id == cartItem.id }
            if (masterItem != null) {
                val newStock = masterItem.stock - cartItem.stock
                repository.update(masterItem.copy(stock = newStock))
            }
        }

        // Bersihkan UI
        _cart.value = emptyList()
        _totalPrice.value = 0.0
        val finalTrx = trx.copy(id = trxId.toInt())
        onResult(finalTrx)

        // 🔥 UPLOAD KE SERVER (BESERTA DETAIL ITEM) 🔥
        viewModelScope.launch {
            repository.uploadTransactionToServer(finalTrx, itemsForUpload)
        }
    }

    // --- SHIFT ---
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
    fun syncData() {
        viewModelScope.launch {
            repository.refreshProductsFromApi()
        }
    }
}