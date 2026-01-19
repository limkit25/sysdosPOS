package com.sysdos.kasirpintar

import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import android.util.Log
import android.widget.Toast
import com.sysdos.kasirpintar.api.ApiClient
import com.sysdos.kasirpintar.api.ProductResponse
import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import com.sysdos.kasirpintar.data.model.Category
import com.sysdos.kasirpintar.data.model.Product
import com.sysdos.kasirpintar.utils.PrinterHelper
import com.sysdos.kasirpintar.viewmodel.ProductAdapter
import com.sysdos.kasirpintar.viewmodel.ProductViewModel
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: ProductViewModel
    private lateinit var productAdapter: ProductAdapter

    // UI Elements
    private lateinit var svSearch: androidx.appcompat.widget.SearchView
    private lateinit var llCategories: LinearLayout
    private lateinit var tvCartCount: TextView
    private lateinit var tvCartTotal: TextView
    private lateinit var btnCheckout: Button

    private var fullList: List<Product> = ArrayList()
    private var selectedCategory: String = "Semua"

    // Printer
    private lateinit var printerHelper: PrinterHelper
    private var selectedPrinterAddress: String? = null
    private var cartDialog: android.app.Dialog? = null
    // PREFERENCE UNTUK SHIFT (MODAL & SESI)
    private lateinit var session: SharedPreferences
    private lateinit var shiftPrefs: SharedPreferences

    private lateinit var drawerLayout: androidx.drawerlayout.widget.DrawerLayout
    private lateinit var navView: com.google.android.material.navigation.NavigationView

    // 🔥 HELPER: Ambil Key Unik Berdasarkan Username yg Login
    private val currentUserKey: String
        get() = session.getString("username", "default") ?: "default"

    // SCANNER LAUNCHER
    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result: ScanIntentResult ->
        if (result.contents != null) {
            viewModel.scanAndAddToCart(
                barcode = result.contents,
                onResult = { product ->
                    if (product == null) Toast.makeText(this, "Barang tidak ditemukan", Toast.LENGTH_SHORT).show()
                },
                onError = { msg -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // ===============================================================
        // 🔥 2. SETUP MENU SAMPING (NAVIGATION DRAWER) - MASUKKAN DISINI
        // ===============================================================
        drawerLayout = findViewById(R.id.drawerLayout)
        navView = findViewById(R.id.navView)
        val btnMenuDrawer = findViewById<android.view.View>(R.id.btnMenuDrawer) // Tombol Burger

        // A. Aksi Klik Tombol Burger -> Buka Laci
        btnMenuDrawer.setOnClickListener {
            drawerLayout.openDrawer(androidx.core.view.GravityCompat.START)
        }

        // B. Setup Header (Nama User & Role)
        // Ambil data dari session yang nanti di-init di bawah, atau ambil langsung disini
        val tempSession = getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
        val realName = tempSession.getString("fullname", "Kasir")
        val role = tempSession.getString("role", "kasir")

        val headerView = navView.getHeaderView(0)
        headerView.findViewById<TextView>(R.id.tvHeaderName).text = realName
        headerView.findViewById<TextView>(R.id.tvHeaderRole).text = "Role: ${role?.uppercase()}"

        // C. Aksi Klik Item Menu
        navView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    // Pindah ke Dashboard
                    startActivity(Intent(this, DashboardActivity::class.java))
                    finish() // Tutup halaman Kasir
                }
                R.id.nav_kasir -> {
                    // Sudah di halaman Kasir, tutup laci saja
                    drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
                }
                R.id.nav_laporan -> startActivity(Intent(this, SalesReportActivity::class.java))
                R.id.nav_stok -> startActivity(Intent(this, ProductListActivity::class.java))
                R.id.nav_user -> startActivity(Intent(this, UserListActivity::class.java))
                R.id.nav_logout -> {
                    // Logika logout bisa dipanggil disini atau redirect ke Dashboard dulu
                    Toast.makeText(this, "Silakan Logout dari Dashboard", Toast.LENGTH_SHORT).show()
                }
            }
            drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
            true
        }
        // 1. INIT PREFERENCES
        session = getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
        shiftPrefs = getSharedPreferences("shift_prefs", Context.MODE_PRIVATE)

        printerHelper = PrinterHelper(this)
        viewModel = ViewModelProvider(this)[ProductViewModel::class.java]

        // 2. AUTO CONNECT PRINTER
        val prefs = getSharedPreferences("store_prefs", Context.MODE_PRIVATE)
        selectedPrinterAddress = prefs.getString("printer_mac", null)
        if (selectedPrinterAddress != null) connectPrinterSilent(selectedPrinterAddress!!)

        // 3. BIND VIEW
        svSearch = findViewById(R.id.svSearch)
        llCategories = findViewById(R.id.llCategories)
        tvCartCount = findViewById(R.id.tvCartCount)
        tvCartTotal = findViewById(R.id.tvCartTotal)
        btnCheckout = findViewById(R.id.btnCheckout)
        val btnScan = findViewById<ImageButton>(R.id.btnScan)
        val rvProducts = findViewById<RecyclerView>(R.id.rvProducts)

        // Setup RecyclerView
        productAdapter = ProductAdapter(
            onItemClick = { product ->
                handleProductClick(product)
            },
            onItemLongClick = { product -> showCartActionDialog(product) }
        )

        // 1. Hitung Lebar Layar (Pixel ke DP)
        val displayMetrics = resources.displayMetrics
        val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density

        // 2. Tentukan Jumlah Kolom Berdasarkan Ukuran Layar
        if (screenWidthDp >= 800) {
            // TABLET BESAR / 2K (2560px) -> Pakai GRID 4 Kolom
            rvProducts.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 4)
        } else if (screenWidthDp >= 600) {
            // TABLET KECIL -> Pakai GRID 3 Kolom
            rvProducts.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 3)
        } else {
            // HP BIASA -> Tetap LIST KE BAWAH (1 Kolom)
            rvProducts.layoutManager = LinearLayoutManager(this)
        }
        rvProducts.adapter = productAdapter

        // 4. OBSERVE DATA
        viewModel.allProducts.observe(this) { products ->
            fullList = products
            filterData()
        }
        viewModel.allCategories.observe(this) { categories ->
            setupCategoryButtons(categories ?: emptyList())
        }
        viewModel.cart.observe(this) { cartItems ->
            productAdapter.updateCartCounts(cartItems)
            tvCartCount.text = "${cartItems.sumOf { it.stock }} Item"
            tvCartTotal.text = formatRupiah(viewModel.totalPrice.value ?: 0.0)

            // Cukup update harga di header biru saja kalau dialog lagi terbuka
            if (cartDialog?.isShowing == true) {
                val tvTotalTop = cartDialog?.findViewById<TextView>(R.id.tvPreviewTotalTop)
                tvTotalTop?.text = formatRupiah(viewModel.totalPrice.value ?: 0.0)
            }
        }
        viewModel.totalPrice.observe(this) {
            tvCartTotal.text = formatRupiah(it)
        }
        // BIND VIEW BARU
        val cardStatus = findViewById<androidx.cardview.widget.CardView>(R.id.cardStatusServer)
        val tvStatus = findViewById<TextView>(R.id.tvStatusServer)

        viewModel.isOnline.observe(this) { connected ->

            if (connected) {
                cardStatus.setCardBackgroundColor(android.graphics.Color.parseColor("#4CAF50"))
                tvStatus.text = "ONLINE"
            } else {
                cardStatus.setCardBackgroundColor(android.graphics.Color.parseColor("#FF5252"))
                tvStatus.text = "OFFLINE"
            }
        }
        viewModel.storeConfig.observe(this) { config ->
            // Pancingan data toko agar tombol pajak update
        }


        // 5. SEARCH LISTENER
        svSearch.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                filterData()
                return true
            }
        })

        // 6. TOMBOL SCAN & CHECKOUT
        btnScan.setOnClickListener {
            val options = ScanOptions()
            options.setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
            options.setBeepEnabled(true)
            options.setOrientationLocked(true)
            options.setCaptureActivity(ScanActivity::class.java)
            barcodeLauncher.launch(options)
        }

        val bottomBar = findViewById<androidx.cardview.widget.CardView>(R.id.bottomBar)
        bottomBar.setOnClickListener { showCartPreview() }

        btnCheckout.setOnClickListener {
            if (viewModel.totalPrice.value == 0.0) {
                Toast.makeText(this, "Keranjang Kosong!", Toast.LENGTH_SHORT).show()
            } else {
                showCartPreview()
            }
        }

        // 7. HANDLE BACK BUTTON (YANG DIPERBARUI)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // 🔥 Cek dulu: Kalau menu samping terbuka, tutup menu dulu
                if (drawerLayout.isDrawerOpen(androidx.core.view.GravityCompat.START)) {
                    drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
                } else {
                    // Kalau menu tertutup, baru jalankan fungsi Back biasa (Finish)
                    finish()
                }
            }
        })


        // 9. TES KONEKSI
        com.sysdos.kasirpintar.api.ApiClient.getLocalClient(this).getProducts(0).enqueue(object : retrofit2.Callback<List<com.sysdos.kasirpintar.api.ProductResponse>> {
            override fun onResponse(call: retrofit2.Call<List<com.sysdos.kasirpintar.api.ProductResponse>>, response: retrofit2.Response<List<com.sysdos.kasirpintar.api.ProductResponse>>) {
                if (response.isSuccessful) {
                    Toast.makeText(this@MainActivity, "Koneksi OK!", Toast.LENGTH_LONG).show()
                }
            }
            override fun onFailure(call: retrofit2.Call<List<com.sysdos.kasirpintar.api.ProductResponse>>, t: Throwable) {}
        })
    }
    private fun updateTotalHeader() {
        val tvTotalTop = cartDialog?.findViewById<TextView>(R.id.tvPreviewTotalTop)
        viewModel.totalPrice.value?.let { total ->
            tvTotalTop?.text = formatRupiah(total)
        }
        // Update juga total yang ada di bar bawah dashboard utama
        tvCartTotal.text = formatRupiah(viewModel.totalPrice.value ?: 0.0)
    }


    // ==========================================
    // 💰 PEMBAYARAN & CHECKOUT (UPDATE: LOGIKA PIUTANG)
    // ==========================================

    private fun showPaymentDialog() {
        val currentSubtotal = viewModel.totalPrice.value ?: 0.0
        if (currentSubtotal <= 0) return

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_payment, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // --- BINDING VIEW ---
        val tvTitle = dialogView.findViewById<TextView>(R.id.tvPaymentTitle)
        val etReceived = dialogView.findViewById<EditText>(R.id.etAmountReceived)
        val btnBack = dialogView.findViewById<View>(R.id.btnBackPayment)
        val btnConfirm = dialogView.findViewById<View>(R.id.btnConfirmPayment)
        val spMethod = dialogView.findViewById<Spinner>(R.id.spPaymentMethod)
        val gridQuickCash = dialogView.findViewById<View>(R.id.gridQuickCash)

        // Field Input Tambahan
        val cbCredit = dialogView.findViewById<CheckBox>(R.id.cbCredit) // Checkbox Piutang
        val etCustomer = dialogView.findViewById<EditText>(R.id.etCustomer)
        val etTable = dialogView.findViewById<EditText>(R.id.etTable)
        val etNote = dialogView.findViewById<EditText>(R.id.etNote)

        // Tombol Diskon & Pajak
        val btnDisc = dialogView.findViewById<Button>(R.id.btnDisc)
        val btnTax = dialogView.findViewById<Button>(R.id.btnTax)

        // Tombol Uang Cepat
        val btnExact = dialogView.findViewById<Button>(R.id.btnExactMoney)
        val btn20k = dialogView.findViewById<Button>(R.id.btn20k)
        val btn50k = dialogView.findViewById<Button>(R.id.btn50k)
        val btn100k = dialogView.findViewById<Button>(R.id.btn100k)
        val btnOther = dialogView.findViewById<Button>(R.id.btnOtherAmount)

        var grandTotal = currentSubtotal

        // Logic Diskon
        var discountType = "NONE"
        var discountInput = 0.0
        var discValue = 0.0

        // 🔥 UPDATE 1: AMBIL PAJAK DEFAULT DARI SETTINGAN TOKO
        val storeConfig = viewModel.storeConfig.value
        var taxPercentage = storeConfig?.taxPercentage ?: 0.0
        var taxValue = 0.0

        val methods = arrayOf("Tunai", "QRIS", "Debit", "Transfer")
        spMethod.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, methods)

        // --- FUNGSI HITUNG ULANG ---
        fun recalculate() {
            // Hitung Diskon
            discValue = when (discountType) {
                "PERCENT" -> currentSubtotal * (discountInput / 100)
                "NOMINAL" -> discountInput
                else -> 0.0
            }

            // Hitung Pajak (Setelah Diskon)
            val afterDisc = if (currentSubtotal > discValue) currentSubtotal - discValue else 0.0
            taxValue = afterDisc * (taxPercentage / 100)

            // Total Akhir
            grandTotal = afterDisc + taxValue

            tvTitle.text = "Total : ${formatRupiah(grandTotal)}"

            // Update UI Tombol (Warna Biru/Putih)
            val activeBg = R.drawable.bg_solid_primary // Pastikan drawables ini ada
            val inactiveBg = R.drawable.bg_outline_primary

            // Update Tampilan Tombol Pajak
            val isTaxOn = taxPercentage > 0
            btnTax.setBackgroundResource(if (isTaxOn) activeBg else inactiveBg)
            btnTax.setTextColor(if (isTaxOn) Color.WHITE else Color.parseColor("#1976D2"))
            // Hapus .0 di belakang angka (misal 11.0% jadi 11%)
            val taxText = taxPercentage.toString().removeSuffix(".0")
            btnTax.text = if (isTaxOn) "Tax : $taxText%" else "Tax : 0%"

            // Update Tampilan Tombol Diskon
            val isDiscOn = discountType != "NONE"
            btnDisc.setBackgroundResource(if (isDiscOn) activeBg else inactiveBg)
            btnDisc.setTextColor(if (isDiscOn) Color.WHITE else Color.parseColor("#1976D2"))
            btnDisc.text = when (discountType) {
                "PERCENT" -> "Disc : ${discountInput.toInt()}%"
                "NOMINAL" -> "Disc : ${formatRupiah(discountInput)}"
                else -> "Disc : 0%"
            }

            // Update Field Uang Masuk Otomatis
            if (cbCredit.isChecked) {
                etReceived.setText("0") // Piutang = 0
            } else if (spMethod.selectedItem?.toString() != "Tunai") {
                etReceived.setText(grandTotal.toInt().toString()) // Non-Tunai = Pas
            }
        }

        // Logic Checkbox Piutang
        cbCredit.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                etCustomer.requestFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.showSoftInput(etCustomer, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)

                etCustomer.error = "Wajib diisi untuk Piutang"
                etCustomer.hint = "Nama Penghutang (Wajib)"

                etReceived.setText("0")
                gridQuickCash.visibility = View.GONE
                spMethod.isEnabled = false
            } else {
                etCustomer.hint = "Pelanggan"
                etCustomer.error = null
                etReceived.isEnabled = true
                spMethod.isEnabled = true

                if (spMethod.selectedItem.toString() == "Tunai") {
                    gridQuickCash.visibility = View.VISIBLE
                    etReceived.setText("0")
                } else {
                    etReceived.setText(grandTotal.toInt().toString())
                }
            }
        }

        spMethod.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (cbCredit.isChecked) return
                if (methods[position] == "Tunai") {
                    gridQuickCash.visibility = View.VISIBLE
                    etReceived.isEnabled = true
                    etReceived.setText("0"); etReceived.requestFocus()
                } else {
                    gridQuickCash.visibility = View.GONE
                    etReceived.isEnabled = false
                    etReceived.setText(grandTotal.toInt().toString())
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Logic Tombol Pajak
        btnTax.setOnClickListener {
            // Cek lagi nilai config terbaru
            val currentConfig = viewModel.storeConfig.value
            val lockedTax = currentConfig?.taxPercentage ?: 0.0

            if (lockedTax > 0) {
                // 🔒 JIKA ADA PAJAK TOKO -> KUNCI TOMBOL & TAMPILKAN PERINGATAN
                Toast.makeText(this, "🔒 Pajak ${lockedTax.toString().removeSuffix(".0")}% dikunci oleh sistem", Toast.LENGTH_SHORT).show()

                // (Opsional) Goyangkan tombol biar user sadar kalau dikunci
                btnTax.animate().translationX(10f).setDuration(50).withEndAction {
                    btnTax.animate().translationX(-10f).setDuration(50).withEndAction {
                        btnTax.translationX = 0f
                    }
                }.start()
            } else {
                // 🔓 JIKA 0 -> BOLEH EDIT MANUAL
                val layout = LinearLayout(this); layout.orientation = LinearLayout.VERTICAL; layout.setPadding(50, 40, 50, 10)
                val etInput = EditText(this); etInput.hint = "Persen Pajak (ex: 11)"
                etInput.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL

                if (taxPercentage > 0) etInput.setText(taxPercentage.toString().removeSuffix(".0"))
                layout.addView(etInput)

                AlertDialog.Builder(this).setTitle("Atur Pajak (%)").setView(layout)
                    .setPositiveButton("TERAPKAN") { _, _ ->
                        val str = etInput.text.toString()
                        if (str.isNotEmpty()) {
                            taxPercentage = str.toDouble()
                            if(taxPercentage > 100) taxPercentage = 100.0
                            recalculate()
                        }
                    }
                    .setNegativeButton("HAPUS") { _, _ -> taxPercentage = 0.0; recalculate() }
                    .show()
            }
        }

        // Logic Tombol Diskon
        btnDisc.setOnClickListener {
            val layout = LinearLayout(this); layout.orientation = LinearLayout.VERTICAL; layout.setPadding(50, 40, 50, 10)
            val etInput = EditText(this); etInput.hint = "Angka (ex: 10 atau 5000)"; etInput.inputType = InputType.TYPE_CLASS_NUMBER
            if (discountInput > 0) etInput.setText(discountInput.toLong().toString())
            layout.addView(etInput)

            val rg = RadioGroup(this); rg.orientation = RadioGroup.HORIZONTAL; rg.setPadding(0, 20, 0, 0)
            val rbRp = RadioButton(this); rbRp.text = "Rp"; rbRp.id = 1001
            val rbPct = RadioButton(this); rbPct.text = "%"; rbPct.id = 1002
            rg.addView(rbRp); rg.addView(rbPct)

            if (discountType == "PERCENT") rg.check(rbPct.id) else rg.check(rbRp.id)
            layout.addView(rg)

            AlertDialog.Builder(this).setTitle("Atur Diskon").setView(layout)
                .setPositiveButton("TERAPKAN") { _, _ ->
                    val str = etInput.text.toString()
                    if (str.isNotEmpty()) {
                        discountInput = str.toDouble()
                        discountType = if (rg.checkedRadioButtonId == rbPct.id) "PERCENT" else "NOMINAL"
                        if (discountType == "PERCENT" && discountInput > 100) discountInput = 100.0
                        recalculate()
                    }
                }
                .setNegativeButton("HAPUS") { _, _ -> discountType = "NONE"; discountInput = 0.0; recalculate() }
                .show()
        }

        // Logic Uang Cepat
        btnExact.setOnClickListener { etReceived.setText(grandTotal.toInt().toString()) }
        btn20k.setOnClickListener { etReceived.setText("20000") }
        btn50k.setOnClickListener { etReceived.setText("50000") }
        btn100k.setOnClickListener { etReceived.setText("100000") }
        btnOther.setOnClickListener { etReceived.setText(""); etReceived.requestFocus() }
        btnBack.setOnClickListener { dialog.dismiss() }

        // 🔥 LOGIKA KONFIRMASI PEMBAYARAN
        btnConfirm.setOnClickListener {
            var method = spMethod.selectedItem.toString()
            val receivedStr = etReceived.text.toString()
            var cashReceived = if (receivedStr.isNotEmpty()) receivedStr.toDouble() else 0.0

            val customerName = etCustomer.text.toString().trim()
            val tableNumber = etTable.text.toString().trim()
            val noteRaw = etNote.text.toString().trim()

            // Validasi Piutang
            if (cbCredit.isChecked) {
                if (customerName.isEmpty()) {
                    etCustomer.error = "Nama Pelanggan WAJIB DIISI untuk Piutang!"
                    etCustomer.requestFocus()
                    return@setOnClickListener
                }
                method = "PIUTANG"
                if (cashReceived == 0.0) cashReceived = 0.0
            }

            var finalNote = noteRaw
            if (tableNumber.isNotEmpty()) finalNote += " | Meja: $tableNumber"
            if (customerName.isNotEmpty()) finalNote += " | An: $customerName"
            finalNote = finalNote.trim().removePrefix("|").trim()

            // Cek Uang Kurang
            if (!cbCredit.isChecked && method == "Tunai" && cashReceived < grandTotal) {
                Toast.makeText(this, "⚠️ Uang Kurang!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Jika Non-Tunai, Uang Masuk dianggap Pas
            if (method != "Tunai" && !cbCredit.isChecked) cashReceived = grandTotal

            val changeAmount = cashReceived - grandTotal

            // PROSES CHECKOUT
            viewModel.checkout(
                subtotal = currentSubtotal,
                discount = discValue,
                tax = taxValue,
                paymentMethod = method,
                cashReceived = cashReceived,
                changeAmount = changeAmount,
                note = finalNote,
                userId = 0 // Sesuaikan jika ada ID User login
            ) { transaction ->
                if (transaction != null) {
                    // Update Shift (Hanya Uang Tunai yang masuk Laci)
                    if (method == "Tunai") {
                        val currentCash = shiftPrefs.getFloat("CASH_SALES_TODAY_$currentUserKey", 0f)
                        val newTotal = currentCash + grandTotal.toFloat()
                        shiftPrefs.edit().putFloat("CASH_SALES_TODAY_$currentUserKey", newTotal).apply()
                    }

                    dialog.dismiss()

                    val printerMac = getSharedPreferences("store_prefs", Context.MODE_PRIVATE).getString("printer_mac", "")
                    if (!printerMac.isNullOrEmpty()) {
                        printStruk(transaction)
                        Toast.makeText(this, "Mencetak Struk...", Toast.LENGTH_SHORT).show()
                    }

                    val pesanSukses = if (cbCredit.isChecked) "Piutang Berhasil Dicatat" else if (changeAmount > 0) "Kembalian: ${formatRupiah(changeAmount)}" else "Lunas"

                    AlertDialog.Builder(this)
                        .setTitle("✅ Transaksi Berhasil")
                        .setMessage(pesanSukses)
                        .setPositiveButton("TUTUP", null)
                        .setNeutralButton("CETAK LAGI") { _, _ ->
                            if (!printerMac.isNullOrEmpty()) printStruk(transaction)
                        }
                        .show()
                } else {
                    Toast.makeText(this, "Gagal Transaksi", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Panggil recalculate di awal agar Pajak Default langsung terhitung
        recalculate()
        dialog.show()
    }

    private fun showCartPreview() {
        val currentCart = viewModel.cart.value ?: emptyList()
        if (currentCart.isEmpty()) {
            Toast.makeText(this, "Keranjang masih kosong", Toast.LENGTH_SHORT).show()
            return
        }

        // 🔥 GANTI: Gunakan variabel global 'cartDialog'
        cartDialog = android.app.Dialog(this, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
        cartDialog?.setContentView(R.layout.dialog_cart_preview)

        val container = cartDialog?.findViewById<LinearLayout>(R.id.llCartItems)
        val tvTotalTop = cartDialog?.findViewById<TextView>(R.id.tvPreviewTotalTop)
        val btnClose = cartDialog?.findViewById<View>(R.id.btnCloseCart)
        val btnAddMore = cartDialog?.findViewById<Button>(R.id.btnAddMore)
        val btnProceed = cartDialog?.findViewById<Button>(R.id.btnProceedToPay)

        tvTotalTop?.text = formatRupiah(viewModel.totalPrice.value ?: 0.0)

        container?.removeAllViews()

        for (item in currentCart) {
            val itemView = LayoutInflater.from(this).inflate(R.layout.item_cart_row, null)

            val tvName = itemView.findViewById<TextView>(R.id.tvCartName)
            // 🔥 INI TEXTVIEW BARU (Pastikan XML sudah diupdate ya)
            val tvDetail = itemView.findViewById<TextView>(R.id.tvCartVariantDetail)

            val tvPrice = itemView.findViewById<TextView>(R.id.tvCartPrice)
            val tvQty = itemView.findViewById<TextView>(R.id.tvCartQty)
            val tvSubtotal = itemView.findViewById<TextView>(R.id.tvCartSubtotal)

            val btnMinus = itemView.findViewById<View>(R.id.btnMinus)
            val btnPlus = itemView.findViewById<View>(R.id.btnPlus)
            val btnRemove = itemView.findViewById<View>(R.id.btnRemoveItem)

            // ==========================================
            // 🔥 LOGIKA BARU: MEMISAHKAN INDUK & TOPPING
            // ==========================================
            val fullName = item.name

            if (fullName.contains(" - ")) {
                // Jika nama ada pemisahnya (Contoh: "Nasi Goreng - Telur, Ati")
                val parts = fullName.split(" - ", limit = 2)

                // Bagian 1: "Nasi Goreng" (Judul Utama)
                tvName.text = parts[0]

                // Bagian 2: "Telur, Ati" -> Ubah jadi format list ke bawah
                // Hasil:
                // + Telur
                // + Ati
                val rawTopping = parts[1]
                val formattedTopping = "+ " + rawTopping.replace(", ", "\n+ ")

                tvDetail.text = formattedTopping
                tvDetail.visibility = View.VISIBLE
            } else {
                // Jika Produk Biasa (Tanpa Varian/Topping)
                tvName.text = fullName
                tvDetail.visibility = View.GONE
            }
            // ==========================================

            tvPrice.text = "@ ${formatRupiah(item.price)}"
            tvQty.text = item.stock.toString()
            tvSubtotal.text = formatRupiah(item.price * item.stock)

            btnPlus.setOnClickListener {
                // 1. Ambil data stok gudang yang paling segar
                val masterProduct = if (item.id < 0) {
                    fullList.find { it.id == item.parentId }
                } else {
                    fullList.find { it.id == item.id }
                }

                if (masterProduct != null) {
                    val prefs = getSharedPreferences("store_prefs", Context.MODE_PRIVATE)
                    val isStockSystemActive = prefs.getBoolean("use_stock_system", true)

                    if (isStockSystemActive) {
                        // 2. Ambil data keranjang TERBARU langsung dari ViewModel saat diklik
                        val currentCart = viewModel.cart.value ?: emptyList()

                        // Hitung berapa total barang ini yang SUDAH ada di keranjang
                        val qtyBarangIniDiKeranjang = currentCart.filter {
                            it.id == masterProduct.id || it.parentId == masterProduct.id
                        }.sumOf { it.stock }

                        // 3. LOGIKA CEK: Jika yang di keranjang sudah SAMA atau LEBIH dari stok gudang
                        if (qtyBarangIniDiKeranjang >= masterProduct.stock) {
                            Toast.makeText(this, "⚠️ Stok Mentok! Sisa gudang: ${masterProduct.stock}", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                    }

                    // 4. 🔥 SOLUSI KLIK PERTAMA:
                    // Kita kirim 'masterProduct' (data asli gudang) tapi ID-nya pakai ID item keranjang saat ini
                    // Agar ViewModel tidak bingung mana yang mau ditambah.
                    val productToSend = item.copy(stock = masterProduct.stock)

                    viewModel.addToCart(productToSend) {
                        // 5. REFRESH DIALOG
                        cartDialog?.dismiss()
                        showCartPreview()
                    }
                }
            }

// --- TOMBOL MINUS (-) ---
            btnMinus.setOnClickListener {
                if (item.stock > 1) {
                    viewModel.decreaseCartItem(item)
                } else {
                    viewModel.removeCartItem(item)
                }

                // 🔥 Beri jeda sedikit atau pastikan terpanggil agar visual "kedip" langsung muncul
                cartDialog?.dismiss()
                showCartPreview()
            }

// --- TOMBOL HAPUS (Sampah) ---
            btnRemove.setOnClickListener {
                // 1. Perintahkan ViewModel hapus dari database
                viewModel.removeCartItem(item)

                // 2. Langsung hapus baris (View) dari layar saat itu juga
                container?.removeView(itemView)

                // 3. Update total harga di header biru
                updateTotalHeader()

                // 4. Tutup dialog jika sudah tidak ada barang lagi
                if (container?.childCount == 0) {
                    cartDialog?.dismiss()
                    Toast.makeText(this, "Keranjang Kosong", Toast.LENGTH_SHORT).show()
                }
            }

            container?.addView(itemView)
        }

        btnClose?.setOnClickListener { cartDialog?.dismiss() }
        btnAddMore?.setOnClickListener { cartDialog?.dismiss() }
        btnProceed?.setOnClickListener { cartDialog?.dismiss(); showPaymentDialog() }

        cartDialog?.show()
    }

    // 🔥 LOGIKA LONG CLICK (SMART SEARCH)
    private fun showCartActionDialog(product: Product) {
        // 1. Cari item di keranjang yang berhubungan dengan produk ini
        val currentCart = viewModel.cart.value ?: emptyList()

        // Cari yang ID-nya SAMA (Produk Biasa) ATAU ParentID-nya SAMA (Varian/Topping)
        val relatedItems = currentCart.filter { it.id == product.id || it.parentId == product.id }

        if (relatedItems.isEmpty()) {
            Toast.makeText(this, "Item ini belum ada di keranjang", Toast.LENGTH_SHORT).show()
            return
        }

        // 2. KASUS A: Cuma ada 1 jenis item yang cocok (Misal: Cuma Nasi Goreng Telur)
        if (relatedItems.size == 1) {
            val targetItem = relatedItems[0]
            showActionOptions(targetItem)
        }
        // 3. KASUS B: Ada banyak varian (Misal: Ada NG Telur DAN NG Ati di keranjang)
        else {
            // Tampilkan pilihan varian mana yang mau diedit
            val names = relatedItems.map { "${it.name} (Qty: ${it.stock})" }.toTypedArray()

            AlertDialog.Builder(this)
                .setTitle("Pilih Item yang Mau Diedit")
                .setItems(names) { _, which ->
                    val selectedTarget = relatedItems[which]
                    showActionOptions(selectedTarget)
                }
                .show()
        }
    }

    // Helper biar kodingan rapi
    private fun showActionOptions(targetItem: Product) {
        val options = arrayOf("➖ Kurangi 1", "❌ Batal Item")
        AlertDialog.Builder(this)
            .setTitle(targetItem.name)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> viewModel.decreaseCartItem(targetItem)
                    1 -> viewModel.removeCartItem(targetItem)
                }
            }
            .show()
    }

    private fun setupCategoryButtons(categories: List<Category>) {
        llCategories.removeAllViews()
        addCategoryButton("Semua")
        for (cat in categories) addCategoryButton(cat.name)
    }

    private fun addCategoryButton(categoryName: String) {
        val btn = TextView(this)
        btn.text = categoryName
        btn.setPadding(40, 20, 40, 20)
        btn.gravity = Gravity.CENTER
        val params = LinearLayout.LayoutParams(-2, -2)
        params.setMargins(0, 0, 16, 0)
        btn.layoutParams = params

        if (selectedCategory == categoryName) {
            btn.setBackgroundResource(R.drawable.bg_rounded_white)
            btn.background.setTint(Color.WHITE)
            btn.setTextColor(Color.parseColor("#1976D2"))
            btn.setTypeface(null, android.graphics.Typeface.BOLD)
            btn.elevation = 8f
        } else {
            btn.setBackgroundResource(R.drawable.bg_rounded_white)
            btn.background.setTint(Color.WHITE)
            btn.setTextColor(Color.parseColor("#757575"))
            btn.elevation = 0f
        }

        btn.setOnClickListener {
            selectedCategory = categoryName
            setupCategoryButtons(viewModel.allCategories.value ?: emptyList())
            filterData()
        }
        llCategories.addView(btn)
    }

    private fun filterData() {
        val query = svSearch.query.toString().lowercase()
        val filtered = fullList.filter { product ->
            val matchName = product.name.lowercase().contains(query)
            val matchCategory = if (selectedCategory == "Semua") true else product.category == selectedCategory
            matchName && matchCategory
        }
        productAdapter.submitList(filtered)
    }

    private fun connectPrinterSilent(address: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 101)
                return
            }
        }
        printerHelper.connectPrinter(address) {}
    }

    private fun formatRupiah(number: Double): String = String.format(Locale("id", "ID"), "Rp %,d", number.toLong()).replace(',', '.')

    private fun printStruk(trx: com.sysdos.kasirpintar.data.model.Transaction) {
        val prefs = getSharedPreferences("store_prefs", Context.MODE_PRIVATE)
        val session = getSharedPreferences("session_kasir", Context.MODE_PRIVATE)
        val targetMac = prefs.getString("printer_mac", "")
        if (targetMac.isNullOrEmpty()) return

        Thread {
            var socket: android.bluetooth.BluetoothSocket? = null
            var isConnected = false
            var attempt = 0
            val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()

            // 1. Coba Konek Bluetooth
            while (attempt < 3 && !isConnected) {
                try {
                    attempt++
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return@Thread
                    val device = bluetoothAdapter.getRemoteDevice(targetMac)
                    val uuid = java.util.UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                    socket = device.createRfcommSocketToServiceRecord(uuid)
                    socket.connect()
                    isConnected = true
                } catch (e: Exception) { try { socket?.close() } catch (x: Exception) {}; if (attempt < 3) Thread.sleep(1000) }
            }

            if (!isConnected) return@Thread

            try {
                val outputStream = socket!!.outputStream
                val storeName = prefs.getString("name", "Toko Saya")
                val storeAddress = prefs.getString("address", "Indonesia")
                val storePhone = prefs.getString("phone", "")
                val username = session.getString("username", "Admin")
                val kasirName = session.getString("fullname", username)

                val dateStr = java.text.SimpleDateFormat("dd/MM/yy HH:mm", Locale.getDefault()).format(java.util.Date(trx.timestamp))

                // ==========================================
                // 🔥 LOGIKA PEMISAHAN DATA BARU (REVISI) 🔥
                // ==========================================

                // ItemsSummary contoh: "Nasi|1|1000|1000;Teh|1|500|500 || Meja: 1 | An: Budi"
                val rawSummary = trx.itemsSummary
                val productList = ArrayList<String>()
                var infoPelanggan = ""

                if (rawSummary.contains(" || ")) {
                    val parts = rawSummary.split(" || ")
                    // Bagian 1: Produk
                    val productsPart = parts[0]
                    // Bagian 2: Info (Meja/An)
                    if (parts.size > 1) {
                        infoPelanggan = parts[1]
                    }

                    // Pecah produk berdasarkan ";"
                    val items = productsPart.split(";")
                    for (item in items) {
                        if (item.contains("|")) {
                            productList.add(item)
                        }
                    }
                } else {
                    // Jika tidak ada info tambahan, berarti semua adalah produk
                    val items = rawSummary.split(";")
                    for (item in items) {
                        if (item.contains("|")) {
                            productList.add(item)
                        }
                    }
                }

                // ==========================================
                // 🖨️ MULAI CETAK
                // ==========================================
                val p = StringBuilder()

                // Header Toko
                p.append("\u001B\u0061\u0001\u001B\u0045\u0001$storeName\n\u001B\u0045\u0000$storeAddress\n")
                if (!storePhone.isNullOrEmpty()) p.append("Telp: $storePhone\n")
                p.append("--------------------------------\n")

                // Info Transaksi
                p.append("\u001B\u0061\u0000") // Rata Kiri
                p.append("ID   : #${trx.id}\n")
                p.append("Tgl  : $dateStr\n")
                p.append("Kasir: $kasirName\n")

                // 🔥 CETAK INFO MEJA & PELANGGAN DISINI (DI HEADER) 🔥
                if (infoPelanggan.isNotEmpty()) {
                    // Ubah format "Meja: 1 | An: Budi" menjadi baris baru agar rapi
                    val formattedInfo = infoPelanggan.replace(" | ", "\n").trim()
                    p.append("\u001B\u0045\u0001") // Bold Nyala
                    p.append("$formattedInfo\n")
                    p.append("\u001B\u0045\u0000") // Bold Mati
                }

                p.append("--------------------------------\n")

                // Fungsi Helper Baris Rata Kanan Kiri
                fun row(kiri: String, valDbl: Double, isMinus: Boolean = false): String {
                    val formattedVal = String.format(Locale("id","ID"), "Rp %,d", valDbl.toLong()).replace(',', '.')
                    val kanan = if(isMinus) "-$formattedVal" else formattedVal
                    val maxChars = 32
                    val maxKiri = maxChars - kanan.length - 1
                    val textKiri = if (kiri.length > maxKiri) kiri.substring(0, maxKiri) else kiri
                    val sisa = maxChars - textKiri.length - kanan.length
                    return "$textKiri${" ".repeat(if(sisa>0) sisa else 1)}$kanan\n"
                }

                // Loop Hanya Produk Murni
                for (itemStr in productList) {
                    val parts = itemStr.split("|")
                    // Format: Nama|Qty|Harga|Total
                    if (parts.size >= 4) {
                        val fullName = parts[0]

                        // 🔥 LOGIKA BARU: TURUNKAN VARIAN KE BAWAH BIAR RAPI 🔥
                        if (fullName.contains(" - ")) {
                            val nameParts = fullName.split(" - ", limit = 2)
                            p.append("${nameParts[0]}\n")         // Baris 1: Nama Produk Utama
                            p.append("  (${nameParts[1]})\n")     // Baris 2: Varian (Menjorok & Pakai Kurung)
                        } else {
                            p.append("$fullName\n")               // Produk Biasa
                        }

                        // Baris Harga & Qty
                        // Tips: Kita replace "Rp " jadi kosong biar muat di kertas kecil
                        val hargaSatuan = formatRupiah(parts[2].toDouble()).replace("Rp ", "")
                        p.append(row("  ${parts[1]} x $hargaSatuan", parts[3].toDouble()))
                    }
                }

                p.append("--------------------------------\n")

                // Totalan
                p.append(row("Subtotal", trx.subtotal))
                if (trx.discount > 0) p.append(row("Diskon", trx.discount, true))
                if (trx.tax > 0) p.append(row("Pajak", trx.tax))

                p.append("--------------------------------\n")
                p.append("\u001B\u0045\u0001${row("TOTAL", trx.totalAmount)}\u001B\u0045\u0000") // Total Bold

                if (trx.paymentMethod.contains("Tunai")) {
                    p.append(row("Tunai", trx.cashReceived))
                    p.append(row("Kembali", trx.changeAmount))
                } else {
                    p.append("Metode: ${trx.paymentMethod}\n")
                }

                p.append("\u001B\u0061\u0001--------------------------------\n")
                p.append("${prefs.getString("email", "Terima Kasih!")}\n\n\n")
                p.append("Powered by Sysdos POS\n\n\n") // Feed lines

                outputStream.write(p.toString().toByteArray())
                outputStream.flush()
                Thread.sleep(1500)
                socket.close()

            } catch (e: Exception) { try { socket?.close() } catch (e: Exception) {} }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { printerHelper.close() } catch (e: Exception) {}
    }
    // 🔥 LOGIKA KLIK PRODUK (SUDAH SUPPORT STOK LOS)
    private fun handleProductClick(product: Product) {

        // 1. AMBIL SETTINGAN STOK DULU
        val prefs = getSharedPreferences("store_prefs", Context.MODE_PRIVATE)
        val isStockSystemActive = prefs.getBoolean("use_stock_system", true) // Default ON

        // 2. HANYA CEK STOK JIKA SISTEM AKTIF (SAKLAR NYALA)
        if (isStockSystemActive) {

            // Cek Stok Gudang (Database)
            if (product.stock <= 0) {
                Toast.makeText(this, "❌ Stok Gudang Kosong!", Toast.LENGTH_SHORT).show()
                return // Stop!
            }

            // Cek Sisa Stok (Dikurangi yang sudah ada di Keranjang)
            val currentCart = viewModel.cart.value ?: emptyList()
            val qtyInCart = currentCart.filter {
                it.id == product.id || it.parentId == product.id
            }.sumOf { it.stock }

            if (product.stock - qtyInCart <= 0) {
                Toast.makeText(this, "❌ Stok Habis! (Semua sudah di keranjang)", Toast.LENGTH_SHORT).show()
                return // Stop!
            }
        }
        // JIKA SAKLAR OFF (LOS), LANGSUNG LEWAT SINI TANPA DICEGAT

        // --- Lolos Pengecekan? Baru lanjut ke Varian/Cart ---

        // Ambil Data Varian
        val variantsLiveData = viewModel.getVariants(product.id)

        var hasObserved = false
        val observer = object : androidx.lifecycle.Observer<List<com.sysdos.kasirpintar.data.model.ProductVariant>> {
            override fun onChanged(variants: List<com.sysdos.kasirpintar.data.model.ProductVariant>) {
                if (hasObserved) return
                hasObserved = true
                variantsLiveData.removeObserver(this)

                if (!variants.isNullOrEmpty()) {
                    // ADA VARIAN -> Tampilkan Popup
                    showVariantDialog(product, variants)
                } else {
                    // TIDAK ADA VARIAN -> Masuk Cart
                    viewModel.addToCart(product) { msg ->
                        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        variantsLiveData.observe(this, observer)
    }

    // 🔥 POPUP PILIH VARIAN
    // 1️⃣ TAMPILKAN POPUP VARIAN (MODE TOPPING / SATU BARIS)
    private fun showVariantDialog(product: Product, variants: List<com.sysdos.kasirpintar.data.model.ProductVariant>) {
        val variantNames = variants.map {
            "${it.variantName}  (+${formatRupiah(it.variantPrice)})"
        }.toTypedArray()

        val checkedItems = BooleanArray(variants.size)

        AlertDialog.Builder(this)
            .setTitle("Pilih Tambahan / Topping: ${product.name}")
            .setMultiChoiceItems(variantNames, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton("MASUKKAN KERANJANG") { _, _ ->
                // Kumpulkan semua varian yang dicentang ke dalam list
                val selectedVariants = ArrayList<com.sysdos.kasirpintar.data.model.ProductVariant>()
                for (i in checkedItems.indices) {
                    if (checkedItems[i]) selectedVariants.add(variants[i])
                }

                // Panggil fungsi penggabungan
                addModifierToCart(product, selectedVariants)
            }
            .setNegativeButton("Batal", null)
            .show()
    }
    private fun addVariantToCart(parentProduct: Product, variant: com.sysdos.kasirpintar.data.model.ProductVariant, showToast: Boolean = true) {
        // Hitung Harga Total (Induk + Varian)
        val totalHarga = parentProduct.price + variant.variantPrice

        // Buat Produk Sementara dengan ID Minus
        val virtualProduct = parentProduct.copy(
            id = -variant.variantId, // ID Minus biar unik
            name = "${parentProduct.name} - ${variant.variantName}",
            price = totalHarga,
            stock = 9999, // Bypass stok varian
            parentId = parentProduct.id // 🔥 PENTING: Parent ID buat Badge Merah
        )

        // Cek Stok Induk (Strict Mode)
        if (parentProduct.stock <= 0) {
            if (showToast) Toast.makeText(this, "❌ Stok Induk Kosong!", Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.addToCart(virtualProduct) { msg ->
            // Tampilkan toast hanya jika diminta (single add)
            if (showToast) Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }
    // 2️⃣ FUNGSI BARU: GABUNG SEMUA VARIAN JADI SATU PRODUK
    private fun addModifierToCart(parentProduct: Product, selectedVariants: List<com.sysdos.kasirpintar.data.model.ProductVariant>) {
        if (selectedVariants.isEmpty()) {
            viewModel.addToCart(parentProduct) { msg -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
            return
        }

        // 1. Gabung Nama dengan Harga
        val combinedNameBuilder = StringBuilder("${parentProduct.name} - ")
        var additionalPrice = 0.0

        for ((index, variant) in selectedVariants.withIndex()) {
            combinedNameBuilder.append(variant.variantName)
            if (variant.variantPrice > 0) {
                combinedNameBuilder.append(" (+${formatRupiah(variant.variantPrice)})")
            }
            if (index < selectedVariants.size - 1) combinedNameBuilder.append(", ")
            additionalPrice += variant.variantPrice
        }

        val finalName = combinedNameBuilder.toString()
        val finalPrice = parentProduct.price + additionalPrice

        // 2. Bikin ID Unik
        var uniqueId = finalName.hashCode()
        if (uniqueId > 0) uniqueId = -uniqueId
        if (uniqueId == 0) uniqueId = -1

        // 3. Buat Produk Virtual
        val virtualProduct = parentProduct.copy(
            id = uniqueId,
            name = finalName,
            price = finalPrice,
            stock = 9999, // Bypass stok topping
            parentId = parentProduct.id
        )

        // 4. CEK STOK INDUK (DENGAN LOGIKA STOK LOS)
        val prefs = getSharedPreferences("store_prefs", Context.MODE_PRIVATE)
        val isStockSystemActive = prefs.getBoolean("use_stock_system", true)

        // Blokir hanya jika Saklar ON DAN Stok Habis
        if (isStockSystemActive && parentProduct.stock <= 0) {
            Toast.makeText(this, "❌ Stok Induk Kosong!", Toast.LENGTH_SHORT).show()
        } else {
            // Kalau Saklar OFF, Hajar terus masuk keranjang
            viewModel.addToCart(virtualProduct) { msg ->
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
