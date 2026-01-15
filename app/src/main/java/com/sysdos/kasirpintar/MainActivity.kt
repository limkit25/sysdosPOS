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

    // PREFERENCE UNTUK SHIFT (MODAL & SESI)
    private lateinit var session: SharedPreferences
    private lateinit var shiftPrefs: SharedPreferences

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
                viewModel.addToCart(product) { msg -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
            },
            onItemLongClick = { product -> showCartActionDialog(product) }
        )

        rvProducts.layoutManager = LinearLayoutManager(this)
        rvProducts.adapter = productAdapter

        // 4. OBSERVE DATA
        viewModel.allProducts.observe(this) { products ->
            fullList = products
            filterData()
        }
        viewModel.allCategories.observe(this) { categories ->
            setupCategoryButtons(categories ?: emptyList())
        }
        viewModel.cart.observe(this) {
            productAdapter.updateCartCounts(it)
            val itemCount = it.sumOf { item -> item.stock }
            tvCartCount.text = "$itemCount Item"
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

        // 7. HANDLE BACK BUTTON
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })

        // 8. CEK STATUS SHIFT
        checkShiftStatus()

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


    // ==========================================
    // 🔥 LOGIKA SHIFT SYSTEM (PER USER)
    // ==========================================

    private fun checkShiftStatus() {
        val isShiftOpen = shiftPrefs.getBoolean("IS_OPEN_$currentUserKey", false)
        if (!isShiftOpen) {
            showOpenShiftDialog()
        }
    }

    private fun showOpenShiftDialog() {
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(64, 40, 64, 10)

        val etModal = EditText(this)
        etModal.hint = "Rp 0"
        etModal.inputType = InputType.TYPE_CLASS_NUMBER
        layout.addView(etModal)

        AlertDialog.Builder(this)
            .setTitle("☀️ Buka Shift (Input Modal)")
            .setMessage("Halo $currentUserKey! Masukkan modal awal:")
            .setView(layout)
            .setCancelable(false)
            .setPositiveButton("BUKA KASIR") { _, _ ->
                val modalStr = etModal.text.toString()
                if (modalStr.isNotEmpty()) {
                    val modal = modalStr.toDouble()

                    shiftPrefs.edit()
                        .putBoolean("IS_OPEN_$currentUserKey", true)
                        .putFloat("MODAL_AWAL_$currentUserKey", modal.toFloat())
                        .putLong("START_TIME_$currentUserKey", System.currentTimeMillis())
                        .putFloat("CASH_SALES_TODAY_$currentUserKey", 0f)
                        .apply()

                    viewModel.openShift(currentUserKey, modal)
                    Toast.makeText(this, "Shift Dibuka! Semangat 🚀", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Isi 0 jika tanpa modal", Toast.LENGTH_SHORT).show()
                    showOpenShiftDialog()
                }
            }
            .setNegativeButton("Keluar App") { _, _ -> finish() }
            .show()
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
        val cbCredit = dialogView.findViewById<CheckBox>(R.id.cbCredit) // 🔥 Checkbox Piutang
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

        // Logic Diskon & Pajak
        var discountType = "NONE"
        var discountInput = 0.0
        var discValue = 0.0
        var taxPercentage = 0.0
        var taxValue = 0.0

        val methods = arrayOf("Tunai", "QRIS", "Debit", "Transfer")
        spMethod.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, methods)

        // --- FUNGSI HITUNG ULANG ---
        fun recalculate() {
            discValue = when (discountType) {
                "PERCENT" -> currentSubtotal * (discountInput / 100)
                "NOMINAL" -> discountInput
                else -> 0.0
            }

            val afterDisc = if (currentSubtotal > discValue) currentSubtotal - discValue else 0.0
            taxValue = afterDisc * (taxPercentage / 100)
            grandTotal = afterDisc + taxValue

            tvTitle.text = "Total : ${formatRupiah(grandTotal)}"

            // Update UI Tombol
            val activeBg = R.drawable.bg_solid_primary
            val inactiveBg = R.drawable.bg_outline_primary

            val isTaxOn = taxPercentage > 0
            btnTax.setBackgroundResource(if (isTaxOn) activeBg else inactiveBg)
            btnTax.setTextColor(if (isTaxOn) Color.WHITE else Color.parseColor("#1976D2"))
            btnTax.text = if (isTaxOn) "Tax : ${taxPercentage.toInt()}%" else "Tax : 0%"

            val isDiscOn = discountType != "NONE"
            btnDisc.setBackgroundResource(if (isDiscOn) activeBg else inactiveBg)
            btnDisc.setTextColor(if (isDiscOn) Color.WHITE else Color.parseColor("#1976D2"))
            btnDisc.text = when (discountType) {
                "PERCENT" -> "Disc : ${discountInput.toInt()}%"
                "NOMINAL" -> "Disc : ${formatRupiah(discountInput)}"
                else -> "Disc : 0%"
            }

            // Update Field Uang Masuk jika bukan tunai atau jika Piutang
            if (cbCredit.isChecked) {
                etReceived.setText("0") // Kalau hutang, uang masuk 0 (atau DP)
            } else if (spMethod.selectedItem?.toString() != "Tunai") {
                etReceived.setText(grandTotal.toInt().toString())
            }
        }

        // 🔥 LOGIKA CHECKBOX PIUTANG (Saat diklik)
        cbCredit.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // 1. Fokus ke Nama Pelanggan (Wajib Diisi)
                etCustomer.requestFocus()
                // Munculkan Keyboard
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.showSoftInput(etCustomer, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)

                etCustomer.error = "Wajib diisi untuk Piutang"
                etCustomer.hint = "Nama Penghutang (Wajib)"

                // 2. Disable Input Uang (Default 0 / Belum Bayar)
                etReceived.setText("0")
                gridQuickCash.visibility = View.GONE
                spMethod.isEnabled = false // Kunci metode pembayaran
            } else {
                // Reset jika batal piutang
                etCustomer.hint = "Pelanggan"
                etCustomer.error = null
                etReceived.isEnabled = true
                spMethod.isEnabled = true

                // Balikin tampilan sesuai metode bayar
                if (spMethod.selectedItem.toString() == "Tunai") {
                    gridQuickCash.visibility = View.VISIBLE
                    etReceived.setText("0")
                } else {
                    etReceived.setText(grandTotal.toInt().toString())
                }
            }
        }

        // Logic Spinner Metode Bayar
        spMethod.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (cbCredit.isChecked) return // Jangan ubah apa2 kalau lagi mode piutang

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

        // Logic Tombol Pajak & Diskon (Popup)
        btnTax.setOnClickListener {
            val layout = LinearLayout(this); layout.orientation = LinearLayout.VERTICAL; layout.setPadding(50, 40, 50, 10)
            val etInput = EditText(this); etInput.hint = "Persen Pajak (ex: 11)"; etInput.inputType = InputType.TYPE_CLASS_NUMBER
            if (taxPercentage > 0) etInput.setText(taxPercentage.toInt().toString())
            layout.addView(etInput)

            AlertDialog.Builder(this).setTitle("Atur Pajak (%)").setView(layout)
                .setPositiveButton("TERAPKAN") { _, _ ->
                    val str = etInput.text.toString()
                    if (str.isNotEmpty()) { taxPercentage = str.toDouble(); if(taxPercentage>100) taxPercentage=100.0; recalculate() }
                }
                .setNegativeButton("HAPUS") { _, _ -> taxPercentage = 0.0; recalculate() }
                .show()
        }

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

        // 🔥 LOGIKA TOMBOL KONFIRMASI (BAYAR)
        btnConfirm.setOnClickListener {
            var method = spMethod.selectedItem.toString()
            val receivedStr = etReceived.text.toString()
            var cashReceived = if (receivedStr.isNotEmpty()) receivedStr.toDouble() else 0.0

            val customerName = etCustomer.text.toString().trim()
            val tableNumber = etTable.text.toString().trim()
            val noteRaw = etNote.text.toString().trim()

            // 🔥 VALIDASI KHUSUS PIUTANG
            if (cbCredit.isChecked) {
                if (customerName.isEmpty()) {
                    etCustomer.error = "Nama Pelanggan WAJIB DIISI untuk Piutang!"
                    etCustomer.requestFocus()
                    return@setOnClickListener
                }
                // Ubah metode bayar jadi "PIUTANG" agar jelas di laporan
                method = "PIUTANG"

                // Uang diterima dianggap 0 kalau Piutang Full
                // (Kecuali Anda mau fitur DP, tapi sementara kita buat 0 dulu biar simpel)
                if (cashReceived == 0.0) cashReceived = 0.0
            }

            var finalNote = noteRaw
            if (tableNumber.isNotEmpty()) finalNote += " | Meja: $tableNumber"
            if (customerName.isNotEmpty()) finalNote += " | An: $customerName"
            finalNote = finalNote.trim().removePrefix("|").trim()

            // Cek Uang Kurang (Hanya jika BUKAN Piutang)
            if (!cbCredit.isChecked && method == "Tunai" && cashReceived < grandTotal) {
                Toast.makeText(this, "⚠️ Uang Kurang!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

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
                userId = 0
            ) { transaction ->
                if (transaction != null) {
                    // Update Omzet Shift (Hanya Tunai)
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

                    // Tampilkan Info Sukses
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

        recalculate()
        dialog.show()
    }

    private fun showCartPreview() {
        val currentCart = viewModel.cart.value ?: emptyList()
        if (currentCart.isEmpty()) {
            Toast.makeText(this, "Keranjang masih kosong", Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = android.app.Dialog(this, android.R.style.Theme_Material_Light_NoActionBar_Fullscreen)
        dialog.setContentView(R.layout.dialog_cart_preview)

        val container = dialog.findViewById<LinearLayout>(R.id.llCartItems)
        val tvTotalTop = dialog.findViewById<TextView>(R.id.tvPreviewTotalTop)
        val btnClose = dialog.findViewById<View>(R.id.btnCloseCart)
        val btnAddMore = dialog.findViewById<Button>(R.id.btnAddMore)
        val btnProceed = dialog.findViewById<Button>(R.id.btnProceedToPay)

        tvTotalTop.text = formatRupiah(viewModel.totalPrice.value ?: 0.0)

        for (item in currentCart) {
            val itemView = LayoutInflater.from(this).inflate(R.layout.item_cart_row, null)
            val tvName = itemView.findViewById<TextView>(R.id.tvCartName)
            val tvPrice = itemView.findViewById<TextView>(R.id.tvCartPrice)
            val tvQty = itemView.findViewById<TextView>(R.id.tvCartQty)
            val tvSubtotal = itemView.findViewById<TextView>(R.id.tvCartSubtotal)
            val btnMinus = itemView.findViewById<View>(R.id.btnMinus)
            val btnPlus = itemView.findViewById<View>(R.id.btnPlus)
            val btnRemove = itemView.findViewById<View>(R.id.btnRemoveItem)

            tvName.text = item.name
            tvPrice.text = "@ ${formatRupiah(item.price)}"
            tvQty.text = item.stock.toString()
            tvSubtotal.text = formatRupiah(item.price * item.stock)

            btnPlus.setOnClickListener {
                val originalProduct = fullList.find { it.id == item.id }
                if (originalProduct != null) {
                    viewModel.addToCart(originalProduct) { }
                    dialog.dismiss(); showCartPreview()
                }
            }
            btnMinus.setOnClickListener {
                viewModel.decreaseCartItem(item)
                dialog.dismiss(); showCartPreview()
            }
            (btnMinus as? ImageButton)?.setImageResource(android.R.drawable.btn_minus)

            btnRemove.setOnClickListener {
                viewModel.removeCartItem(item)
                dialog.dismiss(); showCartPreview()
            }
            container.addView(itemView)
        }

        btnClose.setOnClickListener { dialog.dismiss() }
        btnAddMore.setOnClickListener { dialog.dismiss() }
        btnProceed.setOnClickListener { dialog.dismiss(); showPaymentDialog() }
        dialog.show()
    }

    private fun showCartActionDialog(product: Product) {
        val options = arrayOf("➖ Kurangi 1", "❌ Batal Item")
        AlertDialog.Builder(this).setTitle(product.name).setItems(options) { _, which ->
            when (which) {
                0 -> viewModel.decreaseCartItem(product)
                1 -> viewModel.removeCartItem(product)
            }
        }.show()
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
                val kasirName = session.getString("username", "Admin")
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
                    // Cek size minimal 4 agar tidak crash
                    if (parts.size >= 4) {
                        p.append("${parts[0]}\n")
                        p.append(row("  ${parts[1]} x ${formatRupiah(parts[2].toDouble())}", parts[3].toDouble()))
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
}