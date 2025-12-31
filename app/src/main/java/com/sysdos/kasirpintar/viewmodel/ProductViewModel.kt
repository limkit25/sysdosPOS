package com.sysdos.kasirpintar.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.sysdos.kasirpintar.data.AppDatabase
import com.sysdos.kasirpintar.data.model.Product
import com.sysdos.kasirpintar.data.model.Transaction
import com.sysdos.kasirpintar.data.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class ProductViewModel(application: Application) : AndroidViewModel(application) {

    // Kita pakai DAO langsung (sesuai kodingan Bapak)
    private val productDao = AppDatabase.getDatabase(application).productDao()

    // --- DATA PRODUK ---
    val allProducts: LiveData<List<Product>> = productDao.getAllProducts().asLiveData()

    // --- RIWAYAT TRANSAKSI ---
    val allTransactions: LiveData<List<Transaction>> = productDao.getAllTransactions().asLiveData()

    // --- KERANJANG BELANJA ---
    private val _cart = MutableLiveData<List<Product>>(emptyList())
    val cart: LiveData<List<Product>> = _cart

    private val _totalPrice = MutableLiveData(0.0)
    val totalPrice: LiveData<Double> = _totalPrice

    // --- MANAJEMEN PRODUK (CRUD) ---
    fun insert(product: Product) {
        viewModelScope.launch { productDao.insertProduct(product) }
    }

    fun update(product: Product) {
        viewModelScope.launch { productDao.updateProduct(product) }
    }

    // Fungsi delete yang dipakai ProductListActivity (FIXED: Pakai productDao)
    fun delete(product: Product) = viewModelScope.launch(Dispatchers.IO) {
        productDao.deleteProduct(product)
    }

    // Fungsi ini duplikat, tapi biarkan saja kalau ada yang pakai
    fun deleteProduct(product: Product) {
        viewModelScope.launch { productDao.deleteProduct(product) }
    }

    // Fungsi Ambil Produk by ID (Untuk Edit)
    fun getProductById(id: Int): LiveData<Product> {
        return productDao.getProductById(id).asLiveData()
    }

    // --- LOGIKA KASIR (SCAN & CART) ---
    fun scanAndAddToCart(barcode: String, onResult: (Product?) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            // Ambil value terbaru dari LiveData
            val productList = allProducts.value ?: emptyList()
            val product = productList.find { it.barcode == barcode }

            if (product != null) {
                if (product.stock > 0) {
                    addToCart(product) {}
                    onResult(product)
                } else {
                    onError("Stok Habis!")
                }
            } else {
                onResult(null)
            }
        }
    }

    fun addToCart(product: Product, onMessage: (String) -> Unit) {
        // 1. CEK STOK
        if (product.stock <= 0) {
            onMessage("Stok Habis!")
            return
        }

        val currentCart = _cart.value?.toMutableList() ?: mutableListOf()
        val existingItem = currentCart.find { it.id == product.id }

        if (existingItem != null) {
            if (existingItem.stock < product.stock) {
                val updatedItem = existingItem.copy(stock = existingItem.stock + 1)
                val index = currentCart.indexOf(existingItem)
                currentCart[index] = updatedItem
                onMessage("Ditambahkan: ${product.name}")
            } else {
                onMessage("Stok tidak cukup!")
            }
        } else {
            currentCart.add(product.copy(stock = 1))
            onMessage("Masuk Keranjang: ${product.name}")
        }

        _cart.value = currentCart
        calculateTotal()
    }

    fun decreaseCartItem(product: Product) {
        val currentCart = _cart.value?.toMutableList() ?: return
        val existingItem = currentCart.find { it.id == product.id } ?: return

        if (existingItem.stock > 1) {
            val updatedItem = existingItem.copy(stock = existingItem.stock - 1)
            val index = currentCart.indexOf(existingItem)
            currentCart[index] = updatedItem
        } else {
            currentCart.remove(existingItem)
        }
        _cart.value = currentCart
        calculateTotal()
    }

    fun removeCartItem(product: Product) {
        val currentCart = _cart.value?.toMutableList() ?: return
        val existingItem = currentCart.find { it.id == product.id }
        if (existingItem != null) {
            currentCart.remove(existingItem)
            _cart.value = currentCart
            calculateTotal()
        }
    }

    private fun calculateTotal() {
        val currentCart = _cart.value ?: emptyList()
        var sum = 0.0
        for (item in currentCart) {
            sum += (item.price * item.stock)
        }
        _totalPrice.value = sum
    }

    // --- CHECKOUT ---
    fun checkout(
        subtotal: Double,
        discount: Double,
        tax: Double,
        paymentMethod: String,
        cashReceived: Double,
        changeAmount: Double,
        onSuccess: (Transaction?) -> Unit
    ) {
        val currentCart = _cart.value ?: return
        if (currentCart.isEmpty()) { onSuccess(null); return }

        viewModelScope.launch {
            val total = (subtotal - discount) + tax
            val timestamp = System.currentTimeMillis()

            val summaryBuilder = StringBuilder()
            var totalProfit = 0.0

            for (item in currentCart) {
                summaryBuilder.append("${item.name}\n")
                summaryBuilder.append("   ${item.stock} x ${String.format("%,.0f", item.price)} = ${String.format("%,.0f", item.price * item.stock)}\n")

                // Kurangi Stok di Database
                productDao.decreaseStock(item.id, item.stock)

                // Hitung Profit
                val profitPerItem = (item.price - item.costPrice) * item.stock
                totalProfit += profitPerItem
            }

            val trx = Transaction(
                itemsSummary = summaryBuilder.toString(),
                totalAmount = total,
                profit = totalProfit,
                timestamp = timestamp,
                paymentMethod = paymentMethod,
                cashReceived = cashReceived,
                changeAmount = changeAmount,
                subtotal = subtotal,
                discount = discount,
                tax = tax
            )
            val id = productDao.insertTransaction(trx)

            // Reset Keranjang
            _cart.value = emptyList()
            _totalPrice.value = 0.0

            onSuccess(trx.copy(id = id.toInt()))
        }
    }

    // --- USER MANAGEMENT ---
    val allUsers: LiveData<List<User>> = productDao.getAllUsers().asLiveData()

    fun insertUser(user: User) {
        viewModelScope.launch { productDao.insertUser(user) }
    }

    fun resetPassword(userId: Int, newPass: String) {
        viewModelScope.launch { productDao.updatePassword(userId, newPass) }
    }

    fun deleteUser(user: User) {
        viewModelScope.launch { productDao.deleteUser(user) }
    }

    fun updateUser(user: User) {
        viewModelScope.launch { productDao.updateUser(user) }
    }

    fun login(username: String, pass: String, onResult: (User?) -> Unit) {
        viewModelScope.launch {
            val user = productDao.login(username, pass)
            onResult(user)
        }
    }

    // --- LAPORAN HARIAN ---
    fun getTodayTransactions(): List<Transaction> {
        val allTrx = allTransactions.value ?: emptyList()
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startOfDay = calendar.timeInMillis
        return allTrx.filter { it.timestamp >= startOfDay }
    }
}