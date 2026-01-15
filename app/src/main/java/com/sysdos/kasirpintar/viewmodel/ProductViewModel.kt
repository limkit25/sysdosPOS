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
        val database = AppDatabase.getDatabase(application)
        repository = ProductRepository(
            application,
            database.productDao(),
            database.shiftDao(),
            database.stockLogDao(),
            database.storeConfigDao()
        )
    }

    private val _currentUserId = MutableLiveData<Int>(0)

    // --- LIVE DATA ---
    val allProducts: LiveData<List<Product>> = repository.allProducts.asLiveData()
    val allCategories: LiveData<List<Category>> = repository.allCategories.asLiveData()
    val allSuppliers: LiveData<List<Supplier>> = repository.allSuppliers.asLiveData()
    val storeConfig: LiveData<StoreConfig?> = repository.storeConfig.asLiveData()

    val allUsers: LiveData<List<User>> = _currentUserId.switchMap { uid ->
        repository.allUsers.asLiveData().map { users -> users.filter { it.id == uid } }
    }

    val allTransactions: LiveData<List<Transaction>> = _currentUserId.switchMap { uid ->
        repository.getTransactionsByUser(uid).asLiveData()
    }

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

    fun login(u: String, p: String, onResult: (User?) -> Unit) = viewModelScope.launch {
        val user = repository.login(u, p)
        if (user != null) _currentUserId.postValue(user.id)
        onResult(user)
    }

    // --- CART ---
    fun addToCart(product: Product, onResult: (String) -> Unit) {
        val currentList = _cart.value?.toMutableList() ?: mutableListOf()
        val index = currentList.indexOfFirst { it.id == product.id }
        val stokGudang = product.stock

        if (index != -1) {
            val existingItem = currentList[index]
            val qtyBaru = existingItem.stock + 1
            if (qtyBaru > stokGudang) { onResult("❌ Stok Habis! Sisa: $stokGudang"); return }
            currentList[index] = existingItem.copy(stock = qtyBaru)
            onResult("Qty ditambah")
        } else {
            if (stokGudang > 0) {
                currentList.add(product.copy(stock = 1))
                onResult("Masuk keranjang")
            } else {
                onResult("❌ Stok Kosong!"); return
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
        val cartItems = _cart.value ?: emptyList()
        if (cartItems.isEmpty()) { onResult(null); return@launch }

        val itemsForUpload = ArrayList(cartItems)
        val summaryBuilder = StringBuilder()
        var totalProfit = 0.0

        for (item in cartItems) {
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
            userId = finalUserId
        )

        val trxId = repository.insertTransaction(trx)

        cartItems.forEach { cartItem ->
            val masterItem = allProducts.value?.find { it.id == cartItem.id }
            if (masterItem != null) repository.update(masterItem.copy(stock = masterItem.stock - cartItem.stock))
        }

        _cart.value = emptyList()
        _totalPrice.value = 0.0

        val finalTrx = trx.copy(id = trxId.toInt())
        onResult(finalTrx)

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

    fun recordPurchase(log: StockLog) = viewModelScope.launch { repository.recordPurchase(log) }

    fun getPurchaseDetails(purchaseId: Long, onResult: (List<StockLog>) -> Unit) = viewModelScope.launch {
        val details = repository.getPurchaseDetails(purchaseId)
        onResult(details)
    }

    // 🔥 FITUR UPDATE INFO TOKO (KIRIM KE VPS)
    fun saveStoreSettings(name: String, address: String, phone: String, footer: String, printerMac: String?) = viewModelScope.launch {
        val config = StoreConfig(1, name, address, phone, footer, printerMac)
        repository.saveStoreConfig(config)
        val uid = _currentUserId.value ?: 0
        if (uid > 0) {
            val allUsers = repository.allUsers.first()
            val currentUser = allUsers.find { it.id == uid }
            if (currentUser != null) {
                sendDataToSalesSystem(currentUser)
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

    // 🔥 FUNGSI KIRIM DATA PROMO (SALES LEAD) KE VPS
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

            Log.d("SalesCRM", "✅ Data LENGKAP dikirim ke VPS Sales")
        } catch (e: Exception) {
            Log.e("SalesCRM", "⚠️ Gagal lapor sales: ${e.message}")
        }
    }

    // ======================================================
    // 🔥 BAGIAN PENTING: CEK LISENSI DENGAN DEVICE ID & MODEL
    // ======================================================

    fun checkServerLicense(email: String) = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val api = ApiClient.webClient

            // 1. AMBIL ID UNIK HP INI
            val deviceId = getDeviceId(getApplication())

            // 2. 🔥 AMBIL MERK HP
            val deviceModel = getDeviceName()

            // 3. 🔥 KIRIM 3 PARAMETER (Email + ID + Model)
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
                Log.d("License", "Status: ${data.status} | Device: $deviceId")
            }
        } catch (e: Exception) {
            Log.e("License", "Gagal cek lisensi: ${e.message}")
        }
    }

    // 🔥 UPDATE: CEK STATUS LENGKAP (AGAR BISA TOLAK YANG BLOCKED)
    fun checkUserOnCloud(email: String, onResult: (com.sysdos.kasirpintar.api.LicenseCheckResponse?) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val api = ApiClient.webClient
                val deviceId = getDeviceId(getApplication())
                val deviceModel = getDeviceName()

                // Kirim data lengkap ke server
                val response = api.checkLicense(email, deviceId, deviceModel).execute()

                if (response.isSuccessful && response.body() != null) {
                    // Balikkan Data Lengkap (Status, Pesan, dll)
                    launch(kotlinx.coroutines.Dispatchers.Main) { onResult(response.body()) }
                } else {
                    launch(kotlinx.coroutines.Dispatchers.Main) { onResult(null) }
                }
            } catch (e: Exception) {
                launch(kotlinx.coroutines.Dispatchers.Main) { onResult(null) }
            }
        }
    }

    // 🔥 HELPER: Ambil ID HP (Android ID)
    private fun getDeviceId(context: Context): String {
        return android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: "UNKNOWN_DEVICE"
    }

    // 🔥 HELPER BARU: AMBIL NAMA/MERK HP
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
}