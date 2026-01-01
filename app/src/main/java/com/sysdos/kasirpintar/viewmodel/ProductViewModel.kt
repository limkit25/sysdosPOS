package com.sysdos.kasirpintar.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.sysdos.kasirpintar.data.AppDatabase
import com.sysdos.kasirpintar.data.model.Category // [1] PENTING: Import ini wajib ada!
import com.sysdos.kasirpintar.data.model.Product
import com.sysdos.kasirpintar.data.model.Transaction
import com.sysdos.kasirpintar.data.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class ProductViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val productDao = database.productDao()
    private val categoryDao = database.categoryDao() // [2] Akses DAO Kategori

    // --- DATA PRODUK & KATEGORI ---
    val allProducts: LiveData<List<Product>> = productDao.getAllProducts().asLiveData()

    // LiveData untuk Kategori (Dipakai di ProductEntryActivity & CategoryActivity)
    val allCategories: LiveData<List<Category>> = categoryDao.getAllCategories().asLiveData()

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

    fun delete(product: Product) = viewModelScope.launch(Dispatchers.IO) {
        productDao.deleteProduct(product)
    }

    // --- MANAJEMEN KATEGORI (BARU) ---
    fun addCategory(name: String) {
        viewModelScope.launch {
            categoryDao.insertCategory(Category(name = name))
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            categoryDao.deleteCategory(category)
        }
    }

    // Fungsi Ambil Produk by ID (Untuk Edit)
    fun getProductById(id: Int): LiveData<Product> {
        return productDao.getProductById(id).asLiveData()
    }

    // --- LOGIKA KASIR (SCAN & CART) ---
    fun scanAndAddToCart(barcode: String, onResult: (Product?) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
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

    // --- FUNGSI CHECKOUT (SESUAI STRUKTUR DB BAPAK) ---
    fun checkout(
        subtotal: Double,
        discount: Double,
        tax: Double,
        paymentMethod: String,
        cashReceived: Double,
        changeAmount: Double,
        onResult: (Transaction?) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentItems = _cart.value ?: emptyList()

            // 1. Hitung Total Akhir (Sesuai rumus Bapak)
            val finalTotal = subtotal - discount + tax

            // 2. Buat Ringkasan Item (itemsSummary)
            // Contoh hasil: "Nasi Goreng x2, Es Teh x1"
            val summary = currentItems.joinToString(", ") { "${it.name} x${it.stock}" }

            // 3. Hitung Profit/Keuntungan (Harga Jual - Modal)
            var totalProfit = 0.0
            for (item in currentItems) {
                // Profit per item = (Harga Jual - Harga Modal) * Jumlah Beli
                val itemProfit = (item.price - item.costPrice) * item.stock
                totalProfit += itemProfit
            }

            // 4. Masukkan ke Objek Transaction
            val newTrx = Transaction(
                itemsSummary = summary,
                totalAmount = finalTotal,    // Ini yang tadi namanya 'totalAmount'
                profit = totalProfit,
                timestamp = System.currentTimeMillis(),
                paymentMethod = paymentMethod,
                cashReceived = cashReceived,
                changeAmount = changeAmount,
                subtotal = subtotal,
                discount = discount,
                tax = tax
            )

            // 5. Simpan ke Database
            val id = productDao.insertTransaction(newTrx)

            // 6. Update Stok Barang (Kurangi stok di Gudang)
            for (cartItem in currentItems) {
                // Ambil data asli biar aman
                val originalItem = productDao.getProductById(cartItem.id)
                if (originalItem != null) {
                    val newStock = originalItem.stock - cartItem.stock
                    // Update stok baru
                    val updatedProduct = originalItem.copy(stock = if (newStock < 0) 0 else newStock)
                    productDao.update(updatedProduct)
                }
            }

            // 7. Bersihkan Keranjang di UI
            withContext(Dispatchers.Main) {
                clearCart() // Panggil fungsi pembersih
                // Kembalikan objek transaksi (yang sudah ada ID-nya) ke UI untuk di-print
                onResult(newTrx.copy(id = id.toInt()))
            }
        }
    }

    // --- FUNGSI BERSIHKAN KERANJANG (YANG TADI HILANG) ---
    fun clearCart() {
        _cart.value = emptyList() // Kosongkan List
        calculateTotal()          // Reset Total Harga jadi 0
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