package com.sysdos.kasirpintar.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.sysdos.kasirpintar.data.AppDatabase
import com.sysdos.kasirpintar.data.ProductRepository
import com.sysdos.kasirpintar.data.model.*
import kotlinx.coroutines.launch

class ProductViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ProductRepository

    // LIVE DATA
    val allProducts: LiveData<List<Product>>
    val allCategories: LiveData<List<Category>>
    val allSuppliers: LiveData<List<Supplier>>
    val allTransactions: LiveData<List<Transaction>>
    val allUsers: LiveData<List<User>>

    // 🔥 DATA LOG YANG DIBUTUHKAN PURCHASE ACTIVITY 🔥
    val stockLogs: LiveData<List<StockLog>>

    // Keranjang Belanja
    private val _cart = MutableLiveData<List<Product>>(emptyList())
    val cart: LiveData<List<Product>> = _cart

    private val _totalPrice = MutableLiveData(0.0)
    val totalPrice: LiveData<Double> = _totalPrice

    init {
        val database = AppDatabase.getDatabase(application)
        val dao = database.productDao()
        repository = ProductRepository(dao)

        allProducts = repository.allProducts.asLiveData()
        allCategories = repository.allCategories.asLiveData()
        allSuppliers = repository.allSuppliers.asLiveData()
        allTransactions = repository.allTransactions.asLiveData()
        allUsers = repository.allUsers.asLiveData()

        // Inisialisasi LiveData Log
        stockLogs = repository.allStockLogs.asLiveData()
    }

    // --- FUNGSI DATABASE UTAMA ---
    fun insert(product: Product) = viewModelScope.launch { repository.insert(product) }
    fun update(product: Product) = viewModelScope.launch { repository.update(product) }
    fun delete(product: Product) = viewModelScope.launch { repository.delete(product) }

    // Fungsi Kategori
    fun insertCategory(category: Category) = viewModelScope.launch { repository.insertCategory(category) }
    fun deleteCategory(category: Category) = viewModelScope.launch { repository.deleteCategory(category) }

    // Fungsi Supplier
    fun insertSupplier(supplier: Supplier) = viewModelScope.launch { repository.insertSupplier(supplier) }
    fun deleteSupplier(supplier: Supplier) = viewModelScope.launch { repository.deleteSupplier(supplier) }

    // Fungsi User
    fun insertUser(user: User) = viewModelScope.launch { repository.insertUser(user) }
    fun updateUser(user: User) = viewModelScope.launch { repository.updateUser(user) }
    fun deleteUser(user: User) = viewModelScope.launch { repository.deleteUser(user) }

    // 🔥 TAMBAHKAN FUNGSI INI DI VIEWMODEL 🔥
    fun recordPurchase(log: StockLog) = viewModelScope.launch {
        repository.recordPurchase(log)
    }

    // --- FUNGSI KERANJANG KASIR ---
    fun addToCart(product: Product, onResult: (String) -> Unit) {
        val currentList = _cart.value?.toMutableList() ?: mutableListOf()
        val existingItem = currentList.find { it.id == product.id }

        if (existingItem != null) {
            // Cek stok master sebelum tambah
            if (existingItem.stock + 1 <= product.stock) {
                existingItem.stock += 1
                onResult("Qty ditambah")
            } else {
                onResult("Stok habis!")
                return
            }
        } else {
            if (product.stock > 0) {
                val item = product.copy(stock = 1)
                currentList.add(item)
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
        val existingItem = currentList.find { it.id == product.id }

        if (existingItem != null) {
            if (existingItem.stock > 1) {
                existingItem.stock -= 1
            } else {
                currentList.remove(existingItem)
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

    fun updateCartCounts(cartItems: List<Product>) {
        // Opsional: untuk sinkronisasi UI jika diperlukan
    }

    private fun calculateTotal() {
        var total = 0.0
        _cart.value?.forEach { total += it.price * it.stock }
        _totalPrice.value = total
    }

    fun checkout(
        subtotal: Double,
        discount: Double,
        tax: Double,
        paymentMethod: String,
        cashReceived: Double,
        changeAmount: Double,
        onResult: (Transaction?) -> Unit
    ) = viewModelScope.launch {

        val cartItems = _cart.value ?: emptyList()
        if (cartItems.isEmpty()) {
            onResult(null)
            return@launch
        }

        // 1. Ringkasan Item
        val itemsSummary = cartItems.joinToString(";") {
            "${it.name}|${it.stock}|${it.price}|${it.price * it.stock}"
        }

        // 2. Hitung Profit
        var totalProfit = 0.0
        cartItems.forEach { item ->
            val profitPerItem = item.price - item.costPrice
            totalProfit += (profitPerItem * item.stock)
        }

        // 3. Simpan Transaksi
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

        // 4. Update Stok
        cartItems.forEach { cartItem ->
            val masterItem = allProducts.value?.find { it.id == cartItem.id }
            if (masterItem != null) {
                val newStock = masterItem.stock - cartItem.stock
                val updatedProduct = masterItem.copy(stock = newStock)
                repository.update(updatedProduct)
            }
        }

        // 5. Bersihkan Keranjang
        _cart.value = emptyList()
        _totalPrice.value = 0.0

        onResult(trx.copy(id = trxId.toInt()))
    }

    // --- SHIFT ---
    fun openShift(user: String, modal: Double) = viewModelScope.launch {
        // Implementasi log shift jika diperlukan
    }

    fun closeShift(user: String, expected: Double, actual: Double) = viewModelScope.launch {
        // Implementasi log tutup shift jika diperlukan
    }
}