package com.sysdos.kasirpintar.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.sysdos.kasirpintar.data.AppDatabase
import com.sysdos.kasirpintar.data.model.Category
import com.sysdos.kasirpintar.data.model.Product
import com.sysdos.kasirpintar.data.model.Transaction
import com.sysdos.kasirpintar.data.model.User
import com.sysdos.kasirpintar.data.model.ShiftLog // <--- Pastikan Import Ini Ada
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class ProductViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val productDao = database.productDao()
    private val categoryDao = database.categoryDao()
    private val supplierDao = database.supplierDao()
    private val stockLogDao = database.stockLogDao()
    private val shiftDao = database.shiftDao() // <--- DAO untuk Shift

    // --- DATA PRODUK & KATEGORI ---
    val allProducts: LiveData<List<Product>> = productDao.getAllProducts().asLiveData()
    val allCategories: LiveData<List<Category>> = categoryDao.getAllCategories().asLiveData()
    val allSuppliers: LiveData<List<com.sysdos.kasirpintar.data.model.Supplier>> = supplierDao.getAllSuppliers()

    // --- RIWAYAT TRANSAKSI & SHIFT ---
    val allTransactions: LiveData<List<Transaction>> = productDao.getAllTransactions().asLiveData()
    val allShiftLogs: LiveData<List<ShiftLog>> = shiftDao.getAllLogs().asLiveData() // <--- LiveData Shift Log

    // --- KERANJANG BELANJA ---
    private val _cart = MutableLiveData<List<Product>>(emptyList())
    val cart: LiveData<List<Product>> = _cart

    private val _totalPrice = MutableLiveData(0.0)
    val totalPrice: LiveData<Double> = _totalPrice

    // ==========================================
    // 1. MANAJEMEN PRODUK (CRUD)
    // ==========================================
    fun insert(product: Product) = viewModelScope.launch { productDao.insertProduct(product) }
    fun update(product: Product) = viewModelScope.launch { productDao.updateProduct(product) }
    fun delete(product: Product) = viewModelScope.launch(Dispatchers.IO) { productDao.deleteProduct(product) }

    fun getProductById(id: Int): LiveData<Product> {
        return productDao.getProductById(id).asLiveData()
    }

    // ==========================================
    // 2. MANAJEMEN SUPPLIER
    // ==========================================
    fun insertSupplier(supplier: com.sysdos.kasirpintar.data.model.Supplier) {
        viewModelScope.launch { supplierDao.insert(supplier) }
    }
    fun updateSupplier(supplier: com.sysdos.kasirpintar.data.model.Supplier) {
        viewModelScope.launch { supplierDao.update(supplier) }
    }
    fun deleteSupplier(supplier: com.sysdos.kasirpintar.data.model.Supplier) {
        viewModelScope.launch { supplierDao.delete(supplier) }
    }

    // ==========================================
    // 3. MANAJEMEN KATEGORI
    // ==========================================
    fun addCategory(name: String) = viewModelScope.launch {
        categoryDao.insertCategory(Category(name = name))
    }
    fun deleteCategory(category: Category) = viewModelScope.launch {
        categoryDao.deleteCategory(category)
    }

    // ==========================================
    // 4. LOGIKA KASIR (SCAN & CART)
    // ==========================================
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

    fun clearCart() {
        _cart.value = emptyList()
        calculateTotal()
    }

    private fun calculateTotal() {
        val currentCart = _cart.value ?: emptyList()
        var sum = 0.0
        for (item in currentCart) {
            sum += (item.price * item.stock)
        }
        _totalPrice.value = sum
    }

    // ==========================================
    // 5. CHECKOUT & TRANSAKSI
    // ==========================================
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

            // 1. Total Akhir
            val finalTotal = subtotal - discount + tax

            // 2. Summary String untuk Database
            val summary = currentItems.joinToString(";") { item ->
                val totalItem = item.price * item.stock
                "${item.name}|${item.stock}|${item.price.toInt()}|${totalItem.toInt()}"
            }

            // 3. Hitung Profit (Laba)
            var totalProfit = 0.0
            for (item in currentItems) {
                val itemProfit = (item.price - item.costPrice) * item.stock
                totalProfit += itemProfit
            }

            // 4. Create Transaction Object
            val newTrx = Transaction(
                itemsSummary = summary,
                totalAmount = finalTotal,
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

            // 6. Kurangi Stok
            for (cartItem in currentItems) {
                val originalItem = productDao.getProductRaw(cartItem.id)
                if (originalItem != null) {
                    val newStock = (originalItem.stock - cartItem.stock).coerceAtLeast(0)
                    productDao.updateProduct(originalItem.copy(stock = newStock))
                }
            }

            // 7. Selesai
            withContext(Dispatchers.Main) {
                clearCart()
                onResult(newTrx.copy(id = id.toInt()))
            }
        }
    }

    // ==========================================
    // 6. USER MANAGEMENT
    // ==========================================
    val allUsers: LiveData<List<User>> = productDao.getAllUsers().asLiveData()

    fun insertUser(user: User) = viewModelScope.launch { productDao.insertUser(user) }
    fun updateUser(user: User) = viewModelScope.launch { productDao.updateUser(user) }
    fun deleteUser(user: User) = viewModelScope.launch { productDao.deleteUser(user) }
    fun resetPassword(userId: Int, newPass: String) = viewModelScope.launch { productDao.updatePassword(userId, newPass) }

    fun login(username: String, pass: String, onResult: (User?) -> Unit) {
        viewModelScope.launch {
            val user = productDao.login(username, pass)
            onResult(user)
        }
    }

    // ==========================================
    // 7. MANAJEMEN GUDANG (RESTOCK)
    // ==========================================
    fun recordPurchase(log: com.sysdos.kasirpintar.data.model.StockLog) {
        viewModelScope.launch { stockLogDao.insert(log) }
    }

    // ==========================================
    // 8. MANAJEMEN SHIFT (OPEN & CLOSE) ✅
    // ==========================================

    // Fungsi Buka Shift (Simpan Modal Awal)
    fun openShift(kasirName: String, modalAwal: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            val log = ShiftLog(
                type = "OPEN",
                timestamp = System.currentTimeMillis(),
                kasirName = kasirName,
                expectedAmount = modalAwal,
                actualAmount = modalAwal,
                difference = 0.0
            )
            shiftDao.insertLog(log)
        }
    }

    // Fungsi Tutup Shift (Simpan Setoran Akhir)
    fun closeShift(kasirName: String, expected: Double, actual: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            val diff = actual - expected
            val log = ShiftLog(
                type = "CLOSE",
                timestamp = System.currentTimeMillis(),
                kasirName = kasirName,
                expectedAmount = expected,
                actualAmount = actual,
                difference = diff
            )
            shiftDao.insertLog(log)
        }
    }
}