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
import java.io.File // 🔥 Fix

class ProductViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ProductRepository

    init {
        val productDao = AppDatabase.getDatabase(application).productDao()
        val shiftDao = AppDatabase.getDatabase(application).shiftDao()
        val stockLogDao = AppDatabase.getDatabase(application).stockLogDao()
        val storeConfigDao = AppDatabase.getDatabase(application).storeConfigDao()
        val transactionDao = AppDatabase.getDatabase(application).transactionDao()
        // 🔥 TAMBAHAN BARU:
        val branchDao = AppDatabase.getDatabase(application).branchDao()
        val recipeDao = AppDatabase.getDatabase(application).recipeDao() // 🔥 Phase 24
        val expenseDao = AppDatabase.getDatabase(application).expenseDao() // 🔥 Phase 34

        repository = ProductRepository(
            application,
            productDao,
            shiftDao,
            stockLogDao,
            storeConfigDao,
            branchDao,
            transactionDao,
            recipeDao, // 🔥 Phase 24
            expenseDao // 🔥 Phase 34
        )
    }

    private val _currentUserId = MutableLiveData<Int>(0)

    // --- LIVE DATA ---
    val allProducts: LiveData<List<Product>> = repository.allProducts.asLiveData()

    // 🔥 DATA POS: Hanya Tampilkan Produk JUAL (Bukan Bahan Baku)
    val posProducts: LiveData<List<Product>> = MediatorLiveData<List<Product>>().apply {
        addSource(allProducts) { list ->
            value = list.filter { !it.isIngredient }
        }
    }

    val allCategories: LiveData<List<Category>> = repository.allCategories.asLiveData()
    val allSuppliers: LiveData<List<Supplier>> = repository.allSuppliers.asLiveData()
    val storeConfig: LiveData<StoreConfig?> = repository.storeConfig.asLiveData()

    val allUsers: LiveData<List<User>> = repository.allUsers.asLiveData()

    val allTransactions: LiveData<List<Transaction>> = repository.allTransactions.asLiveData()

    val allShiftLogs: LiveData<List<ShiftLog>> = _currentUserId.switchMap { uid ->
        repository.getShiftLogsByUser(uid).asLiveData()
    }
    // 🔥 PERBAIKAN: List Global (Untuk yang mau lihat semua history)
    val allShiftLogsGlobal: LiveData<List<ShiftLog>> = repository.getAllShiftLogsGlobal().asLiveData()

    val stockLogs: LiveData<List<StockLog>> = repository.allStockLogs.asLiveData()
    val purchaseHistory: LiveData<List<StockLog>> = repository.purchaseHistory.asLiveData()

    private val _cart = MutableLiveData<List<Product>>(emptyList())
    val cart: LiveData<List<Product>> = _cart

    private val _totalPrice = MutableLiveData(0.0)
    val totalPrice: LiveData<Double> = _totalPrice
    private val _isOnline = MutableLiveData<Boolean>(false)
    val isOnline: LiveData<Boolean> = _isOnline

    // 🔥 LIVE DATA LISENSI
    private val _licenseStatus = MutableLiveData<String>()
    val licenseStatus: LiveData<String> = _licenseStatus

    // 🔥 FIX: Tambahkan branchName
    val branchName: LiveData<String> = _currentUserId.switchMap { uid ->
        allUsers.map { users ->
            users.find { it.id == uid }?.branch?.name ?: "Pusat"
        }
    }

    init {
        loadCurrentUser()
        checkAndSync()
        validateCompositeProducts() // 🔥 Fix Flags on Startup
    }

    private fun validateCompositeProducts() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Tunggu sebentar biar DB siap
                kotlinx.coroutines.delay(2000)
                
                // Ambil snapshot produk saat ini
                val all = repository.allProducts.first()
                var fixedCount = 0
                
                all.forEach { p ->
                    val recipes = repository.getIngredientsForProduct(p.id)
                    // Jika punya resep TAPI trackStock masih TRUE -> FIX IT
                    if (recipes.isNotEmpty() && p.trackStock) {
                        Log.d("AutoFix", "Fixing composite flag for ${p.name}")
                        repository.update(p.copy(trackStock = false))
                        fixedCount++
                    }
                }
                
                if (fixedCount > 0) {
                     Log.d("AutoFix", "Fixed $fixedCount composite products.")
                }
            } catch (e: Exception) {
                Log.e("SysdosVM", "AutoFix Error: ${e.message}")
            }
        }
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

    // 🔥 UBAH FUNGSI INI (Hapus Loop while & delay)
    // Ganti 'private' jadi 'fun' (public) biar bisa dipanggil dari Activity
    fun checkAndSync() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // ❌ DISABLE SYNC FOR OFFLINE MODE (Permintaan User: Export/Import Only)
            /*
            try {
                val api = ApiClient.getLocalClient(getApplication())
                val response = api.getCategories(0).execute()
                _isOnline.postValue(response.isSuccessful)

                if (response.isSuccessful) {
                    repository.refreshProductsFromApi()
                }

            } catch (e: Exception) {
                _isOnline.postValue(false)
            }

            try {
                repository.syncStoreConfigFromLocal()
                val currentUid = _currentUserId.value ?: 0
                if (currentUid > 0) {
                     repository.syncShiftLogsFromWeb(currentUid)
                }
            } catch (e: Exception) {
                Log.e("SysdosVM", "Gagal Sync config/shift: ${e.message}")
            }
            */
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
    
    // 🔥 UPDATE DENGAN CALLBACK (Urutan Aman)
    fun updateProductWithCallback(product: Product, onComplete: () -> Unit) = viewModelScope.launch {
        repository.update(product)
        try { repository.updateProductToServer(product) } catch (e: Exception) {}
        launch(kotlinx.coroutines.Dispatchers.Main) { onComplete() }
    }
    fun delete(product: Product) = viewModelScope.launch { repository.delete(product) }

    fun insertCategory(category: Category) = viewModelScope.launch { repository.insertCategory(category) }
    fun deleteCategory(category: Category) = viewModelScope.launch { repository.deleteCategory(category) }

    fun insertSupplier(supplier: Supplier) = viewModelScope.launch { repository.insertSupplier(supplier) }
    fun deleteSupplier(supplier: Supplier) = viewModelScope.launch { repository.deleteSupplier(supplier) }
    fun updateSupplier(supplier: Supplier) = viewModelScope.launch { repository.updateSupplier(supplier) }

    fun insertUser(user: User) = viewModelScope.launch { repository.insertUser(user) }
    
    // 🔥 SAFE INSERT: Pakai Callback untuk memastikan data masuk sebelum pindah Activity
    fun insertUserWithCallback(user: User, onComplete: () -> Unit) = viewModelScope.launch {
        repository.insertUser(user)
        launch(kotlinx.coroutines.Dispatchers.Main) { onComplete() }
    }
    fun updateUser(user: User) = viewModelScope.launch { repository.updateUser(user) }
    fun deleteUser(user: User) = viewModelScope.launch { repository.deleteUser(user) }
    // 🔥 Panggil fungsi replace dari repo
    fun updateVariants(productId: Int, variants: List<ProductVariant>) {
        viewModelScope.launch {
            repository.replaceVariants(productId, variants)
        }
    }

    // 🔥 FITUR RESEP (Phase 24)
    fun getRecipeForProduct(productId: Int, onResult: (List<Recipe>) -> Unit) = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        Log.d("RecipeDebug", "VM: Loading recipes for Product ID: $productId")
        val recipes = repository.getIngredientsForProduct(productId)
        Log.d("RecipeDebug", "VM: Loaded ${recipes.size} recipes for Product ID: $productId")
        launch(kotlinx.coroutines.Dispatchers.Main) { onResult(recipes) }
    }

    fun updateRecipes(productId: Int, recipes: List<Recipe>, onComplete: () -> Unit = {}) = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        Log.d("RecipeDebug", "VM: Updating recipes for Product ID: $productId. Count: ${recipes.size}")
        repository.replaceRecipes(productId, recipes)
        Log.d("RecipeDebug", "VM: Update complete for Product ID: $productId")
        launch(kotlinx.coroutines.Dispatchers.Main) { onComplete() }
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

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // 🔥 SELF-HEALING: Cek kebenaran apakah produk ini punya resep?
            val recipes = repository.getIngredientsForProduct(product.id)
            val hasRecipe = recipes.isNotEmpty()
            
            // Logic TrackStock Final:
            // Jika punya resep -> False (Jangan cek stok gudang)
            // Jika bahan baku -> False
            // Jika tidak punya resep -> Ikuti settingan asli produk
            val realTrackStock = if (hasRecipe || product.isIngredient) false else product.trackStock

            // 🔥 AUTO-CORRECT DB JIKA SALAH
            if (hasRecipe && product.trackStock) {
                Log.d("StockDebug", "FIXING DATA: Product ${product.name} has recipes but trackStock=TRUE. Updating DB...")
                repository.update(product.copy(trackStock = false))
            }

            launch(kotlinx.coroutines.Dispatchers.Main) {
                val currentList = _cart.value?.toMutableList() ?: mutableListOf()
                val index = currentList.indexOfFirst { it.id == product.id }
                val stokGudang = product.stock

                if (index != -1) {
                    // --- LOGIKA TAMBAH QTY ---
                    val existingItem = currentList[index]
                    val qtyBaru = existingItem.stock + 1

                    if (isStockSystemActive && realTrackStock) { // 🔥 Pakai realTrackStock
                        if (qtyBaru > stokGudang) {
                            onResult("❌ Stok Habis! Sisa: $stokGudang")
                            return@launch
                        }
                    }

                    currentList[index] = existingItem.copy(stock = qtyBaru)
                } else {
                    // --- LOGIKA BARANG BARU ---
                    if (isStockSystemActive && realTrackStock) { // 🔥 Pakai realTrackStock
                         Log.d("StockDebug", "AddToCart Check: ${product.name}, Track=$realTrackStock, Stock=$stokGudang")
                        if (stokGudang <= 0) {
                            onResult("❌ Stok Kosong! (Mode Stok: Aktif)")
                            return@launch
                        }
                    }

                    // Jika produk komposit, kita simpan versi aslinya ke keranjang 
                    val productToCart = if (realTrackStock != product.trackStock) product.copy(trackStock = realTrackStock) else product
                    
                    currentList.add(productToCart.copy(stock = 1))
                }

                // ✅ UPDATE DATA
                _cart.value = currentList
                calculateTotal()
                onResult("Berhasil")
            }
        }
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

    // 🔥 Phase 30: Online Order Markup
    private var _currentMarkupPercentage = 0.0
    private var _currentOrderType = "Dine In"

    // 🔥 Function to Set Order Type (Called from UI Spinner)
    // 🔥 Function to Set Order Type (Called from UI Spinner)
    fun setOrderType(type: String) {
        _currentOrderType = type
        
        // Ambil Config Terbaru (Pastikan StoreConfig sudah di-load di Activity)
        val config = storeConfig.value
        
        // Ambil Persentase (Default 20.0 jika null) dan Bagi 100 agar jadi desimal (0.2)
        val goFoodPct = (config?.markupGoFood ?: 20.0) / 100.0
        val grabPct = (config?.markupGrab ?: 20.0) / 100.0
        val shopeePct = (config?.markupShopee ?: 20.0) / 100.0

        _currentMarkupPercentage = when (type) {
            "GoFood" -> goFoodPct
            "GrabFood" -> grabPct
            "ShopeeFood" -> shopeePct
            else -> 0.0
        }
        calculateTotal() // Recalculate total immediately
    }

    private fun calculateTotal() {
        var baseTotal = 0.0
        _cart.value?.forEach { baseTotal += it.price * it.stock }
        
        // Apply Markup (Total yg ditampilkan ke UI sudah termasuk Markup)
        val markupAmount = baseTotal * _currentMarkupPercentage
        _totalPrice.value = baseTotal + markupAmount
    }

    // Helper untuk UI Checkout Breakdown
    fun getCurrentOrderType(): String = _currentOrderType

    fun getBaseSubtotal(): Double {
        var baseTotal = 0.0
        _cart.value?.forEach { baseTotal += it.price * it.stock }
        return baseTotal
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

    fun importProducts(inputStream: java.io.InputStream, onResult: (String) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // val inputStream is passed directly
                val parsedList = com.sysdos.kasirpintar.utils.CsvImportHelper.parseCsv(inputStream)
                
                if (parsedList.isEmpty()) {
                    launch(kotlinx.coroutines.Dispatchers.Main) { onResult("Gagal: File kosong atau format salah!") }
                    return@launch
                }

                parsedList.forEach { parsed ->
                    val product = parsed.product
                    val variants = parsed.variants
                    
                    // 1. Insert Parent Product -> Get ID
                    val parentId = repository.insertProductReturnId(product).toInt()
                    
                    // 2. Insert Variants (If any)
                    if (variants.isNotEmpty()) {
                        val finalVariants = variants.map { it.copy(productId = parentId) }
                        repository.insertVariants(finalVariants)
                    }
                }

                launch(kotlinx.coroutines.Dispatchers.Main) { onResult("✅ Sukses Import ${parsedList.size} Produk!") }
            } catch (e: Exception) {
                launch(kotlinx.coroutines.Dispatchers.Main) { onResult("Error: ${e.message}") }
            }
        }
    }
    fun syncUser(user: User) = viewModelScope.launch { repository.syncUserToServer(user) }

    fun checkout(
        subtotal: Double, discount: Double, tax: Double, paymentMethod: String,
        cashReceived: Double, changeAmount: Double, note: String = "", userId: Int = 0,
        onResult: (Transaction?) -> Unit
    ) = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { // 🔥 Run di IO Thread

        // 1. Validasi Keranjang
        val cartItems = _cart.value ?: emptyList()
        if (cartItems.isEmpty()) { 
             launch(kotlinx.coroutines.Dispatchers.Main) { onResult(null) }
             return@launch 
        }

        // 🔥 VALIDASI STOK SEBELUM PROSES (PRE-CHECK)
        // Ambil Settingan Toko
        val prefs = getApplication<Application>().getSharedPreferences("store_prefs", Context.MODE_PRIVATE)
        val isStockSystemActive = prefs.getBoolean("use_stock_system", true)

        if (isStockSystemActive) {
            val totalRequiredInfo = mutableMapOf<Int, Double>() // ID -> Qty Needed

            cartItems.forEach { item ->
                val realId = if (item.id < 0) item.parentId else item.id
                val qtyJual = item.stock.toDouble()
                
                // Cek apakah punya resep?
                val ingredients = repository.getIngredientsForProduct(realId)
                if (ingredients.isNotEmpty()) {
                    ingredients.forEach { ing ->
                        val current = totalRequiredInfo.getOrDefault(ing.ingredientId, 0.0)
                        totalRequiredInfo[ing.ingredientId] = current + (ing.quantity * qtyJual)
                    }
                } else {
                    val current = totalRequiredInfo.getOrDefault(realId, 0.0)
                    totalRequiredInfo[realId] = current + qtyJual
                }
            }

            // Cek ke Database
            for ((id, needed) in totalRequiredInfo) {
                val dbItem = repository.getProductById(id)
                if (dbItem != null && dbItem.trackStock) {
                    if (dbItem.stock < needed) {
                        val name = dbItem.name
                        launch(kotlinx.coroutines.Dispatchers.Main) { 
                            Toast.makeText(getApplication(), "❌ Stok Tidak Cukup!\n$name kurang. (Sisa: ${dbItem.stock}, Butuh: ${needed})", Toast.LENGTH_LONG).show()
                            onResult(null) 
                        }
                        return@launch
                    }
                }
            }
        }

        // 2. Siapkan Data Transaksi (Header & Summary)
        // 🔥 RE-CALCULATE BASE SUBTOTAL (Murni Barang)
        var baseSubtotal = 0.0
        val itemsForUpload = ArrayList(cartItems)
        val summaryBuilder = StringBuilder()
        var totalProfit = 0.0

        for (item in cartItems) {
            baseSubtotal += item.price * item.stock // Harga asli tanpa markup
            
            // Format: Nama|Qty|Harga|Total
            summaryBuilder.append("${item.name}|${item.stock}|${item.price.toLong()}|${(item.price * item.stock).toLong()};")
            totalProfit += (item.price - item.costPrice) * item.stock
        }
        
        // 🔥 HITUNG MARKUP
        val markupValue = baseSubtotal * _currentMarkupPercentage

        val finalUserId = if (userId != 0) userId else (_currentUserId.value ?: 0)
        val finalSummary = summaryBuilder.toString().removeSuffix(";") + (if(note.isNotEmpty()) " || $note" else "")

        val trx = Transaction(
            timestamp = System.currentTimeMillis(),
            itemsSummary = finalSummary,
            
            // 🔥 Formula Total: Base + Markup + Tax - Discount
            // Perhatikan parameter 'subtotal' yang dikirim UI mungkin sudah mengandung Markup karena UI observe totalPrice.
            // Jadi kita abaikan param 'subtotal' dan pakai 'baseSubtotal' hitungan sendiri biar akurat.
            totalAmount = baseSubtotal + markupValue + tax - discount,
            subtotal = baseSubtotal,
            
            discount = discount, 
            tax = tax,
            profit = totalProfit + markupValue, // 🔥 Profit juga nambah dari Markup (Service fee itu untung)
            
            paymentMethod = paymentMethod,
            cashReceived = cashReceived, changeAmount = changeAmount,
            userId = finalUserId,
            note = note,
            
            // 🔥 DATA MARKUP
            orderType = _currentOrderType,
            markupAmount = markupValue
        )

        // 3. Simpan Transaksi ke Database
        val trxId = repository.insertTransaction(trx)

        // ============================================================
        // 🔥 LOGIKA STOK & RESEP (PHASE 24) 🔥
        // ============================================================

        // Ambil Settingan Toko
        // Note: prefs & isStockSystemActive already declared above for Pre-Check
        
        // HANYA KURANGI STOK JIKA SAKLAR ON
        if (isStockSystemActive) {

            // Map ID Produk -> Jumlah Kurang (Double agar support resep pecahan 0.5 kg)
            val stockDeductions = mutableMapOf<Int, Double>()

            cartItems.forEach { item ->
                // Jika ID item < 0 (Varian), pakai parentId. Jika positif, pakai ID sendiri.
                val realId = if (item.id < 0) item.parentId else item.id
                val qtyJual = item.stock.toDouble()
                
                // 1. Cek apakah produk ini punya Resep?
                val ingredients = repository.getIngredientsForProduct(realId)
                
                if (ingredients.isNotEmpty()) {
                    // A. PRODUK KOMPOSIT: Kurangi stok bahan baku
                    ingredients.forEach { ing ->
                        val totalBahan = ing.quantity * qtyJual
                        val current = stockDeductions.getOrDefault(ing.ingredientId, 0.0)
                        stockDeductions[ing.ingredientId] = current + totalBahan
                    }
                    // Stok produk utama TIDAK dikurangi (stok virtual)
                } else {
                    // B. PRODUK BIASA: Kurangi stok produk itu sendiri
                    val current = stockDeductions.getOrDefault(realId, 0.0)
                    stockDeductions[realId] = current + qtyJual
                }
            }

            // Eksekusi Potong Stok di Database
            stockDeductions.forEach { (productId, totalQtyToDeduct) ->
                // Ambil data produk TERBARU dari Database
                val masterItem = repository.getProductById(productId)

                if (masterItem != null && masterItem.trackStock) { // 🔥 Cek Flag trackStock
                    val newStock = masterItem.stock - totalQtyToDeduct.toInt() // Convert Double ke Int (sementara DB pakai INT)
                    repository.update(masterItem.copy(stock = newStock))
                }
            }
        }
        
        // ============================================================

        // 4. Reset Keranjang & Selesai
        launch(kotlinx.coroutines.Dispatchers.Main) {
            _cart.value = emptyList()
            _totalPrice.value = 0.0
            
            val finalTrx = trx.copy(id = trxId.toInt())
            onResult(finalTrx)
        }

        // 5. Upload ke Server (Background)
        try { repository.uploadTransactionToServer(trx.copy(id = trxId.toInt()), itemsForUpload) } catch (e: Exception) {}
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
    // 🔥 UPDATE: Tambah Parameter 'tax' dan Markup
    fun saveStoreSettings(name: String, address: String, phone: String, footer: String, printerMac: String?, tax: Double,
                          markupGoFood: Double, markupGrab: Double, markupShopee: Double) = viewModelScope.launch {

        // Membuat objek konfigurasi baru
        val config = StoreConfig(
            id = 1, // ID Selalu 1
            storeName = name,
            storeAddress = address,
            storePhone = phone,
            storeEmail = "", // Default kosong (karena tidak ada input di UI Settings)
            strukFooter = footer, // 🔥 Pastikan ini 'strukFooter' sesuai file StoreConfig.kt Anda
            printerMac = printerMac,
            taxPercentage = tax, // 🔥 Simpan Pajak
            markupGoFood = markupGoFood,
            markupGrab = markupGrab,
            markupShopee = markupShopee
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
        // 🔥 FIX OFFLINE LOGOUT: JANGAN HAPUS DATA DATABASE, CUKUP SESSION SAJA
        // Agar kalau offline, user masih bisa login pakai data lokal yang tersimpan.
        // try { repository.clearAllData() } catch (e: Exception) {} 
        
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

    fun checkServerLicense(userEmail: String) = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        try {
            // 🔥 LOGIKA BARU: Cek Lisensi Toko (Bukan User)
            // Ambil email pemilik toko dari Config
            val storeConfig = repository.getStoreConfigDirect()
            val ownerEmail = if (!storeConfig?.storeEmail.isNullOrEmpty()) storeConfig!!.storeEmail else userEmail

            val api = ApiClient.webClient
            val deviceId = getDeviceId(getApplication())
            val deviceModel = getDeviceName()
            val response = api.checkLicense(ownerEmail, deviceId, deviceModel).execute()

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
                        editor.putString("expiration_info", data.message) // 🔥 Simpan info expired

                        // 🔥 FIX: SIMPAN EMAIL OWNER KE DATABASE AGAR USER LAIN KEBAGIAN LISENSI
                        // (Hanya jika email ini benar-benar Premium)
                        val currentConfig = repository.getStoreConfigDirect()
                        if (currentConfig != null) {
                            // Update Email saja
                            repository.saveStoreConfig(currentConfig.copy(storeEmail = ownerEmail))
                        } else {
                            // Buat Config Baru (Default)
                            repository.saveStoreConfig(StoreConfig(
                                storeName = "Toko Saya",
                                storeAddress = "Alamat Belum Diatur",
                                storePhone = "-", 
                                storeEmail = ownerEmail // Simpan disini
                            ))
                        }
                    }
                    "EXPIRED" -> {
                        editor.putBoolean("is_full_version", false)
                        editor.putBoolean("is_expired", true)
                        
                        // 🔥 GUNAKAN PESAN DARI SERVER AGAR DINAMIS (BISA TRIAL ATAU PREMIUM EXPIRED)
                        val msg = if(data.message.isNotEmpty()) data.message else "Masa Aktif Habis! Silakan Perpanjang."
                        editor.putString("license_msg", msg)
                        editor.putString("expiration_info", "Expired")
                    }
                    else -> {
                        editor.putBoolean("is_full_version", false)
                        editor.putBoolean("is_expired", false)
                        editor.putString("license_msg", "Trial Sisa ${data.days_left} Hari")
                        editor.putString("expiration_info", "Trial (${data.days_left} Hari)")
                    }
                }
                editor.apply()

                // 🔥 UPDATE LIVE DATA
                _licenseStatus.postValue(data.message)
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

    // 🔥 PENGELUARAN (PHASE 34)
    fun insertExpense(amount: Double, category: String, note: String, onComplete: () -> Unit) = viewModelScope.launch {
        val uid = _currentUserId.value ?: 0
        val expense = Expense(
            amount = amount,
            category = category,
            note = note,
            timestamp = System.currentTimeMillis(),
            userId = uid
            // branchId default 0
        )
        repository.insertExpense(expense)
        launch(kotlinx.coroutines.Dispatchers.Main) { onComplete() }
    }
    
    fun getExpensesByDate(start: Long, end: Long, onResult: (List<Expense>) -> Unit) = viewModelScope.launch {
        val list = repository.getExpensesByDateRange(start, end)
        launch(kotlinx.coroutines.Dispatchers.Main) { onResult(list) }
    }
    
    // For Export Feature
    fun exportExpensesToCsv(start: Long, end: Long, onResult: (File?, String?) -> Unit) = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        val list = repository.getExpensesByDateRange(start, end)
        if(list.isEmpty()) {
            launch(kotlinx.coroutines.Dispatchers.Main) { onResult(null, "Tidak ada data pada tanggal yang dipilih.") }
            return@launch
        }
        
        // Use Helper to generate CSV
        val file = com.sysdos.kasirpintar.utils.CsvExportHelper.generateExpenseReport(getApplication(), list, start, end)
        launch(kotlinx.coroutines.Dispatchers.Main) { 
            if (file != null) onResult(file, null)
            else onResult(null, "Terjadi kesalahan saat membuat file.")
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
                        type = "VOID", // Tipe Khusus
                        
                        // 🔥 OTOMATIS GENERATE NOMOR VOID
                        invoiceNumber = "VOID-${trx.id}"
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

    // 🔥 FITUR BARU: Export Return/Void
    suspend fun getLogsByDateRangeAndType(start: Long, end: Long, type: String): List<StockLog> {
        return repository.getLogsByDateRangeAndType(start, end, type)
    }
    // ==========================================
    // 📦 FITUR RETUR STOK (VERSI FIX SESUAI DB ANDA)
    // ==========================================
    fun returnStockToSupplier(product: Product, qtyToReturn: Int, reason: String, supplierName: String, returnNumber: String) = viewModelScope.launch {

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
            type = "OUT",
            
            // 🔥 SIMPAN NOMOR RETUR DI KOLOM INVOICE
            invoiceNumber = returnNumber
        )

        // 3. Simpan Log (Pakai fungsi recordPurchase yang sudah ada di Repo)
        repository.recordPurchase(log)
    }
    // Di ProductViewModel.kt
    fun getUserByEmail(email: String, onResult: (User?) -> Unit) = viewModelScope.launch {
        val user = repository.getUserByEmail(email)
        onResult(user)
    }

    fun getUserByPhone(phone: String, onResult: (User?) -> Unit) = viewModelScope.launch {
        val user = repository.getUserByPhone(phone)
        onResult(user)
    }

    // 🔥 FITUR BARU: Cek User di Server Lokal (Fallback jika tidak ada di Cloud)
    fun checkUserOnLocalServer(email: String, onResult: (User?) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val api = ApiClient.getLocalClient(getApplication())
                val response = api.getAllUsers().execute()
                if (response.isSuccessful && response.body() != null) {
                    val users = response.body()!!
                    val found = users.find { it.username == email }
                    launch(kotlinx.coroutines.Dispatchers.Main) { onResult(found) }
                } else {
                    launch(kotlinx.coroutines.Dispatchers.Main) { onResult(null) }
                }
            } catch (e: Exception) {
                launch(kotlinx.coroutines.Dispatchers.Main) { onResult(null) }
            }
        }
    }

    // 🔥 SYNC STORE CONFIG KHUSUS SAAT LOGIN LOKAL (Agar dapat Email Owner)
    fun syncStoreConfigFromLocal(branchId: Int, onComplete: () -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val api = ApiClient.getLocalClient(getApplication())
                // Request Config untuk Branch user ini (atau 0 jika pusat)
                // Server akan return config yang berisi email owner
                val response = api.getStoreConfig(branchId).execute()
                
                if (response.isSuccessful && response.body() != null) {
                    val serverConfig = response.body()!!.copy(id = 1)
                    
                    // Simpan ke DB Local
                    repository.saveStoreConfig(serverConfig)
                    
                    // Simpan ke Prefs
                    val prefsStore = getApplication<Application>().getSharedPreferences("store_prefs", Context.MODE_PRIVATE)
                    prefsStore.edit().apply {
                        putString("name", serverConfig.storeName)
                        putString("address", serverConfig.storeAddress)
                        putString("phone", serverConfig.storePhone)
                        putString("email", serverConfig.strukFooter)
                        apply()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                launch(kotlinx.coroutines.Dispatchers.Main) { onComplete() }
            }
        }
    }


}